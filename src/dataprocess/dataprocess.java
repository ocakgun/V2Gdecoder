package dataprocess;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Timestamp;

import javax.xml.transform.Result;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.stream.StreamResult;

import org.eclipse.risev2g.shared.enumerations.GlobalValues;
import org.eclipse.risev2g.shared.messageHandling.MessageHandler;
import org.eclipse.risev2g.shared.utils.MiscUtils;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.XMLReaderFactory;

import com.siemens.ct.exi.EXIFactory;
import com.siemens.ct.exi.GrammarFactory;
import com.siemens.ct.exi.api.sax.EXIResult;
import com.siemens.ct.exi.api.sax.EXISource;
import com.siemens.ct.exi.exceptions.EXIException;
import com.siemens.ct.exi.helpers.DefaultEXIFactory;

import binascii.BinAscii;

/*
 *  Copyright (C) V2Gdecoder by FlUxIuS (Sebastien Dudek)
 */

public class dataprocess {
	public MessageHandler messageHandler;
	
	public MessageHandler getMessageHandler() {
		return messageHandler;
	}
	
	public static void initConfig() {
		MiscUtils.setV2gEntityConfig("./test.properties");
	}

	public static String Xml2Exi(String xmlstr, decodeMode mode) throws IOException, SAXException, EXIException {
		/*
		 * 		Encode XML to EXI
		 * 		In(1): XML string or input file path string
		 * 		In(2): (decodeMode) Input/Output mode
		 * 		Out: encoded result string
		 * */
		EXIFactory exiFactory = DefaultEXIFactory.newInstance();
		String grammar = null;
		ByteArrayOutputStream bosEXI = null;
		OutputStream osEXI = null;
		String result = null;
		String inputsc = null;
		String outfile = null;
		
		if (mode == decodeMode.FILETOSTR || mode == decodeMode.FILETOFILE)
		{ // In case the input is a file
			byte[] rbytes = Files.readAllBytes(Paths.get(xmlstr));
			inputsc = new String(rbytes);
		} else {
			inputsc = xmlstr;
		}
		if (inputsc.contains("supportedAppProtocol"))
		{ // select AppProtocol schema to set V2G grammar
			grammar = GlobalValues.SCHEMA_PATH_APP_PROTOCOL.toString();
		} else if (inputsc.contains("V2G_Message")) { // select XMLDSIG
			grammar = GlobalValues.SCHEMA_PATH_MSG_DEF.toString();
		} else { // MSG DEF by default
			grammar = GlobalValues.SCHEMA_PATH_XMLDSIG.toString();
		}
		exiFactory.setGrammars(GrammarFactory.newInstance().createGrammars("." + grammar));
		EXIResult exiResult = new EXIResult(exiFactory);
		if (mode == decodeMode.FILETOSTR || mode == decodeMode.STRTOSTR)
		{ // stream output
			bosEXI = new ByteArrayOutputStream();
			exiResult.setOutputStream(bosEXI);
		} else { // file output
			if (mode == decodeMode.STRTOFILE) {
				Timestamp timestamp = new Timestamp(System.currentTimeMillis());
				String filename = new String("out."+timestamp.toString()+".exi");
				outfile = filename;
				osEXI = new FileOutputStream(filename);
			}
			else
				osEXI = new FileOutputStream(xmlstr + ".exi");
			exiResult.setOutputStream(osEXI);
		}
		
		XMLReader xmlReader = XMLReaderFactory.createXMLReader();
		xmlReader.setContentHandler( exiResult.getHandler() );
		
		xmlReader.parse(new InputSource(new StringReader(inputsc))); // parse XML input
		if (mode == decodeMode.FILETOSTR || mode == decodeMode.STRTOSTR)
		{
			result = BinAscii.hexlify(bosEXI.toByteArray());
			bosEXI.close();
		} else {
			osEXI.close();
			result = new String("File written in '" + mode + outfile + "'");
		}
		return result;
	}
	
	public static String Exi2Xml(String existr, decodeMode mode, String grammar) throws IOException, SAXException, EXIException, TransformerException {
		/*
		 * 		Decode EXI data to XML
		 * 		In(1): String to decode
		 * 		In(2): (decodeMode) Input/Output
		 * 		In(3): String of grammar XSD path to use 
		 * 		Out: decoded result string
		 * */
		EXIFactory exiFactory = DefaultEXIFactory.newInstance();
		String result = null;
		String inputsc = existr;
		Result res = null;
		ByteArrayOutputStream outputStream = null;
		InputSource is = null;
		exiFactory.setGrammars(GrammarFactory.newInstance().createGrammars("." + grammar));
		
		if (mode == decodeMode.FILETOSTR || mode == decodeMode.FILETOFILE)
			is = new InputSource(inputsc);
		else
			is = new InputSource(new ByteArrayInputStream(BinAscii.unhexlify(inputsc)));
		
		SAXSource exiSource = new EXISource(exiFactory);
		exiSource.setInputSource(is);
		TransformerFactory tf = TransformerFactory.newInstance();
		Transformer transformer = tf.newTransformer();
		
		if (mode == decodeMode.STRTOFILE || mode == decodeMode.FILETOFILE)
		{
			String filename = null;
			if (mode == decodeMode.STRTOFILE)
			{
				Timestamp timestamp = new Timestamp(System.currentTimeMillis());
				filename = new String("out."+timestamp.toString()+".xml");
			} else
				filename = existr + ".xml";
			
			res = new StreamResult(filename);
			result = new String("File written in '" + filename + "'");
		} else {
			outputStream = new ByteArrayOutputStream();
			res = new StreamResult(outputStream);
		}	
		
		transformer.transform(exiSource, res);	
		
		if (mode == decodeMode.FILETOSTR || mode == decodeMode.STRTOSTR)
			result = new String(outputStream.toByteArray());
		
		return result;
	}
	
	public static String fuzzyExiDecoded(String strinput, decodeMode dmode)
	{
		/*
		 * 		Enumerate V2G grammar to decode EXI data
		 * 		In(1): Input string
		 * 		In(2): (decodeMode) Input/Output modes
		 * 		Out: Decoded result string
		 */
		String grammar = null;
		String result = null;
		
		grammar = GlobalValues.SCHEMA_PATH_MSG_DEF.toString();
		try {
			result = Exi2Xml(strinput, dmode, grammar);
		} catch (Exception e1) {
			try {
				grammar = GlobalValues.SCHEMA_PATH_APP_PROTOCOL.toString();
				result = Exi2Xml(strinput, dmode, grammar);
			} catch (Exception e2) {
				grammar = GlobalValues.SCHEMA_PATH_XMLDSIG.toString();
				try {
					result = Exi2Xml(strinput, dmode, grammar);
				} catch (EXIException e3) {
					// do nothing
					//e3.printStackTrace();
				} catch (Exception b3) {
					// do nothing
					//b3.printStackTrace();
				}
			}
		}
		
		return result;
	}
}
