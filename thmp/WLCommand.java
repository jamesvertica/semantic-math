package thmp;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ListMultimap;

/**
 * WL command, containing the components that need to be hashed. 
 * For commands where multiple types could fit, e.g. ent or symb,
 * store those types in a List,  
 * 
 * Each type should have two components, 
 * Similar types: symb/ent/noun, verbs/vbs/verbphrases, 
 * 
 * e.g. Element of this group.
 * Use the trigger words to prepare list of potential matches,
 * To build a command, set the commandBits using the hashes of the commands. 
 * 
 * ********
 * Each command has bucket (nested class with (Linked)ListMultimap and counter) of commands,
 * every time the deque adds something, it gets added to all awaiting commands, when a command
 * gets fulfilled, it turns into a WL command and gets popped off the deque.
 * Commands should know when to substitute subcomands for components.
 * ListMultimap contains components, each component contains type (eg ent), and optional name (eg field)
 * eg (pre; of) <--ent_prep
 * Commands should call structMap rules instead of hardcoding patterns.
 * Have nested static Class for each command component, each component 
 * has type, name (which can be wildcard to indicate any name)
 * 
 * @author yihed
 *
 */

public class WLCommand {
	
	//bucket to keep track of components needed in this command
	private ListMultimap<WLCommandComponent, Struct> commandsMap;  // e.g. element
	//need to keep track how filled the bucket is
	//
	private Map<WLCommandComponent, Integer> commandsCountMap;
	//private String triggerWord; 
	//which WL expression to turn into using map components and how.
	//need to keep references to Structs in commandsMap
	// List of PosTerm with its position, {entsymb, 0}, {\[Element], -1}, {entsymb,2}
	//entsymb, 0, entsymb 1, use these to build grammar
	private List<PosTerm> posTermList;

	/**
	 * Index of trigger word in posTermList.
	 * (expand to list to include multiple trigger words?)
	 */
	private int triggerWordIndex;
	
	/**
	 * Track the number of components left in this WLCommand.
	 * Used to determine whether this WLCommand has all the commandComponents it needs yet.
	 * Command is satisfied if componentCounter is 0.
	 */
	private int componentCounter;
	
	/**
	 * PosTerm stores a part of speech term, and the position in commandsMap
	 * it occurs, to build a WLCommand, ie turn triggered phrases into WL commands.
	 * 
	 */
	public static class PosTerm{
		/**
		 * posTerm can be the terms that are used in structMap, 
		 * eg ent, symb, entsymb (either ent or symb works), pre, etc
		 */
		private WLCommandComponent commandComponent;
		
		/**
		 * Struct filling the current posTerm. Have to be careful eg for "of",
		 * which often is not a Struct itself, just part of a Struct
		 */
		private Struct posTermStruct;
		
		/**
		 * position of the relevant term inside a list in commandsMap.
		 * Ie the order it shows up in in the built-out command.
		 * -1 if it's a WL command, eg \[Element].
		 */
		private int positionInMap;
		
		/**
		 * Whether or not to include in the built String created by build()
		 */
		private boolean includeInBuiltString;		
		
		public PosTerm(WLCommandComponent commandComponent, int position, boolean includeInBuiltString){
			this.commandComponent = commandComponent;
			this.positionInMap = position;
			this.includeInBuiltString = includeInBuiltString;
		}
		
		@Override
		public String toString(){
			return "{" + this.commandComponent + ", " + this.positionInMap + "}";
		}
		
		public WLCommandComponent commandComponent(){
			return this.commandComponent;
		}
		
		/**
		 * @return the Struct corresponding to this posTerm
		 */
		public Struct posTermStruct(){
			return this.posTermStruct;
		}
		
		/**
		 * Set posTermStruct
		 * @param posTermStruct
		 */
		public void set_posTermStruct(Struct posTermStruct){
			this.posTermStruct = posTermStruct;
		}
		
		public int positionInMap(){
			return this.positionInMap;
		}
		
		public boolean includeInBuiltString(){
			return this.includeInBuiltString;
		}
	}
	
	//build list of commands?
	//private String ; // 
	// compiled bits to command
	//private CommandBits commandBits;
	
	//private constructor. Should be built using build()
	//
	private WLCommand(){
	}
	
