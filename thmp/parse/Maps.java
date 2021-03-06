package thmp.parse;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.ServletContext;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.google.common.collect.SetMultimap;

import food.utils.FoodLexicon;
import food.utils.FoodLexicon.FoodMapNode;
import thmp.search.WordFrequency;
import thmp.utils.FileUtils;
import thmp.utils.WordForms;

import java.util.Map.Entry;

/** contains the dictionaries and hashmaps 
 * used as vocabulary and rules in parsing
 * in ThmP1.java.
 * Part of speech atlas:
 * pre: preposition.
 * prep: prepositional phrase.
 * ent: math entity.
 * adj: adjective.
 * adverb: adverb.
 * verb: verb.
 * vbs: singular verb.
 * verbAlone: sentence could end here, does not need subsequent words to 
 * complete, e.g. converge in "$f_n$ converges".
 */

public class Maps {

	private static final Logger logger = LogManager.getLogger(Maps.class);
	// map for composite adjectives, eg positive semidefinite
	// value is regex string to be matched
	//protected static Map<String, String> adjMap;

	// implmented via ImmutableMultimap. String is trigger word.
	//private static final ImmutableListMultimap<String, FixedPhrase> fixedPhraseMMap;
	private static ImmutableListMultimap<String, FixedPhrase> fixedPhraseMMap;
		
	private static final Pattern LEXICON_LINE_PATTERN = Pattern.compile("\"([^\"]+)\" ([^\\s]+)");
	private static final String POS_TAGGER_PATH_STR;
	
	// replace string with a break, usully a comma
	//protected static List<String> breakList;
	
	// list of parts of speech, ent, verb etc
	protected static List<String> posList;	
	
	static{
		String pathToPosTagger = "src/thmp/data/models/english-bidirectional-distsim.tagger";
		POS_TAGGER_PATH_STR = FileUtils.getPathIfOnServlet(pathToPosTagger);		
	}

	public static String getServerPosTaggerPathStr(){
		return POS_TAGGER_PATH_STR;
	}
	
	/**
	 * Retrieve part of speech map.
	 * @return
	 */
	public static ListMultimap<String, String> posMMap(){
		return BuildMaps.posMMap;
	}
	
	/**
	 * Essential pos map used for searching. This includes the words in the lexicon.
	 * @return
	 */
	public static ListMultimap<String, String> essentialPosMMap(){
		return BuildMaps.essentialPosMMap;
	}
	
	/**
	 * Map of anchoring words, e.g. "of"
	 * @return
	 */
	public static Map<String, String> anchorMap(){
		return BuildMaps.anchorMap;
	}
	
	/**
	 * Map of pairs of word pairs and their probabilities of 
	 * occurence.
	 * @return
	 */
	public static Map<String, Double> probMap(){
		return BuildMaps.probMap;
	}
	
	/**
	 * structMap gives reduction rules for parts of speech. 
	 * @return
	 */
	public static Multimap<String, Rule> structMap(){
		return BuildMaps.structMap;
	}	
	
	/**
	 * Check to see if food token. If food token, returns int > 0. Else not 
	 * a food token.
	 * @param inputAr
	 * @param curIndex
	 * @param inputArLen
	 * @return
	 */
	public static int checkFoodToken(String[] inputAr, int curIndex, int inputArLen){
		/*if("eggs".equals(inputAr[curIndex])){
			System.out.println("Maps -egg");
		}*/
		return BuildMaps.FOOD_TRIE.getTokenCount(inputAr, curIndex, inputArLen);
	}
	
	/**
	 * @param inputAr
	 * @param curIndex
	 * @param inputArLen
	 * @return
	 */
	public static int checkCookingActionToken(String[] inputAr, int curIndex, int inputArLen){
		/*if("combine".equals(inputAr[curIndex])){
			System.out.println("Maps - combine");
			//throw new IllegalStateException("combine");
		}*/
		return BuildMaps.COOKING_ACTION_TRIE.getTokenCount(inputAr, curIndex, inputArLen);		
	}		
	
	//subclass, to allow for setting resource in parent class.
	public static class BuildMaps{
		// parts of speech map, e.g. "open", "adj"
		//specify it's a ListMultimap, to make known that insertion order is preserved
		protected static final ListMultimap<String, String> posMMap;
		//essential pos map used for searching. This includes the words in the lexicon.
		protected static final ListMultimap<String, String> essentialPosMMap;
		// map of structures, for all, disj, etc
		// protected static HashMap<String, Rule> structMap;
		protected static final Multimap<String, Rule> structMap;
		
		protected static final Map<String, String> mathObjMap;
		// fluff words, e.g. "the", "a"
		protected static final Map<String, String> fluffMap;

		// probability hashmap for pairs of phrase constructs
		// vs noun_verb high prob, verb_verb low prob
		protected static final Map<String, Double> probMap;

		protected static final Map<String, String> anchorMap;
		private static final boolean FOOD = FileUtils.isFoodParse();
		//deliberately not final
		private static FoodMapNode FOOD_TRIE ;
		private static FoodMapNode COOKING_ACTION_TRIE;
		private static Multimap<String, String> extraFoodLexiconMMap;

