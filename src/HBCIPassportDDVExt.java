
import java.io.UnsupportedEncodingException;

import org.kapott.hbci.callback.HBCICallback;
import org.kapott.hbci.datatypes.SyntaxCtr;
import org.kapott.hbci.exceptions.CTException;
import org.kapott.hbci.manager.HBCIKey;
import org.kapott.hbci.manager.HBCIUtils;
import org.kapott.hbci.manager.HBCIUtilsInternal;
import org.kapott.hbci.passport.HBCIPassportDDV;


public class HBCIPassportDDVExt extends HBCIPassportDDV {
	
    public HBCIPassportDDVExt(Object init,int dummy)
    {
        super(init, dummy);
    }

    public HBCIPassportDDVExt(Object init)
    {
        super(init);
    }
    
    public boolean isAlive() {
    	boolean result = true;
    	try {
    		ctReadBankData();
    	}
    	catch(Exception e) {
    		result = false;
    	}
    	return result;
    }
    
    String bytesToString(byte[] bytes) {
    	StringBuffer ret = new StringBuffer();
    	
    	for(byte x: bytes) {
    		int b = x;
    		String hex = Integer.toHexString(b).toUpperCase();
    		if(hex.length() == 8) {
    			hex = hex.substring(6);
    		}
    		if(hex.length() == 1) {
    			ret.append("0");
    		}
    		ret.append(hex);
    	}
    	return ret.toString();
    }
    
    byte[] stringToBytes(String data) {
    	byte[] ret = new byte[data.length()/2];
        for (int i = 0; i < data.length(); i += 2) {
            ret[i / 2] = (byte) ((Character.digit(data.charAt(i), 16) << 4)
                                 + Character.digit(data.charAt(i+1), 16));
        }
        return ret;
    }
    
    protected void initCT() {
        StringBuffer retData=new StringBuffer();
    	Integer port = this.getComPort();
    	Integer ctNum = this.getCTNumber();
    	
    	retData.append(port);
    	retData.append("|");
    	retData.append(ctNum);
        HBCIUtilsInternal.getCallback().callback(this,HBCIServerCallback.CT_INIT,"initCT",HBCICallback.TYPE_TEXT,retData);
        String result = retData.toString();
        if(result.equals("<error>")) throw new CTException("error while initializing reader and card");
        String[] results = result.split("\\|");
        byte[] cid = this.stringToBytes(results[0]);
        try {
			this.setCID(new String(cid,"ISO-8859-1"));
			HBCIUtils.log("cid: "+this.getCID(), HBCIUtils.LOG_DEBUG);
			HBCIUtils.log("cid_bytes: "+ this.bytesToString(cid), HBCIUtils.LOG_DEBUG);
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		}
        this.setCardId(results[1]);
    }
    
    protected void ctReadBankData() {
    	Integer idx = this.getEntryIdx();
        StringBuffer retData=new StringBuffer();
        retData.append(idx);
        HBCIUtilsInternal.getCallback().callback(this,HBCIServerCallback.CT_READ_BANK_DATA,"readBankData",HBCICallback.TYPE_TEXT,retData);
        String result = retData.toString();
        if(result.equals("<error>")) throw new CTException("error while reading institute data from chipcard");
        String[] results = result.split("\\|");
        String ccode = results[0];
        this.setCountry(SyntaxCtr.getName(ccode));
        this.setBLZ(results[1]);
        this.setHost(results[2]);
        this.setUserId(results[3]);
    }
    
    protected void ctReadKeyData() {
        StringBuffer retData=new StringBuffer();
        HBCIUtilsInternal.getCallback().callback(this,HBCIServerCallback.CT_READ_KEY_DATA,"readKeyData",HBCICallback.TYPE_TEXT,retData);
        String result = retData.toString();
        if(result.equals("<error>")) throw new CTException("readKeyData");
        String[] results = result.split("\\|");
        
        this.setSigId(Long.decode(results[0]));
        HBCIKey key = new HBCIKey(this.getCountry(), this.getBLZ(), this.getUserId(), results[1], results[2], null);
        this.setInstSigKey(key);
        key = new HBCIKey(this.getCountry(), this.getBLZ(), this.getUserId(), results[3], results[4], null);
        this.setInstEncKey(key);
    }
    
