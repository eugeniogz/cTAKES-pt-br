package br.ufmg.ctakes;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;

import org.apache.ctakes.core.ae.SentenceDetector;
import org.apache.ctakes.core.resource.FileLocator;
import org.apache.ctakes.core.sentence.EndOfSentenceScannerImpl;
import org.apache.log4j.Logger;

import opennlp.tools.dictionary.Dictionary;
import opennlp.tools.sentdetect.SentenceDetectorME;
import opennlp.tools.sentdetect.SentenceModel;
import opennlp.tools.sentdetect.SentenceSample;
import opennlp.tools.sentdetect.SentenceSampleStream;
import opennlp.tools.util.MarkableFileInputStreamFactory;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.PlainTextByLineStream;
import opennlp.tools.util.TrainingParameters;

public class SentenceDetectorTrain {
	/**
	 * Train a new sentence detector from the training data in the first file
	 * and write the model to the second file.<br>
	 * The training data file is expected to have one sentence per line.
	 * 
	 * @param args
	 *            training_data_filename name_of_model_to_create iters? cutoff?
	 * @throws IOException
	 */
	@SuppressWarnings("deprecation")
	public static void main(String[] args) throws IOException {
		final Logger logger = Logger.getLogger(SentenceDetector.class.getName()
				+ ".main()");

		// Handle arguments
		if (args.length < 2 || args.length > 4) {
			usage(logger);
			System.exit(-1);
		}
		
		File inFile = FileLocator.locateFile(args[0]);

		File outFile = FileLocator.locateFile(args[1]);
		// File outFile = new File(args[1]);

		int iters = 100;
		if (args.length > 2) {
			iters = parseInt(args[2], logger);
		}

		int cut = 5;
		if (args.length > 3) {
			cut = parseInt(args[3], logger);
		}

		// Now, do the actual training
		EndOfSentenceScannerImpl scanner = new EndOfSentenceScannerImpl();
		int numEosc = scanner.getEndOfSentenceCharacters().length;

		logger.info("Training new model from " + inFile.getAbsolutePath());
		logger.info("Using " + numEosc + " end of sentence characters.");


		Charset charset = Charset.forName("UTF-8");
		SentenceModel mod = null;
    
		MarkableFileInputStreamFactory mfisf = new MarkableFileInputStreamFactory(inFile);
		try (ObjectStream<String> lineStream = new PlainTextByLineStream(mfisf, charset)) {
		  
		  ObjectStream<SentenceSample> sampleStream =  new SentenceSampleStream(lineStream);

		  // Training Parameters
		  TrainingParameters mlParams = new TrainingParameters();
		  mlParams.put(TrainingParameters.ALGORITHM_PARAM, "MAXENT");
		  mlParams.put(TrainingParameters.ITERATIONS_PARAM, Integer.toString(iters));
		  mlParams.put(TrainingParameters.CUTOFF_PARAM, Integer.toString(cut));

		  // Abbreviations dictionary
		  // TODO: Actually import a Dictionary of abbreviations
		  Dictionary dict = new Dictionary();
		  //dict.put(new StringList("Dr."));dict.put(new StringList("Mr."));
		  try {
		    mod = SentenceDetectorME.train("pt-br", sampleStream, true, dict, mlParams);
		  } finally {
			  sampleStream.close();
		  }
		}
		
		try(FileOutputStream outStream = new FileOutputStream(outFile)){
		  logger.info("Saving the model as: " + outFile.getAbsolutePath());
		  mod.serialize(outStream);
		}
	}

	public static void usage(Logger log) {
		log.info("Usage: java "
				+ SentenceDetector.class.getName()
				+ " training_data_filename name_of_model_to_create <iters> <cut>");
	}

	public static int parseInt(String s, Logger log) {
		try {
			return Integer.parseInt(s);
		} catch (NumberFormatException nfe) {
			log.error("Unable to parse '" + s + "' as an integer.");
			throw (nfe);
		}
	}

	public static File getReadableFile(String fn) throws IOException {
		File f = new File(fn);
		if (!f.canRead()) {
			throw new IOException("Unable to read from file " + f.getAbsolutePath());
		}
		return f;
	}

	public static File getFileInExistingDir(String fn) throws IOException {
		File f = new File(fn);
		File parent = f.getAbsoluteFile().getParentFile();
		if (!parent.isDirectory()) {
			throw new IOException("Directory not found: "
					+ f.getParentFile().getAbsolutePath());
		}
		return f;
	}


}
