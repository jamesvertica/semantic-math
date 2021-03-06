package thmp.search;

import java.io.Serializable;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.ServletContext;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.common.collect.Multimap;
import com.google.common.collect.TreeMultimap;

import thmp.parse.ProcessInput;
import thmp.parse.TheoremContainer;
import thmp.utils.DBUtils;
import thmp.utils.DataUtility;
import thmp.utils.FileUtils;
import thmp.utils.WordForms;

/**
 * Search that combines svd/nearest and intersection.
 * @author yihed
 *
 */
public class SearchCombined {

	private static final int NUM_NEAREST_SVD = 100;
	//return 60 now, but the FE should only display all when "show more" is clicked.
	//Need to be large, if the top-scoring bracket contains many results, e.g. for short search terms,
	//so context vector based searches can kick in.
	protected static final int NUM_NEAREST = 60; //100
	//combined number of vectors to take from search results of
	//svd/nearest and intersection
	//protected static final int NUM_COMMON_VECS = 4;
	//private static ServletContext servletContext;
	//should update at the very beginning!
	//private static final int LIST_INDEX_SHIFT = 1;
	
	private static final Pattern INPUT_PATTERN = Pattern.compile("(\\d+)\\s+(.+)");
	private static final Pattern CONTEXT_INPUT_PATTERN = Pattern.compile("(context|relation)\\s+(.*)");
	protected static final int CONTEXT_SEARCH_TUPLE_SIZE = 10;
	//private static final Logger logger = LogManager.getLogger(SearchCombined.class);
	
	/**
	 * Thm and hyp pair, used to display on web separately.
	 */
	public static class ThmHypPair implements Serializable, TheoremContainer{
		
		private static final long serialVersionUID = -6913500705834907026L;
		private String thmStr;
		private String hypStr;
		//srcFileName two possible forms, e.g. math0211002, or 4387.86213.
		private String srcFileName;
		private String arxivURL;
		//the type of thm, e.g. theorem, lemma, proposition, conjecture
		private String thmType = "Theorem"; 
		
		private static final ThmHypPair PLACEHOLDER_PAIR = new ThmHypPair("", "", "");
		
		public ThmHypPair(String thmStr_, String hypStr_, String srcFileName_){
			this.thmStr = thmStr_;
			this.hypStr = hypStr_;
			this.srcFileName = srcFileName_;
			this.arxivURL = DataUtility.createArxivURLFromFileName(srcFileName_);
		}
		
		public ThmHypPair(String thmStr_, String hypStr_, String srcFileName_, String thmType_){
			this(thmStr_, hypStr_, srcFileName_);
			this.thmType = thmType_;
		}
		
		@Override
		public String toString(){
			StringBuilder sb = new StringBuilder(500);
			
			sb.append("[").append(srcFileName).append("] ");
			sb.append(thmStr);
			//leave thmType here!
			sb.append(thmType);
			if(!WordForms.getWhiteEmptySpacePattern().matcher(hypStr).matches()){
				sb.append(" HYP: ").append(hypStr).append("\n");
			}			
			return sb.toString();
		}
		
		public String thmStr(){
			return this.thmStr;
		}
		
		public String hypStr(){
			return this.hypStr;
		}
		
		/**
		 * the type of thm, e.g. theorem, lemma, proposition, conjecture
		 * @return
		 */
		public String thmType() {
			return this.thmType;
		}
		
		/**
		 * srcFileName of the tex file. Two possible forms, 
		 * e.g. math0211002, or 4387.86213.
		 * Names are preprocessed so slashes "/" are removed.
		 * @return
		 */
		public String srcFileName(){
			return this.srcFileName;
		}
		public String arxivURL(){
			return this.arxivURL;
		}
		
		public static ThmHypPair PLACEHOLDER_PAIR(){
			return PLACEHOLDER_PAIR;
		}

		@Override
		public String getEntireThmStr() {
			//thmStr first so literal search can catch words in thmStr first.
			return this.thmStr + " " + this.hypStr;
		}
		
	}

	private static final boolean DEBUG = FileUtils.isOSX();
	
	/**
	 * Turn list of indices of thms into list of String's.
	 * Does *NOT* include hyp in returned results! Avoid using this, since slower.
	 * @param highestThms
	 * @return
	 */
	public static List<String> thmListIndexToString(List<Integer> highestThms){
		List<String> foundThmList = new ArrayList<String>();
		
		List<ThmHypPair> thmHypPairList = thmListIndexToThmHypPair(highestThms);
		for(ThmHypPair pair : thmHypPairList) {
			foundThmList.add(pair.thmStr());
		}
		return foundThmList;
	}
	
