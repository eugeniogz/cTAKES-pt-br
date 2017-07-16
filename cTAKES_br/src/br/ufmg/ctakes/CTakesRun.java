package br.ufmg.ctakes;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.logging.LogManager;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.apache.ctakes.core.resource.FileLocator;
import org.apache.ctakes.typesystem.type.syntax.BaseToken;
import org.apache.ctakes.typesystem.type.syntax.NewlineToken;
import org.apache.ctakes.typesystem.type.textspan.Sentence;
import org.apache.uima.UIMAFramework;
import org.apache.uima.analysis_engine.AnalysisEngine;
import org.apache.uima.cas.CAS;
import org.apache.uima.cas.CASException;
import org.apache.uima.cas.Feature;
import org.apache.uima.cas.SofaFS;
import org.apache.uima.cas.impl.CASImpl;
import org.apache.uima.cas.impl.XmiCasDeserializer;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.internal.util.Timer;
import org.apache.uima.resource.ResourceSpecifier;
import org.apache.uima.util.FileUtils;
import org.apache.uima.util.Level;
import org.apache.uima.util.Logger;
import org.apache.uima.util.ProcessTrace;
import org.apache.uima.util.XMLInputSource;

public class CTakesRun {

  private static final String loggerPropertiesFileName = "br/ufmg/CTakesRun/Logger.properties";

  private File textFile = null;

  private File fileOpenDir = null;

  private File annotOpenDir = null;

  private File xcasFileOpenDir = null;

  private CAS cas = null;

  private AnalysisEngine ae = null;

  private File logFile = null;

  private Logger log = null;

  private String dataPathName;

  private String textArea;

  public static void main(String[] args) {
	try {
	  CTakesRun t = new CTakesRun();
	  t.initializeLogging();
	  String inputText = "Teste.txt";
	  String descFile = "cTAKES_br/desc/AggregatePos.xml";
	  if (args.length>0) inputText = args[0];
	  if (args.length>1) descFile = args[1];
	  t.loadTextResource(FileLocator.getAsStream(inputText));
	  t.loadAEDescriptor(descFile);
	  t.runAE(true);
	} catch (FileNotFoundException e) {
		e.printStackTrace();
	} catch (Throwable e) {
		e.printStackTrace();
	}
	  
  }
  

  public CTakesRun() {
	  
  }
  
  public void runAE(boolean doCasReset) {
    Timer timer = new Timer();
    timer.start();
    if (this.ae == null) {
      System.err.println("No AE loaded.");
      return;
    }
    internalRunAE(doCasReset);
    timer.stop();
    System.out.println("Done running AE " + this.ae.getAnalysisEngineMetaData().getName() + " in "
        + timer.getTimeSpan() + ".");
  }


  public void setDataPath(String dataPath) {
    this.dataPathName = dataPath;
  }

  public void loadAEDescriptor(String aeDir) {
    Timer time = new Timer();
    time.start();
    boolean success = false;
    try {
      String aePath = FileLocator.getFullPath(aeDir);
      success = setupAE(FileLocator.getAsStream(aeDir), aePath);
    } catch (Exception e) {
      handleException(e);
    } catch (NoClassDefFoundError e) {
      // We don't want to catch all errors, but some are ok.
      handleException(e);
    }
    time.stop();
    if (!success) {
      System.err.println("Failed to load AE");
      return;
    }
    if (this.ae != null) {
      String annotName = this.ae.getAnalysisEngineMetaData().getName();
      System.out.println("Done loading AE " + annotName + " in " + time.getTimeSpan() + ".");
    }
    // Check for CAS multiplier
    // TODO: properly handle CAS multiplication
    if (this.ae != null) {
      if (this.ae.getAnalysisEngineMetaData().getOperationalProperties().getOutputsNewCASes()) {
        System.err.println("This analysis engine uses a CAS multiplier component.\nCAS multiplication/merging is not currently supported.\nYou can run the analysis engine, but will not see any results."); }
    }
  }

  public void handleException(Throwable e) {
    StringBuffer msg = new StringBuffer();
    handleException(e, msg);
  }

  protected void handleException(Throwable e, StringBuffer msg) {
    msg.append(e.getClass().getName() + ": ");
    if (e.getMessage() != null) {
      msg.append(e.getMessage());
    }
    if (this.log != null) {
      if (e instanceof Exception) {
        this.log.log(Level.SEVERE, ((Exception) e).getLocalizedMessage(), e);
      } else {
        this.log.log(Level.SEVERE, e.getMessage(), e);
      }
      msg.append("\nMore detailed information is in the log file.");
    }
    System.err.println(msg.toString());
  }

