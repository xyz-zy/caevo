package timesieve;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.AnnotationPipeline;
import edu.stanford.nlp.pipeline.POSTaggerAnnotator;
import edu.stanford.nlp.pipeline.PTBTokenizerAnnotator;
import edu.stanford.nlp.pipeline.WordsToSentencesAnnotator;
import edu.stanford.nlp.time.SUTimeMain;
import edu.stanford.nlp.time.TimeAnnotations;
import edu.stanford.nlp.time.TimeAnnotator;
import edu.stanford.nlp.time.Options.RelativeHeuristicLevel;
import edu.stanford.nlp.util.CoreMap;


/**
 * This is just a wrapper around Stanford's SUTime tagger.
 * It includes some specific rules about fiscal quarters that fixes incorrect SUTime
 * performance on the finance genre.
 * 
 * @author chambers
 */
public class TimexClassifier {
  String _serializedGrammar = "/home/nchamber/code/resources/englishPCFG.ser.gz";
  String posTaggerData = "/home/nchamber/code/resources/english-left3words-distsim.tagger";
  private String _nerPath = "/home/nchamber/code/resources/all.3class.distsim.crf.ser.gz";

  AnnotationPipeline timexPipeline = null;
  InfoFile _infofile;
  

  public TimexClassifier(InfoFile info) {
    this._infofile = info;
  }
  

  /**
   * Mash the given list of CoreLabel objects into a single space-delimited string.
   */
  private String buildStringFromCoreLabels(List<CoreLabel> sentence, int starti, int endi) {
    StringBuffer buf = new StringBuffer();
    for( int xx = starti; xx < endi; xx++ ) {
      if( xx > starti ) buf.append(' ');
      buf.append(sentence.get(xx).getString(CoreAnnotations.OriginalTextAnnotation.class));
    }
    return buf.toString();
  }
  
  /**
   * Use the global .info file and destructively mark it up for time expressions.
   */
  public void markupTimex3() {
    for( String docname : _infofile.getFiles() ) {
      System.out.println("doc = " + docname);
      List<Sentence> sentences = _infofile.getSentences(docname);
      List<Timex> dcts = _infofile.getDocstamp(docname);
      if( dcts.size() > 1 ) {
        System.out.println("markupTimex3 dct size is " + dcts.size());
        System.exit(1);
      }
      String docDate = dcts.get(0).value();
      System.out.println("markupTimex3 docDate = " + docDate);
//      System.out.println(sentences.size() + " sentences.");
      int tid = 1;
      
      // Loop over each sentence and get TLinks.
      int sid = 0;
      for( Sentence sent : sentences ) {
//        System.out.println("TimexClassifier markupTimex3 tokens = " + sent.tokens());
        List<Timex> stanfordTimex = markupTimex3(sent.tokens(), tid, docDate);
        myRevisedTimex3(stanfordTimex, docDate);
        tid += stanfordTimex.size();
//        System.out.println("GOT " + stanfordTimex.size() + " new timexes.");
        _infofile.addTimexes(docname, sid, stanfordTimex);
        sid++;
      }
    }
  }
  
  private void myRevisedTimex3(List<Timex> timexes, String docDate) {
    if( docDate != null ) {
      docDate = docDate.replaceAll("-", "");
      if( docDate.length() == 8 ) {
        int year  = Integer.parseInt(docDate.substring(0, 4));
        int month = Integer.parseInt(docDate.substring(4,6));
       
        for( Timex timex : timexes ) {
          String text = timex.text().toLowerCase();
          
          // 1.2 F1 improvement on Tempeval-3 training, value attribute.
//          if( text.equals("a year ago") || text.contains("a year earlier") || text.contains("last year") ) {
          if( text.equals("a year ago") || text.equals("a year earlier") ) {
            int quarter = determineFiscalQuarter(year, month);
            if( quarter > 0 ) {
              String newvalue = (year-1) + "-Q" + quarter;
              timex.setValue(newvalue);
//              System.out.println("Changing timex " + timex.text() + " value: " + timex.value() + " to " + newvalue);
            }
          }
          
          // 0.2 F1 improvement with the following two if statements.
          // SUTime is overly specific on years. Strip off the month and day.
          if( text.equals("last year") )
            timex.setValue(timex.value().substring(0,4));
          if( text.contains("years ago") )
            timex.setValue(timex.value().substring(0,4));

          // 0.4 F1 improvement. This fixed ~8 errors, and didn't add any errors of its own.
          // SUTime sometimes does "PXM" when there is a clear quarter to choose.
          if( text.equals("the latest quarter") && timex.value().equals("PXM") ) {
            int quarter = determineFiscalQuarter(year, month);
            if( quarter > 0 ) {
              String newvalue = year + "-Q" + quarter;
              timex.setValue(newvalue);
//              System.out.println("Changing timex " + timex.text() + " value: " + timex.value() + " to " + newvalue);
            }          
          }
        }
      }
    }
  }
  
  private int determineFiscalQuarter(int year, int month) {
    int current = -1;
    if( month >= 10 && month <= 12 )
      current = 1;
    else if( month >= 1 && month <= 3 )
      current = 2;
    else if( month >= 4 && month <= 7 )
      current = 3;
    else if( month >= 7 && month <= 9 )
      current = 4;
    else return -1;
    
    // Subtract 2 quarters. News discusses two quarters ago when the reports come out.
    current = current - 2;
    if( current < 1 ) current = current + 4;
    return current;
  }
  
