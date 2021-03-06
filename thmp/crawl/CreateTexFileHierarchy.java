package thmp.crawl;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import thmp.parse.WLCommand;
import thmp.utils.FileUtils;
import thmp.utils.WordForms;

/**
 * Create appropriate tex files hierarchy, extracting tar files 
 * into directories bearing their names.
 * @author yihed
 */
public class CreateTexFileHierarchy {

	//set of file names known to be tex
	//private static final Set<String> texFileNamesSet = new HashSet<String>();
	private static final Runtime rt = Runtime.getRuntime();
	private static final Logger logger = LogManager.getLogger(CreateTexFileHierarchy.class);
	//math0208056: POSIX tar archive (GNU)
	private static final Pattern TAR_FILE_PATTERN = Pattern.compile(".+tar archive.+");
	static final Pattern TEX_PATTERN = Pattern.compile(".*(?:tex|TeX) .*");
	private static final Pattern MX_PATT = Pattern.compile(".+\\.mx");
	
	private static final String FILE_RENAME_PREFIX = "1";
	
	/**
	 * Should already be cd'd into directory with files that are *already* gun-zipped.
	 * Assuming non-math files have been deleted already.
	 * @param srcDirAbsPath path to directory to drop tex files to. E.g. 0323_2453Untarred/0323
	 * ("$newDir""/""${newDir:0:4}")
	 * Actually should keep track of the names of tex files. 
	 * @return Set of filenames that are tex files.
	 */
	public static Map<String, String> createFileHierarchy(String srcDirAbsPath){
		
		Map<String, String> texFileNamesMap = new HashMap<String, String>();
		//check file type. don't need to untar if not tar ball
		File dir = new File(srcDirAbsPath);
		//System.out.println("createFileHierarchy - srcDirAbsPath: "+srcDirAbsPath);
		File[] files = dir.listFiles();	
		
		//each file corresponds to a paper, or an auxiliary file.
		fileLoop: for(File file : files){
			//System.out.println("CreateTexFileHiearchy - processing file "+ file);
			String fileName = file.getName();
			Process pr = null;
			String fileAbsolutePath = file.getAbsolutePath();
			fileAbsolutePath = WordForms.escapeWhiteSpace(fileAbsolutePath);
			pr = executeShellCommand("file " + fileAbsolutePath);
			//System.out.println("executed command: "+"file \"" + fileAbsolutePath + "\"");
			if(null == pr){
				continue;
			}
			InputStreamReader inputReader = new InputStreamReader(pr.getInputStream());
			BufferedReader br = new BufferedReader(inputReader);
			//Matcher matcher;
			try{
				String line;
				while(null != (line = br.readLine())){					
					//System.out.println("CreateTexFileHiearchy - file info for file " + file.getName() + ": " + line);
					if(UnzipFile2.TEX_PATTERN.matcher(line).matches() && !UnzipFile2.NONTEX_EXTENSION_PATTERN.matcher(line).matches()){
						//is tex *file*, e.g. math0209381, or 353263467, so don't delete.
						texFileNamesMap.put(fileAbsolutePath, fileName);
						continue fileLoop;
					}
					//process if tar file, 
					if(TAR_FILE_PATTERN.matcher(line).matches() ){
						//if tar file of directory, untar into directory of same name, so not to create tarball explosion. 
						//make directory
						String tarDirName = fileAbsolutePath;
						/*boolean fileCreated = new File(tarDirName).mkdir();
						if(!fileCreated){
							continue fileLoop;
						}*/		
						//rename, then makedir
						Path curTarFilePath = Paths.get(tarDirName);
						String renamedTarFileName = tarDirName + FILE_RENAME_PREFIX;
						Path renamedTarFilePath = Paths.get(renamedTarFileName);
						renamedTarFilePath = Files.move(curTarFilePath, renamedTarFilePath);
						
						Files.createDirectory(curTarFilePath);
						
						Process pr1 = executeShellCommand("tar xf " + renamedTarFileName + " -C "+ tarDirName);
						
						//flush the subprocess output stream.
						getOrFlushCommandOutput(pr1);
						
						//System.out.println("CreteTexFileHiearchy - deleting file because tar file");
						Files.delete(renamedTarFilePath);
						if(null == pr1){
							System.out.println("CreteTexFileHiearchy - createFileHierarchy - process is null!");
							continue fileLoop;
						}
						pr1.destroy();
						findTexFilesInTarDir(tarDirName, fileName, texFileNamesMap);
						continue fileLoop;
					}
					//is neither tex file nor tarball, delete file, unless .mx file, leave alone
					//if intentionally kept by script, so no need to regenerate files again.
					//System.out.println("CreteTexFileHiearchy - deleting file with data "+line);
					
					if(!MX_PATT.matcher(fileName).matches()) {
						file.delete();
					}
				}				
			}catch(IOException e){
				String msg = "IOException in createFileHierarchy!";
				System.out.println(msg);
				logger.error(msg);				
			}finally {
				FileUtils.silentClose(br);
				FileUtils.silentClose(inputReader);
				pr.destroy();
			}
		}		
		return texFileNamesMap; 		
	}
	
