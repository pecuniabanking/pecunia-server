import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;

import org.kapott.hbci.GV_Result.GVRDauerList;
import org.kapott.hbci.GV_Result.GVRKKSettleReq;
import org.kapott.hbci.GV_Result.GVRKUms;
import org.kapott.hbci.GV_Result.GVRTermUebList;
import org.kapott.hbci.GV_Result.GVRKKSaldoReq;
import org.kapott.hbci.GV_Result.GVRSaldoReq;
import org.kapott.hbci.GV_Result.GVRKKUms;
import org.kapott.hbci.GV_Result.GVRKKSettleList;
import org.kapott.hbci.GV_Result.GVRTANMediaList.TANMediaInfo;
import org.kapott.hbci.GV_Result.GVRTANMediaList;
import org.kapott.hbci.manager.HBCIUtils;
import org.kapott.hbci.manager.HBCIHandler;
import org.kapott.hbci.passport.HBCIPassport;
import org.kapott.hbci.passport.HBCIPassportPinTan;
import org.kapott.hbci.passport.HBCIPassportDDV;
import org.kapott.hbci.structures.Konto;
import org.kapott.hbci.structures.Saldo;
import org.kapott.hbci.structures.Value;

public class XmlGen {
	private StringBuffer xmlBuf;
	
	XmlGen(StringBuffer buf) {
		xmlBuf = buf;
	}
	
    private String escapeSpecial(String s) {
    	String r = s.replaceAll("&", "&amp;");
    	r = r.replaceAll("<", "&lt;");
    	r = r.replaceAll(">", "&gt;");
    	r = r.replaceAll("\"", "&quot;");
    	r = r.replaceAll("'", "&apos;");
    	return r;
    }
    
    public void tag(String tag, String value) throws IOException {
    	if(value == null) return;
    	xmlBuf.append("<"+tag+">"+escapeSpecial(value)+"</"+tag+">");
    }
    
    public void valueTag(String tag, Value value) {
    	if(value == null) return;
    	xmlBuf.append("<"+tag+" type=\"value\">"+Long.toString(value.getLongValue())+"</"+tag+">");
    }
    
    public void longTag(String tag, long l) {
    	xmlBuf.append("<"+tag+" type=\"long\">"+Long.toString(l)+"</"+tag+">");
    }
    
    public void dateTag(String tag, Date date) {
    	if(date == null) return;
    	xmlBuf.append("<"+tag+" type=\"date\">"+HBCIUtils.date2StringISO(date)+"</"+tag+">");
    }
    
    public void intTag(String tag, String value) {
    	if(value == null) return;
    	xmlBuf.append("<"+tag+" type=\"int\">"+value+"</"+tag+">");
    }

    public void intTag(String tag, int value) {
    	xmlBuf.append("<"+tag+" type=\"int\">"+Integer.toString(value)+"</"+tag+">");
    }

    public void binaryTag(String tag, String value) throws UnsupportedEncodingException {
    	if(value != null) xmlBuf.append("<"+tag+" type=\"binary\">"+HBCIUtils.encodeBase64(value.getBytes("ISO-8859-1"))+"</"+tag+">");
    }
    
    public void booleTag(String tag, boolean b) {
    	if(b) xmlBuf.append("<"+tag+" type=\"boole\">yes</"+tag+">");
    	else xmlBuf.append("<"+tag+" type=\"boole\">no</"+tag+">");
    }
    
