package br.ufmg.ctakes;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.logging.LogManager;

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
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.internal.util.Timer;
import org.apache.uima.resource.ResourceSpecifier;
import org.apache.uima.util.Level;
import org.apache.uima.util.Logger;
import org.apache.uima.util.ProcessTrace;
import org.apache.uima.util.XMLInputSource;

public class CTakesRun {

	private static final String loggerPropertiesFileName = "br/ufmg/CTakesRun/Logger.properties";

	private String outputFile;

	private CAS cas = null;

	private AnalysisEngine ae = null;

	private File logFile = null;

	private Logger log = null;

	private String texto;

	public static void main(String[] args) {
		try {
			CTakesRun t = new CTakesRun();
			t.initializeLogging();
			String inputText = "Teste.txt";
			String descFile = "cTAKES_br/desc/AggregatePos.xml";
			t.outputFile = FileLocator.getFullPath(inputText) + ".out";
			if (args.length > 0)
				inputText = args[0];
			if (args.length > 1)
				descFile = args[1];
			if (args.length > 2)
				t.outputFile = args[2];
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
				System.err.println(
						"This analysis engine uses a CAS multiplier component.\nCAS multiplication/merging is not currently supported.\nYou can run the analysis engine, but will not see any results.");
			}
		}
	}

	private void handleException(Throwable e) {
		StringBuffer msg = new StringBuffer();
		handleException(e, msg);
	}

	private void handleException(Throwable e, StringBuffer msg) {
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

	private String convertStreamToString(InputStream is) {
		java.util.Scanner s = new java.util.Scanner(is, "UTF-8");
		s.useDelimiter("\\A");
		String ret = "";
		if (s.hasNext()) {
			ret = s.next();
		}
		s.close();
		return ret;
	}

	// Set the text.
	private void setTextNoTitle(String text) {
		this.texto = text;
	}

	private Writer getOutPutWriter() {
		if (outputFile == null)
			return null;
		File fout = new File(outputFile);
		if (fout.exists() && !fout.canWrite()) {
			showError("File is not writable: " + fout.getAbsolutePath());
			return null;
		}
		FileOutputStream fileOutStream = null;
		try {
			fileOutStream = new FileOutputStream(fout);
		} catch (FileNotFoundException e) {
			handleException(e);
			return null;
		}
		Writer writer = null;
		writer = new OutputStreamWriter(fileOutStream);
		return writer;
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
				System.out.println("WARNING: failed to load " + loggerPropertiesFileName);
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

	private boolean setupAE(InputStream aeText, String aePath) {
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
		this.cas.setDocumentText(this.texto);
	}

	private void internalRunAE(boolean doCasReset) {
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
					System.err.println(sofaId + "=================");
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
		Writer out = getOutPutWriter();
		try {
			println(out, "=====> Sentenças:");

			for (Sentence sentence : sentences) {
				println(out, sentence.getCoveredText());
			}
			println(out, "");
			for (Sentence sentence : sentences) {

				List<BaseToken> printableTokens = new ArrayList<>();
				String space = "";
				println(out, "====> Tokens:");
				for (BaseToken token : JCasUtil.selectCovered(BaseToken.class, sentence)) {
					if (token instanceof NewlineToken)
						continue;
					printableTokens.add(token);
					print(out, space + token.getCoveredText() + ":" + token.getType().getShortName());
					space = " ";
				}
				println(out, "\n");

				String[] words = new String[printableTokens.size()];
				for (int i = 0; i < words.length; i++) {
					words[i] = printableTokens.get(i).getCoveredText();
				}

				if (words.length > 0) {
					try {
						println(out, "====> Part of Speech:");
						space = "";
						for (int i = 0; i < printableTokens.size(); i++) {
							BaseToken token = printableTokens.get(i);
							print(out, space + token.getCoveredText() + "_" + token.getPartOfSpeech());
							space = " ";
						}
						println(out, "\n");
					} catch (Exception e) {
						System.err.println(e.getMessage());
					}
				}
			}
			if (out != null)
				out.close();
		} catch (IOException e1) {
			e1.printStackTrace();
		}

	}

	private void println(Writer out, String string) throws IOException {
		System.err.println(string);
		if (out != null)
			out.write(string + "\n");
	}

	private void print(Writer out, String string) throws IOException {
		System.err.print(string);
		if (out != null)
			out.write(string);
	}

	public void destroyAe() {
		this.cas = null;
		if (this.ae != null) {
			this.ae.destroy();
			this.ae = null;
		}
	}
}