	/** look through content and cp tex file(s), combine if necessary, 
	 * into parent directory.
	 * Should have cd'd into directory containing the tar file before calling this.
	 * Set of file names, some of which are relative file path, relative to current dir.
	 * @param tarDirAbsPath
	 * @param fileName
	 * @param texFileNamesMap
	 */
	private static void findTexFilesInTarDir(String tarDirAbsPath, String tarFileName, Map<String, String> texFileNamesMap){
		
		File tarDirFile = new File(tarDirAbsPath);
		assert(tarDirFile.isDirectory());		
		File[] tarDirFiles = tarDirFile.listFiles();
		//System.out.println("CreateTexFileHiearchy - tarDirFiles.length "+tarDirFiles.length);
		for(File file : tarDirFiles){
			String fileName = file.getName();
			String fileAbsolutePathName = tarDirAbsPath + File.separator + fileName;
			fileAbsolutePathName = WordForms.escapeWhiteSpace(fileAbsolutePathName);
			//get all tex files inside dir, move them to parent dir
			Process pr = executeShellCommand("file " + fileAbsolutePathName);			
			//System.out.println("executing command: "+ "file " + fileAbsolutePathName);
			if(null == pr){
				System.out.println("CreteTexFileHiearchy - findTexFilesInTarDir - process is null!");
				continue;
			}
			
			String output = getOrFlushCommandOutput(pr);
			//System.out.println("findTexFilesInTarDir: getCommandOutput: " + output);
			//add to map if Tex file,
			if(UnzipFile2.TEX_PATTERN.matcher(output).matches() && !UnzipFile2.NONTEX_EXTENSION_PATTERN.matcher(output).matches()){
				texFileNamesMap.put(fileAbsolutePathName, tarFileName);
			}
		}
		//gather set of tex files in dir, cp them all to  
		//parent directory with names paper.tex1, paper.tex2, etc.			
	}
	
	/**
	 * Executes the input command in a separate process, created
	 * by Java runtime. 
	 * Note: The Caller *must* clean up the returned process by calling
	 * process.destroy().
	 * @param cmd
	 * @return nullable
	 */
	private static Process executeShellCommand(String cmd){
		Process pr = null;
		try{
			pr = rt.exec(cmd);
		}catch(IOException e){
			String msg = "CreateTexFileHierarchy - IOException in executeShellCommand while executing: " + cmd;
			System.out.println(msg);
			logger.error(msg);
			throw new IllegalStateException(msg + e);
		}
		return pr;
	}
	
	/**
	 * Reads the output of the command just executed.
	 * @return
	 */
	private static String getOrFlushCommandOutput(Process pr){
		StringBuffer sb = new StringBuffer(30);
		InputStreamReader inputReader = new InputStreamReader(pr.getInputStream());
		BufferedReader br = new BufferedReader(inputReader);		
		try{
			//System.out.println("br.readLine() "+br.readLine());
			String line;
			while(null != (line = br.readLine())){				
				sb.append(line).append("\n");
				//System.out.println("getCommandOutput - next line: " + line);
			}
			int sbLen = sb.length();
			if(sbLen > 1){
				sb.delete(sbLen-1, sbLen);
			}
		}catch(IOException e){
			String msg = "IOException while reading command output!";
			System.out.println(msg);
			logger.error(msg);
		}finally {
			FileUtils.silentClose(br);
			FileUtils.silentClose(inputReader);
		}
		return sb.toString();
	}
	
	public static void main(String[] args){
		//String directoryName;
		//cd into directory containing gzip'ed files
		String srcBasePath = System.getProperty("user.dir") + "/" + args[0];
		Map<String, String> texFileNamesSet = createFileHierarchy(srcBasePath);
		
		List<Map<String, String>> texFileNamesSetList = new ArrayList<Map<String, String>>();
		texFileNamesSetList.add(texFileNamesSet);
		String fileStr = srcBasePath +"/texFileNamesSetList.dat";
		FileUtils.serializeObjToFile(texFileNamesSetList, fileStr);
		String stringFileStr = srcBasePath + "/texFileNamesSetList.txt";
		FileUtils.writeToFile(texFileNamesSet, stringFileStr);
	}
	
}