    public void supportedJobsToXml(HBCIHandler handler, ArrayList<String> gvs) throws IOException {
        if(gvs != null && gvs.contains("HKUEB") || gvs == null && handler.isSupported("Ueb")) tag("name", "Ueb");
        if(gvs != null && gvs.contains("HKTUE") || gvs == null && handler.isSupported("TermUeb")) tag("name", "TermUeb");
        if(gvs != null && gvs.contains("HKAOM") || gvs == null && handler.isSupported("UebForeign")) tag("name", "UebForeign");
        if(gvs != null && gvs.contains("HKCCS") || gvs == null && handler.isSupported("UebSEPA")) tag("name", "UebSEPA");
        if(gvs != null && gvs.contains("HKUMB") || gvs == null && handler.isSupported("Umb")) tag("name", "Umb");
        if(gvs != null && gvs.contains("HKLAS") || gvs == null && handler.isSupported("Last")) tag("name", "Last");
        if(gvs != null && gvs.contains("HKDAE") || gvs == null && handler.isSupported("DauerNew")) tag("name", "DauerNew");
        if(gvs != null && gvs.contains("HKDAN") || gvs == null && handler.isSupported("DauerEdit")) tag("name", "DauerEdit");
        if(gvs != null && gvs.contains("HKDAL") || gvs == null && handler.isSupported("DauerDel")) tag("name", "DauerDel");
        if(gvs != null && gvs.contains("HKTAB") || gvs == null && handler.isSupported("TANMediaList")) tag("name", "TANMediaList");
        if(gvs != null && gvs.contains("HKSUB") || gvs == null && handler.isSupported("MultiUeb")) tag("name", "MultiUeb");
        if(gvs != null && gvs.contains("HKCCM") || gvs == null && handler.isSupported("MultiUebSEPA")) tag("name", "MultiUebSEPA");
        if(gvs != null && gvs.contains("DKKKU") || gvs == null && handler.isSupported("KKUmsAll")) tag("name", "KKUmsAll");
        if(gvs != null && gvs.contains("DKKAU") || gvs == null && handler.isSupported("KKSettleList")) tag("name", "KKSettleList");
        if(gvs != null && gvs.contains("DKKKA") || gvs == null && handler.isSupported("KKSettleReq")) tag("name", "KKSettleReq");
        if(gvs != null && gvs.contains("HKKAZ") || gvs == null && handler.isSupported("KUmsAll")) tag("name", "KUmsAll");
        if(gvs != null && gvs.contains("DKPAE") || gvs == null && handler.isSupported("ChangePINOld")) tag("name", "ChangePin");
        if(gvs != null && gvs.contains("HKPAE") || gvs == null && handler.isSupported("ChangePIN")) tag("name", "ChangePin");
        if(gvs != null && gvs.contains("HKCSE") || gvs == null && handler.isSupported("TermUebSEPA")) tag("name", "TermUebSEPA");
        if(gvs != null && gvs.contains("HKCDE") || gvs == null && handler.isSupported("DauerSEPANew")) tag("name", "DauerSEPANew");
        if(gvs != null && gvs.contains("HKCDN") || gvs == null && handler.isSupported("DauerSEPAEdit")) tag("name", "DauerSEPAEdit");
        if(gvs != null && gvs.contains("HKCDL") || gvs == null && handler.isSupported("DauerSEPADel")) tag("name", "DauerSEPADel");
        if(gvs != null && gvs.contains("HKCDB") || gvs == null && handler.isSupported("DauerSEPAList")) tag("name", "DauerSEPAList");
    }
    
    
    public void accountToXml(Konto account, HBCIHandler handler) throws IOException {
    	HBCIPassport pp = handler.getPassport();
    	
    	xmlBuf.append("<object type=\"Account\">");
    	if(account.curr == null || account.curr.length() == 0) account.curr = "EUR";
    	if(account.country == null || account.curr.length() == 0) account.country = "DE";
    	tag("name", account.type);
    	tag("bankName", HBCIUtils.getNameForBLZ(account.blz));
    	tag("bankCode", account.blz);
    	tag("accountNumber", account.number);
    	tag("ownerName", account.name);
    	tag("currency", account.curr.toUpperCase());
    	tag("country", account.country.toUpperCase());
    	tag("iban", account.iban);
    	tag("bic", account.bic);
    	tag("userId", pp.getUserId());
    	tag("customerId", account.customerid);
    	tag("subNumber", account.subnumber);
    	intTag("type", account.acctype);
		ArrayList<String> gvs = (ArrayList<String>)account.allowedGVs;
		//if(gvs.contains("DKKKU")) intTag("type", 1); else intTag("type",0);
        xmlBuf.append("<supportedJobs type=\"list\">");
        supportedJobsToXml(handler, gvs);
        xmlBuf.append("</supportedJobs>");
    	xmlBuf.append("</object>");
    }
    
    public void accountJobsToXml(Konto account, HBCIHandler handler) throws IOException {
		xmlBuf.append("<object type=\"AccountJobs\">");
		tag("accountNumber", account.number);
		tag("subNumber", account.subnumber);
        xmlBuf.append("<supportedJobs type=\"list\">");
		ArrayList<String> gvs = (ArrayList<String>)account.allowedGVs;
        supportedJobsToXml(handler, gvs);
        xmlBuf.append("</supportedJobs></object>");
    }
    