		static{
			
			String fixedPhraseFileStr = "src/thmp/data/fixedPhrases.txt";
			String lexiconFileStr = "src/thmp/data/lexicon.txt";
			//create temporary hashMultimap to avoid duplicates, 
			//temporary since ListMultimap is a better structure for this.
			SetMultimap<String, String> posPreMMap = LinkedHashMultimap.create();
			//pos to remove from posPreMMap
			ListMultimap<String, String> negativePosMMap = ArrayListMultimap.create();
			fillInNegativePosMMap(negativePosMMap);
			//ideally make this immutable
			structMap = ArrayListMultimap.create();
			mathObjMap = new HashMap<String, String>();
			fluffMap = new HashMap<String, String>();
			// anchors, contains of, with
			anchorMap = new HashMap<String, String>();
			// probabilities for pair constructs.
			probMap = new HashMap<String, Double>();
			
			BufferedReader fixedPhraseBuffer = null;
			BufferedReader lexiconBuffer = null;
			
			ServletContext servletContext = FileUtils.getServletContext();
			if(null != servletContext){				
				InputStream fixedPhraseStream = servletContext.getResourceAsStream(fixedPhraseFileStr);
				InputStream lexiconFileStrStream = servletContext.getResourceAsStream(lexiconFileStr);
				
				fixedPhraseBuffer = new BufferedReader(new InputStreamReader(fixedPhraseStream));
				lexiconBuffer = new BufferedReader(new InputStreamReader(lexiconFileStrStream));
				
				FileUtils.silentClose(fixedPhraseStream);
				FileUtils.silentClose(lexiconFileStrStream);
			}else{
				try{
					fixedPhraseBuffer = new BufferedReader(new FileReader(fixedPhraseFileStr));
					lexiconBuffer = new BufferedReader(new FileReader(lexiconFileStr));
				}catch(FileNotFoundException e){
					String msg = "FileNotFoundException when opening buffered readers: " + e.getStackTrace();
					logger.error(msg);
					FileUtils.silentClose(fixedPhraseBuffer);
					FileUtils.silentClose(lexiconBuffer);
					throw new IllegalStateException(e);
				}
			}
			readFixedPhrases(fixedPhraseBuffer);
	    	readLexicon(lexiconBuffer, posPreMMap);
	    	
	    	try{
	    		fixedPhraseBuffer.close();
	    		lexiconBuffer.close();
	    	}catch(IOException e){
	    		String msg = "FileNotFoundException when opening buffered readers: " + e.getStackTrace();
				logger.error(msg);
				FileUtils.silentClose(fixedPhraseBuffer);
				FileUtils.silentClose(lexiconBuffer);
	    	}
	    	
	    	SetMultimap<String, String> essentialPosPreMMap = LinkedHashMultimap.create();
			posPreMMap = buildMap(posPreMMap, essentialPosPreMMap);
			essentialPosMMap = ArrayListMultimap.create(essentialPosPreMMap);
			
			for(Map.Entry<String, String> entry : negativePosMMap.entries()){
				posPreMMap.remove(entry.getKey(), entry.getValue());	
			}
			
			if(FOOD){
				FOOD_TRIE = FoodLexicon.foodTrie();
				COOKING_ACTION_TRIE = FoodLexicon.cookingActionTrie();
				//String[][] extraPosAr = new String[][]{{"stir","verb_COMP"}, {"stir in","verb"},
					//{"combine","verb_COMP"}, {"combine with","verb"}};
				String[][] extraFoodActionPosAr = new String[][]{{"stir","in"}, {"combine", "with"}};	
				for(String[] extraPos : extraFoodActionPosAr){
					int extraPosLen = extraPos.length;
					FoodMapNode curFoodMapNode = COOKING_ACTION_TRIE.addIfAbsent(extraPos[0], false);
					for(int i = 1; i < extraPosLen; i++){
						curFoodMapNode = curFoodMapNode.addIfAbsent(extraPos[1], false);
					}
					curFoodMapNode.setValidTokenEnd();
					//FoodMapNode firstFoodMapNode = COOKING_ACTION_TRIE.addIfAbsent(extraPos[0]);					
				}
				extraFoodLexiconMMap = FoodLexicon.additionalFoodLexiconMMap();				
			}
			posMMap = ArrayListMultimap.create(posPreMMap);
		}
		
		//used to initialize BuildMaps class
		public static void initialize(){			
		}
		
		/**
		 * Update posList with additional food pos.
		 * @param posList
		 */
		public static Collection<String> updateWithAdditionalFoodPos(String word){
			Collection<String> col = extraFoodLexiconMMap.get(word);
			//col.addAll(posMMap.get(word)); //add later
			if(col.isEmpty()){
				col = extraFoodLexiconMMap.get(WordForms.getSingularForm(word));
			}
			return col;		
		}
		
		/**
		 * Can be used to fend off some parse combinatorial explosion.
		 * @param negativePosMMap
		 */
		private static void fillInNegativePosMMap(ListMultimap<String, String> negativePosMMap) {
			negativePosMMap.put("there", "adverb");
			negativePosMMap.put("which", "det");
			negativePosMMap.put("run", "ent");
			negativePosMMap.put("the", "adj");
			negativePosMMap.put("under", "adverb");
			negativePosMMap.put("let", "hyp");
			negativePosMMap.put("be", "verb");
			if(!FOOD){
				negativePosMMap.put("let", "verb");				
			}
		}

		/**
		 * Reads from BufferedReader into temporary map. 
		 * @param lexiconReader BufferedReader for lexicon
		 * @param posPreMMap Premap used to build the lexicon
		 * @throws FileNotFoundException
		 */
		private static void readLexicon(BufferedReader lexiconReader, SetMultimap<String, String> posPreMMap) {
			String[] lineAr;
			String pos = "", word = "", replacement = "";
			String nextLine;
			Pattern whiteSpacePattern = WordForms.getWhiteNonEmptySpaceNotAllPattern();
			try{
				while ((nextLine = lexiconReader.readLine()) != null) {					
					lineAr = whiteSpacePattern.split(nextLine);
					Matcher matcher;
					if (lineAr.length < 2){
						//System.out.println("nextline: " + nextLine);
						continue;
					}else if (lineAr.length == 2){
						word = lineAr[0];
						pos = lineAr[1];
					}
					// compound first word, e.g "comm alg"
					else if((matcher=LEXICON_LINE_PATTERN.matcher(nextLine)).matches()){
						//if(true) throw new RuntimeException();						
						word = matcher.group(1);
						pos = matcher.group(2);						
					}		
					// format: "new_word pos"
					// if fluff word: "new_word fluff replacement", 
					//eg "to be" fluff, goes to "as"
					switch (pos) {
					case "ent":
						//mathObjMap.put(word, "mathObj");
						posPreMMap.put(word, "ent");
						break;
					case "ent_comp":
						mathObjMap.put(word, "mathObj_COMP");
						break;
					case "fluff":
						replacement = replacement.equals("") ? word : replacement;
						fluffMap.put(word, replacement);
						break;
					default:
						posPreMMap.put(word, pos);
					}
				}
			}catch(IOException e){
				e.printStackTrace();
				throw new RuntimeException(e);
			}
		}
		
