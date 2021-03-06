package thmp.search;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.ServletContext;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Set;
import java.util.TreeMap;
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Multimap;

import thmp.parse.Maps;
import thmp.parse.ParsedExpression;
import thmp.parse.DetectHypothesis.DefinitionListWithThm;
import thmp.search.LiteralSearch.LiteralSearchIndex;
import thmp.search.SearchCombined.ThmHypPair;
import thmp.search.Searcher.SearchMetaData;
import thmp.utils.WordForms.ThmPart;
import thmp.utils.WordForms.WordFreqComparator;
import thmp.utils.FileUtils;
import thmp.utils.GatherRelatedWords;
import thmp.utils.WordForms;
import thmp.utils.GatherRelatedWords.RelatedWords;

/**
 * Collects thms by reading in thms from Latex files. Gather
 * keywords from each thm. 
 * Keep map of common English words that should not be used
 * in mathObjMx. 
 * Maintains a Multimap of keywords and the theorems they are in, 
 * in particular their indices in thmList.
 * Build a structure that TriggerMathThm2 can use to build its
 * mathObjMx.
 * 
 * @author yihed
 */
public class CollectThm {
	
	/* Commented out June 2017.
	 * private static final List<String> rawFileStrList = Arrays.asList(new String[]{
			//"src/thmp/data/testContextVector.txt", 
			//"src/thmp/data/collectThmTestSample.txt"
			"src/thmp/data/fieldsRawTex.txt",
			//"src/thmp/data/CommAlg5.txt", 
			//"src/thmp/data/multilinearAlgebra.txt",
			//"src/thmp/data/functionalAnalysis.txt",			
			//"src/thmp/data/topology.txt"
			});*/
	
	private static final Logger logger = LogManager.getLogger();
	//latex macros source file name src/thmp/data/CommAlg5.txt
	private static final String MACROS_SRC = "src/thmp/data/texMacros.txt";
	
	//macros file
	private static volatile BufferedReader macrosDefReader;
	//InputStream for serialized parsed expressions list
	//private static volatile InputStream parsedExpressionListInputStream;
	//containing all serialized words from previous run.
	//private static volatile InputStream allThmWordsSerialInputStream;
	
	//wordFrequency.txt containing word frequencies and their part of speech (pos)
	//private static BufferedReader wordFrequencyBR;
	//servlet context if run from server
	private static ServletContext servletContext;
	
	/* Words that should be included as math words, but occur too frequently in math texts
	 * to be detected as non-fluff words. Put words here to be intentionally included in words map.
	 * Only singletons here. N-grams should be placed in N-gram files.
	 */
	private static final String[] SCORE_AVG_MATH_WORDS = new String[]{"ring", "field", "ideal", "finite", "series",
			"complex", "combination", "regular", "domain", "local", "smooth", "definition", "map", "standard", "prime",
			"injective", "surjective", "commut", "word", "act", "second", "every", "fppf"};
	//could be included, if already included, adjust the score to 1. If not, don't add.
	private static final String[] MIN_SCORE_MATH_WORDS = new String[]{"show","have","any", "many", "suppose","end","psl",
			"is"
	};
	//don't use this to affect building words map, since need entry for vital terms such as "is" (despite its insignificance
	//in terms of score), for forming contextual vectors, i.e need "is" in the words index map.
	private static final String[] SCORE0MATH_WORDS = new String[]{"such","say","will", "following","goodwillie","send", "iii",
			"ii","i","both"
	};
	//additional fluff words to add, that weren't listed previously
	private static final String[] ADDITIONAL_FLUFF_WORDS = new String[]{"tex", "is", "are", "an"};
	
	public static class FreqWordsSet{

		//Map of frequent words and their parts of speech (from words file data). Don't need the pos for now.
		private static final Set<String> commonEnglishNonMathWordsSet; 
		
		static{
			//only get the top N words
			commonEnglishNonMathWordsSet = WordForms.searchStopWords();
		}
		
		public static Set<String> commonEnglishNonMathWordsSet(){
			return commonEnglishNonMathWordsSet;
		}
	}
	
	/**
	 * Set servlet context, if run from server.
	 * @param srcFileReader
	 */
	public static void setServletContext(ServletContext servletContext_) {
		servletContext = servletContext_;
	}
	
	public static ServletContext getServletContext() {
		return servletContext;
	}
	
	/**
	 * The terms used by the SVD search, which are collected dynamically from the thms,
	 * are different than the ones used by context and relational vector search, whose
	 * terms also include the 2/3 grams and lexicon words (not just the ones that show up
	 * in the current set of thms), in addition to the terms used in the *previous* round
	 * of search (since these data were serialized).
	 */
	public static class ThmWordsMaps{
		
		private static final ImmutableMultimap<String, String> stemToWordsMMap;
		
		/*words and their document-wide frequencies. These words are normalized, 
		e.g. "annihilator", "annihiate" all have the single entry "annihilat" */
		private static final ImmutableMap<String, Integer> docWordsFreqMap;
		//entries are word and the indices of thms that contain that word.
		private static final ImmutableMultimap<String, IndexPartPair> wordThmsIndexMMap;
		
		private static final int CONTEXT_VEC_SIZE;
		
		private static final String NAMED_THMS_FILE_STR = "src/thmp/data/thmNames.txt";
		private static final Set<String> FLUFF_WORDS_SET = WordForms.stopWordsSet();
		//private static final Map<String, Integer> twoGramsMap = NGramsMap.get_twoGramsMap();
		//private static final Map<String, Integer> threeGramsMap = NGramsMap.get_threeGramsMap();	
		private static final List<String> skipGramWordsList;
		//set that contains the first word of the two and three grams of twoGramsMap and threeGramsMap		
		//so the n-grams have a chance of being called.
		private static final Set<String> nGramFirstWordsSet = new HashSet<String>();
		private static final int averageSingletonWordFrequency;
		//strip "theorem" away from words, to further reduce number of words
		private static final Pattern THEOREM_END_PATTERN = Pattern.compile("(.+) theorem");
		public static final Pattern THEORY_END_PATTERN = Pattern.compile("(.+) theory");
		
		/* Words and their indices.
		 * Deserialize the words used to form context and relation vectors. Note that this is a 
		 * separate* list from the words used in term document matrix.
		 * Absolute frequencies don't matter for forming context or relational vectors.
		 * List is ordered with respect to relative frequency, more frequent words come first,
		 * to optimize relation vector formation with BigIntegers.
		 * These words *alone* are used throughout all search algorithms by all maps, to guarantee consistency. 
		 */
		private static final Map<String, Integer> CONTEXT_VEC_WORDS_INDEX_MAP;
		//list in which the index CONTEXT_VEC_WORDS_INDEX_MAP
		private static final List<String> CONTEXT_VEC_WORDS_LIST;
		
		private static final ImmutableMap<String, Integer> CONTEXT_VEC_WORDS_FREQ_MAP;
		
		/** Map of keywords and their scores in document, the higher freq in doc, the lower 
		 * score, along the lines of  1/(log freq + 1) since log 1 = 0.  */		
		private static final ImmutableMap<String, Integer> wordsScoreMap;	
		//The number of frequent words to take
		//private static final int NUM_FREQ_WORDS = 500;
		//multiplication factors to deflate the frequencies of 2-grams and 3-grams to weigh
		//them more
		private static final double THREE_GRAM_FREQ_REDUCTION_FACTOR = 3.8/5;
		private static final double TWO_GRAM_FREQ_REDUCTION_FACTOR = 2.3/3;
		private static final Set<String> skipGramSkipWordsSet;
		//whether synonyms have been added to related words map.
		private static volatile boolean synonymsAddedToRelatedWordsQ = false;
		
		// \ufffd is unicode representation for the replacement char. But need to let special
		//chars such as ka"hler, C* algebra through.
		private static final Pattern SPECIAL_CHARACTER_PATTERN = 
				Pattern.compile("[-\\\\=$\\{\\}\\[\\]()^_+%&\\./,:;.!~\"\\d\\/@><*|`�\ufffd]");
		
	    private static final Set<String> specialCharVocabSet;
	    /**map of words without umlauts to their version with umlaut. To be used at search runtime.*/
	    private static final Map<String, String> umlautVocabMap;
	    
	    //allow typical chars containing umlauts.
	    //Pattern.compile("(\\\\\"u|\\\\\"o|\\\\\"a)");
		private static final Pattern umlautCharPatt = WordForms.umlautTexPatt;
		
		private static final boolean GATHER_SKIP_GRAM_WORDS = ThmList.gather_skip_gram_words();
		//private static final boolean GATHER_SKIP_GRAM_WORDS = true;
		/* Related words scraped from wiktionary, etc. 
		 * Related words are *only* used.
		 * to process queries, not the corpus; applied to all search algorithms. Therefore
		 * intentionally *not* final.
		 * Keys to relatedWordsMap are not necessarily normalized, only normalized if key not 
		 * already contained in docWordsFreqMap
		 */
		private static final Map<String, GatherRelatedWords.RelatedWords> relatedWordsMap;
		private static final Map<String, Integer> stockFrequencyMap = WordFrequency.ComputeFrequencyData.englishStockFreqMap();
		