    public void singleUmsToXml(GVRKUms.UmsLine line, Konto account, boolean isPreliminary) throws IOException {
		StringBuffer purpose = new StringBuffer();
		if(line.gvcode.equals("999")) {
			purpose.append(line.additional);
		}
		else {
    		for(Iterator<String> j = line.usage.iterator(); j.hasNext();) {
    			String s = j.next();
    			purpose.append(s);
    			if(j.hasNext()) purpose.append("\n");
    		}
		}
		
    	xmlBuf.append("<cdObject type=\"BankStatement\">");
    	tag("localAccount", account.number);
    	tag("localSuffix", account.subnumber);
    	tag("localBankCode", account.blz);
    	tag("bankReference", line.instref);
    	tag("currency", line.value.getCurr());
    	tag("customerReference", line.customerref);
    	dateTag("date", line.bdate);
    	dateTag("valutaDate", line.valuta);
    	valueTag("value", line.value);
    	if(line.saldo != null) valueTag("saldo", line.saldo.value);
//    	if(line.saldo != null) dateTag("saldoTimestamp", line.saldo.timestamp);
    	valueTag("charge", line.charge_value);
    	// todo: orig_value
    	tag("primaNota", line.primanota);
    	tag("purpose", purpose.toString());
    	if(line.other != null) {
        	tag("remoteAccount", line.other.number);
        	tag("remoteBankCode", line.other.blz);
        	tag("remoteBIC", line.other.bic);
        	tag("remoteCountry", line.other.country);
        	tag("remoteIBAN", line.other.iban);
        	if(line.other.name2 == null) tag("remoteName", line.other.name);
        	else tag("remoteName", line.other.name + line.other.name2);
    	}
        tag("transactionCode", line.gvcode);
        tag("transactionText", line.text);
        tag("additional", line.additional);
        booleTag("isStorno", line.isStorno);
        booleTag("isPreliminary", isPreliminary);
    	xmlBuf.append("</cdObject>");
    }
  
	@SuppressWarnings("unchecked")
    public boolean umsToXml(GVRKUms ums, Konto account) throws IOException {
    	Value balance = null;
    	
    	List<GVRKUms.UmsLine> lines = ums.getFlatData();
    	List<GVRKUms.UmsLine> lines_unbooked = ums.getFlatDataUnbooked();
    	
    	// if there are no statements, try to get saldo from BTag
    	if(lines.size() == 0) {
    		List<GVRKUms.BTag> days = ums.getDataPerDay();
    		// as there are no statements, it should be o.k. to get the first day
    		if(days.size() > 0) {
    			GVRKUms.BTag dayInfo = days.get(0);
    			balance = dayInfo.end.value;
    		} else {
    			return false;
    		}
    	}

    	xmlBuf.append("<object type=\"BankQueryResult\">");
    	tag("bankCode", account.blz);
    	tag("accountNumber", account.number);
    	tag("accountSubnumber", account.subnumber);
    	
    	if(balance != null) {
			valueTag("balance", balance);    		
    	}
    	
    	xmlBuf.append("<statements type=\"list\">");

    	for(Iterator<GVRKUms.UmsLine> i = lines.iterator(); i.hasNext(); ) {
    		GVRKUms.UmsLine line = i.next();
    		singleUmsToXml(line, account, false);
    	}
    	
    	for(Iterator<GVRKUms.UmsLine> i = lines_unbooked.iterator(); i.hasNext(); ) {
    		GVRKUms.UmsLine line = i.next();
    		singleUmsToXml(line, account, true);
    	}
    	
    	xmlBuf.append("</statements></object>");
    	return true;
    }
    
	public void saldoUmsToXml(GVRSaldoReq res, Konto account) throws IOException  {
    	xmlBuf.append("<object type=\"BankQueryResult\">");
    	tag("bankCode", account.blz);
    	tag("accountNumber", account.number);
    	tag("accountSubnumber", account.subnumber);
    	
    	GVRSaldoReq.Info[] infos = res.getEntries();
    	for(GVRSaldoReq.Info info : infos) {
    		tag("currency", info.ready.value.getCurr());
    		valueTag("balance", info.ready.value);
    		break;
    	}
    	xmlBuf.append("</object>");
	}
	
