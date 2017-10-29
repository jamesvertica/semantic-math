package thmp.search;

import java.io.Serializable;
import java.util.Arrays;
import java.util.List;

import thmp.search.Searcher.QueryVecContainer;

/**
 * Search interface to be implemented by various search methods.
 * Interface useful for dynamic dispatch.
 * 
 * @author yihed
 */
public interface Searcher<S> {

	/**
	 * 
	 * @param thm
	 * @param list List of top candidates already found
	 * @param searchState
	 * @return
	 */
	List<Integer> search(String thm, List<Integer> list, SearchState searchState);
	
	void setSearcherState(QueryVecContainer<S> searcherState_);
	QueryVecContainer<S> getSearcherState();
	
	/**
	 * Searcher state for storing any already-computed vectors,
	 * to avoid computing search vecs multiple times. 
	 * T is the type of the vector, e.g. BigInteger for relational vecs,
	 * and Map<Integer, Integer> for context vec.
	 */
	public static class QueryVecContainer<T>{
		private T queryVector;
		
		public QueryVecContainer(T vec){
			queryVector = vec;
		}
		
		public T getQueryVec(){
			return queryVector;
		}
	}
	
	/**
	 * Containing data serialized during data processing and building. 
	 * To be used later for post processing,
	 * such as combining lists, etc.
	 * Should only contain fields that need to be serialized.
	 */
	public static class SearchConfiguration implements Serializable{
		
		private static final long serialVersionUID = 1408487446908500897L;
		//Where an instance of this class should be serialized.
		private static final String searchConfigurationSerialPath = "src/thmp/data/searchConfiguration.dat";
		//used in ThmHypPairGet. Containing indices of thms that are the first in a bundle. 
		private List<Integer> bundleStartThmIndexList;
		private int totalThmsCount;
		
		/**
		 * @param bundleStartThmIndexList_ used in ThmHypPairGet. Containing indices of thms 
		 * that are the first in a bundle. 
		 * @param totalThmsCount_
		 */
		public SearchConfiguration(List<Integer> bundleStartThmIndexList_, int totalThmsCount_){
			this.bundleStartThmIndexList = bundleStartThmIndexList_;
			this.totalThmsCount = totalThmsCount_;
		}
		
		public List<Integer> bundleStartThmIndexList(){
			return bundleStartThmIndexList;
		}
		
		public int totalThmsCount(){
			return this.totalThmsCount;
		}
		public static String searchConfigurationSerialPath(){
			return searchConfigurationSerialPath;
		}
	}
	
	public static class SearchMetaData{
		
		//whether currently gathering data (e.g. in DetectHypothesis.java), 
		//rather than actively searching.
		private static boolean gatheringDataBool;
		/* Used to separate the case when gatheringDataBool, where in fact want maps collected
		 * in previous runs, e.g. when using a pre-computed projection matrix for SVD.*/
		//private static boolean usePreviousWordDocFreqMaps;
		private static String previousWordDocFreqMapsPath;
		//one is created for each tar, then combined for multiple tars, in ProjectionMatrix.java.
		private static final String wordThmIndexMMapSerialFileName = "wordThmIndexMMap.dat";
		private static final String wordThmIndexMMapSerialFilePath = "src/thmp/data/" + wordThmIndexMMapSerialFileName;
		private static final String wordDocFreqMapPath = "src/thmp/data/allThmWordsMap.dat";
		private static final String threeGramsFreqMapPath = "src/thmp/data/threeGramsMap.dat";
		private static final String twoGramsFreqMapPath = "src/thmp/data/twoGramsMap.dat";
		private static final String trueFluffWordsSetPath = "src/thmp/data/trueFluffWordsSet.dat";
		private static final String literalSearchIndexMapPath = "src/thmp/data/literalSearchIndexMap.dat";
		public static final String texFilesSerializedListFileName = "texFileNamesSetList.dat";
		
		/**Path to file containing database names data 
		 * e.g. '1710.01696','Daniel','','Lemke' Note no thm index*/
		private static final String nameRawDataPath = "src/thmp/data/metaDataStringNameDB.txt";
		/**Path to csv file containing database names data 
		 * e.g. '5523','1710.01696','Daniel','','Lemke'*/
		private static final String nameCSVDataPath = "src/thmp/data/metaDataNameDB.csv";
		
		public static void set_gatheringDataBoolToTrue(){
			//System.out.println(Arrays.toString(Thread.currentThread().getStackTrace()));
			gatheringDataBool = true;
		}
		
		public static boolean gatheringDataBool(){
			return gatheringDataBool;
		}
		
		/**Path to file containing database names data 
		 * e.g. '1710.01696','Daniel','','Lemke' Note no thm index
		 * */
		public static String nameRawDataPath() {
			return nameRawDataPath;
		}
		
		/**Path to csv file containing database names data 
		 * e.g. '5523','1710.01696','Daniel','','Lemke'
		 * Current value: "src/thmp/data/metaDataNameDB.csv"*/
		public static String nameCSVDataPath() {
			return nameCSVDataPath;
		}
		
		public static String wordThmIndexMMapSerialFilePath(){
			return wordThmIndexMMapSerialFilePath;
		}
		
		public static String wordThmIndexMMapSerialFileName(){
			return wordThmIndexMMapSerialFileName;
		}
		
		public static String wordDocFreqMapPath(){
			return wordDocFreqMapPath;
		}
		
		public static String threeGramsFreqMapPath(){
			return threeGramsFreqMapPath;
		}
		
		public static String twoGramsFreqMapPath(){
			return twoGramsFreqMapPath;
		}
		
		public static String trueFluffWordsSetPath(){
			return trueFluffWordsSetPath;
		}
		
		public static String literalSearchIndexMapPath() {
			return literalSearchIndexMapPath;
		}
		
		/** Used to separate the case when gatheringDataBool, where in fact want maps collected
		 * in previous runs, e.g. when using a pre-computed projection matrix for SVD.*/
		public static String previousWordDocFreqMapsPath() {
			return previousWordDocFreqMapsPath;
		}

		public static void set_previousWordDocFreqMapsPath(String path) {
			SearchMetaData.previousWordDocFreqMapsPath = path;
		}
	}
}
