package thmp.search;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.ArrayUtils;

import com.wolfram.jlink.Expr;
import com.wolfram.jlink.ExprFormatException;
import com.wolfram.jlink.KernelLink;
import com.wolfram.jlink.MathLinkException;

import thmp.ThmP1;
import thmp.utils.FileUtils;
import thmp.utils.WordForms;

/**
 * Search results based on context vectors produced during parsing.
 * 
 * @author yihed
 *
 */
public class ContextSearch {

	//bare thm list, without latex \label's or \index's, or refs, etc
	//private static final List<String> bareThmList = CollectThm.ThmList.get_bareThmList();
	private static final KernelLink ml = FileUtils.getKernelLinkInstance();
	//list of strings "{1, 0, ...}" corresponding to contexts of thms, same indexing as in thmList.
	private static final List<String> contextVecStringList = new ArrayList<String>();
	//private static final int LIST_INDEX_SHIFT = 1;
	private static final Pattern BRACKETS_PATTERN = WordForms.BRACKETS_PATTERN();
	private static final boolean DEBUG = false;
	
	static{
		//need to set this when deployed to VM
		//String contextVecFileStr = "src/thmp/data/contextVecAll.txt";
		String contextVecFileStr = "src/thmp/data/contextVectorsFields.txt";
		//String contextVecFileStr = "src/thmp/data/contextVecCommm5.txt";
		//String contextVecFileStr = "src/thmp/data/contextVectorsMultilinearAlgebra.txt";
		
		//read in contextVecStringList from file, result of GenerateContextVectors.
		//FileReader contextVecFile;
		//BufferedReader contextVecFileBReader;
		
		BufferedReader contextVecFileBReader = CollectThm.contextVecBR();
		if(contextVecFileBReader == null){
			try{
				FileReader contextVecFileReader = new FileReader(contextVecFileStr);
				contextVecFileBReader = new BufferedReader(contextVecFileReader);
			}catch(FileNotFoundException e){
				System.out.println("Context vectors file not found!");
				e.printStackTrace();
				throw new RuntimeException(e);
			}
		}
		
		try{			
			String line;
			while((line = contextVecFileBReader.readLine()) != null){
				//a line is a String of the form "{1, 0, ...}"
				contextVecStringList.add(line);
			}			
			contextVecFileBReader.close();
		}catch(FileNotFoundException e){
			e.printStackTrace();
			throw new RuntimeException(e);
		}catch(IOException e){
			e.printStackTrace();
			throw new RuntimeException(e);
		}finally{
			if(contextVecFileBReader != null){
				closeBuffer(contextVecFileBReader);
			}
		}
		
	}
	
	/**
	 * Close the bufferedReader.
	 * @param bf
	 */
	private static void closeBuffer(BufferedReader bf){
		try{
			bf.close();
		}catch(IOException e){
			e.printStackTrace();
		}
	}
	
