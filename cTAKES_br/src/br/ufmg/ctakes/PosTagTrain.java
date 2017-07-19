package br.ufmg.ctakes;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;

import org.apache.ctakes.core.ae.SentenceDetector;
import org.apache.ctakes.core.resource.FileLocator;
import org.apache.ctakes.core.sentence.EndOfSentenceScannerImpl;
import org.apache.log4j.Logger;

import opennlp.tools.postag.POSModel;
import opennlp.tools.postag.POSSample;
import opennlp.tools.postag.POSTaggerFactory;
import opennlp.tools.postag.POSTaggerME;
import opennlp.tools.postag.WordTagSampleStream;
import opennlp.tools.util.MarkableFileInputStreamFactory;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.PlainTextByLineStream;
import opennlp.tools.util.TrainingParameters;

public class PosTagTrain {
	/**
	 * Train a new PosTag the training data in the first file and write the model to
	 * the second file.<br>
	 * The training data file is expected to have words_tag format.
	 * 
	 * @param args
	 *            training_data_filename name_of_model_to_create
	 * @throws IOException
	 */
	public static void main(String[] args) throws IOException {
		final Logger logger = Logger.getLogger(PosTagTrain.class.getName() + ".main()");

		// Handle arguments
		if (args.length < 2 || args.length > 4) {
			usage(logger);
			System.exit(-1);
		}

		File inFile = FileLocator.locateFile(args[0]);

		File outFile = LocateOrCreateFile(args[1]);

		// Now, do the actual training
		EndOfSentenceScannerImpl scanner = new EndOfSentenceScannerImpl();
		int numEosc = scanner.getEndOfSentenceCharacters().length;

		logger.info("Training new model from " + inFile.getAbsolutePath());
		logger.info("Using " + numEosc + " end of sentence characters.");

		Charset charset = Charset.forName("UTF-8");
		POSModel mod = null;

		MarkableFileInputStreamFactory mfisf = new MarkableFileInputStreamFactory(inFile);
		try (ObjectStream<String> lineStream = new PlainTextByLineStream(mfisf, charset)) {

			ObjectStream<POSSample> sampleStream = new WordTagSampleStream(lineStream);

			// Training Parameters
			TrainingParameters mlParams = TrainingParameters.defaultParams();
			// TrainingParameters mlParams = new TrainingParameters();
			// mlParams.put(TrainingParameters.ALGORITHM_PARAM, "MAXENT");
			// mlParams.put(TrainingParameters.ITERATIONS_PARAM, Integer.toString(iters));
			// mlParams.put(TrainingParameters.CUTOFF_PARAM, Integer.toString(cut));

			try {
				mod = POSTaggerME.train("pt", sampleStream, mlParams, new POSTaggerFactory());
			} finally {
				sampleStream.close();
			}
		}

		try (FileOutputStream outStream = new FileOutputStream(outFile)) {
			logger.info("Saving the model as: " + outFile.getAbsolutePath());
			mod.serialize(outStream);
		}
	}

	private static File LocateOrCreateFile(String fileName) {
		File ret = null;
		try {
			ret = FileLocator.locateFile(fileName);
		} catch (FileNotFoundException e) {
			ret = new File(fileName);
		}
		return ret;
	}

	public static void usage(Logger log) {
		log.info("Usage: java " + SentenceDetector.class.getName()
				+ " training_data_filename name_of_model_to_create");
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
			throw new IOException("Directory not found: " + f.getParentFile().getAbsolutePath());
		}
		return f;
	}

}