		/**
		 * Build the various maps, e.g. parts of speech map
		 * @param posPreMMap
		 * @param essentialPosPreMMap The essential math words that were included by hand.
		 * @return
		 */
		private static SetMultimap<String, String> buildMap(SetMultimap<String, String> posPreMMap, 
				SetMultimap<String, String> essentialPosPreMMap) {

			fluffMap.put("moreover", "");

			// list of parts of speech
			posList = new ArrayList<String>();
			posList.add("mathObj");
			posList.add("verb");
			posList.add("adj");
			posList.add("adverb");
			posList.add("pre");
			posList.add("vbs");
			// posList.add("noun");

			String[] ents = { "quotient", "ideal", "filtration", "combination", "surjection", "presentation", "tex-module",
					"polynomial", "isomorphism", "composition", "kernel", "system", "colimit", "tex-algebra", "projection",
					"subset" };

			// most should already be filtered in entity/property
			// pos should preserve connective words, or stays or
			String[] verbs = { "present", "mean", "say", "order", "direct", "index" };

			String[] adjs = { "first", "successive", "some", "transitive", "reflexive", "together_COMP", "empty", "short",
					"natural", "partial", "multiplicative" };

			String[] adverbs = {};

			String[] pres = { "together with" };

			String[] nouns = { "notion", "permanence", "inclusion", "relation", "notion",
					"inclusion", "case" };

			// list of all the String arrays above
			// ArrayList<String[]> posArraysList = new ArrayList<String[]>();
			// partsOfSpeech must be ordered the same way as posArraysList

			Map<String, String[]> posArraysMap = new HashMap<String, String[]>();
			posArraysMap.put("ent", ents);
			posArraysMap.put("adverb", adverbs);
			posArraysMap.put("verb", verbs);
			posArraysMap.put("noun", nouns);
			posArraysMap.put("adj", adjs);
			posArraysMap.put("pre", pres);

			List<String[]> pList = new ArrayList<String[]>(); // pos List

			Iterator<Entry<String, String[]>> posArraysMapIter = posArraysMap.entrySet().iterator();

			while (posArraysMapIter.hasNext()) {
				Entry<String, String[]> curEntry = posArraysMapIter.next();
				String curPos = curEntry.getKey();
				String[] curArray = curEntry.getValue();
				String tempPos = curPos;
				String tempWord;
				String[] tempArray;

				for (int j = 0; j < curArray.length; j++) {
					// if _COMP
					tempWord = curArray[j];
					tempArray = curArray[j].split("_");
					if (tempArray.length > 1 && tempArray[tempArray.length - 1].equals("COMP")) {
						tempPos = curPos + "_" + "COMP";
						tempWord = tempArray[0];
					}
					pList.add(new String[] { tempWord, tempPos });

				}
			}

			/*
			 * for(int i = 0; i < verbs.length; i++){ pList.add(new
			 * String[]{verbs[i], "verb"}); }
			 * 
			 * for(int i = 0; i < adjs.length; i++){ pList.add(new String[]{adjs[i],
			 * "adj"}); }
			 * 
			 * for(int i = 0; i < adverbs.length; i++){ pList.add(new
			 * String[]{adverbs[i], "adverb"}); } for(int i = 0; i < nouns.length;
			 * i++){ pList.add(new String[]{nouns[i], "noun"}); }
			 */

			//pre map used to build posMMap.
			//Multimap<String, String> posPreMMap = LinkedHashMultimap.create();
			
			for (int i = 0; i < pList.size(); i++) {
				posPreMMap.put(pList.get(i)[0], pList.get(i)[1]);
			}		
			
			if(FOOD){
				posPreMMap.put("warm", "adj");
				/*posPreMMap.put("oven", "ent");
				posPreMMap.put("flour", "ent");
				posPreMMap.put("salt", "ent");*/
				posPreMMap.put("baking", "ent_COMP");
				posPreMMap.put("baking soda", "ent");
				posPreMMap.put("soda", "ent");
				posPreMMap.put("oil", "ent");
				posPreMMap.put("skillet", "ent");
				posPreMMap.put("medium", "adj");
				//posPreMMap.put("heat", "ent");
				//posPreMMap.put("verb", "ent");
				posPreMMap.put("transparent", "adj");
				posPreMMap.put("speed", "ent");
				posPreMMap.put("high", "adj");
				posPreMMap.put("heat", "ent");
				posPreMMap.put("once", "hyp");
				posPreMMap.put("once", "adj");
				
				posPreMMap.put("let", "verb");
				/*posPreMMap.put("stir", "verb_COMP");
				posPreMMap.put("stir in", "verb");*/
				
				//e.g. "bring water to boil"
				structMap.put("pre_verbAlone", new Rule("prep", 0.85));//FOOD
				/////just verb, can't trigger structMap.put("verb_prep", new Rule("newchild", "verb", .8));
			}
			
			posPreMMap.put("disjoint", "adj");
			posPreMMap.put("perfect", "adj");
			//deliberate spelling for C^* algebra. No dash
			posPreMMap.put("c algebra", "ent");
			posPreMMap.put("equivalent", "adj");
			posPreMMap.put("finite", "adj_comp");
			posPreMMap.put("linear", "adj");
			posPreMMap.put("invertible", "adj");
			posPreMMap.put("independent", "adj");
			posPreMMap.put("many", "adj");
			posPreMMap.put("every", "adj");
			posPreMMap.put("same", "adj");
			posPreMMap.put("conjugate", "adj");
			posPreMMap.put("symmetric", "adj");
			posPreMMap.put("equal", "adj");
			posPreMMap.put("all", "adj"); //predet
			posPreMMap.put("isomorphic", "adj");
			posPreMMap.put("for", "pre_COMP"); // can be composite word, use first pos
											// if composite not in posmap
			posPreMMap.put("for", "hyp");
			posPreMMap.put("measurable", "adj");
			posPreMMap.put("nondecreasing", "adj");
			posPreMMap.put("positive", "adj");
			posPreMMap.put("negative", "adj");
			posPreMMap.put("abelian", "adj");
			posPreMMap.put("normal", "adj");
			posPreMMap.put("cyclic", "adj");
			posPreMMap.put("dimensional", "adj");
			posPreMMap.put("odd", "adj");
			posPreMMap.put("even", "adj");
			posPreMMap.put("any", "adj");
			posPreMMap.put("simple", "adj");
			posPreMMap.put("unique", "adj");
			posPreMMap.put("more", "adj_COMP");
			posPreMMap.put("more than", "pre");
			posPreMMap.put("nilpotent", "adj");
			posPreMMap.put("most", "adj");
			posPreMMap.put("nontrivial", "adj");
			posPreMMap.put("only", "adj");
			posPreMMap.put("commutative", "adj");
			posPreMMap.put("canonical", "adj");
			posPreMMap.put("exact", "adj");
			posPreMMap.put("injective", "adj");
			posPreMMap.put("surjective", "adj");
			posPreMMap.put("last", "adj");

			// adverbs. Adverbs of the form "adj-ly" are detected by code
			posPreMMap.put("there", "det");

			// adverbs qualify verbs, adj, noun phrases, determiners, clauses etc
			posPreMMap.put("does", "verb_COMP");
			posPreMMap.put("do", "verb_COMP");
			posPreMMap.put("does not", "not"); //verb?? <-"not" for now
			posPreMMap.put("do not", "not");
			posPreMMap.put("not", "not");
			
			// nouns that are not mathObj, only put not-so-relevant terms here
			posPreMMap.put("form", "noun_COMP");

			// determiners qualify nouns or noun phrases
			posPreMMap.put("each", "adj");
			posPreMMap.put("this", "det");
			posPreMMap.put("both", "det");
			posPreMMap.put("no", "det"); //<--better than "det"??

			//articles
			posPreMMap.put("a", "art");
			posPreMMap.put("an", "art");
			posPreMMap.put("the", "art");
			
			// parts of speech
			posPreMMap.put("for every", "hyp");
			posPreMMap.put("suppose", "hyp_COMP");
			posPreMMap.put("suppose that", "hyp");
			posPreMMap.put("assume", "hyp");
			posPreMMap.put("assuming", "hyp");
			posPreMMap.put("consider", "hyp");
			posPreMMap.put("for all", "hyp");
			posPreMMap.put("for each", "hyp");
			posPreMMap.put("for any", "hyp");
			
			// prepositions
			posPreMMap.put("or", "or");
			posPreMMap.put("and", "and");
			posPreMMap.put("at most", "adj");
			posPreMMap.put("is", "vbs_comp");
			posPreMMap.put("at", "pre_COMP");
			posPreMMap.put("when", "if");
			posPreMMap.put("whenever", "if");
			posPreMMap.put("if", "if_COMP");
			posPreMMap.put("if and", "if_COMP");
			posPreMMap.put("if and only", "if_COMP");
			posPreMMap.put("if and only if", "iff");
			posPreMMap.put("then", "then");
			posPreMMap.put("therefore", "then");
			posPreMMap.put("between", "pre");
			// between... -> between, and...->and, between_and->between_and

			posPreMMap.put("in", "pre_comp");			
			posPreMMap.put("from", "pre");
			posPreMMap.put("to", "pre_comp");
			posPreMMap.put("to be", "be");
			
			posPreMMap.put("equal", "verb_COMP");
			posPreMMap.put("equal to", "pre");
			posPreMMap.put("on", "pre");
			posPreMMap.put("let", "let");
			//posPreMMap.put("let", "hyp"); //don't add this, parse explosion
			posPreMMap.put("be", "be");
			posPreMMap.put("of", "pre"); // of is primarily used as anchor
			posPreMMap.put("over", "pre");
			posPreMMap.put("with", "pre");
			posPreMMap.put("without", "pre");
			posPreMMap.put("by", "pre");
			posPreMMap.put("via", "pre");
			posPreMMap.put("as", "pre_COMP");
			posPreMMap.put("as well", "adj");
			posPreMMap.put("such", "pre_COMP");
			posPreMMap.put("such that", "cond");
			//posPreMMap.put("except", "cond");
			posPreMMap.put("except", "hyp");
			posPreMMap.put("where", "hyp");
			posPreMMap.put("which is", "hyp");
			posPreMMap.put("which are", "hyp");
			posPreMMap.put("which", "hyp_COMP");
			posPreMMap.put("that is", "hyp");
			posPreMMap.put("that are", "hyp");
			posPreMMap.put("fix", "hyp");
			posPreMMap.put("take", "hyp");
			
			// pronouns
			posPreMMap.put("it", "pro");
			posPreMMap.put("we", "pro");
			posPreMMap.put("they", "pro");
			posPreMMap.put("their", "poss");
			posPreMMap.put("its", "poss");
			// relative pronouns
			posPreMMap.put("whose", "rpro");
			posPreMMap.put("which", "rpro_COMP");
			posPreMMap.put("that", "rpro_COMP");
			posPreMMap.put("that", "hyp");
			posPreMMap.put("whom", "rpro");

			// verbs, verbs map does not support -ing form, ie divide->dividing
			// 3rd person singular form that ends in "es" are checked with
			// the "es" stripped
			posPreMMap.put("belong", "verb");
			posPreMMap.put("divide", "verb");
			posPreMMap.put("extend", "verb");
			posPreMMap.put("exist", "verb");
			posPreMMap.put("consist", "verb");
			posPreMMap.put("call", "verb");
			posPreMMap.put("contain", "verb");
			posPreMMap.put("are", "verb"); //////////// ***
			posPreMMap.put("have", "verb");
			posPreMMap.put("obtain", "verb");
			posPreMMap.put("generate", "verb");
			posPreMMap.put("replace", "verb");
			posPreMMap.put("act", "verb");
			posPreMMap.put("follow", "verb");
			posPreMMap.put("denote", "verb_COMP");
			posPreMMap.put("define", "verb");
			posPreMMap.put("has", "vbs");

			// special participles			
			posPreMMap.put("given", "parti_COMP");		
			posPreMMap.put("been", "parti");
			posPreMMap.put("written", "parti");
			posPreMMap.put("given by", "partiby");
			posPreMMap.put("given", "hyp");
			
			// Heads for starting definitions.
			//"denote by...", "call...", 
			posPreMMap.put("denote by", "def");
			posPreMMap.put("call", "def");
			//posPreMMap.put("define", "def");
			
			// build in quantifiers into structures, forall (indicated
			// by for all, has)
			// entityMap.put("for all", null); //change null, use regex
			// entityMap.put("there exists", null);

			// map for math objects
			
			mathObjMap.put("characteristic", "mathObj");
			mathObjMap.put("set", "mathObj");
			mathObjMap.put("Fp", "mathObj");
			mathObjMap.put("transformation", "mathObj");
			mathObjMap.put("ring", "mathObj");
			mathObjMap.put("matrix", "mathObj");
			mathObjMap.put("function", "mathObj");
			mathObjMap.put("bilinear form", "mathObj");
			mathObjMap.put("basis", "mathObj");
			mathObjMap.put("sum", "COMP");
			mathObjMap.put("direct sum", "mathObj");
			mathObjMap.put("number", "mathObj");
			mathObjMap.put("partition", "mathObj");
			// composite math objects
			mathObjMap.put("field", "COMP");
			mathObjMap.put("space", "COMP");
			mathObjMap.put("vector space", "mathObj");
			mathObjMap.put("finite field", "mathObj");
			mathObjMap.put("vector field", "mathObj");
			mathObjMap.put("vector", "mathObj");
			mathObjMap.put("angle", "mathObj");
			mathObjMap.put("zero", "mathObj");
			mathObjMap.put("extension", "COMP"); /////////// ****
			mathObjMap.put("field extension", "mathObj");
			mathObjMap.put("element", "noun");
			mathObjMap.put("group", "COMP");
			mathObjMap.put("symmetric group", "mathObj");
			mathObjMap.put("p group", "mathObj");
			mathObjMap.put("p subgroup", "mathObj");
			mathObjMap.put("subgroup", "COMP");
			mathObjMap.put("automorphism group", "mathObj");
			mathObjMap.put("abelian group", "mathObj");
			mathObjMap.put("type", "COMP");
			mathObjMap.put("cycle type", "mathObj");
			mathObjMap.put("cycle", "mathObj");
			mathObjMap.put("decomposition", "COMP");
			mathObjMap.put("entry", "mathObj");
			mathObjMap.put("cycle decomposition", "mathObj");
			mathObjMap.put("measure", "mathObj");
			mathObjMap.put("sequence", "mathObj");
			mathObjMap.put("integer", "mathObj");
			mathObjMap.put("class", "COMP");
			mathObjMap.put("conjugacy class", "mathObj");
			mathObjMap.put("subgroup", "mathObj");
			mathObjMap.put("automorphism", "mathObj");
			mathObjMap.put("order", "mathObj");
			mathObjMap.put("conjugation", "mathObj");
			mathObjMap.put("prime", "mathObj");
			mathObjMap.put("power", "mathObj");
			mathObjMap.put("nilpotence", "mathObj");
			//mathObjMap.put("map", "mathObj");
			mathObjMap.put("diagram", "mathObj_COMP");
			mathObjMap.put("commutative diagram", "mathObj");
			mathObjMap.put("module", "mathObj");
			
			mathObjMap.put("tex", "mathObj"); // TEMPORARY
			
			// put in template matching, prepositions, of, by, with

			////anchorMap.put("of", "of");
			// anchorMap.put("that is", "that is"); //careful with spaces
			// local "or", ie or of the same types
			// anchorMap.put("or", "or");

			// struct map, statement/global structures
			// can be written as,
			// structMap = new HashMap<String, String>();
			// structMap = new HashMap<String, Rule>();
			
			structMap.put("if_hypo", new Rule("If", 1));
			structMap.put("iff_hypo", new Rule("Iff", 1));
			structMap.put("then_assert", new Rule("Then", 1));
			structMap.put("then_texAssert", new Rule("Then", 1));

			// expression, e.g. a map from A to B
			// structMap.put("ent", new Rule("expr", 1));
			//structMap.put("ifstate_thenstate", new Rule("ifthen", 1));
			structMap.put("If_Then", new Rule("IfThen", 1));
			structMap.put("Iff_Then", new Rule("IffThen", 1));
			structMap.put("If_assert", new Rule("IfThen", 1));
			structMap.put("has_ent", new Rule("hasent", 1));
			structMap.put("or_ent", new Rule("orent", 1));
			structMap.put("ent_orent", new Rule("or", 1));
			structMap.put("or_symb", new Rule("orsymb", 1));
			structMap.put("symb_orsymb", new Rule("or", 1));
			structMap.put("or_assert", new Rule("orass", 1));
			structMap.put("or_texAssert", new Rule("orass", 1));
			structMap.put("ent_orass", new Rule("or", 1));
			structMap.put("or_is", new Rule("assert", 1));
			structMap.put("assert_orass", new Rule("or", 1));
			structMap.put("ent_ppt", new Rule("newchild", 1));
			//e.g. "integer linear combination", in particular ones involving conj/disj
			structMap.put("ent_ent", new Rule("fuse", .7));
			//"genus one"
			structMap.put("ent_quant", new Rule("absorb2", .7));
			// e.g. between A and B.
			// structMap.put("pre_conj", new Rule("prep", 1));
			// structMap.put("pre_disj", new Rule("prep", 1));

			// preposition_entity, eg "over field", "on ...". Create new child in
			// this case
			// latter ent child of first ent
			// structMap.put("pre_ent", new Rule("preent", 1));
			// prep stands for "pre phrase"
			structMap.put("pre_ent", new Rule("prep", 1));
			structMap.put("pre_texAssert", new Rule("prep", 1));
			structMap.put("pre_adj", new Rule("prep", 0.8));
			//structMap.put("tobe_ent", new Rule("Tobe", 0.85));
			//"$F$ is said to be a field."
			//structMap.put("verbphrase_Tobe", new Rule("verbphrase", .85));
			
			structMap.put("ent_prep", new Rule("newchild", 1));
			structMap.put("ent_adj", new Rule("newchild", .9));
			structMap.put("pre_symb", new Rule("prep", 1));
			structMap.put("parti_prep", new Rule("phrase", 1));
			structMap.put("pre_phrase", new Rule("prep", 1));
			structMap.put("pre_np", new Rule("prep", 1));
			structMap.put("pre_expr", new Rule("prep", 1));
			structMap.put("pre_pobj", new Rule("prep", 1));
			//"combine flour and water to form dough"
			structMap.put("pre_verbphrase", new Rule("prep", 0.9));//FOOD
			structMap.put("csubj_prep", new Rule("csubj", 1));
			structMap.put("noun_prep", new Rule("np", 1));
			structMap.put("noun_verbphrase", new Rule("assert", 1));
			structMap.put("pre_noun", new Rule("prep", 1));
			structMap.put("gerund_verbphrase", new Rule("assert", 1));
			structMap.put("parti_assert", new Rule("hypo", .8));
			structMap.put("parti_texAssert", new Rule("hypo", .8));
			// participle: called, need to take care of "said" etc
			structMap.put("parti_ent", new Rule("partient", 1));
			structMap.put("ent_partient", new Rule("newchild", 1));
			//"field which is perfect."
			structMap.put("ent_hypo", new Rule("newchild", .85));
			
			// phrases: been there, x in X,
			structMap.put("parti_adj", new Rule("phrase", 1));
			structMap.put("symb_prep", new Rule("phrase", 1));

			//////////
			structMap.put("symb_adj", new Rule("phrase", 0.7));

			//structMap.put("ent_pre", new Rule("exp1", .3));
			//structMap.put("exp1_phrase", new Rule("exp2", .1));

			// symb_adj
			// noun_symb should be combined in code

			//structMap.put("pre_noun", new Rule("ppt", 1)); // nounphrase
			structMap.put("adj_symb", new Rule("phrase", 1));			
			structMap.put("adj_noun", new Rule("noun", 1));
			//"independent of $n$". New pos should not be just "adj".
			structMap.put("adj_prep", new Rule("newchild", "qualifier", .97));
			structMap.put("adverb_prep", new Rule("newchild", "qualifier", .85));
			structMap.put("gerund_noun", new Rule("gerundp", 1)); // gerundphrase
			structMap.put("ent_gerundp", new Rule("newchild", 1));
			
			// involving nums
			structMap.put("pre_num", new Rule("prep", 1));

			ArrayList<String[]> entList = new ArrayList<String[]>();

			for (int i = 0; i < ents.length; i++) {
				String newEnt = ents[i];
				if (newEnt.split("_").length > 1) {
					// composite
					entList.add(new String[] { newEnt.split("_")[0], "mathObj_COMP" });
				} else {
					entList.add(new String[] { newEnt, "mathObj" });
				}
			}

			for (int i = 0; i < entList.size(); i++) {
				mathObjMap.put(entList.get(i)[0], entList.get(i)[1]);
			}

			// structMap.put("from ", "wildcard"); structMap.put("to", "wildcard");
			// structMap.put("fromto", "newchild");
			//////////// combine preposition with whatever comes
			// verb_ent, not including past tense verbs, only present tense
			structMap.put("verb_ent", new Rule("verbphrase", 0.989)); //0.989 for e.g. "fix a prime"
			structMap.put("verb_csubj", new Rule("verbphrase", 1));
			//structMap.put("be_ent", new Rule("verbphrase", .7));
			structMap.put("verb_adj", new Rule("verbphrase", 1));
			//e.g. "$s_n$ converges independently of $n$", "...implies that..."
			structMap.put("verb_qualifier", new Rule("newchild", "verbAlone", 0.8));
			structMap.put("verb_pro", new Rule("verbphrase", 1));
			structMap.put("verb_symb", new Rule("verbphrase", 1));
			structMap.put("verb_np", new Rule("verbphrase", 1));
			structMap.put("verb_num", new Rule("verbphrase", 1));
			structMap.put("verb_np", new Rule("verbphrase", 1));
			structMap.put("verb_pre", new Rule("verbphrase", .6));
			structMap.put("verb_phrase", new Rule("verbphrase", 0.8));
			structMap.put("verb_partient", new Rule("verbphrase", 1));
			structMap.put("verb_noun", new Rule("verbphrase", 1));
			structMap.put("verbAlone_qualifier", new Rule("newchild", "verbAlone", 0.85));
			
			structMap.put("det_verbphrase", new Rule("assert", 1));
			//remove, because verbphrase should be able to finish a sentence
			//structMap.put("verb_parti", new Rule("verbphrase", 1));
			//e.g. auxpass: "is connected"
			structMap.put("auxpass_pobj", new Rule("verbphrase", 1)); // passive
			structMap.put("auxpass_ent", new Rule("verbphrase", 1));
			structMap.put("auxpass_symb", new Rule("verbphrase", 0.7));
			structMap.put("auxpass_adj", new Rule("verbphrase", 1));	// auxiliary
			structMap.put("auxpass_prep", new Rule("verbphrase", 1));
			
			//TEMP RULE
			//structMap.put("assert_csubj", new Rule("", 1));
			
			structMap.put("vbs_ent", new Rule("verbphrase", 1));
			structMap.put("vbs_adj", new Rule("verbphrase", .9));
			structMap.put("vbs_pro", new Rule("verbphrase", 1));
			structMap.put("vbs_symb", new Rule("verbphrase", 1));
			structMap.put("vbs_np", new Rule("verbphrase", 1));
			structMap.put("vbs_prep", new Rule("verbphrase", 1));
			structMap.put("vbs_qualifier", new Rule("newchild", "verbAlone", 1)); //<-trigger
			//structMap.put("vbs_qualifier", new Rule("newchild", "verbphrase", 1));
			
			structMap.put("vbs_num", new Rule("verbphrase", 1));
			//structMap.put("vbs_pre", new Rule("verbphrase", .7));
			structMap.put("vbs_phrase", new Rule("verbphrase", 1));
			structMap.put("vbs_partient", new Rule("verbphrase", 1));
			structMap.put("vbs_noun", new Rule("verbphrase", 1));
			//remove, because verbphrase should be able to finish a sentence
			//structMap.put("vbs_parti", new Rule("verbphrase", 1));

			structMap.put("symb_verbphrase", new Rule("assert", 1));
			structMap.put("ent_verbphrase", new Rule("assert", 1));
			structMap.put("ent_verbAlone", new Rule("assert", 1));			
			structMap.put("symb_verbAlone", new Rule("assert", 1));
			structMap.put("pro_verbphrase", new Rule("assert", 1));
			structMap.put("pro_verbAlone", new Rule("assert", 1));
			//"A is p, so is B"
			structMap.put("so_verbphrase", new Rule("So", 1));
			structMap.put("pro_csubj", new Rule("pobj", 1)); // could be iobj ->
																// indirect obj
			structMap.put("poss_csubj", new Rule("pobj", .95));
			structMap.put("poss_ent", new Rule("absorb1", .95));
			structMap.put("predet_csubj", new Rule("csubj", 1));
			structMap.put("np_verbphrase", new Rule("assert", 1));
			structMap.put("symb_auxpass", new Rule("assert", .8)); //"$X$ is connected."
			structMap.put("np_auxpass", new Rule("assert", .8));
			structMap.put("ent_auxpass", new Rule("assert", .8));
			structMap.put("pro_auxpass", new Rule("assert", .8));

			structMap.put("hypo_assert", new Rule("assert", 1));
			structMap.put("hypo_texAssert", new Rule("assert", 1));
			structMap.put("verbphrase_prep", new Rule("verbphrase", .6));
			structMap.put("vbs_partiby", new Rule("verb", 1));
			structMap.put("partiby_ent", new Rule("phrase", 1));
			structMap.put("partiby_noun", new Rule("phrase", 1));
			structMap.put("partiby_symb", new Rule("phrase", 1));
			structMap.put("partiby_expr", new Rule("phrase", 1));
			structMap.put("be_parti", new Rule("verb", 1));
			structMap.put("be_partiby", new Rule("verb", 1));
			structMap.put("be_parti", new Rule("be_parti", .8));
			structMap.put("verb_be_parti", new Rule("verb", 1));
			//structMap.put("det_verb", new Rule("assert", .5));

			structMap.put("disj_verbphrase", new Rule("assert", 1));
			structMap.put("conj_verbphrase", new Rule("assert", 1));
			structMap.put("csubj_verbphrase", new Rule("assert", 1));
			structMap.put("csubj_verb", new Rule("assert", .5));

			structMap.put("let_symb", new Rule("let", 1));
			structMap.put("be_ent", new Rule("be", 1));
			structMap.put("be_symb", new Rule("be", .7));
			structMap.put("let_assert", new Rule("letbe", 1));
			structMap.put("let_texAssert", new Rule("letbe", 1));
			structMap.put("let_be", new Rule("letbe", 1));
			structMap.put("let_ent", new Rule("let", 1));
			structMap.put("if_assert", new Rule("If", 1));
			structMap.put("iff_assert", new Rule("Iff", 1));
			structMap.put("hyp_assert", new Rule("If", 1));			
			structMap.put("if_texAssert", new Rule("If", 1));
			structMap.put("iff_texAssert", new Rule("Iff", 1));
			structMap.put("hyp_texAssert", new Rule("If", 1));
			
			structMap.put("assert_If", new Rule("assert", .5));
			structMap.put("assert_Iff", new Rule("assert", .5));
			structMap.put("assert_hypo", new Rule("assert", .5));
			//structMap.put("assert_prep", new Rule("assert", .5));
			structMap.put("assert_iff", new Rule("assert", .4));
			structMap.put("texAssert_If", new Rule("assert", .5));
			structMap.put("texAssert_Iff", new Rule("assert", .5));
			structMap.put("texAssert_hypo", new Rule("assert", .5));
			structMap.put("texAssert_prep", new Rule("assert", .5));
			structMap.put("texAssert_qualifier", new Rule("assert", .5));
			structMap.put("texAssert_iff", new Rule("assert", .5));
			structMap.put("hyp_hyp", new Rule("hyp", 1));
			structMap.put("hyp_verbphrase", new Rule("hypo", 0.6));
			structMap.put("hyp_assert", new Rule("hypo", 1));
			structMap.put("hyp_texAssert", new Rule("hypo", 1));
			//$F$ that is $abc$. 
			structMap.put("hyp_ent", new Rule("hypo", .99));
			structMap.put("def_ent", new Rule("Def", 1));
			structMap.put("def_symb", new Rule("Def", 1));
			//e.g. "denote by $F$ a field";
			structMap.put("Def_ent", new Rule("hypo", .9));
			structMap.put("cond_ent", new Rule("Cond", .9));
			structMap.put("cond_assert", new Rule("Cond", .9));		
			structMap.put("cond_texAssert", new Rule("Cond", .9));		
			structMap.put("hyp_phrase", new Rule("hypo", 1));
			structMap.put("hyp_adj", new Rule("hypo", 1));
			//e.g. "which are complementary with respect to $R$" 
			structMap.put("hyp_qualifier", new Rule("hypo", 1));
			structMap.put("hyp_symb", new Rule("hypo", 1));
			structMap.put("rpro_ent", new Rule("rproent", 1));
			structMap.put("ent_rproent", new Rule("newchild", 1));
			structMap.put("rpro_verbphrase", new Rule("phrase", 1));
			//e.g. "which contains"
			structMap.put("rpro_verb", new Rule("hyp", .9));
			structMap.put("rpro_assert", new Rule("phrase", 1));//HERE
			
			structMap.put("parti_phrase", new Rule("hypo", .8));
			structMap.put("amod_noun", new Rule("csubj", 1));
			structMap.put("amod_ent", new Rule("csubj", 1)); // structMap.put("gerund_np",
																// new Rule("csubj",
																// 1));
			structMap.put("pro_ent", new Rule("csubj", 1));
			//absorb the non-struct into the struct. Should only 
			//have one non-ent
			structMap.put("adj_ent", new Rule("absorb1", 1));
			structMap.put("not_ent", new Rule("absorb1", 1));
			structMap.put("not_symb", new Rule("absorb1", 1));
			structMap.put("not_adj", new Rule("absorb1", "adj", 1));
			
			structMap.put("adj_qualifier", new Rule("newchild", "adj", .8));
			structMap.put("verb_prep", new Rule("verbphrase", .9));
			/*Leave these comments.
			 * structMap.put("verb_not", new Rule("newchild", "verb", .9));
			structMap.put("vbs_not", new Rule("newchild", "vbs", .9));
			structMap.put("verbAlone_not", new Rule("newchild", "verbAlone", .9));*/
			structMap.put("not_verb", new Rule("verb", .85));
			structMap.put("not_verbAlone", new Rule("verbAlone", .85));
			structMap.put("verbAlone_prep", new Rule("newchild", "verbAlone", .85));
			structMap.put("verbAlone_adverb", new Rule("newchild", "verbAlone", .85));
			structMap.put("verb_adverb", new Rule("newchild", "verb", .85));//FOOD
			structMap.put("ent_symb", new Rule("absorb2", 1));
			
			// eg "property that a is b"
			structMap.put("noun_phrase", new Rule("np", 1));
			structMap.put("ent_phrase", new Rule("newchild", 1));
			structMap.put("ent_qualifier", new Rule("newchild", .7));
			structMap.put("ent_Cond", new Rule("newchild", 1));
			/*$A$ is such that $A > B$*/
			structMap.put("verb_Cond", new Rule("verbphrase", .8));
			structMap.put("ent_ppt", new Rule("newchild", 1));
			structMap.put("ent_expr", new Rule("addstruct", 1)); // ent_tex. Add
																	// member to
																	// Struct.struct

			//structMap.put("adverb_adj", new Rule("adj", .7)); /// *******
			structMap.put("adverb_verbphrase", new Rule("assert", 1));
			 structMap.put("qualifier_prep", new Rule("newchild", "qualifier", .8)); //could also be adverb //HERE
		

			// probability map for pair constructs.
			// need to gather/compute prob from labeled data
			// used in round 1 parsing
			// "FIRST"/"LAST" indicate positions in sentence.
			//This prob should be averg of the probabilities.
			probMap.put("FIRST", .7);
			probMap.put("LAST", .7);
			// ent_
			probMap.put("ent_anchor", .85);
			probMap.put("ent_verb", .8);
			probMap.put("ent_verbAlone", .81);
			probMap.put("ent_pre", .85);

			// symb_
			probMap.put("symb_verb", .8);
			probMap.put("symb_pre", .6);
			
			// pre_
			probMap.put("pre_ent", .8);
			probMap.put("pre_adj", .6);
			probMap.put("pre_ent", .65);

			// verb_
			probMap.put("verb_ent", .6);
			probMap.put("vbs_ent", .6); // probMap.put("verb_adj", .6);
			probMap.put("vbs_adj", .75);
			probMap.put("verb_adj", .75);
			probMap.put("verb_ent", .75);
			probMap.put("be_adj", .75);
			
			// verbAlone
			probMap.put("verbAlone_LAST", 1.);
						
			// adj_
			probMap.put("adj_ent", .9);
			probMap.put("adj_pre", .7);
			probMap.put("adj_adj", .45);
			//could be used in new definitions
			//e.g. "A is blah if..."
			probMap.put("adj_if", .7);
			probMap.put("adj_hyp", .7);
			
			// adverb_
			probMap.put("adverb_adj", .9);
			
			// art_ , e.g. "let $S$ be a nonempty set"
			//probMap.put("art_adj", .6);
			probMap.put("art_ent", .9);
			
			// hyp_
			probMap.put("hyp_ent", .8);
			probMap.put("hyp_adj", .7);
			
			// for adding combinations to structMap
			/*
			 * ArrayList<String> structs = new ArrayList<String>();
			 * structs.add("has");
			 * 
			 * //for loop constructing more structs to put in maps, e.g. entityhas,
			 * entityis for(int i = 0; i < structs.size(); i++){ //entityhas, vs
			 * forall
			 * 
			 * }
			 */
			
			essentialPosPreMMap.putAll(posPreMMap);
			//adds all words from the stock frequent words. Add these last,
			//to give conflicting pos the least priority. E.g. "open" should
			//have pos "adj" and "ent" before "verb"
			posPreMMap.putAll(WordFrequency.ComputeFrequencyData.freqWordsPosMMap());
			
			return posPreMMap;
		}
	}	
	