		static{
			/*map of words and their representatives, e.g. "annihilate", "annihilator", etc all map to "annihilat"
			i.e. word of maps to their stems. */
			stemToWordsMMap = WordForms.stemToWordsMMap();
			
			/**Versions with no annotation, eg "hyp"/"stm" **/
			//ImmutableList.Builder<ImmutableMap<String, Integer>> thmWordsListBuilderNoAnno = ImmutableList.builder();
			/* *Only* used in data-gathering mode*/
			/** June 2017 Map<String, Integer> docWordsFreqPreMapNoAnno = new HashMap<String, Integer>();
			ImmutableSetMultimap.Builder<String, Integer> wordThmsMMapBuilderNoAnno = ImmutableSetMultimap.builder();*/
			
			//read in n-grams from file named NGRAM_DATA_FILESTR and put in appropriate maps, 
			//either twogrammap or threegrammap
			//readAdditionalNGrams(NGRAM_DATA_FILESTR);
			
			nGramFirstWordsSet.addAll(NGramSearch.get_2GramFirstWordsSet());
			nGramFirstWordsSet.addAll(ThreeGramSearch.get_3GramFirstWordsSet());
			skipGramWordsList = new ArrayList<String>();

			skipGramSkipWordsSet = new HashSet<String>();
			String[] skipGramSkipWordsAr = new String[] {"let","then","and","have"};
			for(String w : skipGramSkipWordsAr) {
				skipGramSkipWordsSet.add(w);
			}
			/** This list is smaller when in gathering data mode, and consists of a representative set 
			 * of theorems. Much larger in search mode.*/
			////.....List<String> processedThmList = ThmList.allThmsWithHypList; 
			
			//anything containing umlautCharPatt don't need to be included here.
			String[] specialCharVocabAr = new String[] {"l\\\'evy", "k\\\"ahler", "k\\\"ahlerian",
					"hyperk\\\"ahler", "C*", "C^*", "h\\\"older", "pl\\\"ucker", "lindel\\\"of",
					"m\\\"obius", "schro\\\"dinger", "l\\\"owner", "schr\\\"oder", "k\"unneth",
					"szeg\\\"o"};
			/*create reverse map of elements of specialCharVocabSet without umlaut to the version with umlaut, 
			  for use during search runtime */
			umlautVocabMap = new HashMap<String, String>();
			
			specialCharVocabSet = new HashSet<String>();
			for(String w : specialCharVocabAr) {
				specialCharVocabSet.add(w);		
				umlautVocabMap.put(WordForms.umlautTexPatt.matcher(w).replaceAll(""), w);
			}
			
			Map<String, Integer> wordsScorePreMap = new HashMap<String, Integer>();
			
			/*deserialize the word frequency map from file, as gathered from last time the data were generated.*/
			//CONTEXT_VEC_WORDS_FREQ_MAP = extractWordFreqMap();
			
			//the values are just the words' indices in wordsList.
			//this orders the list as well. INDEX map. Can rely on order as map is immutable.
			
			if(!FileUtils.isOSX()) {
				//putting in the OS check here, so running locally on OSX does not take forever to load.
				//should not build if not searching
				String wordThmIndexMMapPath = FileUtils.getPathIfOnServlet(SearchMetaData.wordThmIndexMMapSerialFilePath());				
				@SuppressWarnings("unchecked")
				Multimap<String, IndexPartPair> wordThmsIndexMultimap = ((List<Multimap<String, IndexPartPair>>)
						FileUtils.deserializeListFromFile(wordThmIndexMMapPath)).get(0);
				//temporary ugly conversion code for data migration, Dec 2017. Remove one month later.
				/*Collection<IndexPartPair> indexPartPairCol = wordThmsIndexMultimap.get("group");
				Iterator<IndexPartPair> iter = indexPartPairCol.iterator();
				try {
					IndexPartPair p = iter.next();					
				}catch(java.lang.ClassCastException e) {
					
					@SuppressWarnings("unchecked")
					Multimap<String, Integer> wordThmsIntMultimap = ((List<Multimap<String, Integer>>)
							FileUtils.deserializeListFromFile(wordThmIndexMMapPath)).get(0);
					wordThmsIndexMultimap = HashMultimap.create();
					for(Map.Entry<String, Integer> entry : wordThmsIntMultimap.entries()) {
						wordThmsIndexMultimap.put(entry.getKey(), new IndexPartPair(entry.getValue(), ThmPart.STM, new byte[] {}));
					}					
				}*/		
				wordThmsIndexMMap = ImmutableMultimap.copyOf(wordThmsIndexMultimap);
			}else {
				wordThmsIndexMMap = null;
			}				
				 //SearchMetaData.wordDocFreqMapPath()
				String docWordsFreqMapPath = FileUtils.getPathIfOnServlet(SearchMetaData.allThmWordsFreqListPath());
				/****@SuppressWarnings("unchecked")
				Map<String, Integer> docWordsFreqPreMap = ((List<Map<String, Integer>>)
						FileUtils.deserializeListFromFile(docWordsFreqMapNoAnnoPath)).get(0);*/
				
				@SuppressWarnings("unchecked")
				List<WordFreqPair> docWordsFrePreMapEntryList = (List<WordFreqPair>)
						FileUtils.deserializeListFromFile(docWordsFreqMapPath);
				
				List<String> wordsList = new ArrayList<String>();
				//The underlying map is a tree map, don't create immutable map from it for now.
				CONTEXT_VEC_WORDS_INDEX_MAP = createContextKeywordIndexDict(docWordsFrePreMapEntryList, wordsList);
				CONTEXT_VEC_WORDS_LIST = ImmutableList.copyOf(wordsList);
				/**construct map from list, since hashmap iteration does not guarantee ordering,
				//and we need ordering to create context vec map. */
				Map<String, Integer> docWordsFreqPreMap = new HashMap<String, Integer>();
				for(WordFreqPair pair : docWordsFrePreMapEntryList) {
					docWordsFreqPreMap.put(pair.word, pair.freq);
				}
				docWordsFreqMap = ImmutableMap.copyOf(docWordsFreqPreMap);
				
				//compute the average word frequencies for singleton words
				averageSingletonWordFrequency = computeSingletonWordsFrequency(docWordsFreqPreMap);			
				//add lexicon words to docWordsFreqMapNoAnno, which only contains collected words from thm corpus,
				//collected based on frequnency, right now. These words do not have corresponding thm indices.
				//***addLexiconWordsToContextKeywordDict(docWordsFreqPreMap, averageSingletonWordFrequency); //<--but these should have been adjusted already!!
				/*use stemToWordsMMap to re-adjust frequency of word stems that came from multiple forms, 
				 as these are much more likely to be math words, so don't want to scale down too much */
				//***adjustWordFreqMapWithStemMultiplicity(docWordsFreqPreMap, stemToWordsMMap);				
				
				buildScoreMap(wordsScorePreMap, docWordsFreqMap);	

				//***docWordsFreqMapNoAnno = ImmutableMap.copyOf(keyWordFreqTreeMap); //<--previous one
				/* RelatedWordsMap is only used during search, not data gathering! 
				 * Keys to relatedWordsMap are not necessarily normalized, only normalized if key not 
				 * already contained in docWordsFreqMapNoAnno.*/
				relatedWordsMap = deserializeAndProcessRelatedWordsMapFromFile(docWordsFreqMap);
				CONTEXT_VEC_WORDS_FREQ_MAP = docWordsFreqMap;
				
			/*}else{				
				buildScoreMapNoAnno(wordsScorePreMap, CONTEXT_VEC_WORDS_FREQ_MAP);
				/*Do *not* re-order map based on frequency, since need to be consistent with word row
				 * indices in term document matrix. Also should already be ordered. */
				/*Commented out June 2017.
				 * docWordsFreqMapNoAnno = CONTEXT_VEC_WORDS_FREQ_MAP;	
				relatedWordsMap = deserializeAndProcessRelatedWordsMapFromFile(docWordsFreqMapNoAnno);
				CONTEXT_VEC_WORDS_INDEX_MAP = createContextKeywordIndexDict(CONTEXT_VEC_WORDS_FREQ_MAP);
			}*/
			
			wordsScoreMap = ImmutableMap.copyOf(wordsScorePreMap);
			System.out.println("*********wordsScoreMapNoAnno.size(): " + wordsScoreMap.size());
			
			CONTEXT_VEC_SIZE = docWordsFreqMap.size();
			
			if(GATHER_SKIP_GRAM_WORDS){
				String skipGramWordsListFileStr = "src/thmp/data/skipGramWordsList.txt";
				FileUtils.writeToFile(skipGramWordsList, skipGramWordsListFileStr);
			}
		}
		
		/**
		 * Because HashMap$Node is not serializable.
		 */
		public static class WordFreqPair implements Serializable{
			
			private static final long serialVersionUID = -6594919249191163474L;
			
			public String word;
			private int freq;
			
			public WordFreqPair(String word_, int freq_) {
				this.word = word_;
				this.freq = freq_;
			}
			
		}
		
		/**
		 * Pair of thm index and thm component, whether context/hyp or main stm
		 * in thm. To be used to create map of word-thm index, to 
		 * differentiate scores between hyp and stm.
		 * Equals and HashCode Only takes into account the index, 
		 * not the ThmPart.
		 * Each instance corresponds to *one particular* word. Serialized as values of maps.
		 */
		public static class IndexPartPair implements Serializable{
			
			private static final long serialVersionUID = 3243328850026183247L;
			private static final int initIndexCountPerWord = 3;
			private static final byte placeholderIndex = LiteralSearchIndex.PLACEHOLDER_INDEX;
			
			//current length of wordIndexAr, used for efficient construction.
			private transient int curWordIndexLen;
			/*the word for this indexPartPair, used to prune generic-word thms, 
			 * e.g. ones whose sole word is "equation", "module", etc. Make transient 
			 * to not bloat up memory when serializing*/
			//private transient String word;
			
			private ThmPart thmPart;
			private int thmIndex;
			/**array of indices of the corresponding word in thm*/
			private byte[] wordIndexAr;
			//default to "Theorem". All descripter chars Upper-case, e.g. 'C', 'L', 'P'
			private char thmType = 'T';
			
			public IndexPartPair(int index_, ThmPart thmPart_, byte[] wordIndexAr_) {
				this.thmIndex = index_;
				this.thmPart = thmPart_;
				this.wordIndexAr = wordIndexAr_;				
			}
			
			public IndexPartPair(int index_, ThmPart thmPart_, char thmType_) {
				this.thmIndex = index_;
				this.thmPart = thmPart_;
				this.thmType = thmType_;
				this.wordIndexAr = new byte[initIndexCountPerWord];
				for(int i = 0; i < initIndexCountPerWord; i++) {
					wordIndexAr[i] = placeholderIndex;
				}
			}
			
			public void addWordIndex(byte index) {
				//currently don't expand.
				if(curWordIndexLen >= initIndexCountPerWord) {
					return;
				}
				wordIndexAr[curWordIndexLen] = index;
				curWordIndexLen++;
			}
			
			/**
			 * If in hypothetical/contextual part, e.g. to determine whether
			 * to downgrade score.
			 * @return
			 */
			public boolean isContextPart() {
				return this.thmPart == ThmPart.HYP;
			}
			/**
			 * Set the word for this indexPartPair, used to prune generic-word thms
			 * later.
			 * @param word_
			 */
			/*public void setWord(String word_) {
				this.word = word_;
			}*/
			
