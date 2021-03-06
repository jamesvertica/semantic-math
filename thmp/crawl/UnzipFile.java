package thmp.crawl;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;

import thmp.parse.ThmInput;

/**
 * Unzips files, extract math directories, and reads the latex files from them.
 * 
 * @author yihed
 *
 */
public class UnzipFile {

	private static final String[] srcBasePaths = new String[] { "/Users/yihed/Documents/A/arxiv/0002_1",
			"/Users/yihed/Documents/A/arxiv/0003" };

	
	// uses zip4j to unzip files. <-- Except zip4j doesn't work on gz files.
	// this unzip method did not work, so had to resort to Java utility method
	// with streams
	/*private static void unzip() {
		String src = "/Users/yihed/Documents/A/arxiv/0002/math0002001.gz";
		String dest = "/Users/yihed/Documents/A/arxiv/0002content";
		try {
			ZipFile zipFile = new ZipFile(src);
			zipFile.extractAll(dest);
		} catch (ZipException e) {
			e.printStackTrace();
		}

	}*/

	/**
	 * Retrieves list of filenames in this directory. In this case .gz files to
	 * be uncompressed.
	 * 
	 * @return
	 * @param source
	 *            directory whose files are to be checked.
	 */
	private static List<String> getFileNames(String srcDir) {
		//
		List<String> fileNames = new ArrayList<String>();
		File dir = new File(srcDir);
		if (!dir.isDirectory()) {
			System.out.println(srcDir + " is not a directory!");
			return null;
		}
		File[] files = dir.listFiles();
		for (File file : files) {
			String fileName = file.getName();
			System.out.println(fileName);
			// only append ones with .gz extension
			if (fileName.matches("[^.]*\\.gz$")) {
				fileNames.add(fileName);
			}
		}
		return fileNames;
	}

	/**
	 * Unzips gz file. List of .gz fileNames, to be appended to the base path.
	 * 
	 * @return list of fileNames of extracted files.
	 */
	private static List<String> unzipGz(String srcBasePath, String destBasePath, List<String> fileNames) {
		if (fileNames.isEmpty())
			return null;

		List<String> extractedFileNames = new ArrayList<String>();
		// String src = "/Users/yihed/Documents/A/arxiv/0002/math0002077.gz";
		// String dest =
		// "/Users/yihed/Documents/A/arxiv/0002content/output.txt";
		// path to append the string names to
		//srcBasePath = "/Users/yihed/Documents/A/arxiv/0002_1/";
		//destBasePath = "/Users/yihed/Documents/A/arxiv/0002Content/";

		// length of buffer read in, optimal size?
		byte[] buf = new byte[1024];
		try {
			// fileNames is non-empty list.
			GZIPInputStream gzipInputStream = null;
			FileOutputStream gzipOutputStream = null;

			for (String fileName : fileNames) {
				//if not .gz file or a math file		
				if (!fileName.matches("math[^.]*\\.gz$")) {
					System.out.println("Not a .gz or math file!");
					continue;
				}
				String src = srcBasePath + fileName;
				String dest = destBasePath + fileName.replaceAll("([^\\.]*).gz$", "$1.txt");
				extractedFileNames.add(dest);

				gzipInputStream = new GZIPInputStream(new FileInputStream(src));

				gzipOutputStream = new FileOutputStream(dest);
				int l;
				while ((l = gzipInputStream.read(buf)) > 0) {
					// 0 is the offset, l is the length to read.
					gzipOutputStream.write(buf, 0, l);
				}
			}
			gzipInputStream.close();
			gzipOutputStream.close();

		} catch (IOException e) {
			e.printStackTrace();
		}
		return extractedFileNames;
	}

	public static void main(String[] args) throws FileNotFoundException, IOException {
		System.out.println(System.getProperty("user.dir"));
		// File file = new
		// File("/Users/yihed/Documents/A/arxiv/0002/math0002001.gz");
		// System.out.println(file.isFile());
		// String srcDir = "/Users/yihed/Documents/A/arxiv/0002content";
		// String srcBasePath = "/Users/yihed/Documents/A/arxiv/0002_1/";
		// String destBasePath = "/Users/yihed/Documents/A/arxiv/0002Content/";

		for (String srcBasePath : srcBasePaths) {
			String destBasePath = srcBasePath + "Content";
			//get all file names in the directory, eg directory "/0002/"
			List<String> fileNames = getFileNames(srcBasePath);
			System.out.println(fileNames);
			// list of files we just extracted. These should be .txt files.
			List<String> extractedFiles = unzipGz(srcBasePath, destBasePath, fileNames);
			// reads in those files and extract theorems
			for (String file : extractedFiles) {
				//File fileFrom = new File(file);
				// InputStream fileStream = new FileInputStream(file);
				FileReader fileReader = new FileReader(file);
				BufferedReader fileBufferedReader = new BufferedReader(fileReader);

				Path fileTo = Paths.get(file.replaceAll("([^.]*)(\\.txt)", "$1_thms$2"));
				
				List<String> thmList = ThmInput.readThm(fileBufferedReader, null, null);

				// write list of theorems to file
				Files.write(fileTo, thmList, Charset.forName("UTF-8"));
			}
		}
	}
}
