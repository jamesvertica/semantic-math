package thmp.search;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Scanner;
import java.util.Set;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import thmp.search.CollectThm.ThmWordsMaps;

/**
 * Read in the most frequently-used words from file, 
 * along with their POS
 *
 */
public class CollectFreqWords {

	//Map of most frequent words and their pos.
	//entrySet of immutable maps preserve the order the entries are inserted,
	//so to get the top N words, just iterate the top N entries.
	private static final ImmutableMap<String, String> wordPosMap;
	private static final ImmutableMap<String, Integer> wordRankMap;
	//this list contains 5000 most frequent words, ordered by freq. Oftentimes we need fewer than those,
	//maybe only top 500, so words such as "ring" don't get screened out.
	private static final File wordsFile = new File("src/thmp/data/wordFrequency.txt");
	private static final Path nonMathFluffWordsFilePath = Paths.get("src/thmp/data/nonMathFluffWords.txt");
	private static final File nonMathFluffWordsFile = new File("src/thmp/data/nonMathFluffWords.txt");
	
	static{
		Map<String, String> wordPosPreMap = new HashMap<String, String>();
		Map<String, Integer>  wordRankPreMap = new HashMap<String, Integer>();
		
		//pass premap into file
		try{
			readWords(wordPosPreMap, wordRankPreMap);
		}catch(FileNotFoundException e){
			e.printStackTrace();
		}
		
		wordPosMap = ImmutableMap.copyOf(wordPosPreMap);
		wordRankMap = ImmutableMap.copyOf(wordRankPreMap);
	}
	
	private static void readWords(Map<String, String> wordPosPreMap, Map<String, Integer> wordRankPreMap) throws FileNotFoundException{
		Scanner sc = new Scanner(wordsFile);
		//skip first line with header info
		sc.nextLine();
		//List<String> defaultList = new ArrayList<String>();
		/**
		 * Also Read in the frequent words from frequent words file (wordsFile), add to list, 
		 * recording their rank. Put rank as values to words in Hashmap.
		 */
		int curRank = 0;
		while(sc.hasNextLine()){
			String line = sc.nextLine();
			String[] lineAr = line.split("\\s+");
			//2nd is word, 3rd is pos
			String word = lineAr[1].trim();
			
			String pos;
			switch(lineAr[2].trim()){
			case "i":
				pos = "pre";
				break;
			case "p":
				pos = "pro";
				break;
			case "v":
				pos = "verb";
				break;
			case "n":
				pos = "noun";
				break;
			case "x":
				//not, no etc
				pos = "not";
				break;
			case "d":
				//determiner
				pos = "det";
				break;
			case "j":
				pos = "adj";
				break;
			case "r":
				pos = "adverb";
				break;
			case "e":
				//"existential there"
				pos = "det";
				break;
			case "a":
				//article, eg the, every, a.
				//classify as adj because of the rules for
				//fusing adj and ent's
				pos = "adj";
				break;
			case "m":
				pos = "num";
				break;
			case "u":
				//interjection, eg oh, yes, um.
				pos = "intj";
				break;
			case "c":
				//conjunctions, eg before, until, although
				//and/or should be parsed as conj/disj, will
				//be overwritten in Maps.java
				pos = "con";				
				break;
			default:
				pos = word;
				System.out.println("default pos: " + lineAr[2]);
				//defaultList.add(lineAr[2]);
			}
			
			wordPosPreMap.put(word, pos);
			//record the word's rank
			wordRankPreMap.put(word, curRank++);			
			//System.out.println(word + " " + pos);			
		}
		
		//System.out.println("LIST: " + defaultList);
		sc.close();
	}
	
	public static ImmutableMap<String, String> get_wordPosMap(){
		return wordPosMap;
	}
	
	public static ImmutableMap<String, Integer> get_wordRankMap(){
		return wordRankMap;
	}	

