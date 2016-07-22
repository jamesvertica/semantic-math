package thmp;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Scanner;

import com.wolfram.alpha.parser.preparser.TexConverter;

/*
 * Test class for ThmP1
 */
public class ThmP1Test {
	
	static{
		Maps.buildMap();
		try {
			Maps.readLexicon();
			Maps.readFixedPhrases();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
	}

	//the char of F_p is p
		public static void main(String[] args) throws IOException{
			
			//ThmP1.buildMap();
			
			//ThmP1 p1 = new ThmP1();
			//String[] strAr = p1.preprocess("a disjoint or perfect field is a field".split(" "));
			//String[] strAr = p1.preprocess("quadratic extension has degree 2".split(" "));
			//String[] strAr = p1.preprocess("finite field is field".split(" "));
			//String[] strAr = p1.preprocess("field F extend field F".split(" "));
			
			//String[] strAr = p1.preprocess("a field or ring is a ring".split(" "));
			//String[] strAr = p1.preprocess("let T be any linear transformation ".split(" "));
			//String[] strAr = "let f be a linear transformation between V and W ".split(" ");
			//String[] strAr = "a linear transformation between V and W ".split(" ");
			//String[] strAr2 = "f is an invertible matrix".split(" ");
			//String[] strAr = p1.preprocess("if a field is ring then ring is ring".split(" "));
			//String[] strAr = p1.preprocess("a basis of a vector space consists of a set of linearly independent vectors".split(" "));
			//String[] strAr = p1.preprocess("finitely many vectors are called linearly independent if their sum is zero".split(" "));
			//String[] strAr = p1.preprocess("elements in symmetric group are conjugate if they have the same cycle type".split(" "));
			String[] strAr; 
			strAr = "for all x x is a number".split(" ");
			strAr = "suppose f is measurable and finite on E, and E has finite measure".split(" ");
			strAr = "the number of conjugacy class of the symmetric group is equal to the number of partitions of n".split(" ");
			String st = "let H be a normal subgroup of the group G. G acts on H as automorphisms of H.";
			st = "conjugate elements and conjugate subgroups have the same order";
			st = "A is a group and is a subgroup";
			st = "let G be a group, conjugation by g is called a automorphism of G";
			st = "if p is an odd prime and n is an integer, then the automorphism group of the cyclic group of order p is cyclic";
			st = "let p be a prime and let V be an abelian group, with the property that b is c, then V is an n dimensional vector space over the finite field";
			st = "the automorphism group of the cyclic group of order 2 is isomorphic to Z";
			//st = "the number of conjugacy class of the symmetric group is equal to the number of partitions of n";
			//st = "let G be a group, then G is a group";
			st = "let G be a group and let p be a prime, a group of order that is a power of p is called a p group";
			st = "a group with order that is a power of p is called a p group. a subgroup of G that is a p group is called a p subgroup. p subgroup.";
			st = "a group with order that is a power of p is defined to be a p subgroup of G";
			st = "the p subgroups of G are denoted by Syl";
			st = "a subgroup of order a power of p is called a p subgroup, the number of p subgroup of G is 2"; //or "of the form p^k"
			st = "the number of p subgroup of G is 2";
			st = "subgroups of G exist";
			st = "there exists a finite semiring with order 11";
			st = "n is the index in G of the normalizer for any p subgroup";
			st = "let G be a group of order p, where p is a prime not dividing m";
			st = "Z for prime p are the only abelian simple groups";
			st = "Z/p for prime p are the only abelian simple groups";
			st = "the number of p groups of G is of the form kp";
			st = "let P be a p subgroup of G, the following are equivalent, "
					+ "P is the unique subgroup, P is normal in a group G, "
					+ "all subgroups generated by elements of p power order are p groups,"
					+ "if X is any subset of G such that x is a power of p for all x in X";
			st = "if X is any subset of G such that x is a power of p for all x in X, "
					+ "then X is a p group";
			st = "if the order of G is 60 and G has more than 1 p subgroup, then G is simple";
			st = "a nontrivial group is simple if it contains no nontrivial normal subgroups";
			//st = "";
			st = "if a group G is abelian, then it is nilpotent";
			st = "let p be a prime and let P be a group of order p, then P is nilpotent of nilpotence class at most d";
			st = "suppose that tex and tex are finite ring maps. then tex is finite";
			//String[] strAr = p1.preprocess("F is a extension over Q".split(" "));			
			st = "a  system tex of tex-modules over tex consists of a family of tex-modules tex indexed by tex and a family of tex-module maps tex such that for all tex tex";	
			st = "a field extends a field";
			//st = "given field of a field of a field";
			st = "A system tex of tex-modules over tex consists of a tex of tex-modules tex";
			st = "the colimit of the system tex is the quotient tex-module tex where tex is the tex-submodule generated by all elements tex where tex is the natural inclusion.";
			st = "a ring is field if and only if it is a field";
			//st = "A system tex of tex-modules over tex consists of a tex";
			//st = "A system tex over tex-modules";
			//st = "given a commutative diagram tex of abelian groups of family";
			//st = "given a commutative diagram of rows";
			//st = "given a commutative diagram ";
			//st = "tex is of  finite presentation if there exist integers tex and polynomials tex and an isomorphism of tex-algebras tex";
			st = "a ring is a field, if it is a field";
			st = "a system tex of tex-modules over tex consists of a family of tex-"
					+ "modules tex indexed by tex and a family of tex-"
					+ "module maps tex such that for all tex tex.";
			//st = "family of fields indexed by tex and a family of tex-module maps such that for all tex";
			st = "a  partially ordered set is a set tex together with a relation tex which is transitive  and reflexive.";
			st = "a  directed set tex is a partially ordered set tex such that tex is not empty and such that tex, there exists tex with tex";
			//st = "there exists tex with tex";
			//st = "a field that is a field extension";			
			//st = "a  partially ordered set is a set tex together with a relation tex which is transitive  and reflexive";
			//st = " a set tex together with a relation tex which is transitive  and reflexive";
			st = "Let tex be a ring. Let tex a multiplicative subset. Let tex, tex be tex-modules. Assume all the elements of tex act as automorphisms on tex. "
					+ "Then the canonical map tex induced by the localization map, is an isomorphism.";
			st = "Let tex be a ring. Let tex be a multiplicative subset. Let tex be an tex-module. Then tex where the partial ordering on tex is given by "
					+ "tex for some tex in which case the map tex is given by tex.";
			st = "let $(m_i, \\mu_{ij})$, $(n_i, \nu_{ij})$ be systems of $r$-modules over the same partially ordered set.";
			st = "systems of $f$-modules over the same partially ordered set.";
			st = "$f$-modules over set.";
			st = "for any three \\$r\\$-modules \\$m, n, p\\$, \\$\\$  \\$\\$";
			st = "let $r$ be a ring, let $m$ and $n$ be $r$-modules. ";
			st = "m is finitely presented";
			st = "an abelian group $n$ is called an  $$-bimodule} if it is both an $a$-module and a $b$-module, and the actions "
					+ "$a to end$ and $b to end$ are compatible in the sense that $b = a$ for all $a in a, bin b, xin n$. usually we denote it as $_an_b$.";
			st = "a system $$ of $r$-modules over $i$ consists of a family of $r$-modules ${m_i}_{i in i}$ indexed by $i$ and a family of $r$-module maps ${mu_{ij} : m_i to m_j}_{i leq j}$, such that for all $i leq j leq k$,";
			st = "a system $S$ of $r$-modules over $i$ consists of a family of $R$-modules ${m_i}_{i in i}$ indexed by $i$ and a family of $R$-module maps ${mu_{ij} : m_i to m_j}_{i leq j}$.";
			st = "an abelian group can be written as a direct sum of cyclic groups";
			st = "a finitely generated abelian group is isomorphic to a direct sum of cyclic groups";
			//st = "b is isomorphic to c";
			st = "functor is unique"; //try to parse "unique and field"
			st = "a ring called $ring, ,       ring$ ";
			st = "Let $j$ be a set. "; /////
			//st = "$s$ is a set"; /////
			//st = " For any $R$-multilinear mapping $f : M_1times ldots times M_r to P$ there exists a unique $R$-module homomorphism $f' : T to P$ such that $f'circ g = f$. Such a module $T$ is unique up to unique isomorphism."
			//		+ "We denote it $M_1 otimes_R ldots otimes_R M_r$ and we denote the universal multilinear map $(m_1, ldots, m_r) mapsto m_1 otimes otimes m_r$";
			//st = "field satisfying this property";
			st = "$(A, B)$-bimodule";
			st = "in the sense that F is a field";
			st = "there exists a pair consisting of $F, G$";
			st = "this function is $R$-linear";
			st = "in other words, F is field";
			st = "tensoring each term is perfect";
			st = "group is $R$-module if it is both F and B";
			st = "abelian group $N$ is called an  $(A, B)$-bimodule if it is both an $A$-module and a $B$-module";
			st = "$A, B$-module is perfect";
			st = "tensoring each term in the original right exact sequence preserves the exactness";
			st = "tensoring each term in the original right exact sequence preserves the exactness";
			st = "F and G is canonically isomorphic to H";
			st = "In other words, this is a field";
			st = "a field, a ring, and a group";
			st = "a field is said to be a ring";
			st = "for a multiplicative subset $S$ of $R$ we have a field";
			st = "for a multiplicative subset $S$ of $R$";
			st = "for subset S of R and for subset F of J";
			st = "group of F of F"; 
			st = "given a field , call it F";
			st = "Usually we call field F";
			//st = "for any $R$-linear mapping, there exists a map";
			st = "group of pure ideals";
			st = "then $R$ is a regular local ring";
			st = "if $R_1, R_3$ are rings";
			st = "this is a (perfect) field";
			//st = "this field is perfect and every field is good"; ///*****
			st = "$R$ is Noetherian and every R algebra is catenary.";
			//st = "A ring $R$ is said to be  universally catenary if $R$ is Noetherian and every $R$ algebra of finite type is catenary.";
			
			//st = "let $A : R$ be a ring";
			st = "A maximal ideal $I$ with $I$ proper";
			st = "ideal with $I$ proper";
			st = "Let $x_1, ldots, x_c in mathfrak m$ be elements";
			st = "Then $$ x_1, ldots, x_c text{ is a regular sequence }Leftrightarrow dim(R(x_1, ldots, x_c)) = dim(R) - c $$ If so $x_1, ldots, x_c$ can be extended to a regular sequence of length $dim(R)$ and each quotient $R/(x_1, ldots, x_i)$ is a Cohen-Macaulay ring of dimension $dim(R) - i$";
			//st = "Then $$ x_1, ldots, x_c text{ is a regular sequence }Leftrightarrow dim(R(x_1, ldots, x_c)) = dim(R) - c $$ If so $x_1, ldots, x_c$ can be extended to a regular sequence of length $dim(R)$";
			//st = "a field is a ring and each quotient $R/(x_1, ldots, x_i)$ is a Cohen-Macaulay ring of dimension $dim(R) - i$";
			//st = "a field is a ring and a field is a ring and a field is a ring";
			//st = "a field is a ring and a field is a ring";
			st = "this can be written as F";
			st = "this can be done";
			st = "this gives a field";
			st = "this sentence is a run-on";
			st = "for all but finitely many ideals $I$ ";
			st = "Pure ideals are determined by their vanishing locus.";
			st = "for all but finitely many I";
			st = "if $R$ is a Noetherian ring and $M$ is a Cohen-Macaulay $R$-module with $text{Supp}(M) = Spec(R)$";
			st = "ring is topological if and only if ring is topological";
			st = "there exists a field with $F$ maximal and $K$ free"; //<-- revisit!
			st = "A Noetherian ring $R$ is called  Cohen-Macaulay if all its local rings are Cohen-Macaulay.";
			//st = "a field with extension $Q_2$ ";
			//st = "if $R$ is noetherian, $M$ is also noetherian";
			//st = "this is not coherent"; 
			//st = "for every $s in S$ we have a group";
			//st = "vanishing locus";
			//st = "fields are determined ";
			//st = "topological ideal";
			//st = "$I$ proper";
			//st = "Pure ideals are determined by their vanishing locus.";
			//st = "field are perfect and fields are rings";
			//st = "fields are fields and fields are rings";			
			//st = "assume there exists a module with $M = R$";
			//st = "this topological and perfect field";
			//st = "for subset S of A";
			//st = "for field of F"; //......
			//st = "for field";
			//st = "for field extension of G";
			st = "Let $x$ be an element of $R^n$";
			st = "Let G be a group and p be a prime";
			st = "let G be a field and R be a ring";
			//st = "there exists a field";
			//st = "both pairs are perfect";
			//st = "F is a field and $F = 4$";
			//Maps.readLexicon();
			st = "this is less than C";
			st = "inverse image of B in M";
			st = "for some submodule $MM$, where $MM$ is a submodule of N";
			st = "where $m$ is an element of $M$";
			//st = "if $m$ is an integer where $m$ is an element of $M$";
			st = "take the derivative of f";
			st = "derivative of f";
			//st = "let f be an element of a field";
			//st = "take the union of subsets of F";
			//st = "$m$ is an element of $M$";
			//st = "where $MM$ is a element of N";
			//st = "let S be the union of elements of a field"; //***
			//st = "let $a$ or $b$ be elements of a field";
			//st = "let $s \\subset S$ be an element of a set";
			//st = " B or C is true";
			st = "take the log of derivative of f"; //brackets missing!
			//st = "$f$ is element of $f$";
			//st = "there exists a universal property";
			//st = "f is an element of a set";
			//st = "given an element of a set $S$";
			//st = "f is a function with radius of convergence r and finitely many roots";
			//st = "f is a function with radius of convergence r";
			//st = "f is function with radius";
			//st = "$f$ is a set";
			//st = "the derivative is $f=s$";
			
			//System.out.println("from TexConverter: " + TexConverter.convert("let $m \\subset M$ be an element"));			
			
			strAr = ThmP1.preprocess(st);
			for(int i = 0; i < strAr.length; i++){
				//alternate commented out line to enable tex converter
				//ThmP1.parse(ThmP1.tokenize(TexConverter.convert(strAr[i].trim()) ));
				ThmP1.parse(ThmP1.tokenize(strAr[i].trim()));				
			}
			
			Scanner sc = new Scanner(new File("src/thmp/data/noTex4.txt"));
			
			while(sc.hasNextLine()){
				String nextLine = sc.nextLine();
				st = nextLine;
				if(st.matches("^\\s*$")) continue;
				
				//System.out.println(nextLine + "\n");
				//array of sentences separated by . or !
				strAr = ThmP1.preprocess(st);
				
				for(int i = 0; i < strAr.length; i++){					
					//ThmP1.parse(ThmP1.tokenize(strAr[i].trim())); //p1.parse(p1.tokenize(p1.preprocess(strAr2)));					
				}
				//System.out.println();
			}
			
			//ThmP1.writeUnknownWordsToFile();
			//ThmP1.writeParsedExprToFile();
			sc.close();
			
			//p1.parse(p1.tokenize(p1.preprocess("characteristic of Fp is p".split(" "))));
			
		}
		
}
