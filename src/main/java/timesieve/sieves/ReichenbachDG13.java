package timesieve.sieves;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import edu.stanford.nlp.trees.Tree;

import timesieve.InfoFile;
import timesieve.SieveDocument;
import timesieve.SieveDocuments;
import timesieve.SieveSentence;
import timesieve.TextEvent;
import timesieve.tlink.EventEventLink;
import timesieve.tlink.TLink;
import timesieve.util.TimeSieveProperties;
import timesieve.util.TreeOperator;

/**
 *SUMMARY:
 *This sieve labels pairs of verb events based on a mapping derived
 *from Reichanbach's theory of tense/aspect. The mapping is adapted from
 *Derczynski and Gaizauskas (D&G 2013).
 *
 *SameSentence/SameTense			p=0.65	17 of 26
 *SameOrAdjSent/SameTense			p=0.58	47 of 81
 *SameSentence/AnyTense			  p=0.57	47 of 82
 *SameOrAdjSent/AnyTense			p=0.53	142 of 270
 *
 *
 *
 *DETAILS:
 *
 * D&G2013 report the percentage of TLINKs in timebank for which the following
 * criteria hold:
 * 1) The TLINK links two events that are Verbs
 * 2) The disjunction of allen relations (each of which is equivalent to a 
 * Freksa (1992) semi-interval) associated with a given pair of the form
 * < <event1.tense, event1.aspect>, <event2.tense, event2.aspect> >
 * contains the interval relation annotated in the TLINK's relType field.
 * 3) The two events are in the same "temporal context"
 *
 *A temporal context is a list of criteria in terms of the properties of
 *two or more events based on which it is inferred that the events share the same
 *reference time.
 *
 *D&G2013 report results for 5 methods of determining temporal context based on two parameters:
 *
 *Method0:
 *baseline - any two events are assumed to be in the same temporal context
 *
 *Other methods based on these parameters
 *   Sentence window: 
 *   		value 1: Same sentence - the two events in the same sentence share their temporal context
 *   		value 2:Same/adjacent sentence - two events within one sentence of one another share their
 *	 Same tense - the two events have the same tense (the idea is that the have the same
 *                relative ordering of their R(eference) and S(peech) times.
 *      value 1: true
 *      value 2: false 
 * 
 * Results can be found in D&G table 6
 * The percentage of TLINKs for each setting for which the mapping yields a set of
 * possible TLINK relTypes (interval relations) that contains the  relType in the
 * gold standard is reported (1. including cases where the mapping is trivial; 2. excluding
 * such cases; a case is trivial if the mapping yields a disjunction of all possible relTypes)
 * 
 * In this implementation, the four non-baseline methods can be used for determining
 * whether events share their temporal context with the sentWindow and sameTense
 * parameters.
 * 
 * @author cassidy
 */