    public void dauerListToXml(GVRDauerList dl, Konto account) throws IOException {
    	GVRDauerList.Dauer [] standingOrders = dl.getEntries();
    	for(GVRDauerList.Dauer stord: standingOrders) {
    		
        	xmlBuf.append("<cdObject type=\"StandingOrder\">");
//        	tag("localAccount", stord.my.number);
//        	tag("localBankCode", stord.my.blz);
        	tag("currency",stord.my.curr);
        	valueTag("value", stord.value);
        	
        	// purpose
        	int i=1;
        	for(String s: stord.usage) {
        		tag("purpose"+Integer.toString(i), s);
        		i++;
        		if(i>4) break;
        	}
        	
        	// other
        	if(stord.other != null) {
	        	tag("remoteAccount", stord.other.number);
	        	tag("remoteBankCode", stord.other.blz);
	        	tag("remoteIBAN", stord.other.iban);
	        	tag("remoteBIC", stord.other.bic);
	        	tag("remoteSuffix",stord.other.subnumber);
	        	if(stord.other.name2 == null) tag("remoteName", stord.other.name);
	        	else tag("remoteName", stord.other.name + stord.other.name2);
        	}
        	
        	// timeunit
        	if(stord.timeunit.compareTo("W") == 0) intTag("period", 0); else intTag("period", 1);
        	
        	// turnus
        	intTag("cycle", stord.turnus);
        	intTag("executionDay", stord.execday);
        	
        	tag("orderKey", stord.orderid);
        	
        	// exec dates
        	dateTag("firstExecDate", stord.firstdate);
        	dateTag("nextExecDate", stord.nextdate);
        	dateTag("lastExecDate", stord.lastdate);
        	
        	xmlBuf.append("</cdObject>");
    	}
    }
    
    public void termUebListToXml(GVRTermUebList ul, Konto account) throws IOException {
    	GVRTermUebList.Entry [] uebs = ul.getEntries();
    	
    	for(GVRTermUebList.Entry ueb: uebs) {
    		xmlBuf.append("<cdObject type=\"Transfer\">");
        	tag("localAccount", ueb.my.number);
        	tag("localBankCode", ueb.my.blz);
        	tag("currency",ueb.my.curr);
        	valueTag("value", ueb.value);
        	
        	// purpose
        	int i=1;
        	for(String s: ueb.usage) {
        		tag("purpose"+Integer.toString(i), s);
        		i++;
        		if(i>4) break;
        	}
        	
        	// other
        	if(ueb.other != null) {
	        	tag("remoteAccount", ueb.other.number);
	        	tag("remoteBankCode", ueb.other.blz);
	        	tag("remoteSuffix",ueb.other.subnumber);
	        	if(ueb.other.name2 == null) tag("remoteName", ueb.other.name);
	        	else tag("remoteName", ueb.other.name + ueb.other.name2);
        	}

        	// exec date
        	dateTag("date", ueb.date);
        	tag("orderKey", ueb.orderid);
        	xmlBuf.append("</cdObject>");
    	}
    }
    
    public void passportToXml(HBCIHandler handler, boolean withAccounts) throws IOException {
    	HBCIPassport pp = handler.getPassport();
    	xmlBuf.append("<object type=\"User\">");
    	tag("bankCode", pp.getBLZ());
    	tag("bankName", pp.getInstName());
    	tag("userId",pp.getUserId());
    	tag("customerId", pp.getCustomerId());
    	tag("bankURL", pp.getHost());
    	tag("port", pp.getPort().toString());
    	String filter = pp.getFilterType();
    	if(filter.compareTo("Base64") == 0) booleTag("noBase64", false); else booleTag("noBase64", true);  	
    	String version = pp.getHBCIVersion();
    	if(version.compareTo("plus") == 0) version = "220";
    	tag("hbciVersion", version);

    	if(pp instanceof HBCIPassportPinTan) {
    		HBCIPassportPinTan ppPT = (HBCIPassportPinTan)pp;
    		
        	booleTag("checkCert", ppPT.getCheckCert());
        	Properties sec = ppPT.getCurrentSecMechInfo();
        	if(sec != null)	{
        		intTag("tanMethodNumber", sec.getProperty("secfunc"));
        		tag("tanMethodDescription", sec.getProperty("name"));
        	}    		
    	}
    	
    	if(pp instanceof HBCIPassportDDV) {
    		HBCIPassportDDV ppDDV =(HBCIPassportDDV)pp;
    		tag("chipCardId", ppDDV.getCardId());
    	}
    	
    	if(withAccounts == true) {
    		xmlBuf.append("<accounts type=\"list\">");
    		Konto [] accounts = pp.getAccounts();
    		for(Konto k: accounts) {
    			accountToXml(k, handler);
    		}
    		xmlBuf.append("</accounts>");
    	}
    	
    	xmlBuf.append("</object>");    	
    }
        