			/**
			 * the word for this indexPartPair, can be used to prune generic-word thms
			 * @return
			 */
			/*public String word() {
				return this.word;
			}*/
			
			public char thmType() {
				return this.thmType;
			}
			
			public ThmPart thmPart() {
				return this.thmPart;
			}
			
			public int thmIndex() {
				return this.thmIndex;
			}
			
			public byte[] wordIndexAr() {
				return this.wordIndexAr;
			}
			
			/**
			 * Important: Only takes into account the index, not the ThmPart or word index array.
			 * Since only need for e.g. determining if any previously added indexPair has same index.
			 */
			@Override
			public boolean equals(Object other) {
				if(null == other || !(other instanceof IndexPartPair)) {
					return false;
				}
				return this.thmIndex == ((IndexPartPair)other).thmIndex;				
			}
			
			@Override
			public int hashCode() {				
				return this.thmIndex;
			}

			@Override
			public String toString() {
				return thmIndex + " Index Ar: " +Arrays.toString(wordIndexAr);
			}
			
			/**
			 * Adds to map, only add if thm part.
			 * @param thmIndexPairMap
			 */
			public void addToMap(Map<Integer, ThmPart> thmIndexPairMap) {
				ThmPart part = thmIndexPairMap.get(thmIndex);
				if(ThmPart.STM != part) {
					thmIndexPairMap.put(thmIndex, this.thmPart);
				}				
			}
		}
		
		/**
		 * Builds the relevant maps. E.g. to gather a comprehensive and representative
		 * set of word frequency maps.
		 * @param docWordsFreqPreMapNoAnno
		 * @param wordThmsMMapBuilderNoAnno
		 * @param wordsScorePreMap Ordered w.r.t. frequency, so to optimize 
		 * forming relation search vecs, which are BigInteger's.
		 */
		public static Map<String, Integer> buildDocWordsFreqMap(List<String> thmList) {
			
			//individual words used in two-grams, since they are math two grams with high prob,
			//the component words are very likely to be math words (verified with observation).
			Set<String> twoGramComponentWordsSingularSet = new HashSet<String>();
			Map<String, Integer> docWordsFreqPreMap = buildDocWordsFreqMap2(thmList, twoGramComponentWordsSingularSet);
			
			//first compute the average word frequencies for singleton words
			int avgSingletonWordFreq = computeSingletonWordsFrequency(docWordsFreqPreMap);	
			
			//add the singleton words in named theorems, e.g. Ax-Grothendieck , mostly mathematicians' names
			addNamedThmsToMap(docWordsFreqPreMap, avgSingletonWordFreq);
			
			for(String word : twoGramComponentWordsSingularSet){
				//singleton words are all normalized
				word = WordForms.normalizeWordForm(word);
				if(!docWordsFreqPreMap.containsKey(word)){
					docWordsFreqPreMap.put(word, avgSingletonWordFreq);
				}
			}			
			//add lexicon words to docWordsFreqMapNoAnno, which only contains collected words from thm corpus,
			//collected based on frequnency, right now. These words do not have corresponding thm indices.
			addLexiconWordsToContextKeywordDict(docWordsFreqPreMap, avgSingletonWordFreq);
			/*use stemToWordsMMap to re-adjust frequency of word stems that came from multiple forms, 
			 as these are much more likely to be math words, so don't want to scale down too much */
			adjustWordFreqMapWithStemMultiplicity(docWordsFreqPreMap, stemToWordsMMap);		
			
			removeLowFreqWords(docWordsFreqPreMap);			

			//ReorderDocWordsFreqMap uses a TreeMap to reorder. Used to optimize 
			//forming relation search vecs, which are BigInteger's.
			//Wrap in HashMap, since the comparator for the TreeMap depends on a frequency map, which can be fragile.
			return new HashMap<String, Integer>(reorderDocWordsFreqMap(docWordsFreqPreMap));			
		}
		
		/**
		 * Add the singleton words in named theorems, e.g. Ax-Grothendieck , mostly mathematicians' names
		 * @param docWordsFreqPreMap
		 */
		private static void addNamedThmsToMap(Map<String, Integer> docWordsFreqPreMap,
				int avgSingletonWordFreq) {
			String namedThmsFileStr = FileUtils.getPathIfOnServlet(NAMED_THMS_FILE_STR);
			try{
				BufferedReader bReader = new BufferedReader(new FileReader(namedThmsFileStr));
				try{
					String line;
					while((line = bReader.readLine()) != null){
						//split line into tokens
						String lineNoDiacritics = WordForms.removeDiacritics(line.toLowerCase());
						List<String> lineAr = WordForms.splitThmIntoSearchWordsList(lineNoDiacritics);
						for(String word : lineAr){
							if(word.length() < 3){
								//e.g. "of", but include e.g. "lie"
								continue;
							}
							//System.out.println("CollectThm word - " + word);
							if(!docWordsFreqPreMap.containsKey(word)){
								docWordsFreqPreMap.put(word, avgSingletonWordFreq);															
							}
						}
					}
				}finally{
					FileUtils.silentClose(bReader);
				}
			}catch(FileNotFoundException e){
				//same treatment as IOException for now
				throw new IllegalStateException("FileNotFoundException when adding Named theorems!", e);
			}catch(IOException e){
				throw new IllegalStateException("IOException when adding Named theorems!", e);
			}
		}
		
		/**
		 * Deserializes and processes, normalize the words. Related words are only used
		 * to process queries, not the corpus; applied to all search algorithms.
		 * Process here rather than at map formation, since synonymsMap need to pertain 
		 * to current corpus.
		 * 		
		/* Word and its synonymous representative in the term document matrix, if such 
		 * a synonym has been added to the map already. If not, add the rep. This is for
		 * words that are interchangeable, not similar not non-interchangeable words.
		 * Create only one entry in term-document matrix for each synonym group. 
		 * @param docWordsFreqMapNoAnno 
		 * @return
		 */
		@SuppressWarnings("unchecked")
		private static Map<String, RelatedWords> deserializeAndProcessRelatedWordsMapFromFile(Map<String, Integer> docWordsFreqMapNoAnno) {
			String relatedWordsMapFileStr = FileUtils.getRELATED_WORDS_MAP_SERIAL_FILE_STR();
			Map<String, RelatedWords> relatedWordsMap;
			if(null != servletContext){
				InputStream relatedWordsMapInputStream = servletContext.getResourceAsStream(relatedWordsMapFileStr);				
				List<Map<String, RelatedWords>> list 
					= (List<Map<String, RelatedWords>>)FileUtils.deserializeListFromInputStream(relatedWordsMapInputStream);
				FileUtils.silentClose(relatedWordsMapInputStream);
				relatedWordsMap = list.get(0);
			}else{		
				List<Map<String, RelatedWords>> list 
					= (List<Map<String, RelatedWords>>)FileUtils.deserializeListFromFile(relatedWordsMapFileStr);
				relatedWordsMap = list.get(0);
			}
			
			int relatedWordsUsedCounter = 0;
			Set<Entry<String, RelatedWords>> relatedWordsEntrySet = relatedWordsMap.entrySet();
			Iterator<Entry<String, RelatedWords>> relatedWordsEntrySetIter = relatedWordsEntrySet.iterator();
			
			Map<String, RelatedWords> relatedWordsTempMap = new HashMap<String, RelatedWords>();
			while(relatedWordsEntrySetIter.hasNext()){
				Entry<String, RelatedWords> relatedWordsEntry = relatedWordsEntrySetIter.next();
				String word = relatedWordsEntry.getKey();
				if(!docWordsFreqMapNoAnno.containsKey(word)){
					word = WordForms.normalizeWordForm(word);
				}
				/*if(!docWordsFreqMapNoAnno.containsKey(word)){
					continue;
				}*/				
				relatedWordsEntrySetIter.remove();
				//don't deserialize related words, reconstruct anew each time!!
				RelatedWords relatedWords = relatedWordsEntry.getValue();
				//hack Feb 18, for GatherRelatedWords$RelatedWords.addToSynonyms(GatherRelatedWords.java:205)
				if(relatedWords.getSynonymsList().isEmpty()) {
					relatedWords.setSynonyms(new ArrayList<String>());
				}
				RelatedWords normalizedRelatedWords 
					= relatedWords.normalizeFromValidWordSet(docWordsFreqMapNoAnno.keySet());
				
				relatedWordsTempMap.put(word, normalizedRelatedWords);
				relatedWordsUsedCounter++;
			}
			relatedWordsMap.putAll(relatedWordsTempMap);
			
			System.out.println("CollectThm.ThmWordsMap - Total number of related words entries adapted: " + relatedWordsUsedCounter);
			return relatedWordsMap;
		}
		
		/**
		 * Use stemToWordsMMap to re-adjust frequency of word stems that came from multiple forms, 
			as these are much more likely to be math words, so don't want to scale down too much.
		 *	E.g. annihil is the stem for both annihilator and annihilate, don't want to score annihil
		 *	based on the combined frequency of annihilator and annihilate.
		 * @param docWordsFreqPreMapNoAnno
		 * @param stemtowordsmmap2
		 */
		private static void adjustWordFreqMapWithStemMultiplicity(Map<String, Integer> docWordsFreqPreMapNoAnno,
				ImmutableMultimap<String, String> stemToWordsMMap_) {
			double freqAdjustmentFactor = 3.0/4;
			
			Map<String, Integer> modifiedWordFreqMap = new HashMap<String, Integer>();
			Iterator<Map.Entry<String, Integer>> freqMapIter = docWordsFreqPreMapNoAnno.entrySet().iterator();
			while(freqMapIter.hasNext()){
				Map.Entry<String, Integer> entry = freqMapIter.next();				
				String wordStem = entry.getKey();
				if(stemToWordsMMap_.containsKey(wordStem)){
					int formsCount = (int)(stemToWordsMMap_.get(wordStem).size()*freqAdjustmentFactor);
					//pre-processing should have eliminated stems with freq 1
					if(formsCount < 2) continue;
					int adjustedFreq = entry.getValue()/formsCount;
					adjustedFreq = adjustedFreq > 0 ? adjustedFreq : 1;
					modifiedWordFreqMap.put(wordStem, adjustedFreq);
				}
				//eliminate duplicates, if a word and its singleton forms are both included,
				//e.g graph, graphs
				if(stockFrequencyMap.containsKey(wordStem)){
					//valid word, don't need singularize
					continue;
				}
				String wordSingular = WordForms.getSingularForm(wordStem);
				Integer singularWordFreq = docWordsFreqPreMapNoAnno.get(wordSingular);
				if(null != singularWordFreq && !wordStem.equals(wordSingular)){
					modifiedWordFreqMap.put(wordSingular, (int)((singularWordFreq + entry.getValue())*3./4));
					freqMapIter.remove();
					//logger.info(wordStem + " removed in favor of " + wordSingular);
				}
				//if(FreqWordsSet.commonEnglishNonMathWordsSet.contains(word)
				Matcher m;
				
				if((m=THEOREM_END_PATTERN.matcher(wordStem)).matches()){
					freqMapIter.remove();
					String word = m.group(1);
					//eliminate e.g. "main".
					if(!FreqWordsSet.commonEnglishNonMathWordsSet.contains(word)){						
						modifiedWordFreqMap.put(word, entry.getValue());						
					}
				}
			}
			docWordsFreqPreMapNoAnno.putAll(modifiedWordFreqMap);
		}
		
