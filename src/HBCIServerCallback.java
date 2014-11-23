import org.kapott.hbci.callback.HBCICallbackConsole;


public class HBCIServerCallback extends HBCICallbackConsole {
	public static final int CT_INIT = 9000;
	public static final int CT_READ_BANK_DATA = 9001;
	public static final int CT_READ_KEY_DATA = 9002;
	public static final int CT_GET_FUNCTIONAL_UNITS = 9003;
	public static final int CT_ENTER_PIN = 9004;
	public static final int CT_SAVE_BANK_DATA  = 9005;
	public static final int CT_SAVE_SIG = 9006;
	public static final int CT_SIGN = 9007;
	public static final int CT_ENCRYPT = 9008;
	public static final int CT_DECRYPT = 9009;
	public static final int CT_CLOSE = 9010;	
}