    public void userToXml(User user) throws IOException {
    	xmlBuf.append("<object type=\"User\">");
    	tag("name", user.name);
    	tag("bankCode", user.bankCode);
    	tag("bankName", user.bankName);
    	tag("userId", user.userId);
    	tag("customerId", user.customerId);
    	tag("bankURL", user.host);
    	tag("port", Integer.toString(user.port));
    	String filter = user.filter;
    	if(filter.compareTo("Base64") == 0) booleTag("noBase64", false); else booleTag("noBase64", true);  	
    	String version = user.version;
    	if(version.compareTo("plus") == 0) version = "220";
    	tag("hbciVersion", version);
    	booleTag("checkCert", user.checkCert);
    	xmlBuf.append("</object>");
    }
    
    public void tanMediumToXml(TANMediaInfo info) throws IOException {
    	xmlBuf.append("<cdObject type=\"TanMedium\">");
    	tag("category", info.mediaCategory);
    	tag("status", info.status);
    	tag("cardNumber", info.cardNumber);
    	tag("cardSeqNumber", info.cardSeqNumber);
    	if(info.cardType != null) intTag("cardType", info.cardType);
    	dateTag("validFrom", info.validFrom);
    	dateTag("validTo", info.validTo);
    	tag("tanListNumber", info.tanListNumber);
    	tag("name", info.mediaName);
    	tag("mobileNumber", info.mobileNumber);
    	tag("mobileNumberSecure", info.mobileNumberSecure);
    	if(info.freeTans != null) intTag("freeTans", info.freeTans);
    	dateTag("lastUse", info.lastUse);
    	dateTag("activatedOn", info.activatedOn);
    	xmlBuf.append("</cdObject>");
    }
    
    public void tanMediaListToXml(GVRTANMediaList list) throws IOException {
    	xmlBuf.append("<object type=\"TanMediaList\">");
    	intTag("tanOption", list.getTanOption());
    	xmlBuf.append("<mediaList type=\"list\">");
		List<GVRTANMediaList.TANMediaInfo> mediaList = list.mediaList();
		for (int i=0; i<mediaList.size(); i++) {
			tanMediumToXml(mediaList.get(i));
		}
    	xmlBuf.append("</mediaList></object>");
    }
    
    public void tanMethodToXml(Properties tanMethod) throws IOException {
    	xmlBuf.append("<cdObject type=\"TanMethod\">");
		tag("method", tanMethod.getProperty("secfunc"));
		tag("identifier", tanMethod.getProperty("id"));
		tag("process", tanMethod.getProperty("process"));
		tag("zkaMethodName", tanMethod.getProperty("zkamethod_name"));
		tag("zkaMethodVersion", tanMethod.getProperty("zkamethod_version"));
		tag("name", tanMethod.getProperty("name"));
		tag("inputInfo", tanMethod.getProperty("intputinfo"));
		tag("needTanMedia", tanMethod.getProperty("needtanmedia"));
		String s = tanMethod.getProperty("maxlentan2step");
		if( s != null) intTag("maxTanLength", Integer.parseInt(s));
    	xmlBuf.append("</cdObject>");
    }
    
    public void ccBalanceToXml(GVRKKSaldoReq res) throws IOException {
    	xmlBuf.append("<object type=\"CCSaldo\">");
    	valueTag("saldo", res.saldo.value);
    	tag("currency", res.saldo.value.getCurr());
    	valueTag("amountAvailable", res.amount_available);
    	valueTag("amountPending", res.amount_pending);
    	valueTag("cardLimit", res.cardlimit);
    	dateTag("nextSettleDate", res.nextsettledate);
    	xmlBuf.append("</object>");
    }
    