  private void showError(String msg) {
    System.err.println(msg);
  }

  private void loadTextResource(InputStream rs) {
	setTextNoTitle(convertStreamToString(rs));
  }
  
  String convertStreamToString(InputStream is) {
    java.util.Scanner s = new java.util.Scanner(is,"UTF-8");
    s.useDelimiter("\\A");
    String ret = "";
    if (s.hasNext()) { 
    	ret = s.next();
    }
    s.close();
    return ret;
  }
  
  public void loadFile() {
    try {
      if (this.textFile.exists() && this.textFile.canRead()) {
        String text = null;
        text = FileUtils.file2String(this.textFile);
        setTextNoTitle(text);
        setTitle();
      } else {
        handleException(new IOException("File does not exist or is not readable: "
            + this.textFile.getAbsolutePath()));
      }
    } catch (UnsupportedEncodingException e) {
      StringBuffer msg = new StringBuffer("Unsupported text encoding (code page): ");
      handleException(e, msg);
    } catch (Exception e) {
      handleException(e);
    }
  }

  public void loadXmiFile(File xmiCasFile) {
    try {
      setXcasFileOpenDir(xmiCasFile.getParentFile());
      Timer time = new Timer();
      time.start();
      SAXParser parser = SAXParserFactory.newInstance().newSAXParser();
      XmiCasDeserializer xmiCasDeserializer = new XmiCasDeserializer(getCas().getTypeSystem());
      getCas().reset();
      parser.parse(xmiCasFile, xmiCasDeserializer.getXmiCasHandler(getCas(), true));
      time.stop();
      System.err.println("Done loading XMI CAS file in " + time.getTimeSpan() + ".");
    } catch (Exception e) {
      e.printStackTrace();
      handleException(e);
    }
  }


  
  /**
   * Set the text to be analyzed.
   * 
   * @param text
   *                The text.
   */
  public void setText(String text) {
    this.textFile = null;
    setTextNoTitle(text);
    setTitle();
  }

  /**
   * Load a text file.
   * 
   * @param textFile1
   *                The text file.
   */
  public void loadTextFile(File textFile1) {
    this.textFile = textFile1;
    loadFile();
  }

  // Set the text.
  public void setTextNoTitle(String text) {
    this.textArea=text;
  }

  public void setTitle() {
    
  }

  public boolean saveFile() {
    if (this.textFile.exists() && !this.textFile.canWrite()) {
      showError("File is not writable: " + this.textFile.getAbsolutePath());
      return false;
    }
    final String text = this.textArea;
    FileOutputStream fileOutStream = null;
    try {
      fileOutStream = new FileOutputStream(this.textFile);
    } catch (FileNotFoundException e) {
      handleException(e);
      return false;
    }
    Writer writer = null;
    writer = new OutputStreamWriter(fileOutStream);
    try {
      writer.write(text);
      writer.close();
      setTitle();
    } catch (IOException e) {
      handleException(e);
      return false;
    }
    return true;
  }
  
 
  private void initializeLogging() {

    // set log file path
    File homeDir = new File(System.getProperty("user.home"));
    this.logFile = new File(homeDir, "ctakesRun.log");

    // delete file if it exists
    this.logFile.delete();

    // initialize log framework
    LogManager logManager = LogManager.getLogManager();
    try {
      InputStream ins = ClassLoader.getSystemResourceAsStream(loggerPropertiesFileName);
      // Try the current class loader if system one cannot find the file
      if (ins == null) {
    	ins = this.getClass().getClassLoader().getResourceAsStream(loggerPropertiesFileName);
      }
      if (ins != null) {
    	logManager.readConfiguration(ins);
      } else {
    	System.out.println("WARNING: failed to load "+loggerPropertiesFileName);
      }
    } catch (SecurityException e) {
      handleException(e);
      return;
    } catch (IOException e) {
      handleException(e);
      return;
    }

    // get UIMA framework logger
    this.log = UIMAFramework.getLogger();
  }

 
  protected boolean setupAE(InputStream aeText, String aePath) {
    try {
      

      // Destroy old AE.
      if (this.ae != null) {
        destroyAe();
       }

      // get Resource Specifier from XML file
      XMLInputSource in = new XMLInputSource(aeText, new File(aePath));
      ResourceSpecifier specifier = UIMAFramework.getXMLParser().parseResourceSpecifier(in);

      // for debugging, output the Resource Specifier
      // System.out.println(specifier);

      this.ae = UIMAFramework.produceAnalysisEngine(specifier);
      this.cas = this.ae.newCAS();
      initCas();
    } catch (Exception e) {
      handleException(e);
      return false;
    }
    return true;
  }