	/**
	 * @param query input query, in English 
	 * @param nearestThmIndexList List of thm indices, resulting from other 
	 * search algorithms such as SVD and/or intersection.
	 * Gets list of vectors from GenerateContextVectors.java, 
	 * pick out the nearest structual vectors using Nearest[].
	 * @return Gives an ordered list of vectors based on context.
	 */
	public static List<Integer> contextSearch(String query, List<Integer> nearestThmIndexList){
		//short-circuit if query contains only 1 word		
		
		String queryContextVec = thmp.GenerateContextVector.createContextVector(query);
		//short-circuit if context vec not meaninful (insignificant entries created)
		
		/////////
		//create range vector for Nearest
		Matcher bracketsMatcher = BRACKETS_PATTERN.matcher(nearestThmIndexList.toString());
		String rangeVec = bracketsMatcher.replaceAll("{$1}");
		System.out.println("selected thm indices: "+rangeVec);
		
		StringBuffer nearestThmsContextVecSB = new StringBuffer("{");
		int nearestThmIndexListSz = nearestThmIndexList.size();
		List<Integer> nearestVecList = null;
		
		for(int i = 0; i < nearestThmIndexListSz; i++){
			int thmIndex = nearestThmIndexList.get(i);
			if(i < nearestThmIndexListSz-1){
				//except at the end!
				nearestThmsContextVecSB.append(contextVecStringList.get(thmIndex) + ",");
			}else{
				nearestThmsContextVecSB.append(contextVecStringList.get(thmIndex) + "}");
			}
			
		}
		if(DEBUG){
			System.out.println("nearestThmsContextVecSB " + nearestThmsContextVecSB);
		}
		//get the nearest thms from the list of thm (indices) passed in
		//String nearestContextVecsStr = null;
		String thmVecDim = null;
		String queryVecDim = null;
		try{
			//get the average of the nonzero elements			
			ml.evaluate("nearestThmList = "+ nearestThmsContextVecSB +
					//+ "; nearestThmListFlat = Flatten[nearestThmList];"
					//+ "thmNonZeroPositions = Position[nearestThmList, _?(# != 0 &)][[All,2]]"
						"; Length[nearestThmList[[1]]]"
					);
			//ml.discardAnswer();
			ml.waitForAnswer();
			thmVecDim = ml.getExpr().toString();
			System.out.println("nearestThmVecDim " + thmVecDim);
			
			ml.evaluate("query = " + queryContextVec);
			ml.waitForAnswer();
			System.out.println("query vector " + ml.getExpr());
			
			ml.evaluate("Length[query]");
			ml.waitForAnswer();
			queryVecDim = ml.getExpr().toString();
			/*ml.evaluate("numberNonZeroEntrySize = Length[thmNonZeroPositions]");
			ml.waitForAnswer();
			Expr expr = ml.getExpr();
			if(!expr.integerQ() || expr.asInt() == 0){
				//should log if this happens!
				System.out.println("Something is wrong; or no non-zero entries in theorem vectors!");
				return nearestThmIndexList;
			}
			
			ml.evaluate("nonZeroContextVecMean = Total[nearestThmListFlat]/numberNonZeroEntrySize //N");
			//ml.discardAnswer();	
			ml.waitForAnswer();
			System.out.println("nonZeroContextVecMean " + ml.getExpr());

			//get nonzero entries of query context vector
			ml.evaluate("queryContextVecPositions = Position[query, _?(# != 0 &)][[All,1]]");
			//ml.discardAnswer();
			ml.waitForAnswer();			
			System.out.println("queryContextVecPositions " + ml.getExpr());
			
			//take complement of queryContextVecPositions in numberNonZeroEntries
			ml.evaluate("thmNonZeroEntryPositions = Complement[Union[thmNonZeroPositions], queryContextVecPositions]");
			//ml.discardAnswer();
			ml.waitForAnswer();			
			System.out.println("thmNonZeroEntryPositions " + ml.getExpr());*/
			//replace the query at union of indices, with averages of nonzero entries in nearestThmsContextVecSB.
			//ReplacePart[{1, 2, 4, 5}, Transpose[{{1, 3, 4}}] -> 3]
			//ml.evaluate("q = ReplacePart[query, Transpose[{thmNonZeroEntryPositions}]->nonZeroContextVecMean]");
			//ml.discardAnswer();
			
			//get average of the nearestThmsContextVecSB!
			/*ml.evaluate("contextVecMean = Mean[Flatten[" + nearestThmsContextVecSB + "//N]];");
			ml.discardAnswer();
			ml.evaluate("q=" + queryContextVec + "/.{0->contextVecMean}");*/
			
			//ml.discardAnswer();
			//extract the few positions from thm vectors, so Nearest only needs to find it for vecs of small lengths!!
			
			ml.evaluate("PositionsToReplace = Transpose[{Complement[Range[Length[query]], queryContextVecPositions]}]; "
					+ "nearestThmsPartReplaced = Map[ReplacePart[#, PositionsToReplace->0] &, nearestThmList]" );
			ml.waitForAnswer();
			System.out.println("nearestThmsPartReplaced" + ml.getExpr());
			//ml.discardAnswer();
			
			//ml.evaluate("Nearest[v->Range[Dimensions[v][[1]]], First@Transpose[q],"+numNearest+"] - " + LIST_INDEX_SHIFT);
			ml.evaluate("Nearest[nearestThmsPartReplaced->"+ rangeVec +", query," 
					+ nearestThmIndexListSz +"]");
			
			/*ml.evaluate("Nearest[" + nearestThmsContextVecSB + "->"+ rangeVec +", query," 
					+ nearestThmIndexListSz +"]"); */
			ml.waitForAnswer();
			Expr nearestContextVecs = ml.getExpr();
			
			//nearestContextVecsStr = nearestContextVecs.toString();
			System.out.println(nearestContextVecs);
			//use this when using Nearest
			//int[] nearestVecArray = (int[])nearestVec.part(1).asArray(Expr.INTEGER, 1);
			int[] nearestVecArray = (int[])nearestContextVecs.asArray(Expr.INTEGER, 1);
			Integer[] nearestVecArrayBoxed = ArrayUtils.toObject(nearestVecArray);
			nearestVecList = Arrays.asList(nearestVecArrayBoxed);
			
			//for(int i = nearestVecList.size()-1; i > -1; i--){
			for(int i = 0; i < nearestVecList.size(); i++){
				int thmIndex = nearestVecList.get(i);
				System.out.println(TriggerMathThm2.getThm(thmIndex));
				//System.out.println("thm vec: " + TriggerMathThm2.createQuery(TriggerMathThm2.getThm(d)));
			}
			
		}catch(MathLinkException | ExprFormatException e){
			e.printStackTrace();
			throw new RuntimeException("thmVecDim: " + thmVecDim + " queryVecDim: " + queryVecDim + " keywordDict: " + 
			TriggerMathThm2.keywordDict(), e);
		}
		//System.out.println("keywordDict: " + TriggerMathThm2.keywordDict());
		return nearestVecList;
	}
	
	//testing
	public static void main(String[] args){
	
		Scanner sc = new Scanner(System.in);
		
		while(sc.hasNextLine()){
			String thm = sc.nextLine();
			if(thm.matches("\\s*")) continue;
			
			thm = thm.toLowerCase();
			int NUM_NEAREST = 6;
			List<Integer> nearestVecList = ThmSearch.readThmInput(thm, NUM_NEAREST);
			if(nearestVecList.isEmpty()){
				System.out.println("I've got nothing for you yet. Try again.");
				continue;
			}
			/*SearchState searchState = SearchIntersection.getHighestThm(thm, NUM_NEAREST);
			int numCommonVecs = 4;
			
			String firstWord = thm.split("\\s+")[0];
			if(firstWord.matches("\\d+")){
				numCommonVecs = Integer.parseInt(firstWord);
			}
			//find best intersection of these two lists. nearestVecList is 1-based, but intersectionVecList is 0-based! 
			//now both are 0-based.
			
			List<Integer> bestCommonVecs = findListsIntersection(nearestVecList, searchState, numCommonVecs);
			*/
			List<Integer> bestCommonVecs = contextSearch(thm, nearestVecList);
			
			/*for(int d : bestCommonVecs){
				System.out.println(TriggerMathThm2.getThm(d));
			}*/
		}
		
		sc.close();
		
	}
	
}