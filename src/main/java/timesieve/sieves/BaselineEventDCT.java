package timesieve.sieves;

import java.util.ArrayList;
import java.util.List;

import timesieve.SieveDocument;
import timesieve.SieveDocuments;
import timesieve.TextEvent;
import timesieve.Timex;
import timesieve.tlink.EventTimeLink;
import timesieve.tlink.TLink;

/**
 * Baseline Majority Class Sieve for event-DCT pairs that labels all as BEFORE.
 * 
 * @author chambers
 */
public class BaselineEventDCT implements Sieve {

	/**
	 * The main function. All sieves must have this.
	 */
	public List<TLink> annotate(SieveDocument doc, List<TLink> currentTLinks) {
		List<TLink> proposed = new ArrayList<TLink>();

		// Nothing if no DCT.
		if (doc.getDocstamp() == null || doc.getDocstamp().isEmpty())
			return proposed;
		
		Timex creationTime = doc.getDocstamp().get(0);
		List<TextEvent> allEvents = doc.getEvents();
		
		// Create BEFORE links for all events with the DCT.
		for( TextEvent event : allEvents )
			proposed.add(new EventTimeLink(event.getEiid(), creationTime.getTid(), TLink.Type.BEFORE));

		return proposed;
	}

	/**
	 * No training. Just rule-based.
	 */
	public void train(SieveDocuments trainingInfo) {
		// no training
	}

}