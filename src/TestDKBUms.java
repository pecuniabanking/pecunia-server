

import static org.junit.Assert.*;
import org.kapott.hbci.structures.Konto;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;


import org.junit.Test;

public class TestDKBUms {

	  public String getFile(String name) throws Exception
	  {
	    BufferedReader reader = null;
	    try
	    {
	      StringBuffer sb = new StringBuffer();
	      reader = new BufferedReader(new InputStreamReader(HBCIServer.class.getResourceAsStream(name)));
	      String line = null;
	      while ((line = reader.readLine()) != null) {
	        sb.append(line);
	        sb.append("\n");
	      }
	      return sb.toString();
	    }
	    finally
	    {
	      if (reader != null)
	        reader.close();
	    }
	  }

	
	@Test
	public void test() throws Exception {
		String data = getFile("dkbums.txt");
		Konto account = new Konto();
		account.number = "13520135";
		
        StringBuffer x = new StringBuffer();
        XmlGen gen = new XmlGen(x);
        gen.ccDKBToXml(data, account);
		System.out.println(x.toString());
	}

}