    protected int ctGetFunctionalUnits() {
        StringBuffer retData=new StringBuffer();
        HBCIUtilsInternal.getCallback().callback(this,HBCIServerCallback.CT_GET_FUNCTIONAL_UNITS,"getFunctionalUnits",HBCICallback.TYPE_TEXT,retData);
        String result = retData.toString();
        if(result.equals("<error>")) throw new CTException("error while reading reader information");
        return Integer.parseInt(result);
    }
    
    protected void ctEnterPIN() {
    	int useSoftPin = this.getUseSoftPin();
    	int useBio = this.getUseBio();

        StringBuffer retData=new StringBuffer();
        retData.append(useSoftPin);
        retData.append("|");
        retData.append(useBio);
        retData.append("|");
        
        if(useSoftPin == 1) {
            byte[] pin = this.getSoftPin();
            if(pin.length > 0) {
            	retData.append(this.bytesToString(pin));
            }        	
        }

        HBCIUtilsInternal.getCallback().callback(this,HBCIServerCallback.CT_ENTER_PIN,"enterPIN",HBCICallback.TYPE_TEXT,retData);
        String result = retData.toString();
        if(result.equals("<error>")) {
        	throw new CTException("error verifying PIN");
        }
    }
    
    protected void ctSaveBankData() {
        StringBuffer retData=new StringBuffer();
        retData.append(this.getEntryIdx());
        retData.append("|");
        retData.append(SyntaxCtr.getCode(this.getCountry()));
        retData.append("|");
        retData.append(this.getBLZ());
        retData.append("|");
        retData.append(this.getHost());
        retData.append("|");
        retData.append(this.getUserId());
        
        HBCIUtilsInternal.getCallback().callback(this,HBCIServerCallback.CT_SAVE_BANK_DATA,"saveBankData",HBCICallback.TYPE_TEXT,retData);
        String result = retData.toString();
        if(result.equals("<error>")) {
        	throw new CTException("error while storing bank data on card");
        }
    }
    
    protected void ctSaveSigId() {
        StringBuffer retData=new StringBuffer();
        retData.append(Long.toString(this.getSigId()));
        HBCIUtilsInternal.getCallback().callback(this,HBCIServerCallback.CT_SAVE_SIG,"saveSig",HBCICallback.TYPE_TEXT,retData);
        String result = retData.toString();
        if(result.equals("<error>")) {
        	throw new CTException("error while saving new sigid to chipcard");
        }
    }
    
    protected byte[] ctSign(byte[] data) {
        StringBuffer retData=new StringBuffer();
    	retData.append(this.bytesToString(data));
        HBCIUtilsInternal.getCallback().callback(this,HBCIServerCallback.CT_SIGN,"sign",HBCICallback.TYPE_TEXT,retData);
        String result = retData.toString();
        if(result.equals("<error>")) {
        	throw new CTException("error while signing data");
        }
    	return this.stringToBytes(result);
    }
    
    protected byte[][] ctEncrypt() {
        StringBuffer retData=new StringBuffer();
        retData.append(this.getInstEncKeyNum());
        HBCIUtilsInternal.getCallback().callback(this,HBCIServerCallback.CT_ENCRYPT,"encrypt",HBCICallback.TYPE_TEXT,retData);
        String result = retData.toString();
        if(result.equals("<error>")) {
        	throw new CTException("error while encrypting data");
        }
    	
        String[] results = result.split("\\|");
        byte[] plainkey = this.stringToBytes(results[0]);
        byte[] enckey = this.stringToBytes(results[1]);
        byte[][] res = new byte[][] { plainkey, enckey };
        return res;
    }
    
    protected byte[] ctDecrypt(byte[] key) {
        StringBuffer retData=new StringBuffer();
        retData.append(this.getInstEncKeyNum());
        retData.append("|");
        retData.append(this.bytesToString(key));
        HBCIUtilsInternal.getCallback().callback(this,HBCIServerCallback.CT_DECRYPT,"decrypt",HBCICallback.TYPE_TEXT,retData);
        String result = retData.toString();
        if(result.equals("<error>")) {
        	throw new CTException("error while encrypting data");
        }
        return this.stringToBytes(result);
    }
    
    protected void closeCT() {
        StringBuffer retData=new StringBuffer();
        HBCIUtilsInternal.getCallback().callback(this,HBCIServerCallback.CT_CLOSE,"close",HBCICallback.TYPE_TEXT,retData);
    }
}
