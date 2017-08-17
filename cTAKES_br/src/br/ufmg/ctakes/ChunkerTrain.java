package br.ufmg.ctakes;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;

import org.apache.ctakes.core.ae.SentenceDetector;
import org.apache.ctakes.core.resource.FileLocator;
import org.apache.log4j.Logger;

import opennlp.tools.chunker.ChunkSample;
import opennlp.tools.chunker.ChunkSampleStream;
import opennlp.tools.chunker.ChunkerFactory;
import opennlp.tools.chunker.ChunkerME;
import opennlp.tools.chunker.ChunkerModel;
import opennlp.tools.chunker.DefaultChunkerContextGenerator;
import opennlp.tools.util.MarkableFileInputStreamFactory;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.PlainTextByLineStream;
import opennlp.tools.util.TrainingParameters;
import opennlp.tools.util.model.ModelUtil;

public class ChunkerTrain {
	/**
	 * Train a new chunker detector
	 * 
	 * @param args
	 *            training_data_filename name_of_model_to_create
	 * @throws IOException
	 */
	public static void main(String[] args) throws IOException {
		final Logger logger = Logger.getLogger(SentenceDetector.class.getName() + ".main()");

		// Handle arguments
		if (args.length < 2 || args.length > 4) {
			usage(logger);
			System.exit(-1);
		}

		File inFile = FileLocator.locateFile(args[0]);

		File outFile = LocateOrCreateFile(args[1]);

		logger.info("Training new model from " + inFile.getAbsolutePath());

		Charset charset = Charset.forName("UTF-8");

		MarkableFileInputStreamFactory mfisf = new MarkableFileInputStreamFactory(inFile);
		ObjectStream<String> lineStream =
		    new PlainTextByLineStream(mfisf,charset);
		ObjectStream<ChunkSample> sampleStream = new ChunkSampleStream(lineStream);

		ChunkerModel model =  null;

//		try {
//		  model = ChunkerME.train("pt-br", sampleStream,
//		      new DefaultChunkerContextGenerator(), TrainingParameters.defaultParams());
//		}
//		finally {
//		  sampleStream.close();
//		}

		// Training Parameters
		TrainingParameters mlParams = ModelUtil.createDefaultTrainingParameters();

		try {
			model = ChunkerME.train("pt-br", sampleStream, mlParams, new ChunkerFactory());
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			try {
				sampleStream.close();
			} catch (IOException e) {
				// sorry that this can fail
			}
		}

		try (

				FileOutputStream outStream = new FileOutputStream(outFile)) {
			logger.info("Saving the model as: " + outFile.getAbsolutePath());
			model.serialize(outStream);
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
			throw new IOException("Directory not found: " + f.getParentFile().getAbsolutePath());
		}
		return f;
	}

}
