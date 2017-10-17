package thmp.utils;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

import javax.sql.DataSource;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.mysql.jdbc.jdbc2.optional.MysqlDataSource;

import thmp.search.SearchIntersection;

/**
 * Utility methods for database manipulations, for MySql database.
 * @author yihed
 */
public class DBUtils {
	
	public static final String DEFAULT_USER = "root";
	public static final String DEFAULT_PW = "wolfram";
	public static final String DEFAULT_SERVER = "localhost";
	public static final int DEFAULT_PORT = 3306;	
	public static final String DEFAULT_DB_NAME = "thmDB";

	private static final Connection DEFAULT_CONNECTION;
	private static final DataSource DEFAULT_DATASOURCE;
	
	private static final Logger logger = LogManager.getLogger(DBUtils.class);
	
	static {
		DataSource defaultDS = null;
		Connection defaultConn = null;
		
		try {
			defaultDS = getDataSource(DBUtils.DEFAULT_DB_NAME, DBUtils.DEFAULT_USER, DBUtils.DEFAULT_PW, 
					DBUtils.DEFAULT_SERVER, DBUtils.DEFAULT_PORT);
			defaultConn = defaultDS.getConnection();
		}catch(SQLException e) {
			String msg = "SQLException when trying to establish default connection " + e.getMessage();
			System.out.println(msg);
			logger.error(msg);
		}
		DEFAULT_DATASOURCE = defaultDS;
		DEFAULT_CONNECTION = defaultConn;
	}
	
	/**
	 * Conjunction or disjunction type.
	 */
	public enum ConjDisjType{
		CONJ("and"),
		DISJ("or");
		
		String typeStr;
		
		private ConjDisjType(String s) {
			this.typeStr = s;
		}
		
		/**
		 * Gets the type based on the colloquial name.
		 * @param called the colloquial name, defaults to "or" if string not reognized
		 * @return
		 */
		public static ConjDisjType getType(String called) {
			return CONJ.typeStr.equals(called) ? CONJ : DISJ;
		}
		
		/**
		 * @return "AND" or "OR" for CONJ or DISJ, resp.
		 */
		public String getDbName() {
			return typeStr.toUpperCase();
		}
	}
	
	public static class AuthorName{
		private String firstName;
		private String lastName;
		
		public AuthorName(String firstName_, String lastName_) {
			this.firstName = firstName_;
			this.lastName = lastName_;
		}
		
		/**
		 * First initial. Empty string if no first name in database.
		 * @return
		 */
		public String firstInitial() {
			if(this.firstName.length() == 0) {
				return "";
			}
			return this.firstName.substring(0, 0);
		}
		
		public String firstName() {
			return this.firstName;
		}
		
		public String lastName() {
			return this.lastName;
		}
	}
	
	/**
	 * Execute a mySql statement.
	 * @param stm
	 * @param ds
	 * @return number of rows changed, or -1 on failure.
	 */
	public static int executeSqlStatement(String stmStr, Connection conn) {
		//Connection conn = null;
		try {
			//conn = ds.getConnection();
			//e.g. CREATE TABLE authorTb (thmId INT(20), author VARCHAR(20), content VARCHAR(200))
			//or INSERT INTO authorTb (thmId, author, content) VALUES (1, 's', 'content')
			PreparedStatement stm = conn.prepareStatement(stmStr);
			int rs = stm.executeUpdate();
			System.out.println("restultSet "+rs);
			return rs;
			//stm = conn.prepareStatement("INSERT INTO authorTb (thmId, author, content)"
			//		+ "VALUES (1, 's', 'content')");
			
		} catch (SQLException e) {
			
			e.printStackTrace();
		}
		return -1;
	}
	
	/**
	 * Functions that use database to refine search results from other algorithms.
	 * 
	 * @deprecated see DBSearch class
	 */
	public static void searchByAuthor(List<String> authorList, List<Integer> thmsList) {
		
		//take intersection of results with sql query results
		
	}
	
	/**
	 * Obtain datasource with prescribed params, create DB if none exists.
	 * Default values are as follows:
	 * @param user "root"
	 * @param pw "wolfram"
	 * @param serverName "localhost"
	 * @param portNum "3306"
	 * @return datasource that one can obtain Connections from.
	 */
	public static DataSource getDataSource(String dbName, String user, String pw, String serverName, 
			int portNum) {
		MysqlDataSource ds = new MysqlDataSource();
		ds.setUser(user);
		//ds.setPassword("Lzft+utkk5q2");
		ds.setPassword(pw);
		ds.setServerName(serverName);
		ds.setPortNumber(portNum);
		ds.setCreateDatabaseIfNotExist(true);
		//System.out.println("ds.getCreateDatabaseIfNotExist() "+ds.getCreateDatabaseIfNotExist());
		
		ds.setDatabaseName(dbName);		
		return ds;
	}
	
	/**
	 * Gets the default DataSource connection.
	 * @return
	 * @throws SQLException 
	 */
	public static Connection getNewDefaultDSConnection() throws SQLException {
		return getDataSource(DBUtils.DEFAULT_DB_NAME, DBUtils.DEFAULT_USER, DBUtils.DEFAULT_PW, 
				DBUtils.DEFAULT_SERVER, DBUtils.DEFAULT_PORT).getConnection();
	}
	
	/**
	 * Gets the default DataSource connection .
	 * @return
	 * @throws SQLException 
	 */
	public static Connection getDefaultDSConnection() {
		//handle if default conn times out!!
		return DEFAULT_CONNECTION;
	}
	
	/**
	 * Gets the default DataSource.
	 * @return
	 * @throws SQLException 
	 */
	public static DataSource getDefaultDS() {
		//handle if default conn times out!!
		return DEFAULT_DATASOURCE;
	}
	
	private static void createDatabase() {
		MysqlDataSource ds = new MysqlDataSource();
		ds.setUser("root");
		//ds.setPassword("Lzft+utkk5q2");
		ds.setPassword("wolfram");
		ds.setServerName("localhost");
		ds.setPortNumber(3306);
		ds.setCreateDatabaseIfNotExist(true);
		System.out.println("ds.getCreateDatabaseIfNotExist() "+ds.getCreateDatabaseIfNotExist());
		
		ds.setDatabaseName("thmDB");
		Connection conn = null;
		try {
			conn = ds.getConnection();
			PreparedStatement stm = conn.prepareStatement("CREATE TABLE authorTb (thmId INT(20),"
					+ "author VARCHAR(20), content VARCHAR(200))");
			int rs = stm.executeUpdate();
			System.out.println("restultSet "+rs);
			stm = conn.prepareStatement("INSERT INTO authorTb (thmId, author, content)"
					+ "VALUES (1, 's', 'content')");
			rs = stm.executeUpdate();
			System.out.println("restultSet "+rs);
		} catch (SQLException e) {
			
			e.printStackTrace();
		}
		
	}
	
}