  private final void initCas() {
    this.cas.setDocumentLanguage("pt-br");
    this.cas.setDocumentText(this.textArea);
  }

  protected void internalRunAE(boolean doCasReset) {
    try {
      if (doCasReset) {
        // Change to Initial view
        this.cas = this.cas.getView(CAS.NAME_DEFAULT_SOFA);
        this.cas.reset();
        initCas();
      }
      ProcessTrace pt = this.ae.process(this.cas);
      this.log.log(Level.INFO, "Process trace of AE run:\n" + pt.toString());
      
      Iterator<?> sofas = ((CASImpl) this.cas).getBaseCAS().getSofaIterator();
      Feature sofaIdFeat = this.cas.getTypeSystem().getFeatureByFullName(CAS.FEATURE_FULL_NAME_SOFAID);
      while (sofas.hasNext()) {
        SofaFS sofa = (SofaFS) sofas.next();
        String sofaId = sofa.getStringValue(sofaIdFeat);
        if (!CAS.NAME_DEFAULT_SOFA.equals(sofaId)) {
          System.err.println(sofaId +"=================");
        }
      }
      printResults(cas);
    } catch (Exception e) {
      handleException(e);
    } catch (Error e) {
      StringBuffer buf = new StringBuffer();
      buf.append("A severe error has occured:\n");
      handleException(e, buf);
      throw e;
    }
  }

  private void printResults(CAS cas2) {
	  Collection<Sentence> sentences = new ArrayList<Sentence>();
		try {
			sentences = JCasUtil.select(cas2.getJCas(), Sentence.class);
		} catch (CASException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		System.err.println("=====> Sentenças:");
		for (Sentence sentence : sentences) {
			System.err.println(sentence.getCoveredText());
		}
		System.err.println();
		for (Sentence sentence : sentences) {

			List<BaseToken> printableTokens = new ArrayList<>();
			String space = "";
			System.err.println("====> Tokens:");
			for(BaseToken token : JCasUtil.selectCovered(BaseToken.class,	sentence)){
			  if(token instanceof NewlineToken) continue;
			  printableTokens.add(token);
			  System.err.print(space + token.getCoveredText() + ":" + token.getType().getShortName());
			  space = " ";
			}
			System.err.println();
			System.err.println();
			
			String[] words = new String[printableTokens.size()];
			for (int i = 0; i < words.length; i++) {
				words[i] = printableTokens.get(i).getCoveredText();
			}

			if (words.length > 0) {
				try {
					System.err.println("====> Part of Speech:");
					space = "";
					for (int i = 0; i < printableTokens.size(); i++) {
						BaseToken token = printableTokens.get(i);
						System.err.print(space + token.getCoveredText() + "_" + token.getPartOfSpeech());
						space = " ";
					}
					System.err.println();System.err.println();
				} catch (Exception e) {
					System.err.println(e.getMessage());
				}
			}
		}
	}

 


public CAS getCas() {
    return this.cas;
  }

  public AnalysisEngine getAe() {
    return this.ae;
  }
  public File getFileOpenDir() {
    return this.fileOpenDir;
  }

  public void setFileOpenDir(File fileOpenDir) {
    this.fileOpenDir = fileOpenDir;
  }

  public File getTextFile() {
    return this.textFile;
  }

  public void setTextFile(File textFile) {
    this.textFile = textFile;
  }

  public File getXcasFileOpenDir() {
    return this.xcasFileOpenDir;
  }

  public void setXcasFileOpenDir(File xcasFileOpenDir) {
    this.xcasFileOpenDir = xcasFileOpenDir;
  }

  public void setCas(CAS cas) {
    this.cas = cas;
  }

  public void destroyAe() {
    this.cas = null;
    if (this.ae != null) {
      this.ae.destroy();
      this.ae = null;
    }
  }


  public File getAnnotOpenDir() {
    return this.annotOpenDir;
  }

  public void setAnnotOpenDir(File annotOpenDir) {
    this.annotOpenDir = annotOpenDir;
  }

  public String getDataPathName() {
    return this.dataPathName;
  }

  public void setDataPathName(String dataPathName) {
    this.dataPathName = dataPathName;
  }


}
