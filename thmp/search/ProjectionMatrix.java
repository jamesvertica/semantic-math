package thmp.search;

import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.wolfram.jlink.Expr;
import com.wolfram.jlink.KernelLink;
import com.wolfram.jlink.MathLinkException;

import thmp.ParsedExpression;
import thmp.search.TheoremGet.ContextRelationVecPair;
import thmp.search.ThmSearch.TermDocumentMatrix;
import thmp.utils.FileUtils;
import thmp.utils.MathLinkUtils;

import static thmp.utils.MathLinkUtils.evaluateWLCommand;

/**
 * Tools for manipulating created projection matrix, 
 * apply projection mx to query vecs, 
 * combine serialized matrices together into List in linear time.
 * @author yihed
 */
public class ProjectionMatrix {
	
	private static final KernelLink ml = FileUtils.getKernelLinkInstance();
	private static final Logger logger = LogManager.getLogger(ProjectionMatrix.class);
	private static final String combinedMxPath = "src/thmp/data/" 
			+ TermDocumentMatrix.COMBINED_PROJECTED_TERM_DOCUMENT_MX_NAME + ".mx";
	
	/**
	 * args is list of paths. 
	 * Supply list paths to directories, each contanining a parsedExpressionList and
	 * the *projected* term document matrices for a tar file.
	 * e.g. "0208_001/0208/",
	 * 
	 */
	public static void main(String[] args){
		int argsLen = args.length;
		if(argsLen == 0){
			System.out.println("Suply a list of paths to .mx files!");
			return;
		}
		//List<String> parsedExpressionFilePathList = new ArrayList<String>();
		//List<String> contextRelationVecPairFilePathList = new ArrayList<String>();
		//form list of String's of paths, e.g. "0208_001/0208/termDocumentMatrixSVD.mx".
		List<String> projectedMxFilePathList = new ArrayList<String>();
		List<ParsedExpression> combinedPEList = new ArrayList<ParsedExpression>();
		List<ContextRelationVecPair> combinedVecsList = new ArrayList<ContextRelationVecPair>();
		
		for(int i = 0; i < argsLen; i++){
			//be sure to check it's valid path to valid .mx
			String path_i = FileUtils.addIfAbsentTrailingSlashToPath(args[i]);
			projectedMxFilePathList.add(path_i + ThmSearch.TermDocumentMatrix.PROJECTED_MX_NAME + ".mx");	
			String peFilePath = path_i + ThmSearch.TermDocumentMatrix.PARSEDEXPRESSION_LIST_FILE_NAME;
			String vecsFilePath = path_i + ThmSearch.TermDocumentMatrix.CONTEXT_VEC_PAIR_LIST_FILE_NAME;
			addExprsToLists(peFilePath, combinedPEList, vecsFilePath, combinedVecsList);
		}
		serializeListsToFile(combinedPEList);
		splitCombinedVecsList(combinedVecsList);
		
		String combinedProjectedTDMxName = TermDocumentMatrix.COMBINED_PROJECTED_TERM_DOCUMENT_MX_NAME;
		//String contextPath, String vMxName, String concatenatedListName
		combineProjectedMx(projectedMxFilePathList, //TermDocumentMatrix.PROJECTED_MX_CONTEXT_NAME, 
				TermDocumentMatrix.PROJECTED_MX_NAME, combinedProjectedTDMxName);
		/* Combine parsedExpressionList's */
		//combineParsedExpressionList(parsedExpressionFilePathList);
	}
	
	private static void splitCombinedVecsList(List<ContextRelationVecPair> combinedVecsList) {
		int numThmsInBundle = TheoremGet.ContextRelationVecBundle.numThmsInBundle();
		String baseFileStr = TheoremGet.ContextRelationVecBundle.BASE_FILE_STR;
		int combinedVecsListSz = combinedVecsList.size();
		int numBundle = combinedVecsListSz / numThmsInBundle;
		//int remainder = combinedVecsListSz - numBundle * numThmsInBundle;
		
		for(int fileCounter = 0; fileCounter < numBundle; fileCounter++){
			String path = baseFileStr + String.valueOf(fileCounter);
			List<ContextRelationVecPair> curList = new ArrayList<ContextRelationVecPair>();
			int curBase = numThmsInBundle * fileCounter;
			for(int j = 0; j < numThmsInBundle; j++){
				curList.add(combinedVecsList.get(j+curBase));
			}
			FileUtils.serializeObjToFile(curList, path);
		}
		String path = baseFileStr + String.valueOf(numThmsInBundle);
		List<ContextRelationVecPair> curList = new ArrayList<ContextRelationVecPair>();
		for(int i = numBundle * numThmsInBundle; i < combinedVecsListSz; i++){
				curList.add(combinedVecsList.get(i));
		}
		if(!curList.isEmpty()){
			FileUtils.serializeObjToFile(curList, path);
		}
	}

	private static void serializeListsToFile(List<ParsedExpression> combinedPEList
			//List<ContextRelationVecPair> combinedVecsList
			) {
		String targetFilePath = ThmSearch.getSystemCombinedParsedExpressionListFilePath();
		FileUtils.serializeObjToFile(combinedPEList, targetFilePath);	
		//targetFilePath = ThmSearch.getSystemCombinedContextRelationVecPairListFilePath();
		//FileUtils.serializeObjToFile(combinedPEList, targetFilePath);
	}