	/*
	// build fixedPhraseMap
	static {
		ImmutableListMultimap.Builder<String, FixedPhrase> fixedPhraseMMapBuilder = ImmutableListMultimap
				.<String, FixedPhrase> builder();
		// should read in from file
		File file = new File("src/thmp/data/fixedPhrases.txt");
		System.out.println("cur working dir in Maps.java: " + System.getProperty("user.dir"));
		try {
			Scanner sc = new Scanner(file);

			while (sc.hasNext()) {
				String line = sc.nextLine();
				String[] fixedPhraseData = line.split("\\s*\\|\\s*");

				if (fixedPhraseData.length < 3)
					continue;

				String trigger = fixedPhraseData[0];
				String triggerRegex = fixedPhraseData[1];
				String pos = fixedPhraseData[2];

				fixedPhraseMMapBuilder.put(trigger, new FixedPhrase(triggerRegex, pos));
			}
			sc.close();
		} catch (FileNotFoundException e) {

			e.printStackTrace();
		}

		fixedPhraseMMap = fixedPhraseMMapBuilder.build();
	} */
	
	/*public static void readFixedPhrases(){
		ImmutableListMultimap.Builder<String, FixedPhrase> fixedPhraseMMapBuilder = ImmutableListMultimap
				.<String, FixedPhrase> builder();
		// read in from file
		File file = new File("src/thmp/data/fixedPhrases.txt");
		
		try {
			Scanner sc = new Scanner(file);

			while (sc.hasNext()) {
				String line = sc.nextLine();
				String[] fixedPhraseData = line.split("\\s*\\|\\s*");

				if (fixedPhraseData.length < 3)
					continue;

				String trigger = fixedPhraseData[0];
				String triggerRegex = fixedPhraseData[1];
				String pos = fixedPhraseData[2];

				fixedPhraseMMapBuilder.put(trigger, new FixedPhrase(triggerRegex, pos));
			}
			sc.close();
		} catch (FileNotFoundException e) {

			e.printStackTrace();
		}

		fixedPhraseMMap = fixedPhraseMMapBuilder.build();
		//System.out.print("^^^^fixedPhraseMMap "+fixedPhraseMMap);
	} */