	/**
	 * Static factory pattern.
	 * @param commands   Multimap of WLCommandComponent and the quantity needed for a WLCommand
	 * Also need posList
	 */
	public static WLCommand create(Map<WLCommandComponent, Integer> commandsCountMap, 
			List<PosTerm> posList, int componentCounter, int triggerWordIndex){
		//defensively copy?? Even though not external-facing
		WLCommand curCommand = new WLCommand();
		curCommand.commandsMap = ArrayListMultimap.create();	
		curCommand.commandsCountMap = commandsCountMap;		
		curCommand.posTermList = posList;
		curCommand.componentCounter = componentCounter;
		curCommand.triggerWordIndex = triggerWordIndex;
		return curCommand;
	}
	
	/**
	 * @param curCommand To be copied
	 * @return Deep copy of curCommand
	 */
	public static WLCommand copy(WLCommand curCommand){	
		WLCommand newCommand = new WLCommand();
		newCommand.commandsMap = ArrayListMultimap.create(curCommand.commandsMap) ;
		newCommand.commandsCountMap = new HashMap<WLCommandComponent, Integer>(curCommand.commandsCountMap) ; 
		//ImmutableMap.copyOf(curCommand.commandsCountMap);
		newCommand.posTermList = new ArrayList<PosTerm>(curCommand.posTermList);
		newCommand.componentCounter = curCommand.componentCounter;
		newCommand.triggerWordIndex = curCommand.triggerWordIndex;
		return newCommand;
	}
	
	/**
	 * Builds the WLCommand from commandsMap & posTermList after it's satisfied.
	 * Should be called after being satisfied. 
	 * @return String form of the resulting WLCommand
	 */
	public static String build(WLCommand curCommand){
		if(curCommand.componentCounter > 0) return "";
		ListMultimap<WLCommandComponent, Struct> commandsMap = curCommand.commandsMap;
		//counts should now be all 0
		Map<WLCommandComponent, Integer> commandsCountMap = curCommand.commandsCountMap;
		List<PosTerm> posTermList = curCommand.posTermList;
		//use StringBuilder!
		String commandString = "";
		
		for(PosTerm term : posTermList){
			
			if(!term.includeInBuiltString) continue;
			
			WLCommandComponent commandComponent = term.commandComponent;
			
			int positionInMap = term.positionInMap;
			
			String nextWord;			
			//-1 if WL command or auxilliary String
			if(positionInMap != -1){
				List<Struct> curCommandComponentList = commandsMap.get(commandComponent);
				if(positionInMap >= curCommandComponentList.size()){
					System.out.println("positionInMap >= list size. Should not happen!");
					continue;
				}
				//simple way to present it
				nextWord = curCommandComponentList.get(positionInMap).simpleToString();
			}else{
				nextWord = term.commandComponent.posTerm;
			}
			
			commandString += nextWord + " ";
		}
		return commandString;
	}
	
	/**
	 * Adds new Struct to commandsMap.
	 * @param curCommand	WLCommand we are adding PosTerm to
	 * @param newSrtuct 	Pointer to a Struct
	 * @return 				Whether the command is now satisfied
	 * 
	 * Add to commandsMap only if component is required as indicated by commandsCountMap.
	 * BUT: what if the Struct just added isn't the one needed? Keep adding.
	 * If the name could be several optional ones, eg "in" or "of", so use regex .match("in|of")
	 */
	public static boolean addComponent(WLCommand curCommand, Struct newStruct){
		//if key.name .matches()
		//be careful with type, could be conj_, all sorts of stuff
		String structType = newStruct.type();
		String structName = newStruct instanceof StructH ? newStruct.struct().get("name") : 
			newStruct.prev1() instanceof String ? (String)newStruct.prev1() : "";
			
		//need to iterate through the keys of countMap instead of just getting, 
		//because .hashcode won't find it for us
		
		for(Entry<WLCommandComponent, Integer> commandComponentEntry : curCommand.commandsCountMap.entrySet()){
			WLCommandComponent commandComponent = commandComponentEntry.getKey();
			int commandComponentCount = commandComponentEntry.getValue();
			
			if(structType.matches(commandComponent.posTerm) 
					&& commandComponentCount > 0 
					&& structName.matches(commandComponent.name)){
				//put commandComponent into commandsMap
				//if map doesn't contain newComponent, null !> 0		
				
				curCommand.commandsMap.put(commandComponent, newStruct);
				//here newComponent must have been in the original required set
				curCommand.commandsCountMap.put(commandComponent, commandComponentCount - 1);
				//use counter to track whether map is satisfied
				curCommand.componentCounter--;
				break;
			}
		}			
		
		//shouldn't be < 0!
		return curCommand.componentCounter < 1;
	}