		/**
		 * deserialize words map used to form context and relation vectors, which were
		 * formed while parsing through the papers in e.g. DetectHypothesis.java. This is
		 * so we don't parse everything again at every server initialization.
		 * 
		 * @return Map of words and their frequencies.
		 */
		@SuppressWarnings("unchecked")
		private static ImmutableMap<String, Integer> extractWordFreqMap() {	
			//It is "src/thmp/data/allThmWordsMap.dat";			
			String pathToPrevDocWordFreqMaps = Searcher.SearchMetaData.previousWordDocFreqMapsPath();
			String allThmWordsSerialFileStr = (null == pathToPrevDocWordFreqMaps 
					? thmp.parse.DetectHypothesis.allThmWordsMapSerialFileStr : pathToPrevDocWordFreqMaps);
			
			if(null != servletContext){
				InputStream allThmWordsSerialInputStream = servletContext.getResourceAsStream(allThmWordsSerialFileStr);
				Map<String, Integer> map 
					= ((List<Map<String, Integer>>)FileUtils.deserializeListFromInputStream(allThmWordsSerialInputStream)).get(0);
				FileUtils.silentClose(allThmWordsSerialInputStream);
				return ImmutableMap.copyOf(map);
			}else{				
				Map<String, Integer> map 
					= ((List<Map<String, Integer>>)FileUtils.deserializeListFromFile(allThmWordsSerialFileStr)).get(0);
				return ImmutableMap.copyOf(map);
			}
		}
		
		/**
		 * Creates a map, ordered by frequency, with keys words and words their indices in map.
		 * @param wordsList
		 * @return Map of words and their indices in wordsList.
		 */
		public static Map<String, Integer> createContextKeywordIndexDict(List<WordFreqPair> wordFreqPairList, 
				List<String> wordsList){
			Map<String, Integer> contextKeywordIndexDict = new HashMap<String, Integer>();
			//these are ordered based on frequency, more frequent words occur earlier.
			
			int wordFreqPairsSz = wordFreqPairList.size();
			for(int i = 0; i < wordFreqPairsSz; i++){		
				String word = wordFreqPairList.get(i).word;
				contextKeywordIndexDict.put(word, i);
				wordsList.add(word);
			}
			return contextKeywordIndexDict;
		}

		/**
		 * @param docWordsFreqPreMapNoAnno
		 * @return
		 */
		public static Map<String, Integer> reorderDocWordsFreqMap(Map<String, Integer> docWordsFreqPreMapNoAnno) {
			//re-order the list so the most frequent words appear first, as optimization
			//so that search words can match the most frequently-occurring words.
			WordFreqComparator comp = new WordFreqComparator(docWordsFreqPreMapNoAnno);
			//words and their frequencies in wordDoc matrix.
			Map<String, Integer> keyWordFreqTreeMap = new TreeMap<String, Integer>(comp);
			keyWordFreqTreeMap.putAll(docWordsFreqPreMapNoAnno);
			return keyWordFreqTreeMap;
		}
		
		/**
		 * Map of words in  and their indices.
		 * Words used to form context and relation vectors. Note that this is a 
		 * separate* list from the words used in term document matrix.
		 * Absolute frequencies don't matter for forming context or relational vectors.
		 * List is ordered with respect to relative frequency, more frequent words come first,
		 * to optimize relation vector formation with BigIntegers.
		 */
		public static Map<String, Integer> get_CONTEXT_VEC_WORDS_INDEX_MAP(){
			return CONTEXT_VEC_WORDS_INDEX_MAP;
		}
		
		public static List<String> get_CONTEXT_VEC_WORDS_LIST(){
			return CONTEXT_VEC_WORDS_LIST;
		}
		
		public static int get_CONTEXT_VEC_SIZE(){
			return CONTEXT_VEC_SIZE;
		}
		
		public static ImmutableMap<String, Integer> get_CONTEXT_VEC_WORDS_FREQ_MAP_fromData(){
			return CONTEXT_VEC_WORDS_FREQ_MAP;
		}
		
		/**
		 * Map of words without umlauts to their version with umlaut. 
		 * To be used at search runtime for user input.
		 * @return
		 */
		public static Map<String, String> umlautVocabMap(){
			return umlautVocabMap;
		}
		
		public static Map<String, RelatedWords> getRelatedWordsMap(){
			//add synonyms
			if(!synonymsAddedToRelatedWordsQ) {
				synchronized(ThmWordsMaps.class) {
					if(!synonymsAddedToRelatedWordsQ) {
						//already normalized
						Multimap<String, String> synonymsMMap = WordForms.getSynonymsMap1();
						Set<String> wordSet = synonymsMMap.keySet();
						for(String word : wordSet) {
							Collection<String> synonyms = synonymsMMap.get(word);
							
							RelatedWords relatedWord = relatedWordsMap.get(word);
							if(null == relatedWord) {
								relatedWordsMap.put(word, new RelatedWords(new ArrayList<String>(synonyms), null, null));
							}else {
								//change this
								List<String> list = new ArrayList<String>();
								list.addAll(synonyms);
								list.addAll(relatedWord.getSynonymsList());
								relatedWord.setSynonyms(list);								
							}
						}
						synonymsAddedToRelatedWordsQ = true;
					}
				}
			}
			return relatedWordsMap;
		}
		
		/**
		 * Add lexicon words to docWordsFreqMapNoAnno, which only contains collected words from thm corpus,
		 * collected based on frequnency, right now. This is curated list of words, so don't need much normalization.
		 * e..g. already singular.
		 */
		private static void addLexiconWordsToContextKeywordDict(Map<String, Integer> docWordsFreqMapNoAnno,
				int averageSingletonWordFrequency){
			
			ListMultimap<String, String> posMMap = Maps.essentialPosMMap();
			int avgWordFreq = averageSingletonWordFrequency;
			//add avg frequency based on int
			for(Map.Entry<String, String> entry : posMMap.entries()){
				String pos = entry.getValue();
				if(pos.equals("ent") || pos.equals("adj")){
					String word = entry.getKey();
					word = WordForms.normalizeWordForm(word);
					if(!docWordsFreqMapNoAnno.containsKey(word)){
						docWordsFreqMapNoAnno.put(word, avgWordFreq);
					}
				}
			}			
		}
		
		/**
		 * Remove the low-freq words, to reduce number of words.
		 * Right now remove words with freq 1 and 2.
		 * @param docWordsFreqPreMapNoAnno
		 */
		private static void removeLowFreqWords(Map<String, Integer> docWordsFreqPreMapNoAnno) {
			Iterator<Entry<String, Integer>> entrySetIter = docWordsFreqPreMapNoAnno.entrySet().iterator() ;
			while(entrySetIter.hasNext()){
				int freq = entrySetIter.next().getValue();
				//remove the low-freq words, to reduce number of words
				if(freq < 3){
					entrySetIter.remove();
					//continue;
				}
			}
		}
		
		/**
		 * Computes the averageSingletonWordFrequency.
		 * @param docWordsFreqPreMapNoAnno
		 * @return
		 */
		private static int computeSingletonWordsFrequency(Map<String, Integer> docWordsFreqPreMapNoAnno) {
			int freqSum = 0;
			int count = 0;
			
			Iterator<Entry<String, Integer>> entrySetIter = docWordsFreqPreMapNoAnno.entrySet().iterator() ;
			while(entrySetIter.hasNext()){
				int freq = entrySetIter.next().getValue();
				//don't count the low-freq words, they will be removed to reduce number of words
				if(freq < 2){
					continue;
				}
				freqSum += freq;
				count++;
			}
			
			/*for(int freq : docWordsFreqPreMapNoAnno.values()){
				freqSum += freq;
				count++;
			}*/
			if (0 == count) return 1;
			return freqSum/count;
		}

		/**
		 * Returns average word frequency for singleton words.
		 * @return
		 */
		public static int singletonWordsFrequency(){
			return averageSingletonWordFrequency;
		}
		
		/**
		 * Used for building skip gram word list to create synonyms.
		 * @param thmList
		 * @param skipGramWordList_
		 * @throws IOException
		 * @throws FileNotFoundException
		 */
		public static void createSkipGramWordList(List<String> thmList,
				List<String> skipGramWordList_){
			System.out.print("Inside createSkipGramWordList!");
			Map<String, Integer> docWordsFreqPreMap = new HashMap<String, Integer>();
			ImmutableSetMultimap.Builder<String, Integer> wordThmsMMapBuilder 
				= new ImmutableSetMultimap.Builder<String, Integer>();			
			buildSkipGramMaps(//thmWordsFreqListBuilder, 
					docWordsFreqPreMap, wordThmsMMapBuilder, thmList, skipGramWordList_);						
		}
		