	/**
	 * Turn list of indices of thms into list of ThmHypPair's.
	 * @param highestThms
	 * @return
	 */
	public static List<ThmHypPair> thmListIndexToThmHypPair(List<Integer> highestThms){
		//List<ThmHypPair> bestCommonThmHypPairList = new ArrayList<ThmHypPair>();
		Connection conn = DBUtils.getPooledConnection();
		/*for(Integer thmIndex : highestThms){
			//ThmHypPair thmHypPair = TriggerMathThm2.getWedDisplayThmHypPair(thmIndex);
			//Looks for thm with thmIndex in bundle. Load bundle in not already in memory.
			ThmHypPair thmHypPair = ThmHypPairGet.retrieveThmHypPairWithThm(highestThms, conn);
			bestCommonThmHypPairList.add(thmHypPair);			
		}*/
		try {
			return ThmHypPairGet.retrieveThmHypPairWithThm(highestThms, conn);
		}finally {
			DBUtils.closePooledConnection(conn);
		}
	}
	
	/**
	 * Set resources for list of resource files.
	 * @param freqWordsFileBuffer
	 * @param texSourceFileBufferList
	 * @param macrosReader
	 * @param parsedExpressionListInputStream InputStream containing serialized ParsedExpressions.
	 * @param allThmWordsSerialBReader BufferedReader to file containing words from previous run's theorem data.
	 */
	public static void initializeSearchWithResource(ServletContext servletContext_){
		CollectThm.setServletContext(servletContext_);
		ProcessInput.setServletContext(servletContext_);
		//DBUtils.recompileDatabase();
	}
	
	/**
	 * Gets highest scored vectors that are the common to both lists.
	 * Can weigh the two lists differently.
	 * nearestVecList is 1-based, but intersectionVecList is 0-based!
	 * @param nearestVecList
	 * @param intersectionVecList
	 * @param numVectors is the total number of vectors to get 
	 * @return
	 */
	private static List<Integer> findListsIntersection(List<Integer> nearestVecList,
			SearchState searchState, int numVectors, String input
			){
		
		List<Integer> intersectionVecList = searchState.intersectionVecList();
		//short-circuit
		if(intersectionVecList.isEmpty()){
			return nearestVecList;
		}
		int intersectionVecListSz = intersectionVecList.size();
		
		Map<Integer, Integer> thmScoreMap = searchState.thmScoreMap();
		List<Integer> bestCommonVecList = new ArrayList<Integer>();
		//map to keep track of scores in first list
		Map<Integer, Integer> nearestVecListPositionsMap = new HashMap<Integer, Integer>();
		//use TreeMultimap to keep track of total score
		//keys are scores, and values are indices of thms that have that score
		Multimap<Integer, Integer> scoreThmTreeMMap = TreeMultimap.create();
		int nearestVecListSz = nearestVecList.size();
		Map<Integer, Integer> thmSpanMap = searchState.thmSpanMap();
		
		for(int i = 0; i < nearestVecList.size(); i++){
			int thmIndex = nearestVecList.get(i);
			nearestVecListPositionsMap.put(thmIndex, i);
		}
		
		int totalWordAdded = searchState.totalWordAdded();
		//avoid magic numbers!
		int threshold = totalWordAdded < 3 ? totalWordAdded : (totalWordAdded < 6 ? totalWordAdded-1 
				: totalWordAdded - totalWordAdded/3);
		int maxScore = 0;
		//should iterate to include more pairs! 
		for(int i = 0; i < intersectionVecListSz; i++){
			//index of thm
			int intersectionThm = intersectionVecList.get(i);
			//intersection list is 0-based!
			Integer nearestListThmIndex = nearestVecListPositionsMap.remove(intersectionThm);
			//first check if spanning is good, if spanning above a threshold, say contains more than
			//(total #relevant words) - 2, threshold determined by relative size
			//then don't need to be contained in nearestVecList	
			if(thmSpanMap.get(intersectionThm) >= threshold){
				int score = i;
				if(score > maxScore) maxScore = score;
				scoreThmTreeMMap.put(score, intersectionThm);
			}			
			else if(nearestListThmIndex != null){
				int score = i+nearestListThmIndex;
				if(score > maxScore) maxScore = score;
				scoreThmTreeMMap.put(score, intersectionThm);
			}//else add the length of nearestVecList to the score
			else{
				int score = i+nearestVecListSz;
				if(score > maxScore) maxScore = score;
				scoreThmTreeMMap.put(score, intersectionThm);
			}			
		}
		
		//now add the vecs from nearestVecList that haven't been added to scoreThmTreeMMap
		for(Map.Entry<Integer, Integer> entry : nearestVecListPositionsMap.entrySet()){
					
			int thm = entry.getKey();
			int nearestListThmIndex = entry.getValue();
			//prioritize intersectionList because the results there are more precise. So
			//all added lists there have guaranteed spot
			//int score = Math.max(maxScore, nearestListThmIndex + intersectionVecListSz);
			scoreThmTreeMMap.put(nearestListThmIndex + intersectionVecListSz, thm);
		}
		
		//System.out.println("values size " + scoreThmTreeMMap.values().size());
		//pick out top vecs
		int counter = numVectors;
		Iterator<Integer> scoreThmTreeMMapValIter = scoreThmTreeMMap.values().iterator();
		//take first one from intersectionList if span for top result is not ideal,
		//and score gap not too large
		Integer topThmIndex = scoreThmTreeMMapValIter.next();
		
		int topIntersectionThmIndex = intersectionVecList.get(0);
		int topIntersectionThmScore = thmScoreMap.get(topIntersectionThmIndex);
		int topQueryIndex;
		//intersectionVecList guranteed not empty at this point. Remove magic numbers.
		//adjust top search result
		if( (!intersectionVecList.contains(topThmIndex)
				//make the 4.0/5 into constant after done with tinkering
				|| thmSpanMap.get(topThmIndex) < thmSpanMap.get(topIntersectionThmIndex)*4.0/5)
				&& (!thmScoreMap.containsKey(topThmIndex) || thmScoreMap.get(topThmIndex) < topIntersectionThmScore)){
			bestCommonVecList.add(topIntersectionThmIndex);
			topQueryIndex = topIntersectionThmIndex;
		}else{
			bestCommonVecList.add(topThmIndex);		
			topQueryIndex = topThmIndex;
		}		
		counter--;
		
		while(scoreThmTreeMMapValIter.hasNext()){
			int thmIndex = scoreThmTreeMMapValIter.next();
			if(counter == 0 || thmIndex == topQueryIndex) break;			
			bestCommonVecList.add(thmIndex);	
			counter--;
		}
		
		return bestCommonVecList;
	}
	
