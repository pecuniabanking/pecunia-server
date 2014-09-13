import java.io.IOException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Properties;

import org.kapott.hbci.callback.HBCICallback;
import org.kapott.hbci.manager.HBCIHandler;
import org.kapott.hbci.manager.HBCIUtils;
import org.kapott.hbci.manager.HBCIUtilsInternal;
import org.kapott.hbci.structures.Konto;

import com.gargoylesoftware.htmlunit.Page;
import com.gargoylesoftware.htmlunit.TextPage;
import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlImageInput;
import com.gargoylesoftware.htmlunit.html.HtmlOption;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.gargoylesoftware.htmlunit.html.HtmlPasswordInput;
import com.gargoylesoftware.htmlunit.html.HtmlSelect;
import com.gargoylesoftware.htmlunit.html.HtmlTextInput;
import com.gargoylesoftware.htmlunit.html.HtmlInput;





public class DKBVisaManager {
	
	private HBCIServer server;
	
    DKBVisaManager(HBCIServer s) {
    	server = s;
    }
	
	void getStatements(Properties map, XmlGen xmlGen) throws IOException {
		Properties queries = new Properties();
		ArrayList list = (ArrayList)map.get("accinfolist");
		
		// first collect all orders separated by handlers
		for(int i=0; i<list.size(); i++) {
			Properties tmap = (Properties)list.get(i);
			String bankCode = server.getParameter(tmap, "accinfo.bankCode");
			
			// bank code DKB
			if(!bankCode.equals("12030000")) continue;
			String accountNumber = server.getParameter(tmap, "accinfo.accountNumber");
			
			// credit card?
			if(accountNumber.length() != 16) continue;
			String userId = server.getParameter(tmap, "accinfo.userId");

			HBCIHandler handler = server.hbciHandler(bankCode, userId);
			ArrayList<Properties> parameters = (ArrayList<Properties>)queries.get(handler);
			if(parameters == null) {
				parameters = new ArrayList<Properties>();
				queries.put(handler, parameters);
			}
			parameters.add(tmap);
		}
		
		// now start export for each handler
		for(Object x: queries.keySet()) {
			HBCIHandler handler = (HBCIHandler)x;
			ArrayList<Properties> parameters = (ArrayList<Properties>)queries.get(handler);
			
			// Start DKB Login
			HBCIUtils.log("DKB-Login with customer number "+handler.getPassport().getUserId(), HBCIUtils.LOG_INFO);
			
			WebClient webClient = new WebClient();
			webClient.getOptions().setJavaScriptEnabled(false);
			webClient.getOptions().setCssEnabled(false);
			
            StringBuffer s=new StringBuffer();
            HBCIUtilsInternal.getCallback().callback(handler.getPassport(),
                                             HBCICallback.NEED_PT_PIN,
                                             HBCIUtilsInternal.getLocMsg("CALLB_NEED_PTPIN"),
                                             HBCICallback.TYPE_SECRET,
                                             s);
            if (s.length()==0) {
    			HBCIUtils.log("Bitte PIN angeben!", HBCIUtils.LOG_ERR);
    			continue;
            }
            
            HtmlInput input = null;
            try {
                HtmlPage pageLogin = webClient.getPage("https://banking.dkb.de");
            
                URL pageURL = pageLogin.getUrl();
                if (pageURL.toString().indexOf("/portal") >= 0) {
                    HtmlForm formLogin = pageLogin.getFirstByXPath("//form[@class='anmeldung']");
                    HtmlTextInput elem = formLogin.getFirstByXPath("//input[@maxlength='16']");
                    elem.setValueAttribute(handler.getPassport().getUserId());
                    HtmlPasswordInput pwdElem = formLogin.getFirstByXPath("//input[@type='password']");
                    pwdElem.setValueAttribute(s.toString());
                    input = formLogin.getInputByValue("Anmelden");                
                } else {
                    HtmlForm formLogin = pageLogin.getFirstByXPath("//form[@name='login']");
                    HtmlTextInput elem = formLogin.getFirstByXPath("//input[@maxlength='16']");
                    elem.setValueAttribute(handler.getPassport().getUserId());
                    HtmlPasswordInput pwdElem = formLogin.getFirstByXPath("//input[@type='password']");
                    pwdElem.setValueAttribute(s.toString());
                    //HtmlImageInput imgElem = formLogin.getFirstByXPath("//input[@id='buttonlogin']"); //formLogin.getInputByValue("Anmelden");
                    input = formLogin.getFirstByXPath("//input[@id='buttonlogin']");            	
                }            	
            }
            catch (Exception e) {
    			HBCIUtils.log("Anmeldung bei der DKB war nicht erfolgreich! Fehler beim Zugriff auf die Login-Seite", HBCIUtils.LOG_ERR);
    			continue;
            }
            /*
            HtmlForm formLogin = pageLogin.getFirstByXPath("//form[@name='login']");
            HtmlTextInput elem = formLogin.getFirstByXPath("//input[@maxlength='16']");
            elem.setValueAttribute(handler.getPassport().getUserId());
            HtmlPasswordInput pwdElem = formLogin.getFirstByXPath("//input[@type='password']");
            pwdElem.setValueAttribute(s.toString());
            //HtmlImageInput imgElem = formLogin.getFirstByXPath("//input[@id='buttonlogin']"); //formLogin.getInputByValue("Anmelden");
            HtmlInput input = formLogin.getFirstByXPath("//input[@id='buttonlogin']");
            */
            // submit 
            //HtmlPage postLoginPage = (HtmlPage) imgElem.click();
            HtmlPage postLoginPage = (HtmlPage)input.click();
            
            if (postLoginPage == null || postLoginPage.asXml().contains("class=\"anmeldung\"")) {
    			HBCIUtils.log("Anmeldung bei der DKB war nicht erfolgreich!", HBCIUtils.LOG_ERR);
    			continue;
            } else {
    			HBCIUtils.log("Anmeldung bei der DKB war erfolgreich", HBCIUtils.LOG_INFO);
            }
            
            // from now on we're logged in
            HtmlPage kkPage=null;
            try {
    			HBCIUtils.log(postLoginPage.asText(), HBCIUtils.LOG_INFO);

                String kkURL = "https://banking.dkb.de"+postLoginPage.getAnchorByText("Kreditkartenums\u00E4tze").getHrefAttribute();
                kkPage = webClient.getPage(kkURL);
            }
            catch(Exception e) {
            	webClient.getPage("https://banking.dkb.de/dkb/-?$part=DkbTransactionBanking.infobar.logout-button&$event=logout");
            	e.printStackTrace();
            	return;
            }
 
			for(Properties tmap: parameters) {
				String bankCode = server.getParameter(tmap, "accinfo.bankCode");
				String accountNumber = server.getParameter(tmap, "accinfo.accountNumber");
				String subNumber = tmap.getProperty("accinfo.subNumber");
				String userId = server.getParameter(tmap, "accinfo.userId");
				String fromDateStr = tmap.getProperty("accinfo.fromDate");

				Konto account = server.accountWithId(userId, bankCode, accountNumber, subNumber);
				if(account == null) {
					HBCIUtils.log("Konto "+accountNumber+" nicht gefunden", HBCIUtils.LOG_WARN);
					continue;
				}
				
    			HBCIUtils.log("Starte CSV-Import fuer Konto "+account.number, HBCIUtils.LOG_INFO);
				
    			try {
        			//HtmlForm form = kkPage.getFormByName("form-772007528_1");
        			//HtmlForm form = kkPage.getFirstByXPath("//form[@name='.']");
        			HtmlSelect kk = kkPage.getElementByName("slCreditCard");
        			String ccNumberSecret = account.number.substring(0, 4) + "********" + account.number.substring(12,16);
        			
        			// Select credit card...
        			List<HtmlOption> optList = kk.getOptions();
        			for (int j=0; j < optList.size(); j++) {
        			        HtmlOption d = (HtmlOption)optList.get(j);
        			        if ((d.asText()).substring(0,16).equals(ccNumberSecret))        
        			        {
        						HBCIUtils.log("Kreditkartenauswahl auf "+d, HBCIUtils.LOG_INFO);
        			        	d.setSelected( true ); 
        			        	ccNumberSecret = "***";
        			        	break;
        			        }
        			}
        			if(!ccNumberSecret.equals("***")) {
        				HBCIUtils.log("Kreditkarte "+account.number+" nicht gefunden", HBCIUtils.LOG_ERR);
        				continue;
        			}
        			
        			// select free period
        			HtmlInput hi;
        			hi = kkPage.getElementByName("searchPeriod");
        			hi.setValueAttribute("0");

        			Date n = new Date();
        		    Date ad = null;
        			if(fromDateStr != null) {
        				ad = HBCIUtils.string2DateISO(fromDateStr);
        			}
        			if(ad == null) {
            		    ad = new Date((n.getTime()-31104000000L));         				
        			}
        			
        		    // Tag und Monat muss 2stellig sein
        		    String nDateString = new SimpleDateFormat("dd.MM.yyyy").format(n);
        		    String adDateString = new SimpleDateFormat("dd.MM.yyyy").format(ad);
        		    
        		    hi = kkPage.getElementByName("postingDate");
        		    hi.setValueAttribute(adDateString);
        		    hi = kkPage.getElementByName("toPostingDate");
        		    hi.setValueAttribute(nDateString);
        		    
        		    hi = kkPage.getElementByName("$$event_search");
        		    hi.click();

        		    // CSV-Export holen
        		    TextPage csv = webClient.getPage("https://banking.dkb.de/dkb/-?$part=DkbTransactionBanking.content.creditcard.CreditcardTransactionSearch&$event=csvExport");

        		    String content = csv.getWebResponse().getContentAsString();
        		    
    				HBCIUtils.log("CSV-Abruf erfolgreich, starte Umsatzkonvertierung für Konto "+account.number, HBCIUtils.LOG_INFO);
        		    xmlGen.ccDKBToXml(content, account, ad);

    			}
    			catch(Exception e) {
  				  HBCIUtils.log("Fehler beim Zugriff auf die DKB-Webseite", HBCIUtils.LOG_ERR);
  				  e.printStackTrace();
    			}
			}
			
			// Logout user
			HBCIUtils.log("DKB Logout", HBCIUtils.LOG_INFO);            	
        	webClient.getPage("https://banking.dkb.de/dkb/-?$part=DkbTransactionBanking.infobar.logout-button&$event=logout");
		}
	}
}
