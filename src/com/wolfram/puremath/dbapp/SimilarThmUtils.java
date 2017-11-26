package com.wolfram.puremath.dbapp;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import thmp.parse.InitParseWithResources;
import thmp.utils.FileUtils;

/**
 * DB utils used for incorporating similar theorems indices
 * into the database.
 * 
 * ISO-8859-1
 * 
 * @author yihed
 *
 */
public class SimilarThmUtils {
	
	/**max length for index string. 
	 * 100 similar thms, need 100*20/8 = 250 bytes/ISO-8859-encoded chars.
	 * Add 1 for padding*/
	private static final int MAX_INDEX_STR_LEN;
	
	private static final int MAX_THM_INDEX_LIST_LEN;
	
	/**This changes as the number of total thms changes,
	 * 20 if num thms below 1 mil*/
	private static final int NUM_BITS_PER_INDEX = 20;
	
	private static final int NUM_BITS_PER_BYTE = 8;
	
	private static final Charset INDEX_STR_CHAR_SET = Charset.forName("ISO-8859-1");
	private static final boolean DEBUG = thmp.utils.FileUtils.isOSX() ? InitParseWithResources.isDEBUG() : false;
	
	static {
		MAX_THM_INDEX_LIST_LEN = 100;
		/**max length for index string. 
		 * 100 similar thms, need 100*20/8 = 250 bytes/ISO-8859-encoded chars.
		 * Add 2 for padding*/
		final int padding = 2;
		int strLen = MAX_THM_INDEX_LIST_LEN * NUM_BITS_PER_INDEX / NUM_BITS_PER_BYTE + padding;
		MAX_INDEX_STR_LEN = strLen;
	}
	
	/** 
	 * Total 770k thms, so each index needs 20 bits. 
	 */
	/**
	 * Convert string to index list.
	 * @param indexStr
	 * @return
	 */
	private static List<Integer> strToIndexList(String str) {
		
		//List<Byte> byteList = new ArrayList<Byte>();
		List<Integer> thmIndexList = new ArrayList<Integer>();
		
		int strLen = str.length();
		byte[] byteAr = new byte[strLen];
		
		for(int i = 0; i < strLen; i++) {			
			//byteList.add((byte)str.charAt(i));
			byteAr[i] = (byte)str.charAt(i);
		}
		
		if(DEBUG) System.out.println("strLen "+strLen + " "+str);
		int totalBitsLen = NUM_BITS_PER_BYTE * strLen;
		
		int numTotal = totalBitsLen/20;
		/* Note: must discard remainder in this division, since the bits padding was put in
		 * to reach a multiple of 8. 
		*/
		
		for(int i = 0; i < numTotal; i++) {
			thmIndexList.add(getIndex(byteAr, i));	
		}
		return thmIndexList;
	}
	
	//curTupleIndex is 0-based
	private static int getIndex(byte[] byteAr, int curTupleIndex) {
		
		List<Byte> zeroOneList = getZeroOneList(byteAr, curTupleIndex);
		int thmIndex = 0;
		if(DEBUG) {
			System.out.println("zeroOneList.size() "+zeroOneList.size());
			System.out.println("zeroOneList "+zeroOneList);
		}
		for(byte power : zeroOneList) {			
			thmIndex += Math.pow(2, power);							
		}
		return thmIndex;
	}
	
	//get next 20 bits, 
	private static List<Byte> getZeroOneList(byte[] byteAr, int curTupleIndex) {
		int inArrayShift = curTupleIndex * 20 / 8;
		if(DEBUG) System.out.println("inArrayShift "+inArrayShift);
		//the next 20-bit thmIndex starts at index inByteShift in current byte
		int inByteShift = curTupleIndex * 20 - 8 * inArrayShift;
		
		//byte[] zeroOneAr = new byte[20];
		List<Byte> zeroOneList = new ArrayList<Byte>();
		int curBitCounter = 0;
		byte curByte = byteAr[inArrayShift];
		int bitDivider = 8 - inByteShift;
		
		byte remainder = curByte;
		//!=0 instead of >=0, since 11111111 will be compared as -1 instead 255.
		while(remainder != 0) {
			byte power = (byte)(Math.log(remainder) / Math.log(2));			
			if(power < inByteShift) {
				break;
			}	
			zeroOneList.add((byte)(power - inByteShift));
			remainder -= Math.pow(2, power);
		}
		curBitCounter+=8;
		
		/*for( ; curBitCounter < bitDivider ; curBitCounter++) {			
			byte tempRem = (byte)(Math.log(curByte) / Math.log(2)); //HERE
			if(tempRem >= 0) {
				zeroOneAr[8 - curBitCounter] = 1;
				remainder = tempRem;
				if(tempRem == 0) {
					break;
				}
			}
		}*/
		outerFor: for(int i = 1; i < 4 && curBitCounter < 20; i++) {
			curByte = byteAr[inArrayShift+i];
			remainder = curByte;
			while(remainder != 0) {	
				byte power = (byte)(Math.log(remainder) / Math.log(2));
				byte curBitToSet = (byte)(power + bitDivider + 8*(i-1));
				if(curBitToSet >= 20) {
					break outerFor;
				}
				zeroOneList.add(curBitToSet);		
				remainder -= Math.pow(2, power);
			}	
			curBitCounter += 8;
		}
		return zeroOneList;
	}
	