	/**
	 * Removes the struct from its corresponding Component list in commandsMap.
	 * Typically used when a command has been satisfied, and its structs should be
	 * removed from other WLCommands in WLCommandList in ParseToWLTree that are partially built.
	 * @param curCommand	WLCommand to be removed from.
	 * @param curStruct		Struct to be removed.
	 * @return		Whether newStruct is found and removed.
	 */
	public static boolean removeComponent(WLCommand curCommand, 
			Struct curStruct){
		//need to iterate through the keys of countMap instead of just getting, 
		//because .hashcode won't find it for us		
		for(WLCommandComponent commandComponent : curCommand.commandsMap.keySet()){
			
			List<Struct> structSet = curCommand.commandsMap.get(commandComponent);
			
			Iterator<Struct> structSetIter = structSet.iterator();
			
			while(structSetIter.hasNext()){
				Struct curComponentStruct = structSetIter.next();
				//only reference equality need to be checked, as it's always the reference to the 
				//particular Struct that's added
				if(curComponentStruct == curStruct){
					structSetIter.remove();
					curCommand.componentCounter++;
					int commandComponentCount = curCommand.commandsCountMap.get(commandComponent);
					curCommand.commandsCountMap.put(commandComponent, commandComponentCount + 1);
					return true;
				}
			}			
		}			
		return false;
	}
	
	/**
	 * Retrieves list of Structs from commandsMap with key component.
	 * @param component		key to retrieve List with
	 * @param curCommand	is current command
	 * @return List in commandsMap
	 */
	public static List<Struct> getStructList(WLCommand curCommand, WLCommandComponent component){
		return curCommand.commandsMap.get(component);
	}
	
	/**
	 * @param curCommand
	 * @return posTermList of current command
	 */
	public static List<PosTerm> posTermList(WLCommand curCommand){
		return curCommand.posTermList;
	}
	
	/**
	 * 
	 * @return
	 */
	public static int triggerWordIndex(WLCommand curCommand){
		return curCommand.triggerWordIndex;
	}
	
	/**
	 * @return Is this command (commandsMap) satisfied. 
	 * 
	 */
	public static boolean isSatisfied(WLCommand curCommand){
		//shouldn't be < 0!
		return curCommand.componentCounter < 1;
	}
	
	@Override
	public String toString(){
		return this.commandsCountMap.toString();
	}
	
	/**
	 * 
	 */
	public static class WLCommandComponent{
		//types should be consistent with types in Map
		//eg ent, symb, pre, etc
		private String posTerm;
		//eg "of". Regex expression to be matched, eg .* to match anything
		private String name;
		
		public WLCommandComponent(String posTerm, String name){
			this.posTerm = posTerm;
			this.name = name;
		}
		
		public String posTerm(){
			return this.posTerm;
		}
		
		public String name(){
			return this.name;
		}
		
		@Override
		public String toString(){
			return "{" + this.posTerm + ", " + this.name + "}";
		}
		
		/**
		 * 
		 */
		@Override
		public boolean equals(Object obj){
			if(!(obj instanceof WLCommandComponent)) return false;
			
			WLCommandComponent other = (WLCommandComponent)obj;
			//here comparing posTerm and name as Strings rather than 
			//matching as regexes; This is ok since the only times we 
			//look up we have the original Pos terms
			//should not be able to access   .posTerm directly!
			if(!this.posTerm.equals(other.posTerm)) return false;
			if(!this.name.equals(other.name)) return false;
			
			return true;
		}
		
		@Override
		public int hashCode(){
			//this does not produce uniform distribution! Need to do some shifting
			int hashcode = this.posTerm.hashCode();
			hashcode += 19 * hashcode + this.name.hashCode();
			return hashcode;
		}
	}
	
}