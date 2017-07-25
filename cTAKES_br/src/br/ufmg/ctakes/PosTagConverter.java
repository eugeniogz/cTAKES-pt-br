package br.ufmg.ctakes;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Hashtable;
import java.util.List;

import org.apache.ctakes.core.pipeline.PipeBitInfo;
import org.apache.ctakes.typesystem.type.syntax.BaseToken;
import org.apache.ctakes.typesystem.type.syntax.NewlineToken;
import org.apache.ctakes.typesystem.type.syntax.PunctuationToken;
import org.apache.ctakes.typesystem.type.textspan.Segment;
import org.apache.ctakes.typesystem.type.textspan.Sentence;
import org.apache.log4j.Logger;
import org.apache.uima.analysis_engine.AnalysisEngineDescription;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.fit.component.JCasAnnotator_ImplBase;
import org.apache.uima.fit.factory.AnalysisEngineFactory;
import org.apache.uima.fit.factory.TypePrioritiesFactory;
import org.apache.uima.fit.factory.TypeSystemDescriptionFactory;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;

@PipeBitInfo(
		name = "Part of Speech Tag Converter pt:connl -> en::penn ",
		description = "Converts Parts of Speech TAGS from pt:connl to en::penn.",
		dependencies = { PipeBitInfo.TypeProduct.SECTION, PipeBitInfo.TypeProduct.SENTENCE,
				PipeBitInfo.TypeProduct.BASE_TOKEN, }
)
public class PosTagConverter extends JCasAnnotator_ImplBase {

	// LOG4J logger based on class name
	private Logger logger = Logger.getLogger(getClass().getName());

	@Override
	public void process(JCas jCas) throws AnalysisEngineProcessException {

		logger.info("process(JCas)");
		
		Hashtable<String, String> tags = new Hashtable<String, String>();
		
		tags.put("adj","JJ");
		tags.put("adv","RB");
		tags.put("art","PDT");
		tags.put("conj","CC");
		tags.put("conj-c","CC");
		tags.put("conj-s","CC");
		tags.put("ec","RP");
		tags.put("in","UH");
		tags.put("n","NN");
		tags.put("num","CD");
		tags.put("pp","RB");
		tags.put("pron","PDT");
		tags.put("pron-det","PDT");
		tags.put("pron-indp","WP");
		tags.put("pron","PRP");
		tags.put("pron-pers","PRP");
		tags.put("prop","NNP");
		tags.put("prp","IN");
		tags.put("v","VBZ");
		tags.put("v-fin","VBZ");
		tags.put("v-ger","VBG");
		tags.put("v-inf","VB");
		tags.put("v-pcp","VBG");

		Collection<Sentence> sentences = JCasUtil.select(jCas, Sentence.class);
		for (Sentence sentence : sentences) {

			List<BaseToken> printableTokens = new ArrayList<>();
			
			for(BaseToken token : JCasUtil.selectCovered(BaseToken.class,	sentence)){
			  if(token instanceof NewlineToken) continue;
			  printableTokens.add(token);
			}
			
			String[] words = new String[printableTokens.size()];
			for (int i = 0; i < words.length; i++) {
				words[i] = printableTokens.get(i).getCoveredText();
			}

			if (words.length > 0) {
				try {
					for (int i = 0; i < printableTokens.size(); i++) {
						BaseToken token = printableTokens.get(i);
						if (token instanceof PunctuationToken) {
							token.setPartOfSpeech(token.getCoveredText());
						}
						else
						{
							if (tags.containsKey(token.getPartOfSpeech())) {
								token.setPartOfSpeech(tags.get(token.getPartOfSpeech()));
							}
						}
					}
				} catch (Exception e) {
					throw new AnalysisEngineProcessException(
							"sentence being tagged is: '"
									+ sentence.getCoveredText() + "'", null, e);
				}
			}
		}
	}

	
	public static AnalysisEngineDescription createAnnotatorDescription()
			throws ResourceInitializationException {
		return AnalysisEngineFactory.createEngineDescription(
				PosTagConverter.class, TypeSystemDescriptionFactory
						.createTypeSystemDescription(), TypePrioritiesFactory
						.createTypePriorities(Segment.class, Sentence.class,
								BaseToken.class));
	}

}