	/**
	 * Encode list of indices to string.
	 * Total 770k thms, so each index needs log(2,770k) = 20 bits. For
	 * say top 100 similar thms, need 100*20/8 = 250 bytes/ISO-8859-encoded chars.
	 * @param thmIndexList
	 * @return
	 */
	private static String indexListToStr(List<Integer> thmIndexList) {
		
		int thmIndexListLen = thmIndexList.size();
		if(thmIndexListLen > MAX_THM_INDEX_LIST_LEN) {
			List<Integer> tempList = new ArrayList<Integer>();
			for(int i = 0; i < MAX_THM_INDEX_LIST_LEN; i++) {
				tempList.add(thmIndexList.get(i));
			}
			thmIndexListLen = MAX_THM_INDEX_LIST_LEN;
			thmIndexList = tempList;
		}
		//create byte array, then turn byte array into string
		
		byte[] indexByteAr = new byte[thmIndexListLen*20/8+1];
		
		for(int i = 0; i < thmIndexListLen; i++) {
						
			fillByteArray(indexByteAr, thmIndexList.get(i), i);
		}
		if(DEBUG) System.out.println("indexByteAr "+Arrays.toString(indexByteAr));
		return new String(indexByteAr, INDEX_STR_CHAR_SET);
	}
	
	/**
	 *add byte to array , along with shift 
	 * @param indexByteAr
	 * @param thmIndex
	 * @param thmCount the count of the current thm amongst the similar thms.
	 * thmCount is 0-based, i.e. starts counting at 0.
	 */
	private static void fillByteArray(byte[] indexByteAr, int thmIndex, int thmCount) {
		
		//higher-indexed bits represent higher powers of 2.
		byte[] zeroOneAr = new byte[NUM_BITS_PER_INDEX];
		int remainder = thmIndex;
		for(int p = NUM_BITS_PER_INDEX-1; p > -1; p--) {
			
			int tempRem = remainder - (int)Math.pow(2, p);
			if(tempRem >= 0) {
				zeroOneAr[p] = 1;
				remainder = tempRem;
				if(tempRem == 0) {
					break;
				}
			}			
		}
		int inArrayShift = thmCount * NUM_BITS_PER_INDEX / NUM_BITS_PER_BYTE;
		/* amount of shift in the byte partially filled from last index.
		 * 8 means*/
		int inByteShift = thmCount * NUM_BITS_PER_INDEX - inArrayShift * NUM_BITS_PER_BYTE;
		//if(inByteShift == 0) {
			//inArrayShift++; //double check!
		//}
		//current bit index between 0 and NUM_BITS_PER_INDEX
		int curBitIndex = 0;
		//at most 3 dividers fit within 20 bits, which span at most 4 bytes.
		//divider indices are starting indices of next byte.
		//int[] dividerIndexAr = new int[3];
		//dividerIndexAr[0] = NUM_BITS_PER_BYTE - inByteShift;
		int firstDivider = NUM_BITS_PER_BYTE - inByteShift;
		/*for(int i = 1; i < 4; i++) {
			dividerIndexAr[i] = dividerIndexAr[i-1] + 8;
		}*/
		byte curByte = indexByteAr[inArrayShift];
		
		for(int i = 0; i < 4; i++) {		
			for(; curBitIndex < firstDivider+8*i && curBitIndex < 20; curBitIndex++) {
				if(zeroOneAr[curBitIndex] == 1) {
					curByte |= (1 << (curBitIndex + inByteShift - 8*i));
				}
			}
			//System.out.println("curByte "+curByte);
			indexByteAr[inArrayShift+i] = curByte;
			if(curBitIndex >= 20) {
				break;
			}
			curByte = 0;
		}
		
	}	
	
	/**
	 * max length for index string used in db. 
	 * @return
	 */
	public static int maxSimilarThmListStrLen() {
		return MAX_INDEX_STR_LEN;
	}
	//use ISO-8859-1 encoding
	
	public static void main(String[] args) {
		
		List<Integer> thmIndexList = new ArrayList<Integer>();
		//thmIndexList.add(770000);
		//thmIndexList.add(400);
		//thmIndexList.add(4);
		for(int i= 0; i  < 10; i++) {
			thmIndexList.add(1000000);
			
		}
		String str = indexListToStr(thmIndexList);
		
		System.out.println("index str: "+str);
		List<Integer> returnedIndex = strToIndexList(str);
		
		System.out.println("returned index: "+returnedIndex);
	}
}
