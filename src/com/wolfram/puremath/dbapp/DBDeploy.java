package com.wolfram.puremath.dbapp;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Utilities used for deploying DB.
 * @author yihed
 *
 */
public class DBDeploy {

	private static final Logger logger = LogManager.getLogger(DBDeploy.class);
	
	/**
	 * Truncates the table , populates it with data from supplied csv file.
	 * @param csvFilePath Path to CSV file containing data to populate table with.
	 * @throws SQLException 
	 */
	public static void populateAuthorDB(String csvFilePath, Connection conn) throws SQLException {
		
		PreparedStatement pstm;
		//drop keys, since otherwise insertion sort n^2 vs say quicksort n log(n), if retain old key and indexes, vs sort once at end
		
		//ALTER TABLE user_customer_permission DROP PRIMARY KEY;
		pstm = conn.prepareStatement("ALTER TABLE " + DBUtils.AUTHOR_TB_NAME + " DROP PRIMARY KEY;");
		pstm.executeUpdate();
		
		List<String> indexList = DBUtils.getAuthorTbIndexes();
		for(String index : indexList) {
			//including quotes result in syntax error. Do not setting string.
			pstm = conn.prepareStatement("DROP INDEX " + index + " ON " + DBUtils.AUTHOR_TB_NAME + ";");		
			pstm.executeUpdate();
		}		
		
		pstm = conn.prepareStatement("TRUNCATE " + DBUtils.AUTHOR_TB_NAME + ";");		
		pstm.executeUpdate();
				
		/*LOAD DATA INFILE  command
		 * mysql> LOAD DATA INFILE "/usr/share/tomcat/webapps/theoremSearchTest/src/thmp/data/metaDataNameDB.csv" 
		 * INTO TABLE authorTb COLUMNS TERMINATED BY "," OPTIONALLY ENCLOSED BY "'" ESCAPED BY "\\";
		 */		
		pstm = conn.prepareStatement("LOAD DATA INFILE ? INTO TABLE " + DBUtils.AUTHOR_TB_NAME 
				+ " COLUMNS TERMINATED BY ',' OPTIONALLY ENCLOSED BY ''' ESCAPED BY '\\';");
		pstm.setString(1, csvFilePath);
		
		int rowsCount = pstm.executeUpdate();
		logger.info("ResultSet from loading csv data: "+rowsCount);
		
		//add keys back
		//CREATE INDEX authorIndex ON authorTb (author);
		StringBuilder keySb = new StringBuilder(200);
		keySb.append("ALTER TABLE ").append(DBUtils.AUTHOR_TB_NAME)
		.append(" ADD PRIMARY KEY (")
		.append(DBUtils.AUTHOR_TB_THMID_COL).append(",")
		.append(DBUtils.AUTHOR_TB_FIRSTNAME_COL).append(",")
		.append(DBUtils.AUTHOR_TB_MIDDLENAME_COL).append(",")
		.append(DBUtils.AUTHOR_TB_LASTNAME_COL).append(");");
		pstm = conn.prepareStatement(keySb.toString());
		//pstm = conn.prepareStatement("ALTER TABLE " + DBUtils.AUTHOR_TB_NAME + " ADD PRIMARY KEY(id  )");
		pstm.executeUpdate();
		
		for(String index : indexList) {
			pstm = conn.prepareStatement("CREATE INDEX " + index + " ON " + DBUtils.AUTHOR_TB_NAME 
					+ " (" + DBUtils.AUTHOR_TB_NAME + ");");	
			pstm.executeUpdate();
		}
		
		
	}
	
	public static void main(String[] args) throws SQLException {
		
		if(args.length < 1) {
			System.out.println("Please enter the path to a csv file to populate the database.");
			return;
		}
		String filePath = args[0];
		Connection conn = DBUtils.getLocalConnection();
		populateAuthorDB(filePath, conn);
	}
	
}