	// get fixed phrase map containing phrases such as "if and only if"
	public static ImmutableListMultimap<String, FixedPhrase> fixedPhraseMap() {
		return fixedPhraseMMap;
	}

	/**
	 * Return part of speech (pos) of word
	 * @param word word
	 * @return pos
	 */
	public static String getPos(String word){
		List<String> posList = BuildMaps.posMMap.get(word);
		//simulate prior API where posMap was not a Multimap
		return posList.isEmpty() ? null : posList.get(0);
	}
	
	/**
	 * Read in files using stream. Called during static initializer.
	 * @throws IOException
	 */
	private static void readFixedPhrases(BufferedReader fixedPhraseReader) {
		
		ImmutableListMultimap.Builder<String, FixedPhrase> fixedPhraseMMapBuilder = ImmutableListMultimap
				.<String, FixedPhrase> builder();
		// should read in from file
		// File file = new File("src/thmp/data/fixedPhrases.txt");
		// System.out.println("cur working dir in Maps.java: " +
		// System.getProperty("user.dir"));
		
		try{
			String line;
			while ((line = fixedPhraseReader.readLine()) != null) {
				
				// String line = sc.nextLine();
				String[] fixedPhraseData = line.split("\\s*\\|\\s*");
	
				if (fixedPhraseData.length < 2){
					continue;
				}
				String trigger;
				String triggerRegexStr;
				String pos;
				if (fixedPhraseData.length == 2){
					triggerRegexStr = fixedPhraseData[0];
					String[] firstTwoWords = triggerRegexStr.split("\\s+");
					if(firstTwoWords.length < 2){
						continue;
					}
					trigger = firstTwoWords[0] + " " + firstTwoWords[1];
					pos = fixedPhraseData[1];
				}else{
					trigger = fixedPhraseData[0];
					triggerRegexStr = fixedPhraseData[1];
					pos = fixedPhraseData[2];
				}
	
				fixedPhraseMMapBuilder.put(trigger, new FixedPhrase(triggerRegexStr, pos));
			}
		}catch(IOException e){
			e.printStackTrace();
			throw new RuntimeException(e);
		}
		fixedPhraseMMap = fixedPhraseMMapBuilder.build();
		
	}