	@SuppressWarnings("unchecked")
	private static void addExprsToLists(String peFilePath, List<ParsedExpression> combinedPEList, String vecsFilePath,
			List<ContextRelationVecPair> combinedVecsList) {
		combinedPEList.addAll( (List<ParsedExpression>)FileUtils.deserializeListFromFile(peFilePath) );		
		combinedVecsList.addAll( (List<ContextRelationVecPair>)FileUtils.deserializeListFromFile(vecsFilePath)  );
	}

	/*private static void combineParsedExpressionList(List<String> parsedExpressionFilePathList) {
		List<ParsedExpression> combinedPEList = new ArrayList<ParsedExpression>();
		for(String path : parsedExpressionFilePathList){
			@SuppressWarnings("unchecked")
			List<ParsedExpression> peList = (List<ParsedExpression>)FileUtils.deserializeListFromFile(path);
			combinedPEList.addAll(peList);
		}
		String targetFilePath = ThmSearch.getSystemCombinedParsedExpressionListFilePath();
		FileUtils.serializeObjToFile(combinedPEList, targetFilePath);
	}*/

	/**
	 * Apply projection matrices (dInverse and uTranspose) to given matrix (vectors should be row vecs).
	 * @param queryMxStrName mx to be applied, could be 1-D. List of row vectors (List's).
	 * @param dInverseName
	 * @param uTransposeName
	 * @param corMxName
	 * @param projectedMxName
	 */
	public static void applyProjectionMatrix(String queryMxStrName, String dInverseName, String uTransposeName, 
			String corMxName, String projectedMxName){
		String msg = "ProjectionMatrix.applyProjectionMatrix- Transposing and applying corMx...";
		logger.info(msg);
		String queryMxStrTransposeName = "queryMxStrNameTranspose";
		queryMxStrTransposeName = queryMxStrName;
		try {
			//process query first with corMx. Convert to column vectors, so rows represent words.		
			ml.evaluate(//queryMxStrTransposeName+" = Transpose[" + queryMxStrName + "];" +
					 "q0 = " + queryMxStrTransposeName + "+ 0.08*" + corMxName + "."+ queryMxStrTransposeName +";");
			ml.discardAnswer();
			//System.out.println("ProjectionMatrix.applyProjectionMatrix, "
				//	+evaluateWLCommand("(" + corMxName + "."+ queryMxStrTransposeName+")[[1]]", true, false));
			//applies projection mx with given vectors. Need to Transpose from column vectors, so rows represent thms.			
			ml.evaluate(projectedMxName + "= Transpose[" + dInverseName + "." + uTransposeName + ".q0];");
			ml.discardAnswer();
		} catch (MathLinkException e) {
			throw new IllegalStateException(e);
		}		
	}
	
	/**
	 * Loads matrices in from mx files, concatenates them into 
	 * one Internal`Bag.
	 * @param  List of .mx file names containing the thm vectors (term doc mx for each).
	 * e.g. "0208_001/0208/termDocumentMatrixSVD.mx".
	 * @param projectedMxContextPath path to context of projected mx. 
	 * @param vMxName name of V matrix (as list of *row* vectors). Name is same for each .mx file.
	 * created from projecting full term-document matrices from full dimensional ones.
	 */
	public static void combineProjectedMx(List<String> projectedMxFilePathList, //String projectedMxContextPath,
			String vMxName, String concatenatedListName){
		String msg = "ProjectionMatrix.combineProjectedMx- about to get paths from files.";
		System.out.println(msg);
		logger.info(msg);
		/* Need to get overall length */
		evaluateWLCommand(ml, "lengthCount=0");
		int fileCounter = 1;
		for(String filePath : projectedMxFilePathList){			
			evaluateWLCommand(ml, "<<" +filePath);
			String ithMxName = vMxName + fileCounter;
			evaluateWLCommand(ml, ithMxName + "=" //+ projectedMxContextPath + "`" 
					+ vMxName + "; lengthCount += Length[" + ithMxName + "]");
			//evaluateWLCommand(ml, "lengthCount += Length[" + ithMxName + "]");
			fileCounter++;
		}
		//create bag with initial capacity.
		evaluateWLCommand(ml, "bag=Internal`Bag[Range[lengthCount]]; rangeCounter=0");
		//System.out.println("ProjectionMatrix, total lengthCount "+evaluateWLCommand("lengthCount", true, true));
		
		msg = "In combineProjectedMx(), Internal`Bag created.";
		System.out.println(msg);
		logger.info(msg);		
		for(int i = 1; i < fileCounter; i++){
			String ithName = vMxName+i;
			evaluateWLCommand(ml, "Internal`BagPart[bag, Range[rangeCounter+1, (rangeCounter+= Length["+ithName+"]) ]] ="
					+ ithName);			
		}
		evaluateWLCommand(ml, concatenatedListName + "= Internal`BagPart[bag, All]");
		//System.out.println("ProjectionMatrix, concatenatedListName "+evaluateWLCommand(concatenatedListName, true, true));
		
		evaluateWLCommand(ml, "DumpSave[\"" + combinedMxPath + "\"," + concatenatedListName + "]");
		msg = "In combineProjectedMx(), Done concatenating matrices!";
		System.out.println(msg);
		logger.info(msg);		
	}
	
}