	/**
	 * Gets only the non math common words. Filters out the math words by 
	 * collecting words of certain frequencies in math texts, e.g. 
	 * CollectThm.docWordsFreqMapNoAnno.
	 * 
	 * @return set of non math fluff words.
	 * @throws FileNotFoundException 
	 */
	public static ImmutableSet<String> get_nonMathFluffWordsSet(){
		Set<String> nonMathFluffWordsSet = new HashSet<String>();
		Scanner sc = null;
		try{
			sc = new Scanner(nonMathFluffWordsFile);
		
			while(sc.hasNextLine()){
				String line = sc.nextLine();
				nonMathFluffWordsSet.add(line);
			}
			//close here instead of in finally, since sc wouldn't
			//have been opened if an Exception was thrown, and 
			//we might have NPE in finally if FileNotFoundException is thrown. 
			sc.close();
		}catch(FileNotFoundException e){
			e.printStackTrace();
		}
		
		return ImmutableSet.copyOf(nonMathFluffWordsSet);
	}
	
	/**
	 * Gets non math fluff words differently, by taking the math words
	 * that are marked as fluff out of the wordPosMap. 
	 * @return
	 */
	public static ImmutableSet<String> get_nonMathFluffWordsSet2(){
		Set<String> nonMathFluffWordsSet = new HashSet<String>(wordPosMap.keySet());
		String[] score1mathWords = CollectThm.score1MathWords();
		String[] additionalFluffWords = CollectThm.additionalFluffWords();
		
		for(String word : score1mathWords){
			nonMathFluffWordsSet.remove(word);
		}
		
		for(String word : additionalFluffWords){
			nonMathFluffWordsSet.add(word);
		}
		return ImmutableSet.copyOf(nonMathFluffWordsSet);
	}
	
	/**
	 * Gets only the non math common words. Filters out the math words by 
	 * collecting words of certain frequencies in math texts, e.g. 
	 * CollectThm.docWordsFreqMapNoAnno.
	 * Intentionally private, so not to create cyclic reliance with CollectThm.
	 * Utility function to write set to file. 
	 * *NOTE* Too many false negatives! Fluff words counted as non fluff.
	 * @return
	 */
	private static ImmutableSet<String> get_nonMathFluffWords(){
		Set<String> nonMathFluffWordsSet = new HashSet<String>();
		ImmutableMap<String, Integer> docWordsFreqMap = CollectThm.ThmWordsMaps.get_docWordsFreqMapNoAnno();
		//System.out.println(docWordsFreqMap);
		//iterate over the math words, remove from wordPosMap words that have freq lower than 150
		//(in docWordsFreqMap)
		
		for(String word : wordPosMap.keySet()){
			Integer wordFreq = docWordsFreqMap.get(word);
			if(wordFreq == null || wordFreq > 200){//|| wordFreq > 1500 for ~1100 thms
				nonMathFluffWordsSet.add(word);
				//System.out.println("word just added " + word + " " + wordFreq);
			}
		}
		
		return ImmutableSet.copyOf(nonMathFluffWordsSet);
	}
	
	/**
	 * Returns the top most frequent words.
	 * @param K is number of most frequent words to use.
	 * @return 
	 */
	public static ImmutableMap<String, String> getTopFreqWords(int K){
		Map<String, String> freqWordsMap = new HashMap<String, String>();
		int counter = K;
		for(Entry<String, String> wordEntry : wordPosMap.entrySet()){
			if(counter == 0) break;
			freqWordsMap.put(wordEntry.getKey(), wordEntry.getValue());
			counter--;
		}
		return ImmutableMap.copyOf(freqWordsMap);
	}
	
	
	private static void wordsRank(){
		
	}
	
	/**
	 * Tests the methods here.
	 * 
	 * @param args
	 * @throws IOException 
	 */
	public static void main(String[] args) throws IOException{
		//System.out.print(CollectThm.get_docWordsFreqMapNoAnno());
		boolean writeNonMathFluffWordsToFile = false;
		if(writeNonMathFluffWordsToFile){
			List<String> nonMathFluffWordsSet = new ArrayList<String>(get_nonMathFluffWords());
			Files.write(nonMathFluffWordsFilePath, nonMathFluffWordsSet, Charset.forName("UTF-8"));
		}
		//get_nonMathFluffWords();
	}
}
