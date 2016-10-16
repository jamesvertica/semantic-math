package thmp;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;

import thmp.ParseToWLTree.WLCommandWrapper;

/*
 * Struct to contain entities in sentence
 * to be parsed
 */

public abstract class Struct {
	//remove this 
	//public int WLCommandStrVisitedCount;

	/*
	public Struct(K struct, String type){
		this.struct = struct;
		this.type = type;
	}
	
	public Struct(Struct<K> prev1, Struct<K> prev2, String type){
		this.prev1 = prev1;
		this.prev2 = prev2;
		this.type = type;
	}
	*/

	/**
	 * 
	 * @return whether this struct is StructA or not
	 */
	public boolean isStructA(){
		return this.struct() == null;
	}
	
	public abstract Struct previousBuiltStruct();

	public abstract Struct posteriorBuiltStruct();
	
	public abstract void set_previousBuiltStruct(Struct previousBuiltStruct);
	
	public abstract void set_posteriorBuiltStruct(Struct posteriorBuiltStruct);
	
	public abstract Struct structToAppendCommandStr();

	public abstract void set_structToAppendCommandStr(Struct structToAppendCommandStr);
	
	public abstract int WLCommandStrVisitedCount();
	
	public abstract void clear_WLCommandStrVisitedCount();
	
	public abstract void set_structList(StructList structList);

	/**
	 * Set parent pointer of this struct
	 */
	public abstract void set_parentStruct(Struct parent);
	
	public abstract Struct parentStruct();

	/**
	 * Only meaningful for StructH.
	 * @param prev
	 */
	public abstract void set_possessivePrev(Struct prev);
	
	public abstract Struct possessivePrev();
	
	/**
	 * Sets the depth from root of the tree. Root has depth 0. *Not* intrinsic to the Struct,
	 * depends on the DFS path.
	 */
	public abstract void set_dfsDepth(int depth);
	
	public abstract int dfsDepth();
	
	//returns probability of relation in Rule
	public abstract double score();
	
	public abstract boolean has_child();
	
	//Simple toString to return the bare minimum to identify this Struct.
	//To be used in ParseToWLTree.
	public abstract String simpleToString(boolean includeType, WLCommand curCommand);
	
	public abstract String simpleToString2(boolean includeType, WLCommand curCommand);
	
	//public abstract void append_WLCommandStr(String WLCommandStr);

	public abstract void setContextVecEntry(int structParentIndex, int[] contextVec);
	
	/**
	 * Sets WLCommandStr to null.
	 */
	//public abstract void clear_WLCommandStr();
	
	//public abstract String WLCommandStr();
	
	public abstract WLCommandWrapper add_WLCommandWrapper(WLCommand newCommand);
	
	public abstract List<WLCommandWrapper> WLCommandWrapperList();
	
	public abstract void clear_WLCommandWrapperList();
	
	public abstract int numUnits();
	
	public abstract double maxDownPathScore();
	
	public abstract void set_maxDownPathScore(double pathScore);
	
	public abstract String contentStr();
	
	public abstract StructList StructList();
	
	public abstract void set_score(double score);
	
	//to be overridden
	public abstract void set_type(String type);
	
	public abstract Struct copy();
	
	//to be overridden
	public abstract String type();
	
	//to be overwritten in StructH
	public abstract ArrayList<Struct> children();

	//to be overwritten in StructH
	public abstract List<String> childRelation();
	
	//to be overwritten in StructH
	public abstract void add_child(Struct child, String relation);
	
	// to be overriden
	public abstract Map<String, String> struct();
	
	public abstract String toString();

	public abstract void set_prev1(String str);
	
	//not abstract, because not applicable to StructH
	public void set_prev2(String str){		
	}
	
	public abstract Object prev1();
	
	public abstract Object prev2();
		
	public abstract String present(String str);
	
	public abstract NodeType prev1NodeType();
	public abstract NodeType prev2NodeType();
	
	/**
	 * Possible types of prev1 and prev2
	 */
	enum NodeType{
		//NONE type indicates that this is none of the previous ones
		//used for instance for StructH.
		STR, STRUCTA, STRUCTH, NONE;
	}
}