	public static List<ThmHypPair> searchCombined(String input, Set<String> searchWordsSet, boolean searchContextBool){
		
		SearchState searchState = new SearchState();
		List<ThmHypPair> bestCommonThmHypPairList 
			= thmListIndexToThmHypPair(searchCombined(input, searchState, searchWordsSet, searchContextBool, false));
		return bestCommonThmHypPairList;
	}
	
	/**
	 * Search interface to be called externally, eg from servlet.
	 * Resources should have been set prior to this if called externally.
	 * @param inputStr search input.
	 * @param searchState searchState to record the intersectionVecList, and map of
	 * tokens and their span scores. And communicate parseState
	 * among different searchers.
	 */
	public static List<Integer> searchCombined(String input, SearchState searchState,
			Set<String> searchWordsSet, boolean searchContextBool,
			boolean searchRelationalBool){
		
		if(WordForms.getWhiteEmptySpacePattern().matcher(input).matches()) return Collections.<Integer>emptyList();
		input = input.toLowerCase();
		
		//String[] thmAr = WordForms.getWhiteNonEmptySpacePattern().split(input);
		Matcher matcher;
		if(!searchContextBool || !searchRelationalBool){
			if((matcher = CONTEXT_INPUT_PATTERN.matcher(input)).matches()){
				String prefix = matcher.group(1);
				if(prefix.equals("context")){
					searchContextBool = true;
					//removes first word
					input = matcher.group(2);			
				}else if(prefix.equals("relation")){
					searchRelationalBool = true;
					input = matcher.group(2);
				}
			}
		}		
		StringBuilder inputSB = new StringBuilder();
		int numCommonVecs = getNumCommonVecs(inputSB, input);
		input = inputSB.toString();
		
		//SearchState searchState = new SearchState();
			
		SearchIntersection.getHighestThmStringList(input, searchWordsSet,
				searchState, searchContextBool, searchRelationalBool);
		
		String[] inputAr = WordForms.getWhiteNonEmptySpacePattern().split(input);		
		//context search doesn't do anything if only one token.
		if(inputAr.length < 2){
			searchRelationalBool = false;
		}
		
		List<Integer> bestCommonVecsList = searchState.intersectionVecList();
		//need good heuristics for when to trigger this, e.g. very few or no results from previous algorithms.
		//Feb 2018 - don't trigger this on server.
		if(null == FileUtils.getServletContext() && bestCommonVecsList.isEmpty()){			
			System.out.println("SVD triggered!");
			//experiment with this constant!
			if(searchState.largestWordSpan() < searchWordsSet.size()*2./3){
				//Only do SVD if no good intersection matches, determine if good match based on span scores.
				List<Integer> nearestVecList = ThmSearch.ThmSearchQuery.findNearestThmsInTermDocMx(input, NUM_NEAREST_SVD);
				if(DEBUG  && nearestVecList.isEmpty()){
					//System.out.println("I've got nothing for you yet. Try again.");
					System.out.println("SVD search returns empty list!");
				}
				
				//find best intersection of these two lists. nearestVecList is 1-based, but intersectionVecList is 0-based! 
				bestCommonVecsList = findListsIntersection(nearestVecList, searchState, 
					numCommonVecs, input);
			}
		}		

		//List<ThmHypPair> bestCommonThmHypPairList = thmListIndexToThmHypPair(bestCommonVecsList);
		
		return bestCommonVecsList;
	}
	