		/**
		 * Add words in a given theorem to word-thm-index MMap that will 
		 * be used for intersection search. 
		 * @param wordThmsMMap demand to be hashMultimap, need O(1) lookup
		 * guarantee.
		 * @param wordThmsMMapBuilder
		 * @param thm
		 * @param thmIndex
		 */
		public static void addToWordThmIndexMap(HashMultimap<String, IndexPartPair> wordThmsMMap,
				String thm, IndexPartPair indexPartPair){
			
			final Map<String, Integer> twoGramsMap = NGramsMap.get_twoGramsMap();
			final Map<String, Integer> threeGramsMap = NGramsMap.get_threeGramsMap();			
			
			//multimap of word and lists of integers.
			ListMultimap<String, Integer> wordIndexMMap = ArrayListMultimap.create();
			
			thm = thm.toLowerCase();
			
			Matcher matcher = WordForms.queryCStarPatt.matcher(thm);
			if(matcher.find()) {
				thm = matcher.replaceAll(WordForms.queryCStarReplStr);
			}
			
			//number of words to skip if an n gram has been added.
			//int numFutureWordsToSkip = 0;
				//split along e.g. "\\s+|\'|\\(|\\)|\\{|\\}|\\[|\\]|\\.|\\;|\\,|:"
			List<String> thmAr = WordForms.splitThmIntoSearchWordsList(thm);
			List<String> thmList = new ArrayList<String>();
			
			for(String word : thmAr) {
				//preprocess so word index wouldn't get too large, e.g. TeX.
				if(!SPECIAL_CHARACTER_PATTERN.matcher(word).find()){
					thmList.add(word);
				}else if(umlautCharPatt.matcher(word).find() || specialCharVocabSet.contains(word)) {
					//note that umlauts can also be written with { } e.g. schr\"{o}dinger.					
					word = WordForms.stripUmlautFromWord(word);
					thmList.add(word);
				}
			}
			
			int thmListSz = thmList.size();
			for(int j = 0; j < thmListSz; j++){
				String word = thmList.get(j);	
					//only keep words with lengths > threshold
					int lengthCap = 3;
					
					boolean isNGramFirstWord = nGramFirstWordsSet.contains(word);
					//word length could change, so no assignment to variable. First check not first
					//word in n-grams. E.g. "p-adic", "C* algebra".
					if(word.length() < lengthCap && !isNGramFirstWord){
						continue;
					}
					String wordOriginal = word;
					
					/*This processing pipeline needs to *exactly* match the one when searching.
					 * In SearchIntersection.intersectionSearch*/
					
					//get singular forms if plural, put singular form in map.					
					word = WordForms.getSingularForm(word);						
					//also don't skip if word is contained in lexicon					
					if(FLUFF_WORDS_SET.contains(word)){ 
						continue;
					}					
					if(FreqWordsSet.commonEnglishNonMathWordsSet.contains(word) && !isNGramFirstWord){ 
						continue;					
					}
					//check the following word
					if(j < thmListSz-1){
						String nextWordCombined = wordOriginal + " " + thmList.get(j+1);
						String twoGramNormal = WordForms.normalizeTwoGram(nextWordCombined);
						
						Integer twoGramFreq = twoGramsMap.get(nextWordCombined);
						Integer twoGramNormalFreq = twoGramsMap.get(twoGramNormal);
						
						if(twoGramFreq != null || twoGramNormalFreq != null){
							if(!SPECIAL_CHARACTER_PATTERN.matcher(twoGramNormal).find()){
								wordIndexMMap.put(twoGramNormal, j);
							}
						}
						//try to see if these three words form a valid 3-gram
						if(j < thmListSz-2){
							String threeWordsCombined = nextWordCombined + " " + thmList.get(j+2);
							String threeGramNormal = WordForms.normalizeNGram(threeWordsCombined);
							
							Integer threeGramFreq = threeGramsMap.get(threeWordsCombined);
							Integer threeGramNormalFreq = threeGramsMap.get(threeGramNormal);
							
							if(threeGramFreq != null || threeGramNormalFreq != null){
								if(!SPECIAL_CHARACTER_PATTERN.matcher(threeGramNormal).find()){									
									wordIndexMMap.put(threeGramNormal, j);
								}
							}
						}
					}
					
					String wordNormalized = null;
					String wordSingularized = null;
					
					//recall umlaut has been stripped.
					if(!LiteralSearch.isInValidSearchWord(word) && (!stockFrequencyMap.containsKey(word) ||
							wordsScoreMap.containsKey(word) || 
							wordsScoreMap.containsKey(wordNormalized=WordForms.normalizeWordForm(word) ) ||
							wordsScoreMap.containsKey(wordSingularized=WordForms.getSingularForm(word) ) ) ) {
						
						//singularize all words full stop, making lookup faster as well, so don't need to play games 
						//in checking.
						word = wordSingularized == null ? WordForms.getSingularForm(word) : wordSingularized;
						
						//removes endings such as -ing, and uses synonym rep.
						//e.g. "annihilate", "annihilator", etc all map to "annihilat"
						word = wordNormalized == null ? WordForms.normalizeWordForm(word) : wordNormalized;
						
						//Note this deliberately includes more words than in lexicon.
						//this is not much different from literal search then, only more stringent in word selection.
						//wordThmsMMap.put(word, indexPartPair);
						wordIndexMMap.put(word, j);
					}
				}
			
			for(String word : wordIndexMMap.keys()) {
				Collection<Integer> indexCol = wordIndexMMap.get(word);
				
				List<Byte> byteList = new ArrayList<Byte>();
				for(Integer index : indexCol) {
					if(index >= Byte.MAX_VALUE) {
						break;
					}
					byteList.add(index.byteValue());
				}
				int byteListSz = byteList.size();
				byte[] byteAr = new byte[byteListSz];
				for(int i = 0; i < byteListSz; i++) {
					byteAr[i] = byteList.get(i);
				}
				IndexPartPair indexPartPair2 = new IndexPartPair(indexPartPair.thmIndex, 
						indexPartPair.thmPart, byteAr);
				wordThmsMMap.put(word, indexPartPair2);				
			}			
		}
		
		/**
		 * Builds map when supplied externally with list of theorems.
		 * @param docWordsFreqPreMap
		 * @param wordThmsMMapBuilder
		 * @param thmList
		 */
		private static Map<String, Integer> buildDocWordsFreqMap2(List<String> thmList, 
				Set<String> twoGramComponentWordsSingularSet){	
			
			Map<String, Integer> docWordsFreqPreMap = new HashMap<String, Integer>();
			Map<String, Integer> twoGramsMap = NGramsMap.get_twoGramsMap();
			Map<String, Integer> threeGramsMap = NGramsMap.get_threeGramsMap();	
			
			//add all N-grams at once, instead of based on current theorem set.
			////docWordsFreqPreMap.putAll(twoGramsMap);
			//gather individual word tokens that are part of two grams.
			for(Map.Entry<String, Integer> twoGramEntry : twoGramsMap.entrySet()){
				String twoGram = WordForms.normalizeTwoGram(twoGramEntry.getKey());
				int freq = (int)(twoGramEntry.getValue()*TWO_GRAM_FREQ_REDUCTION_FACTOR);
				if(freq < 2){
					//now skip word, since these are unlikely to be valid n-grams due to 
					//their low frequency (observation-based).
					continue;
				}
				/////don't delete yet freq = freq == 0 ? 1 : freq;				
				docWordsFreqPreMap.put(twoGram, freq);
				
				String[] twoGramAr = WordForms.getWhiteNonEmptySpaceNotAllPattern().split(twoGram);
				twoGramComponentWordsSingularSet.add(WordForms.getSingularForm(twoGramAr[0]));
				//second word should have been singularized already.
				twoGramComponentWordsSingularSet.add(WordForms.getSingularForm(twoGramAr[1]));
			}
			for(Map.Entry<String, Integer> threeGramEntry : threeGramsMap.entrySet()){
				int freq = (int)(threeGramEntry.getValue()*THREE_GRAM_FREQ_REDUCTION_FACTOR);
				if(freq < 2){
					//now skip word, since these are unlikely to be valid n-grams due to 
					//their low frequency (observation-based).
					continue;
				}
				/////freq = freq == 0 ? 1 : freq;
				docWordsFreqPreMap.put(threeGramEntry.getKey(), freq);
			}			
			for(int i = 0; i < thmList.size(); i++){
				//System.out.println(counter++);
				String thm = thmList.get(i);				
				//split along e.g. "\\s+|\'|\\(|\\)|\\{|\\}|\\[|\\]|\\.|\\;|\\,|:"
				List<String> thmAr = WordForms.splitThmIntoSearchWordsList(thm.toLowerCase());
				int thmArSz = thmAr.size();
				for(int j = 0; j < thmArSz; j++){					
					/*String singletonWordAdded = null;
					String twoGramAdded = null;
					String threeGramAdded = null;*/				
					String word = thmAr.get(j);	
					//only want lower alphabetical words for now, to reduce num of words - July 2017
					if(!NGramSearch.ALPHA_PATTERN.matcher(word).matches()){
						continue;
					}
					//skip words that contain special characters
					if(WordForms.SPECIAL_CHARS_PATTERN.matcher(word).matches()){
						continue;
					}					
					//only keep words with lengths > 2
					//System.out.println(word);
					int lengthCap = 3;
					//word length could change, so no assignment to variable.
					if(word.length() < lengthCap){
						continue;
					}		
					//get singular forms if plural, put singular form in map
					//Should be more careful on some words that shouldn't be singular-ized!					
					word = WordForms.getSingularForm(word);	
					
					//also don't skip if word is contained in lexicon
					if(FLUFF_WORDS_SET.contains(word)){ 
						continue;
					}					
					if(FreqWordsSet.commonEnglishNonMathWordsSet.contains(word) && !nGramFirstWordsSet.contains(word)){ 
						continue;					
					}
					//addNGramsToMap(docWordsFreqPreMap, twoGramsMap, threeGramsMap, i, thmAr, j, word);					
					//removes endings such as -ing, and uses synonym rep.
					//e.g. "annihilate", "annihilator", etc all map to "annihilat"
					word = WordForms.normalizeWordForm(word);
					addWordToMaps(word, i, //thmWordsFreqMap, //thmWordsFreqListBuilder, 
							docWordsFreqPreMap);
				}//done iterating through this thm
			}
			return docWordsFreqPreMap;
		}
		/**
		 * @param docWordsFreqPreMap
		 * @param twoGramsMap
		 * @param threeGramsMap
		 * @param i
		 * @param thmAr
		 * @param thmWordsFreqMap
		 * @param j
		 * @param word
		 * @deprecated July 2017. Wait one month and delete.
		 */
		private static void addNGramsToMap(Map<String, Integer> docWordsFreqPreMap, Map<String, Integer> twoGramsMap,
				Map<String, Integer> threeGramsMap, int i, String[] thmAr, //Map<String, Integer> thmWordsFreqMap, 
				int j,
				String word) {
			//check the following word
			if(j < thmAr.length-1){
				String nextWordCombined = word + " " + thmAr[j+1];
				nextWordCombined = WordForms.normalizeTwoGram(nextWordCombined);
				Integer twoGramFreq = twoGramsMap.get(nextWordCombined);
				if(twoGramFreq != null){
					int freq = (int)(twoGramFreq*TWO_GRAM_FREQ_REDUCTION_FACTOR);
					twoGramFreq = freq == 0 ? 1 : freq;
					addNGramToMaps(nextWordCombined, i, //thmWordsFreqMap, //thmWordsFreqListBuilder, 
							docWordsFreqPreMap, twoGramFreq);
				}
				//try to see if these three words form a valid 3-gram
				if(j < thmAr.length-2){
					String threeWordsCombined = nextWordCombined + " " + thmAr[j+2];
					Integer threeGramFreq = threeGramsMap.get(threeWordsCombined);
					if(threeGramFreq != null){
						//reduce frequency so 3-grams weigh more 
						int freq = (int)(threeGramFreq*THREE_GRAM_FREQ_REDUCTION_FACTOR);
						threeGramFreq = freq == 0 ? 1 : freq;
						addNGramToMaps(threeWordsCombined, i, //thmWordsFreqMap, //thmWordsFreqListBuilder, 
								docWordsFreqPreMap, threeGramFreq);
					}
				}
			}
		}
		