    public void ccUmsToXml(GVRKKUms.UmsLine ums, Konto account) throws IOException {
    	xmlBuf.append("<cdObject type=\"BankStatement\">");
    	tag("localAccount", account.number);
    	tag("localSuffix", account.subnumber);
    	tag("localBankCode", account.blz);
		if(ums.valutaDate != null) dateTag("valutaDate", ums.valutaDate);
		else dateTag("valutaDate", ums.postingDate);
		dateTag("date", ums.postingDate);
		dateTag("docDate", ums.docDate);
		tag("ccNumberUms", ums.cc_number_ums);
		valueTag("value", ums.value);
		tag("currency", ums.value.getCurr());
		valueTag("origValue", ums.origValue);
		tag("origCurrency", ums.origValue.getCurr());
		tag("remoteCountry", ums.country);
		booleTag("isSettled", ums.isSettled);
		tag("bankReference", ums.postingReference);
		tag("ccChargeKey", ums.chargeKey);
		tag("ccChargeForeign", ums.chargeForeign);
		tag("ccCargeTerminal", ums.chargeTerminal);
		tag("ccSettlementRef", ums.settlementReference);
		intTag("type", 1); // credit card statement
		
		StringBuffer purpose = new StringBuffer();
		for(Iterator<String> j = ums.transactionTexts.iterator(); j.hasNext();) {
			String s = j.next();
			purpose.append(s);
			if(j.hasNext()) purpose.append("\n");
		}
        tag("purpose", purpose.toString());
		xmlBuf.append("</cdObject>");    	
    }
    
    public void ccUmsAllToXml(GVRKKUms res, Konto account) throws IOException {
    	xmlBuf.append("<object type=\"BankQueryResult\">");
    	tag("bankCode", account.blz);
    	tag("accountNumber", account.number);
    	tag("accountSubnumber", account.subnumber);
    	tag("ccNumber", res.cc_number);
    	//tag("ccAccount", res.cc_account);
    	dateTag("lastSettleDate", res.lastsettledate);
    	//dateTag("nextSettleDate", res.nextsettledate);
    	valueTag("balance", res.saldo.value);
    	xmlBuf.append("<statements type=\"list\">");

    	for(GVRKKUms.UmsLine ums: res.statements) {
    		ccUmsToXml(ums, account);
    	}
    	xmlBuf.append("</statements></object>");
    }
    
    public void ccSettleListToXml(GVRKKSettleList res) throws IOException {
    	xmlBuf.append("<object type=\"CCSettlementList\">");
    	
    	tag("ccNumber", res.cc_number);
    	tag("ccAccount", res.cc_account);
    	xmlBuf.append("<settlementInfos type=\"list\">");
    	for(GVRKKSettleList.Info info: res.settlements) {
        	xmlBuf.append("<object type=\"CCSettlementInfo\">");
        	tag("settleID", info.settleID);
        	booleTag("received", info.received);
        	dateTag("settleDate", info.settleDate);
        	dateTag("firstReceive", info.firstReceive);
        	valueTag("value", info.value);
        	tag("currency", info.currency);
        	xmlBuf.append("</object>");
    	}
    	xmlBuf.append("</settlementInfos></object>");
    }

	public void ccSettlementToXml(GVRKKSettleReq res, Konto account) throws IOException {
    	xmlBuf.append("<cdObject type=\"CreditCardSettlement\">");
    	tag("ccNumber", res.cc_number);
    	tag("ccAccount", res.cc_account);
    	tag("settleID", res.settleID);
    	if(res.startSaldo != null) valueTag("startBalance", res.startSaldo.value);
    	if(res.endSaldo != null) valueTag("endBalance", res.endSaldo.value);
    	if(res.startSaldo != null) tag("currency", res.startSaldo.value.getCurr());
    	tag("text", res.text);
    	dateTag("nextSettleDate", res.nextSettleDate);
    	binaryTag("ackCode", res.ackCode);
    	binaryTag("document", res.document);
    	/*
    	xmlBuf.append("<umsList type=\"list\">");
    	for(GVRKKUms.UmsLine ums: res.statements) {
    		ccUmsToXml(ums, account);
    	}
    	xmlBuf.append("</umsList>");
    	*/
    	xmlBuf.append("</cdObject>");		
	}
	
