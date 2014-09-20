

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.OutputStreamWriter;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.Properties;
import java.util.Vector;

import org.kapott.hbci.GV.GVDauerList;
import org.kapott.hbci.GV.HBCIJob;
import org.kapott.hbci.GV_Result.GVRAccInfo;
import org.kapott.hbci.GV_Result.GVRDauerList;
import org.kapott.hbci.GV_Result.GVRDauerNew;
import org.kapott.hbci.GV_Result.GVRKKSaldoReq;
import org.kapott.hbci.GV_Result.GVRKKSettleList;
import org.kapott.hbci.GV_Result.GVRKKSettleReq;
import org.kapott.hbci.GV_Result.GVRKKUms;
import org.kapott.hbci.GV_Result.GVRKUms;
import org.kapott.hbci.GV_Result.GVRSaldoReq;
import org.kapott.hbci.GV_Result.GVRTANMediaList;
import org.kapott.hbci.GV_Result.GVRTermUebList;
import org.kapott.hbci.GV_Result.HBCIJobResult;
import org.kapott.hbci.callback.HBCICallbackConsole;
import org.kapott.hbci.exceptions.AbortedException;
import org.kapott.hbci.exceptions.HBCI_Exception;
import org.kapott.hbci.exceptions.InvalidPassphraseException;
import org.kapott.hbci.manager.HBCIHandler;
import org.kapott.hbci.manager.HBCIInstitute;
import org.kapott.hbci.manager.HBCIKernelImpl;
import org.kapott.hbci.manager.HBCIUtils;
import org.kapott.hbci.manager.HBCIUtilsInternal;
import org.kapott.hbci.passport.AbstractHBCIPassport;
import org.kapott.hbci.passport.AbstractPinTanPassport;
import org.kapott.hbci.passport.HBCIPassport;
import org.kapott.hbci.passport.HBCIPassportDDV;
import org.kapott.hbci.passport.HBCIPassportPinTan;
import org.kapott.hbci.status.HBCIDialogStatus;
import org.kapott.hbci.status.HBCIExecStatus;
import org.kapott.hbci.structures.Konto;
import org.kapott.hbci.structures.Value;
import org.kapott.hbci.swift.DTAUS;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;


@SuppressWarnings(value={"unchecked", "rawtypes"})


public class HBCIServer {
	
	public static final int ERR_ABORTED = 0;
	public static final int ERR_GENERIC = 1;
	public static final int ERR_WRONG_PASSWD = 2;
	public static final int ERR_MISS_PARAM = 3;
	public static final int ERR_MISS_USER = 4;
	public static final int ERR_MISS_ACCOUNT = 5;
	public static final int ERR_WRONG_COMMAND = 6;
	public static final int ERR_MISS_SEPA_INFO = 7;
	
    private BufferedReader 	in;
    private BufferedWriter 	out;
    private StringBuffer 	xmlBuf;
    private Properties 		map;
    private Properties 		hbciHandlers;
    private Properties 		accounts;            // account key: userID + BLZ(account) + accountNumber + subNumber
    public  String 			passportPath;
    private String			ddvLibPath;
    private Properties 		countryInfos;
    private XmlGen 			xmlGen;
    private boolean			suppressErrors;
    
    private static HBCIServer server;
    
    HBCIServer() throws UnsupportedEncodingException  {
		countryInfos = new Properties();
		hbciHandlers = new Properties();
		accounts = new Properties();
		map = new Properties();
		suppressErrors = false;
	
		in = new BufferedReader(new InputStreamReader(System.in, "UTF-8"));
		out = new BufferedWriter(new OutputStreamWriter(System.out, "UTF-8"));			
    }
    
	//------------------------------ START CALLBACK ---------------------------------------------------
	
	private static class MyCallback	extends HBCICallbackConsole
	{
		public synchronized void status(HBCIPassport passport, int statusTag, Object[] o) 
		{
		// disable status output
		}