	/**
	 * Finds the number of output vectors as specified by user.
	 * @param inputSB empty StringBuilder to be filled with theorem content.
	 * @param input
	 * @return number of common vecs
	 */
	public static int getNumCommonVecs(StringBuilder inputSB, String input) {
		
		int numCommonVecs = NUM_NEAREST;
		Matcher matcher = INPUT_PATTERN.matcher(input);
		//inputSB.setLength(0);
		if(matcher.matches()){
			numCommonVecs = Integer.parseInt(matcher.group(1));			
			inputSB.append(matcher.group(2));
		}else{
			inputSB.append(input);
		}
		return numCommonVecs;
	}

	/**
	 * Searches using search as specified by searcher, e.g. relational search, in chunks of size tupleSz from index 0
	 * through the entire list. 
	 * @param queryStr The English sentence to be searched.
	 * @param thmCandidateList List produced by previous methods, to be processed through by searcher.
	 * @param tupleSz
	 * @param searcher new search object to be used for searching.
	 */
	public static <T> List<Integer> searchVecWithTuple(String queryStr, List<Integer> thmCandidateList, int tupleSz,
			Searcher<T> searcher, SearchState searchState) {
		if(0 == tupleSz){
			return thmCandidateList;
		}
		int commonVecsLen = thmCandidateList.size();
		int numTupleSzInCommonVecsLen = commonVecsLen/tupleSz;
		List<Integer> reorderedList = new ArrayList<Integer>();
		
		//call relational search for one tuple at a time.
		for(int i = 0; i <= numTupleSzInCommonVecsLen; i++){
			
			int startingIndex = i*tupleSz;
			if(commonVecsLen == startingIndex) break;
			int startingIndexPlusTupleSz = startingIndex + tupleSz; 
			int endingIndex = startingIndexPlusTupleSz > commonVecsLen 
					? commonVecsLen : startingIndexPlusTupleSz;
			//using .subList() avoids creating numTupleSzInCommonVecsLen 
			//number of new short lists as iterating through the list would.
			List<Integer> sublist = thmCandidateList.subList(startingIndex, endingIndex);
			List<Integer> reorderedSublist = searcher.search(queryStr, sublist, searchState);
			reorderedList.addAll(reorderedSublist);
		}		
		return reorderedList;
	}
	
	/**
	 * Searches using search as specified by searcher, e.g. relational search, in chunks of size tupleSz from index 0
	 * through the entire list. 
	 * @param queryStr The English sentence to be searched.
	 * @param thmCandidateList List produced by previous methods, to be processed through by searcher.
	 * @param tupleSz
	 * @param searcher new search object to be used for searching.
	 */
	public static <T> List<Integer> searchVec(String queryStr, List<Integer> thmCandidateList,
			Searcher<T> searcher, SearchState searchState) {
		
		List<Integer> reorderedList = new ArrayList<Integer>();
		
		List<Integer> reorderedSublist = searcher.search(queryStr, thmCandidateList, searchState);
		reorderedList.addAll(reorderedSublist);
		return reorderedList;
	}
	
	/**
	 * Search that invokes different layers
	 * @param args
	 */
	public static void main(String[] args){
		
		//load the necessary classes so the first call doesn't take 
		//disproportionately more time
		Scanner sc = new Scanner(System.in);
		
		while(sc.hasNextLine()){
			String thm = sc.nextLine();
			System.out.println(" ~~~~~~~ ");
			boolean searchContextBool = false;
			boolean searchRelationalBool = false;
			
			Set<String> wordsSet = new HashSet<String>();
			SearchState searchState = new SearchState();
			//this gives the web-displayed versions. 
			List<ThmHypPair> bestCommonThmHypPairList 
				= thmListIndexToThmHypPair(searchCombined(thm, searchState, wordsSet, searchContextBool, searchRelationalBool));
			
			int counter = 0;
			System.out.println("~ SEARCH RESULTS ~");
			for(ThmHypPair thmHypPair : bestCommonThmHypPairList){
				System.out.println(counter++ + " ++ " + thmHypPair);
			}
		}
		
		sc.close();
	}
}