public class ReichenbachDG13 implements Sieve {
	public boolean debug = false;
	private int sentWindow = 0;
	private boolean sameTense = false;
	
	
	public List<TLink> annotate(SieveDocument doc, List<TLink> currentTLinks) {
	// PROPERTIES CODE
			try {
 				TimeSieveProperties.load();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

			// Get property values from the config file
			try {
				sentWindow = TimeSieveProperties.getInt("ReichenbachDG13.sentWindow", 0);
				sameTense = TimeSieveProperties.getBoolean("ReichenbachDG13.sameTense", false);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
		// proposed will hold all TLinks proposed by the sieve
		List<TLink> proposed = new ArrayList<TLink>();
		
		// get all events by sentence
		List<List<TextEvent>> allEvents = doc.getEventsBySentence();
		
		// we need all trees in order to get pos tags
		List<Tree> trees = doc.getAllParseTrees();
		
		// for each event, compare is with all events in range, in accordance
		// with sentWindow.
		int numSents = allEvents.size();
		// iterate over each sentence
		for (int sid = 0; sid < numSents; sid ++) {
			// iterate over events in the sent that corresponds with sid
			int numEvents = allEvents.get(sid).size();
			for (int i = 0; i < numEvents; i++) {
				// get the event in the list at index i
				TextEvent e1 = allEvents.get(sid).get(i);
				// iterate over remaining events in the sentence and try to
				// apply adapted D&G2013 mapping (via getLabel)
				for (int j = i + 1; j < numEvents; j++) {
					TextEvent e2 = allEvents.get(sid).get(j);
					TLink.Type label = getLabel(e1, e2, trees);
					// if the label is null, the mapping couldn't be applied.
					// otherwise, add (e1, e2, label) to proposed.
					if (label == null) continue;
					addPair(e1, e2, label, proposed, doc);
				}
				// iterate over other events in subsequent sentences in accordance
				// with sentWindow.
				// Note that if sentWindow == 0, the loop will never start since
				// sid2 <= sid + sentWindow will never be satisfied
				for (int sid2 = sid + 1; 
						sid2 <= sid + sentWindow && sid2 < numSents; sid2++) {
					// iterate over each event in a given window sentence
					int numEvents2 = allEvents.get(sid2).size();
					// compare e1 with all events in the sentence with id sid2
					for (int k = 0; k < numEvents2; k++) {
						// label (e1, e2, label) only if label is not null, as in above
						TextEvent e2 = allEvents.get(sid2).get(k);
						TLink.Type label = getLabel(e1, e2, trees);
						if (label == null) continue;
						addPair(e1, e2, label, proposed, doc);
					}
				}
			}
		}
				
		
		
		return proposed;
	}
	
	public void train(SieveDocuments trainingInfo) {
		// no training
	}
	
	// given a tree, return the pos tag for the element with TextEvent index "index"
	private String posTagFromTree(Tree tree, int index) {
		String pos = TreeOperator.indexToPOSTag(tree, index);
		return pos;
	}
 // add (e1, e2, label) to proposed list of TLINKs
	private void addPair(TextEvent e1, TextEvent e2, TLink.Type label, List<TLink> proposed, SieveDocument doc) {
		EventEventLink tlink = new EventEventLink(e1.getEiid(), e2.getEiid(), label);
		tlink.setDocument(doc);
		proposed.add(tlink);
	}

 // get the label indicated for (e1, e2) by the D&G mapping.
 // this method also applies filters that eliminate certain events and event pairs
	// from consideration.
	private TLink.Type getLabel(TextEvent e1, TextEvent e2, List<Tree> trees) {
		// get pos tags for e1 and e2
		String e1Pos = posTagFromTree(trees.get(e1.getSid()), e1.getIndex());
		String e2Pos = posTagFromTree(trees.get(e2.getSid()), e2.getIndex());
		// if e1 and e2 aren't both verbs, then label is null
		if (!e1Pos.startsWith("VB") || !e2Pos.startsWith("VB")) { 
			return null;
		}
		// if sameTense property is true then e1/e2 that don't share the same tense 
		// automatically are labeled null
		else if (sameTense && eventsShareTense(e1, e2)) {
			return null;
		}
		// if we've made it this far, apply the mapping to (e1, e2) using 
		else {
			return taToLabel(e1, e2);
		}
	}
	// check if events have the same tense. this is used for the setting in which two events (verbs) are assumed
	// not to share their reference time (ie be a part of the same "temporal context") only if they have
	// the same tense
	public boolean eventsShareTense(TextEvent e1, TextEvent e2) {return e1.getTense() == e2.getTense();}

	// apply mapping adapted from D&G2013
	public TLink.Type taToLabel(TextEvent e1, TextEvent e2){
		
		// First convert e1(2)Tense(Aspect) to their simplified forms 
		// as per D&G's mapping (via simplifyTense and simplifyAspect)
		TextEvent.Tense e1SimpTense = simplifyTense(e1.getTense());
		TextEvent.Aspect e1SimpAspect = simplifyAspect(e1.getAspect());
		TextEvent.Tense e2SimpTense = simplifyTense(e2.getTense());
		TextEvent.Aspect e2SimpAspect = simplifyAspect(e2.getAspect());
		
		// define the boolean variables that we need to check to apply mapping
		// each one specifies whether e1 or e2 has a given tense or aspect (after simplification)
		boolean e1Past = (e1SimpTense == TextEvent.Tense.PAST);
		boolean e2Past = (e2SimpTense == TextEvent.Tense.PAST);
		boolean e1Pres = (e1SimpTense == TextEvent.Tense.PRESENT);
		boolean e2Pres = (e2SimpTense == TextEvent.Tense.PRESENT);
		boolean e1Future = (e1SimpTense == TextEvent.Tense.FUTURE);
		boolean e2Future = (e2SimpTense == TextEvent.Tense.FUTURE);
		boolean e1Perf = (e1SimpAspect == TextEvent.Aspect.PERFECTIVE);
		boolean e1None = (e1SimpAspect == TextEvent.Aspect.NONE);
		boolean e2Perf = (e2SimpAspect == TextEvent.Aspect.PERFECTIVE);
		boolean e2None = (e2SimpAspect == TextEvent.Aspect.NONE);
		
		// this is the mapping, implmented as a long if block
		// see reichenbach_relationmapping.xls
		// note that we only consider cases where the result of applying
		// the mapping is an interval disjunction that translates to only 
		// one relation according to our task spec. 
		// see the table in the spreadsheet FreksaAllenUsInfo and mapping_FreksaAllenUs
		
		if (e1Past && e1None && e2Past && e2Perf) return TLink.Type.AFTER;
		else if (e1Past && e1None && e2Future && e2None) return TLink.Type.BEFORE;
		else if (e1Past && e1None && e2Future && e2Perf) return TLink.Type.BEFORE;
		//
		else if (e1Past && e1Perf && e2Past && e2None) return TLink.Type.BEFORE ; 
		else if (e1Past && e1Perf && e2Pres && e2None) return TLink.Type.BEFORE; 
		else if (e1Past && e1Perf && e2Pres && e2Perf) return TLink.Type.BEFORE; 
		else if (e1Past && e1Perf && e2Future && e2None) return TLink.Type.BEFORE; 
		else if (e1Past && e1Perf && e2Future && e2Perf) return TLink.Type.BEFORE;
		//
		else if (e1Pres && e1None && e2Past && e2Perf) return TLink.Type.AFTER;
		else if (e1Pres && e1None && e2Future && e2None) return TLink.Type.BEFORE;
		//
		else if (e1Pres && e1Perf && e2Past && e2Perf) return TLink.Type.AFTER;
		else if (e1Pres && e1Perf && e2Future && e2None) return TLink.Type.BEFORE;
		else if (e1Pres && e1Perf && e2Future && e2Perf) return TLink.Type.BEFORE;
		//
		else if (e1Future && e1None && e2Past && e2None) return TLink.Type.AFTER;
		else if (e1Future && e1None && e2Past && e2Perf) return TLink.Type.AFTER;
		else if (e1Future && e1None && e2Pres && e2None) return TLink.Type.AFTER;
		else if (e1Future && e1None && e2Pres && e2Perf) return TLink.Type.AFTER;
		//
		else if (e1Future && e1Perf && e2Past && e2None) return TLink.Type.AFTER;
		else if (e1Future && e1Perf && e2Past && e2Perf) return TLink.Type.AFTER;
		else if (e1Future && e1Perf && e2Pres && e2Perf) return TLink.Type.AFTER;
		else return null;
	
	}
	

	
	// Apply D&G's mapping to consolidate tense and aspect labels
	private TextEvent.Tense simplifyTense(TextEvent.Tense tense){
		// simplify past
		if (tense == TextEvent.Tense.PAST ||
			  tense == TextEvent.Tense.PASTPART) 
			{return TextEvent.Tense.PAST;}
		// simplify present
		else if (tense == TextEvent.Tense.PRESENT ||
						 tense == TextEvent.Tense.PRESPART) 
				{return TextEvent.Tense.PRESENT;}
		// future is trivially simplified
		else if (tense == TextEvent.Tense.FUTURE) 
				{return tense;}
		// no other tenses are considered.
		else return null; 
	}
		
	private TextEvent.Aspect simplifyAspect(TextEvent.Aspect aspect){
		// Return none or perfective based on mapping in D&G13 (else null)
		// Note that although their mapping includes progressive, we don't use
		// any tense/aspect profiles that include progressive because no 
		// tense/aspect profile that includes progressive aspect occurs in
		// any tense/aspect profile pair mapped to a single relation (in our
		// relation scheme)
		if (aspect.equals(TextEvent.Aspect.PERFECTIVE_PROGRESSIVE) ||
				aspect.equals(TextEvent.Aspect.PERFECTIVE))
			{return TextEvent.Aspect.PERFECTIVE;}
		else if (aspect.equals(TextEvent.Aspect.NONE)) 
			{return aspect;}
		else return null; 
	}
}