  /**
   * Given a single sentence (represented as a pre-tokenized list of HasWord objects), use stanford's 
   * SUTime to identify temporal entities and mark them up as TIMEX3 elements.
   * 
   * This function should preserve the given words, and result in the same number of words, just returning
   * Timex objects based on the given word indices. Timex objects start 1 indexed: the first word is at
   * position 1, not 0.
   * @param words A single sentence's words.
   * @param idcounter A number to use for an ID of the first timex, and increment from there.
   * @param docDate A string version of the document's creation time, e.g., "19980807"
   * @return A list of Timex objects with resolved time values.
   */
  private List<Timex> markupTimex3(List<CoreLabel> words, int idcounter, String docDate) {
    // Load the pipeline of annotations needed for Timex markup.
    if( timexPipeline == null )
      timexPipeline = getPipeline(true);
    
    // Extract TIMEX3 entities.
    Annotation annotation = SUTimeMain.textToAnnotation(timexPipeline, buildStringFromCoreLabels(words, 0, words.size()), docDate);
    
/*    // Print TIMEX3 results.
    List<CoreLabel> sutimeTokens = annotation.get(CoreAnnotations.TokensAnnotation.class);
    System.out.println("SUTime returned # tokens = " + sutimeTokens.size());
    if( sutimeTokens.size() != words.size() )
      System.out.println("ERROR: SUTime changes size of our tokens: " + sutimeTokens.size() + " vs our original " + words.size());
    for( int xx = 0; xx < words.size(); xx++ ) {
      String orig = ((CoreLabel)words.get(xx)).value();
      String timex = annotation.get(CoreAnnotations.TokensAnnotation.class).get(xx).value();
      if( !orig.equalsIgnoreCase(timex) )
        System.out.println("mismatch tokens: " + orig + " vs " + timex);
    }
    for( CoreMap label : annotation.get(TimeAnnotations.TimexAnnotations.class) ) {
      for( Class theclass : label.keySet() ) System.out.println("-->class=" + theclass);
      System.out.println("begin = " + label.get(CoreAnnotations.TokenBeginAnnotation.class));
      System.out.println("end   = " + label.get(CoreAnnotations.TokenEndAnnotation.class));
      System.out.println("--TIMEX-->" + label);      
      edu.stanford.nlp.time.Timex stanfordTimex = label.get(TimeAnnotations.TimexAnnotation.class);
      System.out.println("\ttimex = " + stanfordTimex);
      System.out.println("\txml   = " + stanfordTimex.toXmlElement());
      System.out.println("\txml value = " + stanfordTimex.toXmlElement().getAttribute("value"));
    }
    Document xmlDoc = SUTimeMain.annotationToXmlDocument(annotation);
    System.out.println("TIMEXED!"); System.out.println(XMLUtils.documentToString(xmlDoc));
*/
    
    // Create my Timex objects from Stanford's Timex objects.
    List<Timex> newtimexes = new ArrayList<Timex>();
    for( CoreMap label : annotation.get(TimeAnnotations.TimexAnnotations.class) ) {
      edu.stanford.nlp.time.Timex stanfordTimex = label.get(TimeAnnotations.TimexAnnotation.class);
      org.w3c.dom.Element stanfordElement = stanfordTimex.toXmlElement();
      Timex newtimex = new Timex();
      newtimex.setType(stanfordElement.getAttribute("type"));
      newtimex.setValue(stanfordElement.getAttribute("value"));
      newtimex.setTID("t" + idcounter++);
      newtimex.setText(stanfordElement.getTextContent());
      newtimex.setDocFunction(stanfordElement.getAttribute("functionInDocument"));
      // Stanford Timex starts at index 0 in the sentence, not index 1.
      newtimex.setSpan(label.get(CoreAnnotations.TokenBeginAnnotation.class)+1, label.get(CoreAnnotations.TokenEndAnnotation.class)+1);
      System.out.println("NEW STANFORD TIMEX: " + newtimex);
      newtimexes.add(newtimex);
    }
    return newtimexes;
  }
  
  /**
   * Adapted this from javanlp's SUTimeMain.java.
   * We could better integrate this with the parsing of the sentences, rather than starting from scratch again.
   * Performance gains would basically just avoid tokenizing and POS tagging.
   */
  private AnnotationPipeline getPipeline(boolean tokenize) {
    Properties props = new Properties();
    props.setProperty("sutime.includeRange", "true");
    props.setProperty("sutime.markTimeRanges", "true");
    props.setProperty("sutime.includeNested", "false");
    props.setProperty("sutime.restrictToTimex3", "true");
    props.setProperty("sutime.teRelHeurLevel", RelativeHeuristicLevel.BASIC.name());
    props.setProperty("sutime.rules", "edu/stanford/nlp/time/rules/defs.sutime.txt,edu/stanford/nlp/time/rules/english.sutime.txt,edu/stanford/nlp/time/rules/english.holidays.sutime.txt");
    System.setProperty("pos.model", posTaggerData);
    
    AnnotationPipeline pipeline = new AnnotationPipeline();
    if (tokenize) {
      pipeline.addAnnotator(new PTBTokenizerAnnotator(false));
      pipeline.addAnnotator(new WordsToSentencesAnnotator(false));
    }
    pipeline.addAnnotator(new POSTaggerAnnotator(false));
    pipeline.addAnnotator(new TimeAnnotator("sutime", props));

    return pipeline;
  }
  
  
  /**
   * @param args
   */
  public static void main(String[] args) {
    if( args.length < 1 ) 
      System.out.println("Tempeval3Parser [-output <path>] [-grammar <parser-grammar>] [-timex] -input <tempeval3-TBAQ-dir>");
    else {
//      TimexClassifier tp3 = new TimexClassifier();
    }
  }

}