	/**
	 * Put word and part-of-speech pair into posMap.
	 */
	/*public static void putWordToPosMap(String word, String pos){
		
		BuildMaps.posMMap.put(word, pos);
	}*/
	
	/**
	 * reads in list of words not in dictionary yet, puts them in appropriate
	 * dictionaries according to classification in the text
	 * 
	 * @throws FileNotFoundException
	 */
	/*private static void readLexicon() throws FileNotFoundException {
		File file = new File("src/thmp/data/lexicon.txt");
		Scanner sc = new Scanner(file);
		String[] lineAr;		

		while (sc.hasNextLine()) {
			String pos = "", word = "", replacement = "";
			
			String nextLine = sc.nextLine();
			lineAr = nextLine.split(" ");
			if (lineAr.length < 2)
				continue;
			else if (lineAr.length == 2) {
				word = lineAr[0];
				pos = lineAr[1];
			}
			// compound first word, e.g "comm alg"
			else {
				//fluff
				if(nextLine.charAt(0) == '"'){
					Pattern pattern = Pattern.compile("\"(.*)\" ([^\\s]+) \"(.*)\"");
					Matcher matcher = pattern.matcher(nextLine);
					// what about partial finds?
					if (matcher.find()) {
						word = matcher.group(1);
						pos = matcher.group(2);
						replacement = matcher.group(3);
					}
				}else{
					//composite words
					//System.out.println("lineAr "  + Arrays.toString(lineAr));
					pos = lineAr[lineAr.length-1];
					
					for(int i = 0; i < lineAr.length - 1; i++){
						word += lineAr[i] + " ";
					}
					word = word.trim();
					//System.out.println("WORD" + word);
				}				
			}

			// format: "new_word pos"
			// if fluff word: "new_word fluff replacement", eg "to be" fluff
			// "as"
			switch (pos) {
			case "ent":
				mathObjMap.put(word, "mathObj");
				break;
			case "ent_comp":
				mathObjMap.put(word, "mathObj_COMP");
				break;
			case "fluff":
				replacement = replacement.equals("") ? word : replacement;
				fluffMap.put(word, replacement);
				break;
			default:
				BuildMaps.posMMap.put(word, pos);
			}
		}
		sc.close();
	}*/


}