		public void log(String msg,int level,Date date,StackTraceElement trace) {
			try {
				if(server.suppressErrors == true && level <= HBCIUtils.LOG_ERR) {
					level = HBCIUtils.LOG_WARN;
				}
				server.out.write("<log level=\""+Integer.toString(level)+"\"><![CDATA[");
				server.out.write(msg);
				server.out.write("]]></log>\n.");
				server.out.flush();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		
		public String callbackClient(HBCIPassport pp, String command, String msg, String def, int reason, int type) throws IOException {
			server.out.write("<callback command=\""+command+"\">");
			if(pp.getBLZ() != null) {
				server.out.write("<bankCode>"+pp.getBLZ()+"</bankCode>");				
			}
			if(pp.getUserId() != null) {
				server.out.write("<userId>"+pp.getUserId()+"</userId>");
			}
			if(msg != null) {
				server.out.write("<message><![CDATA["+msg+"]]></message>");				
			}
			if(def != null) {
				server.out.write("<proposal>"+def+"</proposal>");				
			}
			server.out.write("<reason>"+Integer.toString(reason)+"</reason>");
			server.out.write("<type>"+Integer.toString(type)+"</type>");
			server.out.write("</callback>.");
			server.out.flush();
			
			String res = server.in.readLine();
			return res;
		}

		
	    public void callback(HBCIPassport passport, int reason, String msg, int datatype, StringBuffer retData) 
	    {
	        try {
	            String st = null;
	            String def = null;
	            if(retData != null) def = retData.toString(); else def = "";
	            
	            switch(reason) {
	               	case NEED_COUNTRY: 			st = "DE"; break;
	                case NEED_BLZ: 				st = (String)server.map.get("userBankCode"); break;
	                case NEED_HOST: 			st = (String)server.map.get("host"); break;
	                case NEED_PORT: 			st = (String)server.map.get("port"); break;
	                case NEED_FILTER: 			st = (String)server.map.get("filter"); break;
	                case NEED_USERID: 			st = (String)server.map.get("userId"); break;
	                case NEED_CUSTOMERID: 		st = (String)server.map.get("customerId"); break;
	                case NEED_PASSPHRASE_LOAD:  st = "PecuniaData"; break;
	                	/*
	                	if(password != null) st = password; else {
	                		st = callbackClient(passport, "password_load", msg, def, reason, datatype);
	                		currentPassword = st;
	                	}; break;
	                	*/
	                case NEED_PASSPHRASE_SAVE: 	st = "PecuniaData"; break; //st = callbackClient(passport, "password_save", msg, def, reason, datatype); break; 
	                case NEED_CONNECTION:
	                case CLOSE_CONNECTION: return;
	                case NEED_PT_SECMECH: 		if(server.passportExists(passport)) st = callbackClient(passport, "getTanMethod", msg, def, reason, datatype);
	                							else {
	                								// if passport is created just return the first alternative (will be overwritten later anyway)
	                								String[] methods = def.split("\\|");
	                								st = methods[0].split(":")[0];
	                							}
	                							break;
	                case NEED_SOFTPIN:
	                case NEED_PT_PIN:			st = callbackClient(passport, "getPin", msg, def, reason, datatype); break;
	                case NEED_PT_TAN:			st = callbackClient(passport, "getTan", msg, def, reason, datatype); break;
	                case NEED_PT_TANMEDIA:		st = callbackClient(passport, "getTanMedia", msg, def, reason, datatype); break;
	                case HAVE_INST_MSG:			callbackClient(passport, "instMessage", msg, def, reason, datatype); return;
	                case NEED_CHIPCARD:			callbackClient(passport, "needChipcard", msg, def, reason, datatype); return;
	                case HAVE_CHIPCARD:			callbackClient(passport, "haveChipcard", msg, def, reason, datatype); return;
	                case NEED_HARDPIN:			callbackClient(passport,"needHardPin", msg, def, reason, datatype); return;
	                case HAVE_HARDPIN:			callbackClient(passport, "haveHardPin", msg, def, reason, datatype); return;
	                case WRONG_PIN:				callbackClient(passport, "wrongPin", msg, def, reason, datatype); return;
	                case USERID_CHANGED:		callbackClient(passport, "UserIDChanged", msg, def, reason, datatype); return;
	
	                default: System.err.println("Unhandled callback reason code: " + Integer.toString(reason)); return;
	            }
                if(st != null) {
                	if(st.equals("<abort>")) throw new AbortedException("Abbruch durch Benutzer");
                	retData.replace(0,retData.length(),st);
                }

	            
	        } catch (Exception e) {
	            throw new HBCI_Exception(HBCIUtilsInternal.getLocMsg("EXCMSG_CALLB_ERR"),e);
	        }
	    }
	}
	
	private static void log(String msg,int level,Date date) {
		try {
			server.out.write("<log level=\""+Integer.toString(level)+"\"><![CDATA[");
			server.out.write(msg);
			server.out.write("]]></log>\n.");
			server.out.flush();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	//------------------------------ END CALLBACK ---------------------------------------------------
	
	public static HBCIServer server()
	{
		return server;
	}
	
	public String passportFilepath(String bankCode, String userId) {
		String filename = passportKey(bankCode, userId);
	    String filePath = passportPath + "/" + filename + ".ser";
	    return filePath;
	}
		
	private String passportKey(Properties map) throws IOException
	{
		String bankCode = getParameter(map, "userBankCode");
		String userId = getParameter(map, "userId");
		return passportKey(bankCode, userId);
	}
	
	public String passportKey(String bankCode, String userId) {
		return bankCode + "_" + userId;
	}
	
	public String getParameter(Properties aMap, String parameter) throws IOException {
		String ret = aMap.getProperty(parameter);
		if(ret == null) throw new HBCIParamException(parameter);
		return ret;
	}

    private void initHBCI() {
        HBCIUtils.init(null,new MyCallback());
        
        // Basic Params
        HBCIUtils.setParam("client.connection.localPort",null);
        HBCIUtils.setParam("log.loglevel.default","5");
        
        // PinTan Passport
//        HBCIUtils.setParam("client.passport.default","PinTan");
        HBCIUtils.setParam("client.passport.PinTan.checkcert","1");
        HBCIUtils.setParam("client.passport.PinTan.certfile",null);
        HBCIUtils.setParam("client.passport.PinTan.proxy",null);
        HBCIUtils.setParam("client.passport.PinTan.proxyuser",null);
        HBCIUtils.setParam("client.passport.PinTan.proxypass",null);
        HBCIUtils.setParam("client.passport.PinTan.init","1");
//        HBCIUtils.setParam("client.retries.passphrase","0");
//        HBCIUtils.setParam("client.passport.hbciversion.default","plus");
        
        // DDV Passports
        HBCIUtils.setParam("client.passport.DDV.libname.ddv", ddvLibPath+"libhbci4java-card-mac-os-x-10.6.jnilib");
        HBCIUtils.setParam("client.passport.DDV.path", passportPath+"/");
        HBCIUtils.setParam("client.passport.DDV.libname.ctapi", ddvLibPath+"pcsc-ctapi-wrapper.dylib");
        
        // get countries
        String countryPath = "/CountryInfo.txt";
        InputStream f=HBCIServer.class.getResourceAsStream(countryPath);
        BufferedReader fin = new BufferedReader(new InputStreamReader(f));
        String s;
        try {
			while((s = fin.readLine()) != null && s.length() != 0) {
				String[] info = s.split(";", 0);
				countryInfos.put(info[1], info);
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
    }
    
    
    public HBCIHandler hbciHandler(String bankCode, String userId) {
    	String fname = passportKey(bankCode, userId);
		HBCIHandler handler = (HBCIHandler)hbciHandlers.get(fname);
/*		
		if(handler == null) {
			// check if passport file exists
			String filePath = passportPath + "/" + fname + ".dat";
			HBCIUtils.log("HBCIServer: open passort: "+filePath, HBCIUtils.LOG_DEBUG);
			File file = new File(filePath);
			if(file.exists() == false) {
				HBCIUtils.log("HBCIServer: passport file "+filePath+" not found, checking alternatives", HBCIUtils.LOG_DEBUG);
				
				boolean found = false;
				// check if there is a passport file with userID and different bank code
				for(Enumeration e = users.keys(); e.hasMoreElements(); ) {
					String key  = (String)e.nextElement();
					if(key.endsWith(userId)) {
						// we found an alternative passport that could fit
						
						filePath = passportPath + "/" + key + ".dat";
						HBCIUtils.log("HBCIServer: try alternative passort: "+filePath, HBCIUtils.LOG_DEBUG);
						file = new File(filePath);
						if(file.exists() == true) {
							found = true;
							altName = key;
							HBCIUtils.log("HBCIServer: alternative passport file "+filePath+" found", HBCIUtils.LOG_DEBUG);
							break;
						}						
					}
				}
				if(found == false) {
					HBCIUtils.log("HBCIServer: alternative passport file "+filePath+" not found!", HBCIUtils.LOG_DEBUG);
					return null;					
				}				
			}

			HBCIUtils.setParam("client.passport.PinTan.filename",filePath);
	        HBCIPassport passport=AbstractHBCIPassport.getInstance();
	        if(passport == null) {
				HBCIUtils.log("HBCIServer: failed to create passport from file "+filePath+"!", HBCIUtils.LOG_ERR);
				return null;
	        }
	        HBCIHandler hbciHandle=new HBCIHandler(null, passport);
	        // we currently support only one User per BLZ
	        hbciHandlers.put(fname, hbciHandle);
	        if(altName != null) hbciHandlers.put(altName, hbciHandle);
			HBCIUtils.log("HBCIServer: passport created for bank code "+bankCode+", user "+userId, HBCIUtils.LOG_DEBUG);
	        return hbciHandle;
		}
*/		
		return handler;
    }
        
    private boolean passportExists(HBCIPassport pp) {
		String bankCode = pp.getBLZ();
		String userId = pp.getUserId();
		String name = passportKey(bankCode, userId);
		return hbciHandlers.containsKey(name);
    }
    
    private Konto getAccount(HBCIPassport passport, String accountNumber, String subNumber)
    {
    	Konto [] accounts = passport.getAccounts();
    	
    	for(Konto k: accounts) {
    		if(k.number.equals(accountNumber)) {
    			if(k.subnumber == null && (subNumber == null || subNumber.length() == 0)) return k;
    			if(k.subnumber != null) {
    				if(k.subnumber.equals(subNumber)) return k;
    			}
    		}
    	}
    	return null;
    }
    
    // Passport in hbciHandlers aufnehmen bzw. puffern
    private void registerPassport() throws IOException {
		String bankCode = getParameter(map, "userBankCode");
		String userId = getParameter(map, "userId");
    	String version = map.getProperty("version");
        HBCIHandler hbciHandle = null;

    	// Passport-Typ setzen
		String type = getParameter(map, "passportType");
        HBCIUtils.setParam("client.passport.default",type);

        String key = passportKey(bankCode, userId);
        hbciHandle = (HBCIHandler)hbciHandlers.get(key);
        if(hbciHandle != null) {
        	// Passport already there. If it's a DDV passport, check if it's still alive (chipcard has not been removed in the meanwhile)
        	if(type.equals("DDV")) {
        		HBCIPassportDDVExt passport = (HBCIPassportDDVExt)hbciHandle.getPassport();
        		if (passport.isAlive() == false) {
        			hbciHandlers.remove(key);
        			hbciHandle = null;
        		}
        	}
        }
        
        if(hbciHandle == null) {
            if(type.equals("PinTan")) {
            	HBCIPassport passport = null;
    			if(version.compareTo("220") == 0) version = "plus";
	        	HBCIUtils.setParam("client.passport.hbciversion.default",version);
    			
    			// check if passport file exists
    			String filePath = passportPath + "/" + key + ".dat";
    			HBCIUtils.log("HBCIServer: open passort: "+filePath, HBCIUtils.LOG_DEBUG);
    			File file = new File(filePath);
    			if(file.exists() == false) {
    				// does not exist - try to create new passport
    				HBCIUtils.log("HBCIServer: passport file "+filePath+" not found! Try to rebuild it", HBCIUtils.LOG_WARN);
    				HBCIUtils.setParam("client.passport.PinTan.filename",filePath);
    		        
    		        String checkCert = map.getProperty("checkCert");
    		        if(checkCert != null && checkCert.equals("no")) {
    		        	HBCIUtils.setParam("client.passport.PinTan.checkcert", "0");
    		        } else {
    		        	HBCIUtils.setParam("client.passport.PinTan.checkcert", "1");
    		        }
    		        try {
    		        	passport=AbstractHBCIPassport.getInstance(type);
    		        }
    		        catch(HBCI_Exception e) {
    		        	passport = null;
    		        }
    			} else {
    				HBCIUtils.setParam("client.passport.PinTan.filename",filePath);
    		        passport=AbstractHBCIPassport.getInstance(type);
    			}
		        if(passport == null) {
					HBCIUtils.log("HBCIServer: failed to create passport from file "+filePath+"!", HBCIUtils.LOG_ERR);
		        } else {
		        	try {
				        hbciHandle=new HBCIHandler(version, passport);
		        	}
		        	catch(HBCI_Exception e) {
		        		hbciHandle = null;
		        	}
		        }
    		}
            
    		if(type.equals("DDV")) {
    			String portIdx = getParameter(map, "ddvPortIdx");
    			String readerIdx = getParameter(map, "ddvReaderIdx");
    	        HBCIUtils.setParam("client.passport.DDV.path", passportPath+"/");
    	        HBCIUtils.setParam("client.passport.DDV.port", portIdx);
    	        HBCIUtils.setParam("client.passport.DDV.ctnumber", readerIdx);
    	        HBCIUtils.setParam("client.passport.DDV.usebio", "0");
    	        HBCIUtils.setParam("client.passport.DDV.softpin", "0");	
    	        
    	        //HBCIPassport passport=AbstractHBCIPassport.getInstance("DDV");
    	        HBCIPassport passport = new HBCIPassportDDVExt(null);
    	        
    	        try {
        			if(version.compareTo("220") == 0) version = "plus";
    	        	HBCIUtils.setParam("client.passport.hbciversion.default",version);
    	        	hbciHandle=new HBCIHandler(version, passport);

    	        }
    	        catch(HBCI_Exception e) {
    	        	hbciHandle = null;
    	        }
    		}
    		
    		if(hbciHandle != null) {
    			HBCIUtils.log("HBCIServer: passport created for bank code "+bankCode+", user "+userId, HBCIUtils.LOG_DEBUG);
    			hbciHandlers.put(key, hbciHandle);

    			// set accounts
        		HBCIPassport passport = hbciHandle.getPassport();
            	Konto [] ppAccounts = passport.getAccounts();
            	for(Konto k: ppAccounts) {
            		String accountKey = accountKey(userId, k.blz, k.number, k.subnumber);
            		if (accounts.get(accountKey) == null)
            			accounts.put(accountKey, k);
            	}
    		}        	
        }
		
		xmlBuf.append("<result command=\"registerPassport\">");
		xmlGen.booleTag("ok", hbciHandle != null);
		xmlBuf.append("</result>.");
		out.write(xmlBuf.toString());
		out.flush();
    }
    	
	private void addPassport() throws IOException {
		
		String type = getParameter(map, "passportType");
		HBCIPassport passport=null;
		
		if(type.equals("PinTan")) {
			String filename = passportKey(map);
			if(filename == null) return;
			String filePath = passportPath + "/" + filename + ".dat";

			HBCIUtils.setParam("client.passport.PinTan.filename",filePath);
	        
	        String checkCert = map.getProperty("checkCert");
	        if(checkCert != null && checkCert.equals("no")) {
	        	HBCIUtils.setParam("client.passport.PinTan.checkcert", "0");
	        } else {
	        	HBCIUtils.setParam("client.passport.PinTan.checkcert", "1");
	        }
	        passport=AbstractHBCIPassport.getInstance(type);
		}
		
		if(type.equals("DDV")) {
	        // DDV
			String portIdx = getParameter(map, "ddvPortIdx");
			String readerIdx = getParameter(map, "ddvReaderIdx");
	        HBCIUtils.setParam("client.passport.DDV.port", portIdx);
	        HBCIUtils.setParam("client.passport.DDV.ctnumber", readerIdx);
	        HBCIUtils.setParam("client.passport.DDV.usebio", "0");
	        HBCIUtils.setParam("client.passport.DDV.softpin", "0");
	        passport = new HBCIPassportDDVExt(null);
		}
        
        HBCIUtils.setParam("action.resetBPD","1");
        HBCIUtils.setParam("action.resetUPD","1");
        
        HBCIHandler hbciHandle = null;
        try {
        	String version = getParameter(map, "version");
        	if(version.compareTo("220") == 0 && type.equals("PinTan")) version = "plus";
        	hbciHandle=new HBCIHandler(version, passport);
        }
        catch(HBCI_Exception e) {
        	String fileName = null;
        	if(type.equals("PinTan")) fileName = ((HBCIPassportPinTan)passport).getFileName();
        	if(type.equals("DDV")) fileName = ((HBCIPassportDDV)passport).getFileName();
        	if(fileName != null) {
            	File ppFile = new File(fileName);
            	ppFile.delete();        		
        	}
        	throw e;
        }
        
        // Passport-Schlüssel ermitteln und Handle ablegen
        HBCIUtils.log("DDVPassport BLZ:  "+passport.getBLZ(), HBCIUtils.LOG_DEBUG);
        HBCIUtils.log("DDVPassport UserId:  "+passport.getUserId(), HBCIUtils.LOG_DEBUG);
        
        String passportKey = passportKey(passport.getBLZ(), passport.getUserId());
        hbciHandlers.put(passportKey, hbciHandle);
        
		// check for SEPA account information
		Properties upd = passport.getUPD();
		if(upd != null && upd.containsValue("HKSPA")) {
			hbciHandle.updateSEPAInfo();
		}

        xmlBuf.append("<result name=\"addPassport\">");
        xmlGen.passportToXml(hbciHandle, true);
        xmlBuf.append("</result>.");
		out.write(xmlBuf.toString());
        out.flush();
 	}
	
	private void deletePassport() throws IOException {
		String type = getParameter(map, "passportType");
		String key = null;
		if(type.equals("PinTan")) {
			key = passportKey(map);
			if(key == null) return;
			// remove the passport file
			String filePath = passportPath + "/" + key + ".dat";
	    	File ppFile = new File(filePath);
	    	ppFile.delete();
		}
		if(type.equals("DDV")) {
			// remove the passport file
			key = getParameter(map, "chipCardId");
			String filePath = passportPath + "/" + key;
	    	File ppFile = new File(filePath);
	    	ppFile.delete();			
		}
	
		hbciHandlers.remove(key);
    	
        xmlBuf.append("<result name=\"deletePassport\">");
        xmlBuf.append("</result>.");
		out.write(xmlBuf.toString());
        out.flush();
	}
	
	private String getSEPAJob(String jobName) {
		if(jobName.equals("Ueb")) return "UebSEPA";
		if(jobName.equals("TermUeb")) return "TermUebSEPA";
		if(jobName.equals("DauerList")) return "DauerSEPAList";
		if(jobName.equals("DauerNew")) return "DauerSEPANew";
		if(jobName.equals("DauerEdit")) return "DauerSEPAEdit";
		if(jobName.equals("DauerDel")) return "DauerSEPADel";
		return jobName;
	}
	
	private Properties getOrdersForJob(String jobName) throws IOException {
		Properties orders = new Properties();
		
		// first collect all orders separated by handlers
		ArrayList list = (ArrayList)map.get("accinfolist");
		if(list.size() == 0) {
			HBCIUtils.log("HBCIServer: "+jobName+" called without accounts", HBCIUtils.LOG_DEBUG);
		}
		for(int i=0; i<list.size(); i++) {
			Properties tmap = (Properties)list.get(i);
			String bankCode = getParameter(tmap, "accinfo.bankCode");
			String userId = getParameter(tmap, "accinfo.userId");
			String userBankCode = getParameter(tmap, "accinfo.userBankCode");
			String accountNumber = getParameter(tmap, "accinfo.accountNumber");
			String subNumber = tmap.getProperty("accinfo.subNumber");
			String currentJobName = jobName;
			boolean isSEPA = tmap.getProperty("accinfo.isSEPA") != null && tmap.getProperty("accinfo.isSEPA").equals("yes");
			boolean isCCAccount = false;
			boolean onlyBalance = false;
			
			// get SEPA version of job if SEPA is active
			if(isSEPA) currentJobName = getSEPAJob(jobName);
			
			HBCIHandler handler = hbciHandler(userBankCode, userId);
			if(handler == null) {
				HBCIUtils.log("HBCIServer: "+currentJobName+" skips bankCode "+userBankCode+" user "+userId, HBCIUtils.LOG_DEBUG);
				continue;
			}
			
			// check if job is supported
			Konto account = accountWithId(userId, bankCode, accountNumber, subNumber);
			if(account == null) {
				account = getAccount(handler.getPassport(), accountNumber, subNumber);
				if(account == null) {
					HBCIUtils.log("HBCIServer: "+currentJobName+" skips account "+accountNumber, HBCIUtils.LOG_DEBUG);
					continue;
				}
			}

			if(isJobSupported(currentJobName, account, handler) == false) {
				// if KUmsAll and account is cc-account, try KKUmsAll
				if(currentJobName.equals("KUmsAll") && isJobSupported("KKUmsAll", account, handler)) {
					currentJobName = "KKUmsAll";
					isCCAccount = true;
				} else {
					// check if we can at least get the balance
					if(currentJobName.equals("KUmsAll") && isJobSupported("SaldoReq", account, handler)) {
						currentJobName = "SaldoReq";
						onlyBalance = true;
					} else {
						// Log: job is not supported
						HBCIUtils.log("HBCIServer: "+currentJobName+" skips account "+accountNumber+", job is not supported", HBCIUtils.LOG_WARN);
						continue;						
					}
				}
			}

			// create job
			HBCIJob job = handler.newJob(currentJobName);
			if(isSEPA) job.setParam("src", account); else job.setParam("my", account);
			if(isCCAccount) {
				job.setParam("cc_number", accountNumber);
			}
			
			String fromDateString = tmap.getProperty("accinfo.fromDate");
			if(onlyBalance == false && fromDateString != null) {
				Date fromDate = HBCIUtils.string2DateISO(fromDateString);
				if(fromDate != null) {
					job.setParam("startdate", fromDate);
				}				
			}

			HBCIUtils.log("HBCIServer: "+currentJobName+" customerId: "+account.customerid, HBCIUtils.LOG_DEBUG);
			if(account.customerid == null) job.addToQueue();
			else job.addToQueue(account.customerid);
			ArrayList<Properties> jobs = (ArrayList<Properties>)orders.get(handler);
			if(jobs == null) {
				jobs = new ArrayList<Properties>();
				orders.put(handler, jobs);
			}
			Properties jobacc = new Properties();
			jobacc.put("job", job);
			jobacc.put("account", account);
			jobs.add(jobacc);
		}
		return orders;
	}
	
	
	
	
	private void getAllStatements() throws IOException {
				
		Properties orders = getOrdersForJob("KUmsAll");
		
		// now iterate through orders
		if(orders.size() == 0) {
			HBCIUtils.log("HBCIServer: getStatements: there are no orders!", HBCIUtils.LOG_DEBUG);			
		}
		for(Enumeration e = orders.keys(); e.hasMoreElements(); ) {
			HBCIHandler handler = (HBCIHandler)e.nextElement();
			ArrayList<Properties> jobs = (ArrayList<Properties>)orders.get(handler);
			
			HBCIExecStatus status = handler.execute();
			if(status.isOK()) {
				for(Properties jobacc: jobs) {
					HBCIJob job = (HBCIJob)jobacc.get("job");
					Konto account = (Konto)jobacc.get("account");
					if(job.getName().startsWith("KUmsZeit")) {
						//GVKUmsAll umsJob = (GVKUmsAll)job;
						GVRKUms res = (GVRKUms)job.getJobResult();
						if(res.isOK()) {
							boolean statementsExist = xmlGen.umsToXml(res, account);
							if(!statementsExist) {
								GVRSaldoReq saldoRes = getBalance(handler, account);
								if(saldoRes != null && saldoRes.isOK()) {
									xmlGen.saldoUmsToXml(saldoRes, account);
								}
							}
						}
					} else {
						if(job.getName().startsWith("KKUmsZeit")) {
							GVRKKUms res = (GVRKKUms)job.getJobResult();
							if(res.isOK()) {
								xmlGen.ccUmsAllToXml(res, account);
							}							
						} else {
							if(job.getName().startsWith("Saldo")) {
								GVRSaldoReq res = (GVRSaldoReq)job.getJobResult();
								if(res.isOK()) {
									xmlGen.saldoUmsToXml(res, account);
								}							
							}	
						}
					}
				}
			}
		}
		
		// Get DKB statements, if requested
		DKBVisaManager manager = new DKBVisaManager(this);
		manager.getStatements(map, xmlGen);

		out.write("<result command=\"getAllStatements\">");
		out.write("<list>");
		out.write(xmlBuf.toString());
		out.write("</list>");
		out.write("</result>.");
		out.flush();
	}

	
	private void getAllStandingOrders() throws IOException {
		
		Properties orders = getOrdersForJob("DauerList");

		// now iterate through orders
		for(Enumeration e = orders.keys(); e.hasMoreElements(); ) {
			HBCIHandler handler = (HBCIHandler)e.nextElement();
			ArrayList<Properties> jobs = (ArrayList<Properties>)orders.get(handler);
			
			HBCIExecStatus status = handler.execute();
			if(status.isOK()) {
				for(Properties jobacc: jobs) {
					HBCIJob job = (HBCIJob)jobacc.get("job");
					Konto account = (Konto)jobacc.get("account");
					GVRDauerList res = (GVRDauerList)job.getJobResult();
					if(res.isOK()) {
				    	xmlBuf.append("<object type=\"BankQueryResult\">");
				    	xmlGen.tag("bankCode", account.blz);
				    	xmlGen.tag("accountNumber", account.number);
				    	xmlGen.tag("accountSubnumber", account.subnumber);
				    	xmlBuf.append("<standingOrders type=\"list\">");
						xmlGen.dauerListToXml(res, account);
						xmlBuf.append("</standingOrders></object>");
					}
				}
			}
		}
		out.write("<result command=\"getAllStandingOrders\">");
		out.write("<list>");
		out.write(xmlBuf.toString());
		out.write("</list>");
		out.write("</result>.");
		out.flush();
	}
	
	private void getAllTermUebs() throws IOException {
		
		Properties orders = getOrdersForJob("TermUebList");
		
		// now iterate through orders
		for(Enumeration e = orders.keys(); e.hasMoreElements(); ) {
			HBCIHandler handler = (HBCIHandler)e.nextElement();
			ArrayList<Properties> jobs = (ArrayList<Properties>)orders.get(handler);
			
			HBCIExecStatus status = handler.execute();
			if(status.isOK()) {
				for(Properties jobacc: jobs) {
					HBCIJob job = (HBCIJob)jobacc.get("job");
					Konto account = (Konto)jobacc.get("account");
					GVRTermUebList res = (GVRTermUebList)job.getJobResult();
					if(res.isOK()) {
				    	xmlBuf.append("<object type=\"BankQueryResult\">");
				    	xmlGen.tag("bankCode", account.blz);
				    	xmlGen.tag("accountNumber", account.number);
				    	xmlGen.tag("accountSubnumber", account.subnumber);
				    	xmlBuf.append("<termUebs type=\"list\">");
				    	xmlGen.termUebListToXml(res, account);
						xmlBuf.append("</termUebs></object>");
					}
				}
			}
		}
		out.write("<result command=\"getAllTermUebs\">");
		out.write("<list>");
		out.write(xmlBuf.toString());
		out.write("</list>");
		out.write("</result>.");
		out.flush();
	}
	
	private void sendCollectiveTransfer() throws IOException {
		String bankCode = getParameter(map, "bankCode");
		String userId = getParameter(map, "userId");
		String userBankCode = getParameter(map, "userBankCode");
		String accountNumber = getParameter(map, "accountNumber");
		String subNumber = map.getProperty("subNumber");

		HBCIHandler handler = hbciHandler(userBankCode, userId);
		if(handler == null) {
			error(ERR_MISS_USER, "sendCollectiveTransfer", userId);
			return;			
		}
		
		HBCIPassport passport = handler.getPassport();
		if(passport instanceof HBCIPassportPinTan) {
			HBCIPassportPinTan pp = (HBCIPassportPinTan)passport;
			pp.setCurrentTANMethod(null);
		}

		Konto account = accountWithId(userId, bankCode, accountNumber, subNumber);
		if(account == null) {
			account = getAccount(handler.getPassport(), accountNumber, subNumber);
			if(account == null) {
				error(ERR_MISS_ACCOUNT, "sendCollectiveTransfer", "Konto "+accountNumber+" nicht gefunden!");
				return;
			}
		}

		HBCIJob job = handler.newJob("MultiUeb");
		job.setParam("my", account);

		// Alle Überweisungen in DTAUS-Struktur überführen
		ArrayList<Properties> transfers = (ArrayList<Properties>)map.get("transfers");
		if(transfers.size() == 0) {
			error(ERR_GENERIC, "sendCollectiveTransfer", "Keine Überweisungsdaten vorhanden!");
			return;
		}
		
		DTAUS dtaus = new DTAUS(account, DTAUS.TYPE_CREDIT, null);
		for(Properties map: transfers) {
			DTAUS.Transaction transfer = dtaus.new Transaction();
			
			// Empfänger
			Konto dest = new Konto(	getParameter(map, "transfer.remoteCountry"),
									getParameter(map, "transfer.remoteBankCode"),
									getParameter(map, "transfer.remoteAccount"));

			// RemoteName
			String remoteName = getParameter(map, "transfer.remoteName");
			if(remoteName.length() > 27) {
				dest.name = remoteName.substring(0, 27);
				dest.name2 = remoteName.substring(27);
			} else dest.name = remoteName;
			
			transfer.otherAccount = dest;
			
			// Betrag
			long val = Long.decode(getParameter(map, "transfer.value"));
			transfer.value = new Value(val, getParameter(map, "transfer.currency"));

			// Verwendungszweck
			String purpose = getParameter(map, "transfer.purpose1");
			if(purpose != null) transfer.addUsage(purpose);
			purpose = map.getProperty("transfer.purpose2");
			if(purpose != null) transfer.addUsage(purpose);
			purpose = map.getProperty("transfer.purpose3");
			if(purpose != null) transfer.addUsage(purpose);
			purpose = map.getProperty("transfer.purpose4");
			if(purpose != null) transfer.addUsage(purpose);

			// Überweisung hinzufügen
			dtaus.addEntry(transfer);
		}
		
		job.setParam("data", dtaus.toString());
		job.addToQueue();
		
		HBCIExecStatus status = handler.execute();
		
		boolean isOk = false;
		HBCIJobResult res = null;
		if(status.isOK()) {
			res = job.getJobResult();
			if(res.isOK()) isOk = true;
		}
		
		xmlBuf.append("<result command=\"sendCollectiveTransfer\">");
		xmlGen.booleTag("isOk", isOk);
		xmlBuf.append("</result>.");
		out.write(xmlBuf.toString());
		out.flush();
	}

	
	private void sendTransfer() throws IOException {
		String userId = getParameter(map, "userId");
		String userBankCode = getParameter(map, "userBankCode");
		String bankCode = getParameter(map, "bankCode");
		String accountNumber = getParameter(map, "accountNumber");
		String subNumber = map.getProperty("subNumber");
		String gvCode = null;
		
		HBCIHandler handler = hbciHandler(userBankCode, userId);
		if(handler == null) {
			error(ERR_MISS_USER, "sendTransfer", userId);
			return;			
		}
		
		HBCIPassport passport = handler.getPassport();
		if(passport instanceof HBCIPassportPinTan) {
			HBCIPassportPinTan pp = (HBCIPassportPinTan)passport;
			pp.setCurrentTANMethod(null);
		}
		
		// delete all unsent transfers from previous calls
		handler.reset();

		Konto account = accountWithId(userId, bankCode, accountNumber, subNumber);
		if(account == null) {
			account = getAccount(handler.getPassport(), accountNumber, subNumber);
			if(account == null) {
				error(ERR_MISS_ACCOUNT, "sendTransfer", accountNumber);
				return;
			}
		}
		
		String transferType = getParameter(map, "type");
		boolean isSEPA = map.getProperty("remoteIBAN") != null && !transferType.equals("foreign");

		if(isSEPA) {
			if(transferType.equals("standard")) gvCode = "UebSEPA";
			else if(transferType.equals("sepa")) gvCode = "UebSEPA";
			else if(transferType.equals("dated")) gvCode = "TermUebSEPA"; 			
			else if(transferType.equals("last")) gvCode = "LastSEPA";
		} else {
			if(transferType.equals("standard")) gvCode = "Ueb";
			else if(transferType.equals("dated")) gvCode = "TermUeb"; 
			else if(transferType.equals("foreign")) gvCode = "UebForeign";
		}
		if(transferType.equals("internal")) gvCode = "Umb";
		
		
		HBCIJob job = handler.newJob(gvCode);
		if(transferType.equals("last")) job.setParam("my", account);
		else job.setParam("src", account);
		
		// Gegenkonto
		if(!transferType.equals("foreign") && !isSEPA) {
			String remoteName1;
			String remoteName2;
			
			Konto dest = new Konto(	getParameter(map, "remoteCountry"),
									getParameter(map, "remoteBankCode"),
									getParameter(map, "remoteAccount"));
			
			job.setParam("dst", dest);

			// RemoteName
			// Split remote name
			String remoteName = getParameter(map, "remoteName");
			if(remoteName.length() > 27) {
				remoteName1 = remoteName.substring(0, 27);
				remoteName2 = remoteName.substring(27);
			} else {
				remoteName1 = remoteName;
				remoteName2 = null;
			}

			job.setParam("name", remoteName1);
			if(remoteName2 != null) job.setParam("name2", remoteName2);
			
			String purpose = map.getProperty("purpose1");
			if(purpose != null) job.setParam("usage", purpose);
			purpose = map.getProperty("purpose2");
			if(purpose != null) job.setParam("usage_2", purpose);
			purpose = map.getProperty("purpose3");
			if(purpose != null) job.setParam("usage_3", purpose);
			purpose = map.getProperty("purpose4");
			if(purpose != null) job.setParam("usage_4", purpose);			
		} else {
			// Auslandsüberweisung oder SEPA-GV
			if(isSEPA) {
				Konto dest = new Konto();
				dest.bic = getParameter(map, "remoteBIC");
				dest.iban = getParameter(map, "remoteIBAN");
				dest.name = getParameter(map, "remoteName");
				job.setParam("dst", dest);
				
				if(account.isSEPAAccount() == false) {
					// Konto kann nicht für SEPA-Geschäftsvorfälle verwendet werden
					HBCIUtils.log("Account "+account.number+" is no SEPA account (missing IBAN, BIC), skip transfer", HBCIUtils.LOG_ERR);
					error(ERR_MISS_SEPA_INFO, "sendTransfer", accountNumber);
					return;
				}
			} else {
				// Auslandsüberweisung
				job.setParam("dst.name", getParameter(map, "remoteName"));
				job.setParam("dst.kiname", getParameter(map, "bankName"));
				job.setParam("dst.iban", getParameter(map, "remoteIBAN"));
				if(map.containsKey("chargeTo")) job.setParam("kostentraeger", map.getProperty("chargeTo"));
			}

			String purpose = getParameter(map, "purpose1");
			if(purpose != null) job.setParam("usage", purpose);
		}
		long val = Long.decode(getParameter(map, "value"));
		job.setParam("btg", new Value(val, getParameter(map, "currency")));
		
		if(transferType.equals("dated")) {
			Date date = HBCIUtils.string2DateISO(getParameter(map, "valutaDate"));
			job.setParam("date", date);
		}
		
		job.addToQueue();
		HBCIExecStatus status = handler.execute();
		
		boolean isOk = false;
		HBCIJobResult res = null;
		if(status.isOK()) {
			res = job.getJobResult();
			if(res.isOK()) isOk = true;
		}
		
		xmlBuf.append("<result command=\"sendTransfer\">");
		xmlGen.booleTag("isOk", isOk);
		xmlBuf.append("</result>.");
		out.write(xmlBuf.toString());
		out.flush();
	}
	
	
	private void sendTransfers() throws IOException {
		Properties orders = new Properties();
		HashSet<HBCIHandler> handlers = new HashSet<HBCIHandler>();
		String gvCode=null;
		
		// first collect all orders separated by handlers
		ArrayList<Properties> list = (ArrayList<Properties>)map.get("transfers");
		for(Properties map: list) {
			String userId = getParameter(map, "transfer.userId");
			String userBankCode = getParameter(map, "transfer.userBankCode");
			String bankCode = getParameter(map, "transfer.bankCode");
			String accountNumber = getParameter(map, "transfer.accountNumber");
			String subNumber = map.getProperty("transfer.subNumber");
			String remoteName1;
			String remoteName2;
			
			HBCIHandler handler = hbciHandler(userBankCode, userId);
			if(handler == null) {
				error(ERR_MISS_USER, "sendTransfers", userId);
				return;			
			}
			
			HBCIPassport passport = handler.getPassport();
			if(passport instanceof HBCIPassportPinTan) {
				HBCIPassportPinTan pp = (HBCIPassportPinTan)passport;
				pp.setCurrentTANMethod(null);
			}
			
			// delete all unsent transfers from previous calls
			if(!handlers.contains(handler)) {
				handler.reset();
				handlers.add(handler);
			}
			Konto account = accountWithId(userId, bankCode, accountNumber, subNumber);
			if(account == null) {
				account = getAccount(handler.getPassport(), accountNumber, subNumber);
				if(account == null) continue;
			}
			
			String transferType = getParameter(map, "transfer.type");
			if(transferType.equals("standard")) gvCode = "Ueb";
			else if(transferType.equals("dated")) gvCode = "TermUeb"; 
			else if(transferType.equals("internal")) gvCode = "Umb";
			else if(transferType.equals("foreign")) gvCode = "UebForeign";
			else if(transferType.equals("last")) gvCode = "Last";
			else if(transferType.equals("sepa")) gvCode = "UebSEPA";
			
			HBCIJob job = handler.newJob(gvCode);
			if(transferType.equals("last")) job.setParam("my", account);
			else job.setParam("src", account);
			
			// Split remote name
			String remoteName = getParameter(map, "transfer.remoteName");
			if(remoteName.length() > 27) {
				remoteName1 = remoteName.substring(0, 27);
				remoteName2 = remoteName.substring(27);
			} else {
				remoteName1 = remoteName;
				remoteName2 = null;
			}

			// Gegenkonto
			if(!transferType.equals("foreign") && !transferType.equals("sepa")) {
				Konto dest = new Konto(	getParameter(map, "transfer.remoteCountry"),
										getParameter(map, "transfer.remoteBankCode"),
										getParameter(map, "transfer.remoteAccount"));
				
				if(transferType.equals("last")) job.setParam("other", dest);
				else job.setParam("dst", dest);

				// RemoteName
				job.setParam("name", remoteName1);
				if(remoteName2 != null) job.setParam("name2", remoteName2);
				
				String purpose = getParameter(map, "transfer.purpose1");
				if(purpose != null) job.setParam("usage", purpose);
				purpose = map.getProperty("transfer.purpose2");
				if(purpose != null) job.setParam("usage_2", purpose);
				purpose = map.getProperty("transfer.purpose3");
				if(purpose != null) job.setParam("usage_3", purpose);
				purpose = map.getProperty("transfer.purpose4");
				if(purpose != null) job.setParam("usage_4", purpose);

				
			} else {
				// Auslandsüberweisung oder SEPA Einzelüberweisung
				if(transferType.equals("sepa")) {
					Konto dest = new Konto();
					dest.bic = getParameter(map, "transfer.remoteBIC");
					dest.iban = getParameter(map, "transfer.remoteIBAN");
					dest.name = getParameter(map, "transfer.remoteName");
					job.setParam("dst", dest);
					
					if(account.isSEPAAccount() == false) {
						// Konto kann nicht für SEPA-Geschäftsvorfälle verwendet werden
						HBCIUtils.log("Account "+account.number+" is no SEPA account (missing IBAN, BIC), skip transfer", HBCIUtils.LOG_ERR);
						continue;
					}
				} else {
					// Auslandsüberweisung
					job.setParam("dst.name", getParameter(map, "transfer.remoteName"));
					job.setParam("dst.kiname", getParameter(map, "transfer.bankName"));
					job.setParam("dst.iban", getParameter(map, "transfer.remoteIBAN"));
					if(map.containsKey("chargeTo")) job.setParam("kostentraeger", map.getProperty("chargeTo"));
				}

				String purpose = getParameter(map, "transfer.purpose1");
				if(purpose != null) job.setParam("usage", purpose);
			}
			long val = Long.decode(getParameter(map, "transfer.value"));
			job.setParam("btg", new Value(val, getParameter(map, "transfer.currency")));
			
			if(transferType.equals("dated")) {
				Date date = HBCIUtils.string2DateISO(getParameter(map, "transfer.valutaDate"));
				job.setParam("date", date);
			}
			
			job.addToQueue();
			ArrayList<Properties> jobs = (ArrayList<Properties>)orders.get(handler);
			if(jobs == null) {
				jobs = new ArrayList<Properties>();
				orders.put(handler, jobs);
			}
			Properties jobParam = new Properties();
			jobParam.put("job", job);
			jobParam.put("id", map.getProperty("transfer.transferId"));
			jobParam.put("type", transferType);
			jobs.add(jobParam);
		}
		
		// now iterate through orders
		for(Enumeration e = orders.keys(); e.hasMoreElements(); ) {
			HBCIHandler handler = (HBCIHandler)e.nextElement();
			ArrayList<Properties> jobs = (ArrayList<Properties>)orders.get(handler);
			
			HBCIExecStatus stat = handler.execute();
			boolean isOk = stat.isOK();
			
			for(Properties jobParam: jobs) {
				HBCIJob job = (HBCIJob)jobParam.get("job");
				HBCIJobResult res = job.getJobResult();
		    	xmlBuf.append("<object type=\"TransferResult\">");
		    	xmlGen.tag("transferId", jobParam.getProperty("id"));
		    	xmlGen.booleTag("isOk", res.isOK());
				xmlBuf.append("</object>");
			}
		}

		out.write("<result command=\"sendTransfers\">");
		out.write("<list>");
		out.write(xmlBuf.toString());
		out.write("</list>");
		out.write("</result>.");
		out.flush();
	}
	
	private void handleStandingOrder(String jobName, String cmd) throws IOException {
		String userId = getParameter(map, "userId");
		String userBankCode = getParameter(map, "userBankCode");
		String bankCode = getParameter(map, "bankCode");
		String accountNumber = getParameter(map, "accountNumber");
		String subNumber = map.getProperty("subNumber");
		String orderId = null;
		boolean isSEPA = map.getProperty("remoteIBAN") != null;
		
		HBCIHandler handler = hbciHandler(userBankCode, userId);
		if(handler == null) {
			error(ERR_MISS_USER, cmd, userId);
			return;
		}
		
		HBCIPassport passport = handler.getPassport();
		if(passport instanceof HBCIPassportPinTan) {
			HBCIPassportPinTan pp = (HBCIPassportPinTan)passport;
			pp.setCurrentTANMethod(null);
		}
		
		Konto account = accountWithId(userId, bankCode, accountNumber, subNumber);
		if(account == null) {
			account = getAccount(handler.getPassport(), accountNumber, subNumber);
			if(account == null) {
				error(ERR_MISS_ACCOUNT, cmd,accountNumber);
				return;
			}
		}
		
		// get SEPA job version
		if(isSEPA) jobName = getSEPAJob(jobName);
		
		HBCIJob job = handler.newJob(jobName);
		job.setParam("src", account);
		
		if(isSEPA) {
			if(account.isSEPAAccount() == false) {
				// Konto kann nicht für SEPA-Geschäftsvorfälle verwendet werden
				HBCIUtils.log("Account "+account.number+" is no SEPA account (missing IBAN, BIC), skip transfer", HBCIUtils.LOG_ERR);
				error(ERR_MISS_SEPA_INFO, "sendTransfer", accountNumber);
				return;
			}

			Konto dest = new Konto();
			dest.iban = getParameter(map, "remoteIBAN");
			dest.bic = getParameter(map, "remoteBIC");
			dest.name = getParameter(map, "remoteName");
			job.setParam("dst", dest);
			job.setParam("usage", getParameter(map, "purpose1"));
		} else {
			Konto dest = new Konto(	getParameter(map, "remoteCountry"),
					getParameter(map, "remoteBankCode"),
					getParameter(map, "remoteAccount"));
			job.setParam("dst", dest);
			
			// RemoteName
			String remoteName = getParameter(map, "remoteName");
			if(remoteName.length() > 27) {
				job.setParam("name", remoteName.substring(0, 27));
				job.setParam("name2", remoteName.substring(27));
			} else job.setParam("name", remoteName);
			
			// Purpose
			String purpose = getParameter(map, "purpose1");
			if(purpose != null) job.setParam("usage", purpose);
			purpose = map.getProperty("purpose2");
			if(purpose != null) job.setParam("usage_2", purpose);
			purpose = map.getProperty("purpose3");
			if(purpose != null) job.setParam("usage_3", purpose);
			purpose = map.getProperty("purpose4");
			if(purpose != null) job.setParam("usage_4", purpose);
		}
				
		long val = Long.decode(getParameter(map, "value"));
		job.setParam("btg", new Value(val, getParameter(map, "currency")));

		Date date = HBCIUtils.string2DateISO(getParameter(map, "firstExecDate"));
		job.setParam("firstdate", date);
		String lastExecDate = map.getProperty("lastExecDate");
		if(lastExecDate != null) {
			date = HBCIUtils.string2DateISO(lastExecDate);
			job.setParam("lastdate", date);
		}
		job.setParam("timeunit", getParameter(map,"timeUnit"));
		job.setParam("turnus", Integer.parseInt(getParameter(map,"turnus")));
		job.setParam("execday", Integer.parseInt(getParameter(map,"executionDay")));

		if(jobName.contains("Edit") || jobName.contains("Del")) {
			orderId = getParameter(map, "orderId");
			job.setParam("orderid", orderId);
		}
		
		job.addToQueue();
		HBCIExecStatus stat = handler.execute();
		
		boolean isOk = false;
		if(jobName.equals("DauerNew") || jobName.equals("DauerSEPANew")) {
			GVRDauerNew res = null;
			if(stat.isOK()) {
				res = (GVRDauerNew)job.getJobResult();
				if(res.isOK()) isOk = true;
			}
			xmlBuf.append("<result command=\"addStandingOrder\"><dictionary>");
			xmlGen.booleTag("isOk", isOk);
			if(res != null && isOk) xmlGen.tag("orderId", res.getOrderId());
			xmlBuf.append("</dictionary></result>.");
		} else {
			HBCIJobResult res = null;
			if(stat.isOK()) {
				res = (HBCIJobResult)job.getJobResult();
				if(res.isOK()) isOk = true;
			}
			xmlBuf.append("<result command=\""+cmd+"\">");
			xmlGen.booleTag("isOk", isOk);
			xmlBuf.append("</result>.");
		}
		out.write(xmlBuf.toString());
		out.flush();
	}
	
	private void addStandingOrder() throws IOException {
		handleStandingOrder("DauerNew", "addStandingOrder");
	}
	
	private void changeStandingOrder() throws IOException {
		handleStandingOrder("DauerEdit", "changeStandingOrder");
	}
	
	private void deleteStandingOrder() throws IOException {
		handleStandingOrder("DauerDel", "deleteStandingOrder");		
	}
	
	private void init() throws IOException {
		
		// Pfade besorgen
		ddvLibPath = getParameter(map, "ddvLibPath");
		passportPath = getParameter(map, "passportPath");

		// global inits
		initHBCI();
		
		hbciHandlers = new Properties();

		// return list of registered users
		xmlBuf.append("<result command=\"init\">");
		xmlBuf.append("</result>.");
		out.write(xmlBuf.toString());
		out.flush();
	}
	
	//  Alte Passport-Daten zurückliefern (wird nur für die Migration nach 1.0 benötigt)
	private void getOldBankUsers() throws IOException {		
		ArrayList<User> result = new ArrayList<User>();
		File dir = new File(passportPath);
		String [] files = dir.list();
		
		if(files != null) {
			for(int i=0; i<files.length; i++) {
				String fname = files[i];
				if(!fname.endsWith(".ser")) continue;
				fname = passportPath + "/" + fname;

				FileInputStream userFile = new FileInputStream( fname );
			    ObjectInputStream o = new ObjectInputStream( userFile );
			    try {
				    User user = (User)o.readObject();
				    
				    // check if passport file exists as well
				    String filename = passportKey(user.bankCode, user.userId);
				    String ppPath = passportPath + "/" + filename + ".dat";
				    File ppFile = new File(ppPath);
				    if(ppFile.exists() == true) {
				    	result.add(user);
				    }
			    } 
			    catch (ClassNotFoundException e) {
			    	System.err.println( e );
			    }
			    o.close();
			}
		}

		// return list of registered users
		xmlBuf.append("<result command=\"getOldBankUsers\">");
		xmlBuf.append("<list>");
		for(int i=0; i<result.size(); i++) {
			xmlGen.userToXml(result.get(i));
		}
		xmlBuf.append("</list>");
		xmlBuf.append("</result>.");
		out.write(xmlBuf.toString());
		out.flush();
	}
	
	// Konten einer Bankkennung ermitteln
	private void getAccounts() throws IOException {
		String userId = getParameter(map, "userId");
		String userBankCode = getParameter(map, "userBankCode");

		xmlBuf.append("<result command=\"getAccounts\">");
		xmlBuf.append("<list>");
		HBCIHandler handler = hbciHandler(userBankCode, userId);
		if(handler != null) {
			HBCIPassport pp = handler.getPassport();
			Konto [] accs = pp.getAccounts();
			for(Konto k: accs) xmlGen.accountToXml(k, handler);
		} else {
			error(ERR_MISS_USER, "getAccounts", userId);
			return;
		}
		
		xmlBuf.append("</list>");
		xmlBuf.append("</result>.");
		out.write(xmlBuf.toString());
		out.flush();
	}
	
	private void getBankInfo() throws IOException {
		String bankCode = getParameter(map, "bankCode");
		String data = null;
		if(bankCode.equals("49999924")) data = "Volksbank Online Filiale|Online|GENODEM1XXX|34|hbci.gad.de|https://hbci-pintan.gad.de/cgi-bin/hbciservlet|300|300";
		else if(bankCode.equals("94059541")) data = "Sparkasse Online Filiale|Online||||hbcitest-pintan-wf.s-hbci.de/PinTanServlet||plus";
		else data = HBCIUtilsInternal.getBLZData(bankCode);
		String[] parts=data.split("\\|");
		xmlBuf.append("<result command=\"getBankInfo\">");
    	xmlBuf.append("<object type=\"BankInfo\">");
    	xmlGen.tag("bankCode", bankCode);
		if(parts.length > 0) xmlGen.tag("name", parts[0]);
		if(parts.length > 1) xmlGen.tag("city", parts[1]);
		if(parts.length > 2) xmlGen.tag("bic", parts[2]);
		if(parts.length > 4) xmlGen.tag("host", parts[4]);
		if(parts.length > 5) xmlGen.tag("pinTanURL", parts[5]);
		if(parts.length > 6) xmlGen.tag("hbciVersion", parts[6]);
		if(parts.length > 7) {
			String pinTanVersion = parts[7];
			if(pinTanVersion.compareTo("plus") == 0) pinTanVersion = "220";
			xmlGen.tag("pinTanVersion", parts[7]);
		}
		xmlBuf.append("</object>");
		xmlBuf.append("</result>.");
		out.write(xmlBuf.toString());
		out.flush();
	}
	
	private void checkAccount() throws IOException {
		boolean result = true;
		String bankCode = map.getProperty("bankCode");
		if(bankCode != null) {
			String accountNumber = map.getProperty("accountNumber");
			if(HBCIUtils.checkAccountCRC(bankCode, accountNumber) == false) result = false;
		}
		String iban = map.getProperty("iban");
		if(iban != null) {
			if(HBCIUtils.checkIBANCRC(iban) == false) result = false;
		}

		xmlBuf.append("<result command=\"checkAccount\">");
		xmlGen.booleTag("checkResult", result);
		xmlBuf.append("</result>.");
		out.write(xmlBuf.toString());
		out.flush();
	}
	
	private String accountKey(String userId, String bankCode, String accountNumber, String subNumber)
	{
		if(subNumber == null) {
			return userId+bankCode+accountNumber;
		} else {
			return userId+bankCode+accountNumber+subNumber;			
		}
		
	}
	
	public Konto accountWithId(String userId, String bankCode, String accountNumber, String subNumber)
	{
		return (Konto)accounts.get(accountKey(userId, bankCode, accountNumber, subNumber));
	}
	
	private void setAccount() throws IOException {
		String bankCode = getParameter(map, "bankCode");
		String accountNumber = getParameter(map, "accountNumber");
		String userId = getParameter(map, "userId");
		String subNumber = map.getProperty("subNumber");
		
		Konto acc = accountWithId(userId, bankCode, accountNumber, subNumber);
		if(acc == null) {
			acc = new Konto(getParameter(map, "country"), bankCode, accountNumber);
			acc.curr = map.getProperty("currency");
			if (acc.curr == null) acc.curr = "EUR";
			acc.bic = map.getProperty("bic");
			acc.customerid = map.getProperty("customerId");
			acc.iban = map.getProperty("iban");
			acc.name = map.getProperty("ownerName");
			acc.type = map.getProperty("name");
			acc.subnumber = subNumber;
			accounts.put(accountKey(userId, bankCode, accountNumber, subNumber), acc);
		} else {
			// IBAN und BIC können von aussen gesetzt werden
			if (acc.iban == null) acc.iban = map.getProperty("iban");
			if (acc.bic == null) acc.bic = map.getProperty("bic");
		}
		
		xmlBuf.append("<result command=\"setAccount\">");
		xmlGen.booleTag("checkResult", true);
		xmlBuf.append("</result>.");
		out.write(xmlBuf.toString());
		out.flush();
	}
	
	private void changeAccount() throws IOException {
		String bankCode = getParameter(map, "bankCode");
		String accountNumber = getParameter(map, "accountNumber");
		String userId = getParameter(map, "userId");
		String subNumber = map.getProperty("subNumber");
		
		Konto acc = accountWithId(userId, bankCode, accountNumber, subNumber);
		if(acc != null) {
			acc.bic = map.getProperty("bic");
			acc.iban = map.getProperty("iban");
			acc.name = getParameter(map, "ownerName");
			acc.type = map.getProperty("name");
		}
		
		xmlBuf.append("<result command=\"changeAccount\">");
		xmlGen.booleTag("checkResult", true);
		xmlBuf.append("</result>.");
		out.write(xmlBuf.toString());
		out.flush();
	}
	
	private void getJobRestrictions() throws IOException {
		String userId = getParameter(map, "userId");
		String userBankCode = getParameter(map, "userBankCode");
		String jobName = getParameter(map, "jobName");

		HBCIHandler handler = hbciHandler(userBankCode, userId);
		if(handler != null) {
			HBCIJob job = handler.newJob(jobName);
			Properties props = job.getJobRestrictions();
			ArrayList<String> textKeys = new ArrayList<String>();
			ArrayList<String> countryRestrictions = new ArrayList<String>();
			for(Enumeration e = props.keys(); e.hasMoreElements(); ) {
				String key = (String)e.nextElement();
				if(key.startsWith("key")) textKeys.add(props.getProperty(key)); else
				if(key.startsWith("countryinfo")) {
					String s = props.getProperty(key);
					int i = s.indexOf(";");
					String countryNum = s.substring(0, i);
					String [] cInfos = (String[])countryInfos.get(countryNum);
					if(cInfos == null) {
						System.err.println("Not supported country: " + countryNum);
						continue;
					}
					String countryId = cInfos[2];
					countryRestrictions.add(countryId+";"+s);
				} else
					xmlGen.tag(key, props.getProperty(key));
			}
			xmlBuf.append("<textKeys type=\"list\">");
			for(String k: textKeys) xmlGen.tag("key", k);
			xmlBuf.append("</textKeys>");
			xmlBuf.append("<countryInfos type=\"list\">");
			for(String k: countryRestrictions) xmlGen.tag("info", k);
			xmlBuf.append("</countryInfos>");
			
		}
		out.write("<result command=\"getJobRestrictions\"><dictionary>");
		out.write(xmlBuf.toString());
		out.write("</dictionary></result>.");
		out.flush();
	}
	
	private void getBankParameter() throws IOException {
		String userId = getParameter(map, "userId");
		String userBankCode = getParameter(map, "userBankCode");

		HBCIHandler handler = hbciHandler(userBankCode, userId);
		if(handler == null) {
			error(ERR_MISS_USER, "getBankParameter", userId);
			return;
		}
		
		// Construct the result. Add all values sorted by key.
		HBCIPassport passport = handler.getPassport();
		xmlBuf.append("<result command=\"getBankParameter\"><object type=\"BankParameter\">");
		
		Vector sortedKeys = new Vector();
		Properties bpd = passport.getBPD();
		xmlBuf.append("<bpd type=\"dictionary\">");
		for (Enumeration e = bpd.keys(); e.hasMoreElements(); ) {
			sortedKeys.add((String)e.nextElement());
		}
		Collections.sort(sortedKeys);
		for(Enumeration e = sortedKeys.elements(); e.hasMoreElements(); ) {
			String key = (String)e.nextElement();
			xmlGen.tag(key, bpd.getProperty(key));
		}
		
		xmlBuf.append("</bpd>");
		
		sortedKeys.clear();
		xmlBuf.append("<upd type=\"dictionary\">");
		Properties upd = passport.getUPD();
		for (Enumeration e = upd.keys(); e.hasMoreElements(); ) {
			sortedKeys.add((String)e.nextElement());
		}
		Collections.sort(sortedKeys);
		for(Enumeration e = sortedKeys.elements(); e.hasMoreElements(); ) {
			String key = (String)e.nextElement();
			xmlGen.tag(key, upd.getProperty(key));
		}
		xmlBuf.append("</upd>");
		xmlBuf.append("</object></result>.");
		out.write(xmlBuf.toString());
		out.flush();
	}

	private void getBankParameterRaw() throws IOException {
		String userId = getParameter(map, "userId");
		String userBankCode = getParameter(map, "userBankCode");

		HBCIHandler handler = hbciHandler(userBankCode, userId);
		if(handler == null) {
			error(ERR_MISS_USER, "getBankParameter", userId);
			return;
		}
		HBCIPassport passport = handler.getPassport();
		xmlBuf.append("<result command=\"getBankParameter\"><object type=\"BankParameter\">");
		Properties bpd = passport.getBPD();
		if (bpd != null) {
			xmlBuf.append("<bpd_raw>");
			
			String[] keys = (String[])bpd.keySet().toArray(new String[bpd.keySet().size()]);
			java.util.Arrays.sort(keys);
			
			for(int i=0; i<keys.length; i++) {
				String key = keys[i];
				if(i > 0) xmlBuf.append("\n");
				xmlBuf.append(key+"="+bpd.getProperty(key));
			}
			xmlBuf.append("</bpd_raw>");			
		}

		
		Properties upd = passport.getUPD();
		if (upd != null) {
			xmlBuf.append("<upd_raw>");
			String keys[] = (String[])upd.keySet().toArray(new String[upd.keySet().size()]);
			java.util.Arrays.sort(keys);
			
			for(int i=0; i<keys.length; i++) {
				String key = keys[i];
				if(i > 0) xmlBuf.append("\n");
				xmlBuf.append(key+"="+upd.getProperty(key));
			}
			xmlBuf.append("</upd_raw>");
		}

		xmlBuf.append("</object></result>.");
		out.write(xmlBuf.toString());
		out.flush();
	}
	
	private ArrayList<String> getAllowedGVs(HBCIPassport passport, Konto account) {
		
		ArrayList<String> result = (ArrayList<String>)account.allowedGVs;
		
		if(result == null) {
			// general purpose: get supported GVs for each account - should be separated later
			Properties upd = passport.getUPD();
			result = new ArrayList<String>();
			
			String header=null;
			
			// first find right header (KInfo*)
            for (int i=0;;i++) {
                header=HBCIUtilsInternal.withCounter("KInfo",i);
                String number=upd.getProperty(header+".KTV.number");

                if (number==null) {
                	header = null;
                	break;
                }
                if(number.equals(account.number)) {
                	if(account.subnumber!=null) {
                        String subNumber = upd.getProperty(header+".KTV.subnumber");
                    	if(!account.subnumber.equals(subNumber)) continue;
                	}
            		// workaround: add all codes of all accounts that fit
                    for (int j=0;;j++) {
                    	String gvHeader = HBCIUtilsInternal.withCounter(header+".AllowedGV", j);
                    	String code = upd.getProperty(gvHeader+".code");
                    	if (code == null) break;
                    	result.add(code);
                    }
                }
            }
            if(account != null) account.allowedGVs = result;
            
// workaround            
/*            
            if(header!=null) {
                // allowedGVs
                ArrayList<String> codes = new ArrayList<String>();
                for (int j=0;;j++) {
                	String gvHeader = HBCIUtilsInternal.withCounter(header+".AllowedGV", j);
                	String code = upd.getProperty(gvHeader+".code");
                	if (code == null) break;
                	codes.add(code);
                }
                if(account != null) account.allowedGVs = codes;
                result = codes;
            }
*/             
            
            
/* 			
			Properties accNums = new Properties();
			for(Enumeration e = upd.keys(); e.hasMoreElements(); ) {
				String key = (String)e.nextElement();
				// AccKey = "KInfo_x", collect GVs under this key
				if(key.matches("KInfo\\w*.AllowedGV\\w*.code")) {
					String accKey = key.substring(0, key.indexOf('.'));
					ArrayList<String> gvcodes= (ArrayList<String>)gvs.get(accKey);
					if(gvcodes == null) {
						gvcodes = new ArrayList<String>();
						gvs.put(accKey, gvcodes);
					}
					gvcodes.add((String)upd.get(key));
				} else if(key.matches("KInfo\\w*.KTV.number")) {
					// AccKey = "KInfo_x", store account key under this key
					String accKey = key.substring(0, key.indexOf('.'));
					String val = accNums.getProperty(accKey);
					// if value already exists, it must be the subnumber
					if(val == null) accNums.put(accKey, upd.get(key));
					else accNums.put(accKey, upd.get(key)+val);
				} else if(key.matches("KInfo\\w*.KTV.subnumber")) {
					// AccKey = "KInfo_x", store account key under this key
					String accKey = key.substring(0, key.indexOf('.'));
					String val = accNums.getProperty(accKey);
					String subNum = (String) upd.get(key);
					if(subNum != null) {
						// if value already exists, it must be the account number
						if(val == null) accNums.put(accKey, subNum);
						else accNums.put(accKey, val+subNum);					
					}
				}
			}
			
			HBCIUtils.log(accNums.toString(), HBCIUtils.LOG_DEBUG);
			HBCIUtils.log(gvs.toString(), HBCIUtils.LOG_DEBUG);
			
			// now merge it
			for(Enumeration e = accNums.keys(); e.hasMoreElements(); ) {
				String key = (String)e.nextElement();
				ArrayList<String> gvcodes= (ArrayList<String>)gvs.get(key);
				if(gvcodes != null) {
					String accountKey = (String)accNums.get(key);  // accountNumber+subNumber
					ArrayList<String> existingCodes = (ArrayList<String>)gvs.get(accountKey);
					// workaround until we have full account type support:
					// union all gvcodes of equal account (numbers)
					if(existingCodes == null) {
						gvs.put(accountKey, gvcodes);
					} else {
						existingCodes.addAll(gvcodes);
					}
				}
				gvs.remove(key);
			}
			
			HBCIUtils.log(gvs.toString(), HBCIUtils.LOG_DEBUG);

			result = (ArrayList<String>)gvs.get(accountNumber+subNumber);
			if(account != null) account.allowedGVs = result;
		*/
		}
		
		return result;
	}

	private boolean isJobSupported(String jobName, Konto account, HBCIHandler handler) {
		HBCIPassport passport = handler.getPassport();
		ArrayList<String> gvcodes = getAllowedGVs(passport, account);
		boolean supp = false;
		
		if(handler.isSupported(jobName) == false) {
			HBCIUtils.log("Job "+jobName+" is not supported by handler", HBCIUtils.LOG_DEBUG);
			return false;
		}
		
		if(gvcodes != null) {
			if(jobName.equals("UebForeign")) supp = gvcodes.contains("HKAOM");
			else if(jobName.equals("Ueb")) supp = gvcodes.contains("HKUEB");
			else if(jobName.equals("UebSEPA")) supp = gvcodes.contains("HKCCS");
			else if(jobName.equals("TermUebSEPA")) supp = gvcodes.contains("HKCSE");
			else if(jobName.equals("DauerSEPAList")) supp = gvcodes.contains("HKCDB");
			else if(jobName.equals("DauerSEPANew")) supp = gvcodes.contains("HKCDE");
			else if(jobName.equals("DauerSEPAEdit")) supp = gvcodes.contains("HKCDN");
			else if(jobName.equals("DauerSEPADel")) supp = gvcodes.contains("HKCDL");
			else if(jobName.equals("TermUeb")) supp = gvcodes.contains("HKTUE");
			else if(jobName.equals("DauerList")) supp = gvcodes.contains("HKDAB");
			else if(jobName.equals("DauerNew")) supp = gvcodes.contains("HKDAE");
			else if(jobName.equals("DauerEdit")) supp = gvcodes.contains("HKDAN");
			else if(jobName.equals("DauerDel")) supp = gvcodes.contains("HKDAL");			
			else if(jobName.equals("Umb")) supp = gvcodes.contains("HKUMB");
			else if(jobName.equals("TANMediaList")) supp = gvcodes.contains("HKTAB");
			else if(jobName.equals("MultiUeb")) supp = gvcodes.contains("HKSUB");
			else if(jobName.equals("KUmsAll")) supp = gvcodes.contains("HKKAZ");
			else if(jobName.equals("KKUmsAll")) supp = gvcodes.contains("DKKKU");
			else if(jobName.equals("KKSettleList")) supp = gvcodes.contains("DKKAU");
			else if(jobName.equals("KKSettleReq")) supp = gvcodes.contains("DKKKA");
			else if(jobName.equals("SaldoReq")) supp = gvcodes.contains("HKSAL");
			else if(jobName.equals("ChangePin")) supp = gvcodes.contains("DKPAE") || gvcodes.contains("HKPAE");
		} else supp = true;
		
		if(!supp) {
			HBCIUtils.log("Job "+jobName+" is not supported, supported jobs for account "+account.number+" are:", HBCIUtils.LOG_DEBUG);
			HBCIUtils.log(gvcodes.toString(), HBCIUtils.LOG_DEBUG);
		}
		
		return supp;
	}	
	
	private void isJobSupported() throws IOException {
		String accountNumber = getParameter(map, "accountNumber");
		String subNumber = map.getProperty("subNumber");
		String userId = getParameter(map, "userId");
		String userBankCode = getParameter(map, "userBankCode");
		String bankCode = getParameter(map, "bankCode");
		String jobName = getParameter(map, "jobName");
		boolean isSEPA = map.getProperty("isSEPA") != null && map.getProperty("isSEPA").equals("yes");
		boolean supp = false;

		// get SEPA job version, if not already passed
		if(isSEPA) jobName = getSEPAJob(jobName);
		
		HBCIHandler handler = hbciHandler(userBankCode, userId);
		if(handler != null) {
			Konto account = accountWithId(userId, bankCode, accountNumber, subNumber);
			if(account == null) {
				error(ERR_MISS_ACCOUNT, "isJobSupported",accountNumber);
				return;
			}
			supp = isJobSupported(jobName, account, handler);
		}		
		xmlBuf.append("<result command=\"isSupported\">");
		xmlGen.booleTag("isSupported", supp);
		xmlBuf.append("</result>.");
		out.write(xmlBuf.toString());
		out.flush();
	}
	
	private void supportedJobsForAccount() throws IOException {
		String accountNumber = getParameter(map, "accountNumber");
		String subNumber = map.getProperty("subNumber");
		String userId = getParameter(map, "userId");
		String userBankCode = getParameter(map, "userBankCode");

		HBCIHandler handler = hbciHandler(userBankCode, userId);
		xmlBuf.append("<result command=\"supportedJobsForAccount\"><list>");
		if(handler != null) {
			HBCIPassport passport = handler.getPassport();
			Konto[] ppAccounts = passport.getAccounts();
			for(Konto k: ppAccounts) {
				if((accountNumber.equals(k.number) && (subNumber == null || subNumber.equals(k.subnumber))) || accountNumber.equals("*")) {
					xmlGen.accountJobsToXml(k, handler);
				}
			}
		}
		xmlBuf.append("</list></result>.");
		out.write(xmlBuf.toString());
		out.flush();
	}
	
	private void updateUserData() throws IOException {
		String userId = getParameter(map, "userId");
		String userBankCode = getParameter(map, "userBankCode");
		
		HBCIHandler handler = hbciHandler(userBankCode, userId);
		
		HBCIDialogStatus status = handler.refreshXPD(HBCIHandler.REFRESH_BPD | HBCIHandler.REFRESH_UPD);
		
		// check for SEPA account information
		HBCIPassport passport = handler.getPassport();
		Properties upd = passport.getUPD();
		if(upd != null && upd.containsValue("HKSPA")) {
			handler.updateSEPAInfo();
		}
		
		xmlBuf.append("<result command=\"updateUserData\">");
		if(status.isOK() == false) {
			error(ERR_GENERIC, "updateUserData", status.getErrorString());
			return;
		}
		xmlGen.passportToXml(handler, true);
		xmlBuf.append("</result>.");
		out.write(xmlBuf.toString());
		out.flush();
	}
	
	private void getUserData() throws IOException {
		String userId = getParameter(map, "userId");
		String userBankCode = getParameter(map, "userBankCode");
		
		HBCIHandler handler = hbciHandler(userBankCode, userId);
		if(handler == null) {
			error(ERR_MISS_USER, "getUserData", userId);
			return;			
		}
		xmlBuf.append("<result command=\"getUserData\">");
		xmlGen.passportToXml(handler, true);
		xmlBuf.append("</result>.");
		out.write(xmlBuf.toString());
		out.flush();		
	}
	
	private void error(int code, String command, String msg) throws IOException {
		xmlBuf.delete(0, xmlBuf.length());
		xmlBuf.append("<result command=\""+command+"\">");
		xmlBuf.append("<error code=\""+Integer.toString(code)+"\">");
		xmlGen.tag("msg", msg);
		xmlBuf.append("</error>");
		xmlBuf.append("</result>.");
		out.write(xmlBuf.toString());
		out.flush();
	}
	
	private void resetPinTanMethod() throws IOException {
		String userId = getParameter(map, "userId");
		String userBankCode = getParameter(map, "userBankCode");

		
		HBCIHandler handler = hbciHandler(userBankCode, userId);
		HBCIPassportPinTan passport = (HBCIPassportPinTan)handler.getPassport();
		passport.resetSecMechs();
		passport.getCurrentTANMethod(true);

		xmlBuf.append("<result command=\"resetPinTanMethod\">");
		xmlGen.passportToXml(handler, false);
		xmlBuf.append("</result>.");
		out.write(xmlBuf.toString());
		out.flush();
	}
	
	private void setLogLevel() throws IOException {
		String level = getParameter(map, "logLevel");
		Integer logLevel = Integer.valueOf(level);
		logLevel += 1;
		if(logLevel > 5) logLevel = 5;
        HBCIUtils.setParam("log.loglevel.default", logLevel.toString());
        out.write("<result command=\"setLogLevel\"></result>.");
        out.flush();
	}
	
	private void getAccInfo() throws IOException {
		String bankCode = getParameter(map, "bankCode");
		String userId = getParameter(map, "userId");
		String userBankCode = getParameter(map, "userBankCode");
		String accountNumber = getParameter(map, "accountNumber");
		String subNumber = map.getProperty("subNumber");
		
		HBCIHandler handler = hbciHandler(userBankCode, userId);
		if(handler == null) {
			error(ERR_MISS_USER, "getAccInfo", userId);
			return;			
		}
		
		Konto account = accountWithId(userId, bankCode, accountNumber, subNumber);
		if(account == null) {
			account = getAccount(handler.getPassport(), accountNumber, subNumber);
			if(account == null) {
				error(ERR_MISS_ACCOUNT, "getAccInfo",accountNumber);
				return;
			}
		}
		
		HBCIJob job = handler.newJob("AccInfo");
		job.setParam("my", account);
		job.setParam("all", "J");
		job.addToQueue();
		HBCIExecStatus stat = handler.execute();

//		boolean isOk = false; TODO: unused
		GVRAccInfo res = null;
		if(stat.isOK()) {
			res = (GVRAccInfo)job.getJobResult();
//			if(res.isOK()) isOk = true;
		}
		System.out.println(res.toString());

	}
	
	private GVRSaldoReq getBalance(HBCIHandler handler, Konto account)
	{
		HBCIJob job = handler.newJob("SaldoReq");
		job.setParam("my", account);

		job.addToQueue();
		HBCIExecStatus stat = handler.execute();
		
		if(stat.isOK()) return (GVRSaldoReq)job.getJobResult();
		else return null;
	}
	
	private void getBalance() throws IOException {
		String bankCode = getParameter(map, "bankCode");
		String userId = getParameter(map, "userId");
		String userBankCode = getParameter(map, "userBankCode");
		String accountNumber = getParameter(map, "accountNumber");
		String subNumber = map.getProperty("subNumber");
		
		HBCIHandler handler = hbciHandler(userBankCode, userId);
		if(handler == null) {
			error(ERR_MISS_USER, "getBalance", userId);
			return;			
		}
		
		Konto account = accountWithId(userId, bankCode, accountNumber, subNumber);
		if(account == null) {
			account = getAccount(handler.getPassport(), accountNumber, subNumber);
			if(account == null) {
				error(ERR_MISS_ACCOUNT, "getBalance",accountNumber);
				return;
			}
		}
		
		GVRSaldoReq res = getBalance(handler, account);

		xmlBuf.append("<result command=\"getBalance\"><dictionary>");
		boolean isOk = false;
		if(res != null) {
	        if(res.isOK()) isOk = true;
			GVRSaldoReq.Info[] saldi = res.getEntries();
			if(saldi.length > 0) {
				GVRSaldoReq.Info info = saldi[0];
				Value saldo = info.ready.value;
				xmlGen.valueTag("balance", saldo);
			}
		}
		
		xmlGen.booleTag("isOk", isOk);
		xmlBuf.append("</dictionary></result>.");
		out.write(xmlBuf.toString());
		out.flush();
	}
	
	private void changePin() throws IOException {
		String userId = getParameter(map, "userId");
		String userBankCode = getParameter(map, "userBankCode");

		HBCIHandler handler = hbciHandler(userBankCode, userId);
		if(handler == null) {
			error(ERR_MISS_USER, "changePin", userId);
			return;			
		}
		HBCIPassport passport = handler.getPassport();
		if(passport instanceof HBCIPassportPinTan) {
			HBCIPassportPinTan pp = (HBCIPassportPinTan)passport;
			pp.setCurrentTANMethod(null);
		}
		
		// check: DKPAE or HKPAE?
		String jobName = null;
		Properties upd = passport.getUPD();
		for(Enumeration e = upd.keys(); e.hasMoreElements(); ) {
			String key = (String)e.nextElement();
			if(key.matches("KInfo\\w*.AllowedGV\\w*.code")) {
				if(upd.get(key).equals("DKPAE")) {
					jobName = "ChangePINOld";
					break;
				}
				if(upd.get(key).equals("HKPAE")) {
					jobName = "ChangePIN";
					break;
				}
			}
		}
		boolean isOk = false;
		if(jobName != null) {
			HBCIJob job = handler.newJob(jobName);
			job.setParam("newpin", getParameter(map, "newPin"));
			job.addToQueue();
			HBCIExecStatus stat = handler.execute();
			isOk = stat.isOK();
		}
		xmlBuf.append("<result command=\"changePin\">");
		xmlGen.booleTag("isOk", isOk);
		xmlBuf.append("</result>.");
		out.write(xmlBuf.toString());
		out.flush();
	}
	
	private void customerMessage() throws IOException {
		String bankCode = getParameter(map, "bankCode");
		String userId = getParameter(map, "userId");
		String userBankCode = getParameter(map, "userBankCode");
		String accountNumber = getParameter(map, "accountNumber");
		String subNumber = map.getProperty("subNumber");
		
		String msgHead = map.getProperty("head");
		String msgBody = getParameter(map, "body");
		String receipient = map.getProperty("recpt");
		
		HBCIHandler handler = hbciHandler(userBankCode, userId);
		if(handler == null) {
			error(ERR_MISS_USER, "customerMessage", userId);
			return;			
		}
		HBCIPassport passport = handler.getPassport();
		if(passport instanceof HBCIPassportPinTan) {
			HBCIPassportPinTan pp = (HBCIPassportPinTan)passport;
			pp.setCurrentTANMethod(null);
		}
		
		Konto account = accountWithId(userId, bankCode, accountNumber, subNumber);
		if(account == null) {
			account = getAccount(handler.getPassport(), accountNumber, subNumber);
			if(account == null) {
				error(ERR_MISS_ACCOUNT, "customerMessage",accountNumber);
				return;
			}
		}
		
		HBCIJob job = handler.newJob("CustomMsg");
		job.setParam("my", account);
		if(msgHead != null) job.setParam("betreff", msgHead);
		job.setParam("msg", msgBody);
		if(receipient != null) job.setParam("recpt", receipient);
		
		job.addToQueue();
		HBCIExecStatus stat = handler.execute();

		boolean isOk = false;
		HBCIJobResult res = null;
		if(stat.isOK()) {
			res = job.getJobResult();
			if(res.isOK()) isOk = true;
		}
		xmlBuf.append("<result command=\"customerMessage\">");
		xmlGen.booleTag("isOk", isOk);
		xmlBuf.append("</result>.");
		out.write(xmlBuf.toString());
		out.flush();
	}
	
	private void getCCBalance() throws IOException {
		String bankCode = getParameter(map, "bankCode");
		String userId = getParameter(map, "userId");
		String userBankCode = getParameter(map, "userBankCode");
		String accountNumber = getParameter(map, "accountNumber");
		String subNumber = map.getProperty("subNumber");
		
		HBCIHandler handler = hbciHandler(userBankCode, userId);
		if(handler == null) {
			error(ERR_MISS_USER, "getAllCCStatements", userId);
			return;			
		}
		
		Konto account = accountWithId(userId, bankCode, accountNumber, subNumber);
		if(account == null) {
			account = getAccount(handler.getPassport(), accountNumber, subNumber);
			if(account == null) {
				error(ERR_MISS_ACCOUNT, "getCCBalance",accountNumber);
				return;
			}
		}
	
		HBCIJob job = handler.newJob("KKSaldoReq");
		job.setParam("my", account);
		job.setParam("cc_number", accountNumber);
		
		job.addToQueue();
		HBCIExecStatus stat = handler.execute();

		boolean isOk = false;
		GVRKKSaldoReq res = null;
		if(stat.isOK()) {
			res = (GVRKKSaldoReq)job.getJobResult();
			if(res.isOK()) isOk = true;
		}
		xmlBuf.append("<result command=\"getCCBalance\">");
		if(isOk == true) xmlGen.ccBalanceToXml(res);
		xmlBuf.append("</result>.");
		out.write(xmlBuf.toString());
		out.flush();
	}
	
	private void getCCSettlementList() throws IOException {
		String bankCode = getParameter(map, "bankCode");
		String userId = getParameter(map, "userId");
		String userBankCode = getParameter(map, "userBankCode");
		String accountNumber = getParameter(map, "accountNumber");
		String subNumber = map.getProperty("subNumber");
		
		HBCIHandler handler = hbciHandler(userBankCode, userId);
		if(handler == null) {
			error(ERR_MISS_USER, "getCCSettlementList", userId);
			return;			
		}
		
		Konto account = accountWithId(userId, bankCode, accountNumber, subNumber);
		if(account == null) {
			account = getAccount(handler.getPassport(), accountNumber, subNumber);
			if(account == null) {
				error(ERR_MISS_ACCOUNT, "getCCSettlementList",accountNumber);
				return;
			}
		}
		
		HBCIJob job = handler.newJob("KKSettleList");
		job.setParam("my", account);
		job.setParam("cc_number", accountNumber);
		
		job.addToQueue();
		HBCIExecStatus stat = handler.execute();

		boolean isOk = false;
		GVRKKSettleList res = null;
		if(stat.isOK()) {
			res = (GVRKKSettleList)job.getJobResult();
			if(res.isOK()) isOk = true;
		}
		xmlBuf.append("<result command=\"getCCSettlementList\">");
		if(isOk == true) xmlGen.ccSettleListToXml(res);
		xmlBuf.append("</result>.");
		out.write(xmlBuf.toString());
		out.flush();
	}
	
	private void getCCSettlement() throws IOException {
		String bankCode = getParameter(map, "bankCode");
		String userId = getParameter(map, "userId");
		String userBankCode = getParameter(map, "userBankCode");
		String accountNumber = getParameter(map, "accountNumber");
		String settleID = getParameter(map, "settleID");
		String subNumber = map.getProperty("subNumber");
		
		HBCIHandler handler = hbciHandler(userBankCode, userId);
		if(handler == null) {
			error(ERR_MISS_USER, "getCCSettlement", userId);
			return;			
		}
		
		Konto account = accountWithId(userId, bankCode, accountNumber, subNumber);
		if(account == null) {
			account = getAccount(handler.getPassport(), accountNumber, subNumber);
			if(account == null) {
				error(ERR_MISS_ACCOUNT, "getCCSettlement",accountNumber);
				return;
			}
		}

		HBCIJob job = handler.newJob("KKSettleReq");
		job.setParam("my", account);
		job.setParam("cc_number", accountNumber);
		job.setParam("settleID", settleID);
		
		job.addToQueue();
		HBCIExecStatus stat = handler.execute();

		boolean isOk = false;
		GVRKKSettleReq res = null;
		if(stat.isOK()) {
			res = (GVRKKSettleReq)job.getJobResult();
			if(res.isOK()) isOk = true;
		}
		xmlBuf.append("<result command=\"getCCSettlement\">");
		if(isOk == true) xmlGen.ccSettlementToXml(res, account);
		xmlBuf.append("</result>.");
		out.write(xmlBuf.toString());
		out.flush();
	}
	
	private void getAllCCStatements() throws IOException {
		String bankCode = getParameter(map, "bankCode");
		String userId = getParameter(map, "userId");
		String userBankCode = getParameter(map, "userBankCode");
		String accountNumber = getParameter(map, "accountNumber");
		//String ccnum = getParameter(map,"cc_number");
		String subNumber = map.getProperty("subNumber");
		
		HBCIHandler handler = hbciHandler(userBankCode, userId);
		if(handler == null) {
			error(ERR_MISS_USER, "getAllCCStatements", userId);
			return;			
		}
		
		Konto account = accountWithId(userId, bankCode, accountNumber, subNumber);
		if(account == null) {
			account = getAccount(handler.getPassport(), accountNumber, subNumber);
			if(account == null) {
				error(ERR_MISS_ACCOUNT, "getAllCCStatements",accountNumber);
				return;
			}
		}
	
		HBCIJob job = handler.newJob("KKUmsAll");
		job.setParam("my", account);
		job.setParam("cc_number", accountNumber);
		
		String fromDateString = map.getProperty("fromDate");
		if(fromDateString != null) {
			Date fromDate = HBCIUtils.string2DateISO(fromDateString);
			if(fromDate != null) {
				job.setParam("startdate", fromDate);
			}
		}
		String toDateString = map.getProperty("toDate");
		if(toDateString != null) {
			Date toDate = HBCIUtils.string2DateISO(toDateString);
			if(toDate != null) {
				job.setParam("enddate", toDate);
			}
		}
		
		job.addToQueue();
		HBCIExecStatus stat = handler.execute();

		boolean isOk = false;
		GVRKKUms res = null;
		if(stat.isOK()) {
			res = (GVRKKUms)job.getJobResult();
			if(res.isOK()) isOk = true;
		}
		xmlBuf.append("<result command=\"getAllCCStatements\">");
		if(isOk == true) xmlGen.ccUmsAllToXml(res, account);
		xmlBuf.append("</result>.");
		out.write(xmlBuf.toString());
		out.flush();
	}
	
	private void getTANMediaList() throws IOException {
		String userId = getParameter(map, "userId");
		String userBankCode = getParameter(map, "userBankCode");
		
		HBCIHandler handler = hbciHandler(userBankCode, userId);
		if(handler == null) {
			error(ERR_MISS_USER, "getTANMediaList", userId);
			return;			
		}
		
		// check if job is supported
		//Properties bpd = handler.getPassport().getBPD();
		boolean isSupported = handler.isSupported("TANMediaList");
		/*
		for(Enumeration e = bpd.keys(); e.hasMoreElements(); ) {
			String key = (String)e.nextElement();
			if(key.matches("Params\\w*.PinTanPar1.ParPinTan.PinTanGV\\w*.segcode")) {
				String gvName = bpd.getProperty(key);
				if(gvName.equals("HKTAB")) isSupported = true;
			}
		}
		*/
		
		if(isSupported == true) {
			// check if job is supported for user. As this job is not account-specific, check if HKTAB is allowed
			// for at least one account
			HBCIPassport passport = handler.getPassport();
			Properties upd = passport.getUPD();
			if(upd != null) {
				boolean isSupportedUPD = false;
				for(Enumeration e = upd.keys(); e.hasMoreElements(); ) {
					String key = (String)e.nextElement();
					if(key.matches("KInfo\\w*.AllowedGV\\w*.code")) {
						String gvName = upd.getProperty(key);
						if(gvName.equals("HKTAB")) {
							isSupportedUPD = true;
							break;
						}
					}
				}
				if(isSupportedUPD == false) isSupported = false;
			}
		}
		
		if(isSupported == false) {
			xmlBuf.append("<result command=\"getTANMediaList\"></result>.");
			out.write(xmlBuf.toString());
			out.flush();
			return;
		}
		
		HBCIJob job = handler.newJob("TANMediaList");
		job.setParam("mediatype", "1");
		job.setParam("mediacategory", "A");
		job.addToQueue();
		HBCIExecStatus stat = handler.execute();

		xmlBuf.append("<result command=\"getTANMediaList\">");
		if(stat.isOK()) {
			GVRTANMediaList res = (GVRTANMediaList)job.getJobResult();
			xmlGen.tanMediaListToXml(res);
		}
		xmlBuf.append("</result>.");
		out.write(xmlBuf.toString());
		out.flush();
	}
	
	private void getInitialBPD() throws IOException {
		String bankCode = getParameter(map, "bankCode");
		Properties bpd = null;

		suppressErrors = true;
		HBCIPassportPinTanAnon	passport = new HBCIPassportPinTanAnon(bankCode);
		if(passport.isReady() == true) {
			HBCIKernelImpl kernel = new HBCIKernelImpl(null,passport.getHBCIVersion());
			HBCIInstitute inst = new HBCIInstitute(kernel, passport, true);
			inst.fetchBPD();
			bpd = passport.getBPD();
		}
		
		suppressErrors = false;
		// search for PIN/TAN Information
		xmlBuf.append("<result command=\"getInitialBPD\">");
		xmlBuf.append("<object type=\"BankSetupInfo\">");
		if (bpd != null) {
			for(Enumeration e = bpd.keys(); e.hasMoreElements(); ) {
				String key = (String)e.nextElement();
				String s = bpd.getProperty(key);
				if (key.endsWith("info_userid")) {
					xmlGen.tag("info_userid", bpd.getProperty(key));
				}
				if (key.endsWith("info_customerid")) {
					xmlGen.tag("info_customerid", bpd.getProperty(key));
				}
				if (key.endsWith("pinlen_min")) {
					if(s != null) xmlGen.intTag("pinlen_min", Integer.parseInt(s));
				}
				if (key.endsWith("pinlen_max")) {
					if(s != null) xmlGen.intTag("pinlen_max", Integer.parseInt(s));
				}
				if (key.endsWith("tanlen_max")) {
					if(s != null) xmlGen.intTag("tanlen_max", Integer.parseInt(s));
				}
			}			
		}
		xmlBuf.append("</object></result>.");
		out.write(xmlBuf.toString());
		out.flush();
	}
	
	private void getTANMethods() throws IOException {
		String userId = getParameter(map, "userId");
		String userBankCode = getParameter(map, "userBankCode");
		
		HBCIHandler handler = hbciHandler(userBankCode, userId);
		if(handler == null) {
			error(ERR_MISS_USER, "getTANMethods", userId);
			return;			
		}
		
		AbstractPinTanPassport pp = (AbstractPinTanPassport)handler.getPassport();
		
		Hashtable tanMethods = pp.getTwostepMechanisms();
		List allowedMethods = pp.getAllowedTwostepMechanisms();
		
		xmlBuf.append("<result command=\"getTANMethods\">");
    	xmlBuf.append("<tanMethods type=\"list\">");
    	for (Enumeration e = tanMethods.keys(); e.hasMoreElements(); ) {
    		String key = (String)e.nextElement();
    		if (allowedMethods.contains(key)) xmlGen.tanMethodToXml((Properties)tanMethods.get(key));
    	}
    	xmlBuf.append("</tanMethods>");
		xmlBuf.append("</result>.");
		out.write(xmlBuf.toString());
		out.flush();
	}
		
	private void getSupportedBusinessTransactions() throws IOException {
		String accountNumber = getParameter(map, "accountNumber");
		String bankCode = getParameter(map, "bankCode");
		String subNumber = map.getProperty("subNumber");		
		String userId = getParameter(map, "userId");
		String userBankCode = getParameter(map, "userBankCode");


		HBCIHandler handler = hbciHandler(userBankCode, userId);
		if (handler == null) {
			error(ERR_MISS_USER, "getSupportedBusinessTransactions", userId);
			return;			
		}

		xmlBuf.append("<result command=\"getSupportedBusinessTransactons\"><list>");
		HBCIPassport passport = handler.getPassport();
		
		// Filter by account and return result.
		Konto account = accountWithId(userId, bankCode, accountNumber, subNumber);
		if(account == null) {
			error(ERR_MISS_ACCOUNT, "isJobSupported",accountNumber);
			return;
		}

		ArrayList<String> gvcodes = getAllowedGVs(passport, account);
		if(gvcodes != null) {
			HashSet<String> gvSet = new HashSet<String>();
			gvSet.addAll(gvcodes);
			for(String s: gvSet) {
				xmlGen.tag("gv", s);				
			}
		};
		
		xmlBuf.append("</list></result>.");
		out.write(xmlBuf.toString());
		out.flush();
	}
	

	private void dispatch(String command) throws IOException {
//			cmd = HBCIServer.class.getMethod(command, new Class[0]);
		xmlBuf = new StringBuffer();
		xmlGen = new XmlGen(xmlBuf);
		
		try {
			if(command.compareTo("addUser") ==0 ) addPassport(); else
			if(command.compareTo("registerUser") == 0) registerPassport(); else
			if(command.compareTo("getOldBankUsers") == 0) getOldBankUsers(); else
			if(command.compareTo("init") == 0) init(); else
			if(command.compareTo("getAllStatements") == 0) getAllStatements(); else
			if(command.compareTo("getAccounts") == 0) getAccounts(); else
			if(command.compareTo("getBankInfo") == 0) getBankInfo(); else
			if(command.compareTo("checkAccount") == 0) checkAccount(); else
			if(command.compareTo("deletePassport") == 0) deletePassport(); else
			if(command.compareTo("setAccount") == 0) setAccount(); else
			if(command.compareTo("sendTransfers") == 0) sendTransfers(); else
			if(command.compareTo("sendTransfer") == 0) sendTransfer(); else
			if(command.compareTo("getJobRestrictions") == 0) getJobRestrictions(); else
			if(command.compareTo("isJobSupported") == 0) isJobSupported(); else
			if(command.compareTo("updateUserData") == 0) updateUserData(); else
			if(command.compareTo("getUserData") == 0) getUserData(); else
			if(command.compareTo("resetPinTanMethod") == 0) resetPinTanMethod(); else
			if(command.compareTo("changeAccount") == 0) changeAccount(); else
			if(command.compareTo("addStandingOrder") == 0) addStandingOrder(); else
			if(command.compareTo("changeStandingOrder") == 0) changeStandingOrder(); else
			if(command.compareTo("deleteStandingOrder") == 0) deleteStandingOrder(); else
			if(command.compareTo("getAllStandingOrders") == 0) getAllStandingOrders(); else
			if(command.compareTo("getBankParameter") == 0) getBankParameter(); else
			if(command.compareTo("setLogLevel") == 0) setLogLevel(); else
			if(command.compareTo("getAccInfo") == 0) getAccInfo(); else
			if(command.compareTo("getAllTermUebs") == 0) getAllTermUebs(); else
			if(command.compareTo("customerMessage") == 0) customerMessage(); else
			if(command.compareTo("getAllCCStatements") == 0) getAllCCStatements(); else
			if(command.compareTo("getCCBalance") == 0) getCCBalance(); else
			if(command.compareTo("getInitialBPD") == 0) getInitialBPD(); else
			if(command.compareTo("getTANMediaList") == 0) getTANMediaList(); else
			if(command.compareTo("getBankParameterRaw") == 0) getBankParameterRaw(); else
			if(command.compareTo("getTANMethods") == 0) getTANMethods(); else
			if(command.compareTo("getBalance") == 0) getBalance(); else
			if(command.compareTo("getSupportedBusinessTransactions") == 0) getSupportedBusinessTransactions(); else
			if(command.compareTo("getCCSettlementList") == 0) getCCSettlementList(); else
			if(command.compareTo("sendCollectiveTransfer") == 0) sendCollectiveTransfer(); else
			if(command.compareTo("supportedJobsForAccount") == 0) supportedJobsForAccount(); else
			if(command.compareTo("getCCSettlement") == 0) getCCSettlement(); else
			if(command.compareTo("changePin") == 0) changePin(); else
			{
				System.err.println("HBCIServer: unknown command: "+command);
				error(ERR_WRONG_COMMAND, command, "Ungültiger Befehl");				
			}			
		}
		catch(HBCI_Exception e) {
		    Throwable e2=e;
		    String msg=null;
		    while (e2!=null) {
		        if ((msg=e2.getMessage())!=null) {
		        	System.err.println(msg);
		        	log(msg,1,new Date());
		        }
		        if(e2 instanceof InvalidPassphraseException) {
		        	error(ERR_WRONG_PASSWD, command, "Ungültiges Passwort");
		        	return; }
		        if(e2 instanceof AbortedException) {
		        	error(ERR_ABORTED, command, "Abbruch durch Benutzer");
		        	return;
		        }
		        e2=e2.getCause();
		    }
		    error(ERR_GENERIC, command, msg);
		    e.printStackTrace();
			return;
		}
		catch(HBCIParamException e) {
			error(ERR_MISS_PARAM,command, e.parameter());
			return;
			
		}
		catch (Exception e) {
		    error(ERR_GENERIC, command, e.getMessage());
			e.printStackTrace();
			return;
		}
	}

	
	private void acceptArray(XmlPullParser xpp, Properties map, String tag) throws XmlPullParserException, IOException {
		int eventType;
		ArrayList list = new ArrayList();
		
		eventType = xpp.next();
		while(eventType != XmlPullParser.END_TAG) {
			if(eventType == XmlPullParser.START_TAG) {
				Properties tmap = new Properties();
				acceptTag(xpp, tmap, null);
				list.add(tmap);
			}
			eventType = xpp.next();
		}
		map.put(tag, list);
	}
		
	private void acceptTag(XmlPullParser xpp, Properties map, String tag) throws XmlPullParserException, IOException {
		int eventType;
		String currTag = null;
		
		String name = xpp.getName();
		String type = xpp.getAttributeValue(null, "type");
		if(type != null && type.equals("list")) acceptArray(xpp, map, name);
		else {
			if(tag != null)	currTag = tag+"."+name; else currTag = name;
			eventType = xpp.next();
			while(eventType != XmlPullParser.END_TAG) {
				if(eventType == XmlPullParser.START_TAG) {
					acceptTag(xpp, map, currTag);
				} else if(eventType == XmlPullParser.TEXT) {
	                if(!xpp.isWhitespace()) {
	               	 if(currTag != null) map.put(currTag, xpp.getText());
	                }
				}
				eventType = xpp.next();
			}
		}
	}
	
	void start() {
		XmlPullParserFactory factory;
		String command = null;

		try {
			String s;
			String cmd = "";

			factory = XmlPullParserFactory.newInstance();
			factory.setNamespaceAware(true);
			XmlPullParser xpp = factory.newPullParser();

			while ((s = in.readLine()) != null && s.length() != 0) {
				// cmd += s;
				if (s.endsWith(".")) {
					s = s.substring(0, s.length() - 1);
					cmd += s;
					xpp.setInput(new StringReader(cmd));
					cmd = "";

					int eventType = xpp.getEventType();
					while (eventType != XmlPullParser.END_DOCUMENT) {
						if (eventType == XmlPullParser.START_DOCUMENT) {
						} else if (eventType == XmlPullParser.END_DOCUMENT) {
						} else if (eventType == XmlPullParser.START_TAG) {
							String tag = xpp.getName();
							if (tag.compareTo("command") == 0) {
								command = xpp.getAttributeValue(null, "name");
								map.clear();
							} else
								acceptTag(xpp, map, null);
						} else if (eventType == XmlPullParser.END_TAG) {
							String tag = xpp.getName();
							if (tag.compareTo("command") == 0) {
								dispatch(command);
							}
						} else if (eventType == XmlPullParser.TEXT) {
						}
						eventType = xpp.next();
					}

				} else
					cmd += s;
			}
		} catch (XmlPullParserException e) {
			try {
			    error(ERR_GENERIC, command, e.getMessage());
				e.printStackTrace();
			}
			catch (IOException x) {
				System.err.println("HBCI Server panic: IO exception occured!");
				x.printStackTrace();
			}
		} catch (IOException e) {
			System.err.println("HBCI Server panic: IO exception occured!");
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	
	public static void main(String[] args) {
		
			try {
				server = new HBCIServer();
				System.err.println("HBCI Server up and running...");
				server.start();
 			} catch (UnsupportedEncodingException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
	}
}