		/**
		 * Same as readThm, except without hyp/concl wrappers.
		 * Maps contain the same set of words. 
		 * //@deprecated *Note* Feb 2018: keep this for now for the addition to skipGramWordList.
		 * 
		 * @param thmWordsFreqListBuilder 
		 * 		List of maps, each of which is a map of word Strings and their frequencies. Used for SVD search.
		 * @param thmListBuilder
		 * @param docWordsFreqPreMap
		 * @param wordThmsMMapBuilder
		 * @throws IOException
		 * @throws FileNotFoundException
		 */
		private static void buildSkipGramMaps(//ImmutableList.Builder<ImmutableMap<String, Integer>> thmWordsFreqListBuilder,
				Map<String, Integer> docWordsFreqPreMap,
				ImmutableSetMultimap.Builder<String, Integer> wordThmsMMapBuilder, List<String> thmList,
				List<String> skipGramWordList_){
			
			//Map<String, Integer> twoGramsMap = NGramsMap.get_twoGramsMap();
			//Map<String, Integer> threeGramsMap = NGramsMap.get_threeGramsMap();			
			ListMultimap<String, String> posMMap = Maps.posMMap();
			
			//processes the theorems, select the words
			for(int i = 0; i < thmList.size(); i++){
				//System.out.println(counter++);
				String thm = thmList.get(i);
				//number of words to skip if an n gram has been added.
				int numFutureWordsToSkip = 0;
				//split along e.g. "\\s+|\'|\\(|\\)|\\{|\\}|\\[|\\]|\\.|\\;|\\,|:"
				
				List<String> thmAr = WordForms.splitThmIntoSearchWordsList(thm.toLowerCase());
				
				//words and their frequencies.
				//	Map<String, Integer> wordsFreqMap = new HashMap<String, Integer>();				
				int thmArSz = thmAr.size();
				List<String> curSkipGramList = new ArrayList<String>();
				
				for(int j = 0; j < thmArSz; j++){
					
					String singletonWordAdded = null;
					String twoGramAdded = null;
					String threeGramAdded = null;					
					String word = thmAr.get(j);	
					//only keep words with lengths > 2
					//System.out.println(word);
					int lengthCap = GATHER_SKIP_GRAM_WORDS ? 3 : 3;
					//word length could change, so no assignment to variable.
					if(word.length() < lengthCap){
						continue;
					}
					if(skipGramSkipWordsSet.contains(word)) {
						continue;
					}
					
					//get singular forms if plural, put singular form in map
					//Should be more careful on some words that shouldn't be singular-ized!					
					word = WordForms.getSingularForm(word);	
					
					//also don't skip if word is contained in lexicon										
					//boolean skipWordBasedOnPos = true;
					/*if(GATHER_SKIP_GRAM_WORDS){
						skipWordBasedOnPos = false;
					}else{ 
						if(WordForms.getFluffSet().contains(word)) continue;
						List<String> wordPosList = posMMap.get(word);
						if(!wordPosList.isEmpty()){
							String wordPos = wordPosList.get(0);
							skipWordBasedOnPos = !wordPos.equals("ent") && !wordPos.equals("adj"); 
							
							//wordPos.equals("verb") <--should have custom verb list
							//so don't keep irrelevant verbs such as "are", "take"
						}
					}*/
					/**if(FreqWordsSet.commonEnglishNonMathWordsSet.contains(word) && !nGramFirstWordsSet.contains(word)
							&& skipWordBasedOnPos){ 
						continue;					
					}*/
					//check the following word
					/******don't delete Feb 20 2018 if(j < thmArSz-1){
						String nextWordCombined = word + " " + thmAr.get(j+1);
						nextWordCombined = WordForms.normalizeTwoGram(nextWordCombined);
						Integer twoGramFreq = twoGramsMap.get(nextWordCombined);
						if(twoGramFreq != null){
							int freq = (int)(twoGramFreq*TWO_GRAM_FREQ_REDUCTION_FACTOR);
							twoGramFreq = freq == 0 ? 1 : freq;
							twoGramAdded = addNGramToMaps(nextWordCombined, i, //wordsFreqMap, //thmWordsFreqListBuilder, 
									docWordsFreqPreMap, twoGramFreq);
						}
						//try to see if these three words form a valid 3-gram
						if(j < thmArSz-2){
							String threeWordsCombined = nextWordCombined + " " + thmAr.get(j+2);
							Integer threeGramFreq = threeGramsMap.get(threeWordsCombined);
							if(threeGramFreq != null){
								//reduce frequency so 3-grams weigh more 
								int freq = (int)(threeGramFreq*THREE_GRAM_FREQ_REDUCTION_FACTOR);
								threeGramFreq = freq == 0 ? 1 : freq;
								threeGramAdded = addNGramToMaps(threeWordsCombined, i, //wordsFreqMap, 
										docWordsFreqPreMap, threeGramFreq);
							}
						}
					}		*/			
					
					/*if(!GATHER_SKIP_GRAM_WORDS) {
						singletonWordAdded = addWordToMaps(word, i, //wordsFreqMap, //thmWordsFreqListBuilder, 
								docWordsFreqPreMap);
					}else*/ 
						if(SPECIAL_CHARACTER_PATTERN.matcher(word).find()){
							continue;
						}else {
							singletonWordAdded = word;
						}
					
					//if(GATHER_SKIP_GRAM_WORDS){
						//gather list of relevant words used in this thm
						/**only gather singleton words, to get synonyms for singleton words.
						 * if(numFutureWordsToSkip > 0){
							numFutureWordsToSkip--;
						}else if(null != threeGramAdded){
							skipGramWordList_.add(threeGramAdded);
							numFutureWordsToSkip = 2;
						}else if(null != twoGramAdded){
							skipGramWordList_.add(twoGramAdded);
							numFutureWordsToSkip = 1;
						}else */
						if(null != singletonWordAdded){
							//skipGramWordList_.add(singletonWordAdded);
							curSkipGramList.add(singletonWordAdded);
						}
					//}
				}
				
				//lots of stub sentences, which are not good training data
				if(curSkipGramList.size() > 4) {
					skipGramWordList_.addAll(curSkipGramList);
					//delimit sentence
					skipGramWordList_.add("");
				}				
			}
		}
		
		/**
		 * Auxiliary method for building word frequency maps. Analogous to addWordToMaps(), 
		 * but add 2 and 3 Grams. 
		 * @param word Can be singleton or n-gram.
		 * @param curThmIndex
		 * @param thmWordsMap ThmWordsMap for current thm
		 * @param thmWordsListBuilder
		 * @param docWordsFreqPreMap global document word frequency
		 * @param wordThmsMMapBuilder
		 * @param wordTotalFreq is total frequency of word in corpus. This 
		 * was found when collecting 2 and 3 grams
		 * @return whether the n gram was added.
		 */
		private static String addNGramToMaps(String word, int curThmIndex,// Map<String, Integer> thmWordsMap,
				Map<String, Integer> docWordsFreqPreMap,
				int wordTotalFreq){			
			
			if(SPECIAL_CHARACTER_PATTERN.matcher(word).find()){
				return null;
			}
			/*Integer wordFreq = thmWordsMap.get(word);
			wordFreq = null == wordFreq ? 0 : wordFreq;
			thmWordsMap.put(word, wordFreq + 1);	*/
			//only add word freq to global doc word frequency if not already done so.
			if(!docWordsFreqPreMap.containsKey(word)){
				docWordsFreqPreMap.put(word, wordTotalFreq);
			}
			return word;
		}
		
		/**
		 * Auxiliary method for building word frequency maps. 
		 * @param word Can be singleton or n-gram.
		 * @param curThmIndex
		 * @param thmWordsFreqMap 
		 * @param thmWordsListBuilder
		 * @param docWordsFreqPreMap
		 * @param wordThmsMMapBuilder  Multimap of words and the indices of theorems they occur in.
		 * @return whether word was added
		 */
		private static String addWordToMaps(String word, int curThmIndex, //Map<String, Integer> thmWordsFreqMap,
				Map<String, Integer> docWordsFreqPreMap
				//, ImmutableSetMultimap.Builder<String, Integer> wordThmsMMapBuilder
				){			
			
			//screen the word for special characters, e.g. "/", don't put these words into map.
			if(SPECIAL_CHARACTER_PATTERN.matcher(word).find()){
				return null;
			}
			/*Integer wordFreq = thmWordsFreqMap.get(word);
			wordFreq = null == wordFreq ? 0 : wordFreq;
			thmWordsFreqMap.put(word, wordFreq + 1);*/
			
			Integer docWordFreq = docWordsFreqPreMap.get(word);
			docWordFreq = null == docWordFreq ? 0 : docWordFreq;			
			//increase freq of word by 1
			docWordsFreqPreMap.put(word, docWordFreq + 1);
			
			//wordThmsMMapBuilder.put(word, curThmIndex);
			return word;
		}
		