	public void ccDKBToXml(String content, Konto account, Date fromDate) throws IOException {
        BufferedReader fin = new BufferedReader(new StringReader(content));
        
        String s;
        int line = 0;
        
        // Decimal Formatter
        DecimalFormat df = new DecimalFormat();
        DecimalFormatSymbols symbols = new DecimalFormatSymbols();
        symbols.setDecimalSeparator(',');
        symbols.setGroupingSeparator('.');
        df.setDecimalFormatSymbols(symbols);
        
		GVRKKUms ums = new GVRKKUms();
		ums.statements = new ArrayList<GVRKKUms.UmsLine>();
		ums.cc_number = account.number;

		while((s = fin.readLine()) != null) {
			line++;
			if(line < 5) continue;
			String[] info = s.split(";", 0);

			// extract saldo
			if(line == 5) {
				if(!info[0].replace("\"", "").equals("Saldo:")) {
					HBCIUtils.log("DKB-Datenformat wurde geändert. Interpretation nicht möglich!", HBCIUtils.LOG_ERR);
					return;
				}
				String sField = info[1].replace("\"", "");
				String[] values = sField.split(" ",0);

				Value value = new Value();
				value.setCurr(values[1]);
				
				double xv;
				try {
					// correct wrong formatting of saldo field
					String sValue = values[0].replace(".",",");
					xv = df.parse(sValue).doubleValue();
				} catch (ParseException e) {
					HBCIUtils.log("Number format error", HBCIUtils.LOG_ERR);
					return;
				}
				xv *= 100.0;
				value.setValue(java.lang.Math.round(xv));
				Saldo saldo = new Saldo();
				saldo.value = value;
				ums.saldo = saldo;
			}
			
			// check format
			if(line == 8) {
				boolean ok = true;
				if(!info[0].replace("\"", "").equals("Umsatz abgerechnet")) { ok = false; }
				if(!info[1].replace("\"", "").equals("Wertstellung")) { ok = false; }
				if(!info[2].replace("\"", "").equals("Belegdatum")) { ok = false; }
				if(!info[3].replace("\"", "").equals("Umsatzbeschreibung")) { ok = false; }
				if(!info[4].replace("\"", "").equals("Betrag (EUR)")) { ok = false; }
				if(!info[5].replace("\"", "").startsWith("Urspr")) { ok = false; }
				if(!ok) {
					HBCIUtils.log("DKB-Datenformat wurde geändert. Interpretation nicht möglich!", HBCIUtils.LOG_ERR);
					return;					
				}
			}
			
			if(line >= 9) {
				GVRKKUms.UmsLine umsLine = new GVRKKUms.UmsLine();
				umsLine.cc_number_ums = account.number;
				
				// settled
				String str = info[0].replace("\"", "");
				umsLine.isSettled = str.equals("Ja");
				
				// Valuta
				str = info[1].replace("\"", "");
				try {
					umsLine.valutaDate = new SimpleDateFormat("dd.MM.yyyy").parse(str);
				} catch (ParseException e) {
					//e.printStackTrace();
				}
				
				// DocDate
				str = info[2].replace("\"", "");
				try {
					umsLine.docDate = new SimpleDateFormat("dd.MM.yyyy").parse(str);
					umsLine.postingDate = umsLine.docDate;
					if(umsLine.postingDate == null) {
						umsLine.postingDate = umsLine.valutaDate;
					}
				} catch (ParseException e) {
					//e.printStackTrace();
				}
				
				// filter out non-relevant entries
				if(umsLine.postingDate.before(fromDate)) continue;
				
				// Transaction Text
				umsLine.transactionTexts = new ArrayList<String>();
				umsLine.transactionTexts.add(info[3].replace("\"", ""));
				
				// Amount
				str = info[4].replace("\"", "");
				Value value = new Value();
				value.setCurr("EUR");
				
				double xv;
				try {
					xv = df.parse(str).doubleValue();
				} catch (ParseException e) {
					HBCIUtils.log("Number format error", HBCIUtils.LOG_ERR);
					continue;
				}
				xv *= 100.0;
				value.setValue(java.lang.Math.round(xv));
				umsLine.value = value;
				
				// Original Amount
				str = info[5].replace("\"", "");
				if(str.length() == 0) {
					umsLine.origValue = value;
				} else {
					String[] values = str.split(" ",0);
					
					value = new Value();
					value.setCurr(values[1]);
					
					try {
						xv = df.parse(values[0]).doubleValue();
					} catch (ParseException e) {
						HBCIUtils.log("Number format error", HBCIUtils.LOG_ERR);
						continue;
					}
					xv *= 100.0;
					value.setValue(java.lang.Math.round(xv));
					umsLine.origValue = value;
				}
				ums.statements.add(umsLine);
			}
		}
		ccUmsAllToXml(ums, account);
	}
}