		/**
		 * Fills up wordsScorePreMap. Minimum score is 3, to be consistent with related words search
		 * algorithm in SearchIntersection.java.
		 * @param wordsScorePreMap empty map to be filled.
		 * @param docWordsFreqPreMap Map of words and their document-wide frequencies.
		 */
		public static void buildScoreMap(Map<String, Integer> wordsScorePreMap,
				Map<String, Integer> docWordsFreqPreMap){		
			
			int avgScore = addWordScoresFromMap(wordsScorePreMap, docWordsFreqPreMap);
			
			//System.out.println("docWordsFreqMapNoAnno "+docWordsFreqMapNoAnno);
			//put 2 grams in, freq map should already contain 2 grams
			//addWordScoresFromMap(wordsScorePreMap, twoGramsMap);
			
			//put 1 for math words that occur more frequently than the cutoff, but should still be counted, like "ring"	
			//right now (June 2017) avg still high, around 18, due to the prevalence of n-grams, which have high scores.
			avgScore = (int)(avgScore * 2./3);
			avgScore = avgScore < WordForms.MIN_WORD_SCORE ? WordForms.MIN_WORD_SCORE : avgScore;
			
			for(String word : SCORE_AVG_MATH_WORDS){ 
				wordsScorePreMap.put(word, avgScore);
			}
			
			for(String word : MIN_SCORE_MATH_WORDS){
				if(wordsScorePreMap.containsKey(word)){
					wordsScorePreMap.put(word, WordForms.MIN_WORD_SCORE);
				}
			}
			
			for(String word : SCORE0MATH_WORDS){ 
				wordsScorePreMap.remove(word);
			}
		}
		
		/**
		 * Auxiliary method for buildScoreMapNoAnno. Adds word scores from the desired map.
		 * @param wordsScorePreMap
		 * @return average score of words
		 */
		private static int addWordScoresFromMap(Map<String, Integer> wordsScorePreMap, Map<String, Integer> mapFrom) {
			int totalScore = 0;
			for(Entry<String, Integer> entry : mapFrom.entrySet()){
				//+1 so not to divide by 0.
				//wordsScoreMapBuilderNoAnno.put(entry.getKey(), (int)Math.round(1/Math.log(entry.getValue()+1)*10) );
				//wordsScoreMapBuilderNoAnno.put(entry.getKey(), (int)Math.round(1/Math.pow(entry.getValue(), 1.25)*200) );
				String word = entry.getKey();
				//if(word.equals("tex")) continue;
				int wordFreq = entry.getValue();
				//*Keep* these comments. Experimenting with scoring parameters.
				//for 1200 thms, CommAlg5 + distributions:
				//int score = wordFreq < 110 ? (int)Math.round(10 - wordFreq/4) : wordFreq < 300 ? 1 : 0;	
				//int score = wordFreq < 180 ? (int)Math.round(15 - wordFreq/4) : wordFreq < 450 ? 1 : 0;
				//until april 1:
				//int score = wordFreq < 40 ? (int)Math.round(10 - wordFreq/3) : (wordFreq < 180 ? (int)Math.round(15 - wordFreq/3) : (wordFreq < 450 ? 3 : 0));	
				//starting April 1:
				int score = wordFreq < 40 ? (int)Math.round(20 - wordFreq/3) : (wordFreq < 180 ? (int)Math.round(35 - wordFreq/4) 
						: (wordFreq < 350 ? 4 : (wordFreq < 450 ? 3 : 3)));	
				//frequently occurring words, should not score too low since they are mostly math words.
				score = score <= 0 ? 3 : score;
				wordsScorePreMap.put(word, score);
				totalScore += score;
				//System.out.print("word: "+word +" score: "+score + " freq "+ wordFreq + "$   ");
			}
			return totalScore/mapFrom.size();
		}
		
		/**
		 * Map of keywords and their scores, the higher freq in doc, the lower 
		 * score, along the lines of  1/(log freq + 1) since log 1 = 0. 
		 * Used for all search algorithms.
		 * @return
		 */
		public static ImmutableMap<String, Integer> get_wordsScoreMap(){
			return wordsScoreMap;
		}
		
		/**
		 * Retrieves map of words with their document-wide frequencies.
		 * @return
		 */
		/*public static ImmutableMap<String, Integer> get_docWordsFreqMap(){
			return docWordsFreqMap;
		}*/
		
		/**
		 * Retrieves map of words with their document-wide frequencies.
		 * @return
		 */
		public static ImmutableMap<String, Integer> get_docWordsFreqMap(){
			return docWordsFreqMap; 
		}
		
		public static ImmutableMultimap<String, IndexPartPair> get_wordThmsMMap(){
			return wordThmsIndexMMap;
		}
	}
	//***********End of prev class
	/**
	 * Static nested classes that accomodates lazy initialization (so to avoid circular 
	 * dependency), but also gives benefit of final (cause singleton), immutable (make it so).
	 */
	public static class NGramsMap{
		//private static final Map<String, Integer> twoGramsMap = ImmutableMap.copyOf(NGramSearch.get2GramsMap());
		//map of two grams and their frequencies.
		private static final Map<String, Integer> twoGramsMap = NGramSearch.get2GramsMap();		
		private static final Map<String, Integer> threeGramsMap = ThreeGramSearch.get3GramsMap();
		
		/**
		 * Map of two grams and their frequencies.
		 * @return
		 */
		public static Map<String, Integer> get_twoGramsMap(){
			return twoGramsMap;
		}
		
		public static Map<String, Integer> get_threeGramsMap(){
			return threeGramsMap;
		}
	}
	
	/**
	 * Static nested classes that accomodates lazy initialization (so to avoid circular 
	 * dependency), but also gives benefit of final (cause singleton), immutable (make it so).
	 * Note: This class is initialized BEFORE the static subclass ThmWordsMaps.
	 */
	public static class ThmList{
		
		//*******private static final ImmutableList<String> allThmsWithHypList;
		//just thm. same order as in allThmsWithHypList.
		/*private static final ImmutableList<String> allThmsNoHypList;
		//just hyp. same order as in allThmsWithHypList.
		private static final ImmutableList<String> allHypList;
		private static final ImmutableList<String> allThmSrcFileList;*/
		///private static final int numThms;
		//****private static final ImmutableList<ThmHypPair> allThmHypPairList;
		
		//private static final ImmutableList<BigInteger> allThmsRelationVecList;
		//private static final ImmutableList<String> allThmsContextVecList;
		
		//private static final ImmutableList<String> thmList;
		//processed with options to replace tex with latex, and expand macros with their definitions
		//list of theorems for web display, without \label{} or \index{} etc
		//private static final ImmutableList<String> webDisplayThmList;	
		/*Commented out June 2017.
		 * private static final ImmutableList<String> processedThmList;		
		//list of bare theorems, without label content 
		private static final ImmutableList<String> bareThmList;	
		//thm list with just macros replaced
		private static final ImmutableList<String> macroReplacedThmList;
		//whether to replace latex symbols with the word "tex"
		private static final boolean REPLACE_TEX = true;
		//whether to extract words from latex symbols, eg oplus->direct sum.
		private static final boolean TEX_TO_WORDS = true;
		//whether to expand macros to their definitions
		private static final boolean REPLACE_MACROS = true;*/
		//Whether in skip gram gathering mode. Used by CollectThm.ThmWordsMaps.
		private static boolean gather_skip_gram_words;
		
		static{	
			//instead of getting thmList from ThmList, need to get it from serialized data.
			//List<ParsedExpression> parsedExpressionsList;
			/* Deserialize objects in parsedExpressionOutputFileStr, so we don't 
			 * need to read and parse through all papers on every server initialization.
			 * Can just read from serialized data. */
			
			//need to modularize to multiple lists stored in cache!!!
			/**parsedExpressionsList = extractParsedExpressionList();
			
			List<String> allThmsWithHypPreList = new ArrayList<String>();
			List<String> allThmsNoHypPreList = new ArrayList<String>();
			List<String> allHypPreList = new ArrayList<String>();
			List<String> allThmSrcFilePreList = new ArrayList<String>();
			fillListsFromParsedExpressions(parsedExpressionsList, allThmsWithHypPreList,
					allThmsNoHypPreList, allHypPreList, allThmSrcFilePreList);			
			
			allThmsWithHypList = ImmutableList.copyOf(allThmsWithHypPreList);
			
			allThmHypPairList = createdThmHypPairListFromLists(allThmsNoHypPreList, allHypPreList, allThmSrcFilePreList);
			numThms = allThmHypPairList.size();*/
			
			/*ImmutableList.Builder<String> thmListBuilder = ImmutableList.builder();
			List<String> extractedThmsList = new ArrayList<String>();
			List<String> processedThmsList = new ArrayList<String>();
			List<String> macroReplacedThmsList = new ArrayList<String>();
			List<String> webDisplayThmsList = new ArrayList<String>();
			List<String> bareThmsList = new ArrayList<String>();*/
			//extractedThms = ThmList.get_thmList();
			/* Commented out June 2017.
			 * try {
				if(null == servletContext){
					//this is the case when resources have not been set by servlet, so not on server.
					for(String fileStr : rawFileStrList){
						//FileReader rawFileReader = new FileReader(rawFileStr);
						FileReader rawFileReader = new FileReader(fileStr);
						BufferedReader rawFileBReader = new BufferedReader(rawFileReader);
						//System.out.println("rawFileReader is null ");
						extractedThmsList.addAll(ThmInput.readThm(rawFileBReader, webDisplayThmsList, bareThmsList));							
						//System.out.print("Should be extracting theorems here: " + extractedThms);
					}
					//the third true means to extract words from latex symbols, eg oplus->direct sum.
					//last boolean is whether to replace macros, 
					FileReader macrosReader = new FileReader(MACROS_SRC);
					BufferedReader macrosBReader = new BufferedReader(macrosReader);
					bareThmsList = ProcessInput.processInput(bareThmsList, false, false, false);
					processedThmsList = ProcessInput.processInput(extractedThmsList, macrosBReader, REPLACE_TEX, TEX_TO_WORDS, REPLACE_MACROS);					
					macroReplacedThmsList = ProcessInput.get_macroReplacedThmList();
					macrosBReader.close();
				}else{
					//System.out.println("read from rawFileReader");
					//System.out.print("ready for processing: " +rawFileReader);
					
					for(String fileStr : rawFileStrList){
						InputStream inputStream = servletContext.getResourceAsStream(fileStr);
						BufferedReader rawFileBReader = new BufferedReader(new InputStreamReader(inputStream));						
						extractedThmsList.addAll(ThmInput.readThm(rawFileBReader, webDisplayThmsList, bareThmsList));							
						inputStream.close();
						rawFileBReader.close();						
					}
					
					//to be used for parsing. Booleans specify options such as whether to
					//convert tex symbols to words, replace macros, etc.
					bareThmsList = ProcessInput.processInput(bareThmsList, true, false, false);
					
					macrosDefReader 
						= new BufferedReader(new InputStreamReader(servletContext.getResourceAsStream("src/thmp/data/texMacros.txt")));	
					processedThmsList = ProcessInput.processInput(extractedThmsList, macrosDefReader, REPLACE_TEX, TEX_TO_WORDS, REPLACE_MACROS);
					//the BufferedStream containing macros is set when rawFileReaderList is set.
					macroReplacedThmsList = ProcessInput.get_macroReplacedThmList();
				}
			} catch (IOException e) {
				e.printStackTrace();
				throw new RuntimeException("Processing input via ProcessInput failed!\n", e);
			}*/
			//thmListBuilder.addAll(extractedThmsList);
			//thmList = thmListBuilder.build();			
			//webDisplayThmList = ImmutableList.copyOf(webDisplayThmsList);
			//webDisplayThmList = allThmsWithHypList;
			/*bareThmList = ImmutableList.copyOf(bareThmsList);
			processedThmList = ImmutableList.copyOf(processedThmsList);
			macroReplacedThmList = ImmutableList.copyOf(macroReplacedThmsList);*/
		}

		public static void set_gather_skip_gram_words_toTrue(){
			gather_skip_gram_words = true;
		}
		
		private static ImmutableList<ThmHypPair> createdThmHypPairListFromLists(List<String> allThmsNoHypList_,
				List<String> allHypList_, List<String> allThmSrcFileList_) {
			
			List<ThmHypPair> thmpHypPairList = new ArrayList<ThmHypPair>();
			int allHypListSz = allHypList_.size();
			assert allThmsNoHypList_.size() == allHypListSz && allHypListSz == allThmSrcFileList_.size();
			
			for(int i = 0; i < allHypListSz; i++){
				ThmHypPair thmHypPair = new ThmHypPair(allThmsNoHypList_.get(i), allHypList_.get(i), allThmSrcFileList_.get(i));
				thmpHypPairList.add(thmHypPair);
			}			
			return ImmutableList.copyOf(thmpHypPairList);
		}

		public static boolean gather_skip_gram_words(){
			return gather_skip_gram_words;
		}
		
		/**
		 * Extracts parsedExressionList from serialized data.
		 * @return
		 */
		@SuppressWarnings("unchecked")
		private static List<ParsedExpression> extractParsedExpressionList() {
			
			//List<ParsedExpression> parsedExpressionsList;
			String parsedExpressionSerialFileStr = "src/thmp/data/parsedExpressionListTemplate.dat";
			
			if(null != servletContext){
				if(!Searcher.SearchMetaData.gatheringDataBool()){
					parsedExpressionSerialFileStr = ThmSearch.getSystemCombinedParsedExpressionListFilePathBase();
				}else{
					parsedExpressionSerialFileStr = "src/thmp/data/parsedExpressionList.dat";
				}
				InputStream parsedExpressionListInputStream = servletContext.getResourceAsStream(parsedExpressionSerialFileStr);
				return (List<ParsedExpression>)thmp.utils.FileUtils
						.deserializeListFromInputStream(parsedExpressionListInputStream);	
			}else{
				//when processing on byblis
				if(!System.getProperty("os.name").equals("Mac OS X")){
					if(!Searcher.SearchMetaData.gatheringDataBool()){
						parsedExpressionSerialFileStr = ThmSearch.getSystemCombinedParsedExpressionListFilePathBase();
					}else{
						parsedExpressionSerialFileStr = "src/thmp/data/parsedExpressionList.dat";
					}
				}				
				return (List<ParsedExpression>)thmp.utils.FileUtils
						.deserializeListFromFile(parsedExpressionSerialFileStr);
			}
		}
		
		/**
		 * Fill up thmList, contextvectors, and relational vectors from parsed expressions list
		 * extracted from serialized data.
		 * This is used to load parsed expressions cache.
		 * @param parsedExpressionsList
		 * @param allThmsWithHypList
		 * @param contextVecList
		 * @param relationVecList
		 */
		private static void fillListsFromParsedExpressions(List<ParsedExpression> parsedExpressionsList, 
				List<String> allThmsWithHypList, //List<String> contextVecList, List<BigInteger> relationVecList,
				List<String> allThmsNoHypPreList, List<String> allHypPreList, List<String> allThmSrcFilePreList
				){
			//System.out.println("Should be list: " + parsedExpressionsList);
			for(ParsedExpression parsedExpr : parsedExpressionsList){
				DefinitionListWithThm defListWithThm = parsedExpr.getDefListWithThm();
				
				/* Build the definition string here, could be earlier in DetectHypothesis.java,
				 * but in future may want to do different things with the list elements, so better to keep list form.*/
				/*StringBuilder defListSB = new StringBuilder(200);
				for(VariableDefinition def : defListWithThm.getDefinitionList()){
					defListSB.append(def.getOriginalDefinitionSentence()).append('\n');
				}*/
				String defStr = defListWithThm.getDefinitionStr();
				//temporarry since defStr hasn't been serialized yet.
				defStr = (defStr == null) ? "" : defStr;
				allHypPreList.add(defStr);
				//get original thm and list of definitions separately, for displaying them separately on the web.
				String thmStr = defListWithThm.getThmStr();
				allThmsNoHypPreList.add(thmStr);
				String thmWithDefStr = defStr + " " + thmStr;
				allThmsWithHypList.add(thmWithDefStr);
				
				String fileName = defListWithThm.getSrcFileName();
				if(null != fileName){
					allThmSrcFilePreList.add(fileName);
				}else{
					allThmSrcFilePreList.add("");
				}				
				//allThmSrcFilePreList.add("");
				//contextVecList.add(parsedExpr.contextVecStr());
				//relationVecList.add(parsedExpr.getRelationVec());
			}
		}		
		
		/**
		 * List of relation vectors for all thms, as extracted from deserialized 
		 * ParsedExpressions.
		 * @deprecated
		 * @return
		 */
		/*public static ImmutableList<BigInteger> allThmsRelationVecList(){
			return allThmsRelationVecList;
		}*/

		/**
		 * List of context vectors for all thms, as extracted from deserialized 
		 * ParsedExpressions.
		 * @deprecated
		 * @return
		 */
		/*public static ImmutableList<String> allThmsContextVecList(){
			return allThmsContextVecList;
		}*/

		/**
		 * Get list of theorems with their hypotheses and assumptions attached,
		 * as collected by DetectHypothesis.java. As extracted from deserialized 
		 * ParsedExpressions.
		 * @return an immutable list
		 */
		/*public static ImmutableList<String> allThmsWithHypList(){
			return allThmsWithHypList;
		}*/
		
		/*public static int numThms(){
			return numThms;
		}*/
		/**
		 * Get list of hypotheses and assumptions,
		 * as collected by DetectHypothesis.java. As extracted from deserialized 
		 * ParsedExpressions.
		 * @return an immutable list
		 */
		/*public static ImmutableList<String> allThmsNoHypList(){
			return allThmsNoHypList;
		}*/
		
		/**
		 * Get list of hypotheses and assumptions,
		 * as collected by DetectHypothesis.java. As extracted from deserialized 
		 * ParsedExpressions.
		 * @return an immutable list
		 */
		/*public static ImmutableList<String> allHypList(){
			return allHypList;
		}*/
		
		/**
		 * Get source file names.
		 * @return an immutable list
		 */
		/*public static ImmutableList<String> allThmSrcFileList(){
			return allThmSrcFileList;
		}*/
		
		/**
		 * Get source file names.
		 * @return an immutable list
		 */
		/*public static ImmutableList<ThmHypPair> allThmHypPairList(){
			return allThmHypPairList;
		}*/
		
		/**
		 * Get thmList. Macros are expanded to their full forms by default.
		 * @deprecated
		 * @return
		 */
		/*public static ImmutableList<String> get_thmList(){
			return thmList;
		}*/
		
		/**
		 * Get thmList. List of theorems for web display, without \label{} or \index{} etc.
		 * @return
		 */
		/*public static ImmutableList<String> get_webDisplayThmList(){
			return webDisplayThmList;
		}*/
		
		/**
		 * List of theorems for web parsing, without \label{} or \index{}, or label content etc.
		 * @return
		 */
		/*public static ImmutableList<String> get_bareThmList(){
			//System.out.println("bare thms " + bareThmList);
			return bareThmList;
		}
		
		public static ImmutableList<String> get_processedThmList(){
			return processedThmList;
		}*/
		
		/**
		 * Get original list expanding macros to their full forms.
		 * @return
		 */
		/*public static ImmutableList<String> get_macroReplacedThmList(){
			return macroReplacedThmList;
		}*/		
	}
	
	/**
	 * Math words that should be included, but have been 
	 * marked as fluff due to their common occurance in English.
	 * Eg "ring".
	 * @return
	 */
	public static String[] scoreAvgMathWords(){
		return SCORE_AVG_MATH_WORDS;
	}
	
	/**
	 * Fluff words that are not included in the downloaded usual
	 * English fluff words list. Eg "tex"
	 * @return
	 */
	public static String[] additionalFluffWords(){
		return ADDITIONAL_FLUFF_WORDS;
	}
	
	public static void main(String[] args){
		//int a = ThmWordsMaps.CONTEXT_VEC_SIZE;
	}
}
