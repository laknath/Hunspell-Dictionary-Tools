package sinhaladictionarytools.lib.crawler;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Observer;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jconfig.Configuration;
import websphinx.CrawlEvent;
import websphinx.Crawler;
import websphinx.EventLog;
import websphinx.Link;
import websphinx.Page;
import websphinx.Text;
import sinhaladictionarytools.lib.Trie;


/**
 *
 * @author buddhika
 */
public class LangCrawler extends Crawler{    


    /**
    *
    * @return The crawler singleton object...well not exactly a singleton
    */
    public static LangCrawler getCrawler(){
      if (crawler == null){
        crawler = new LangCrawler();
      }else if (crawler.getState() == CrawlEvent.STOPPED){
        crawler = new LangCrawler();
      }

      return crawler;
    }

    /**
     * Constructor
     */
    private LangCrawler() {
        super();
        setLinkType(Crawler.HYPERLINKS);

    }

    /**
     * Configure the crawler
     *
     * @param config the configuration object
     */
    public void configure(Configuration conf){

        try {
            this.setName(conf.getProperty("name", "LangCrawler", "crawl"));
            this.setMaxDepth(conf.getIntProperty("maxDepth", 5, "crawl"));
            this.setMaxPages(conf.getIntProperty("maxPages", 500, "crawl"));
            this.setMaxWords(conf.getIntProperty("maxWords", 10000, "crawl"));
            this.setCharRange(conf.getCharProperty("charRangeMin", '\u0D80', "parsing"), 
                    conf.getCharProperty("charRangeMax", '\u0DFF', "parsing"),
                    conf.getProperty("charExceptions", "", "parsing"));
            this.setCharset(conf.getProperty("charset", "UTF-8", "parsing"));
            this.setMaxBadWords(conf.getDoubleProperty("maxBadWordPercentage", 0.5, "parsing"));
            this.setOmitWords(conf.getProperty("bannedWordsPath", "config/banned.txt", "parsing"));
            this.setUniqueWords(conf.getBooleanProperty("onlyUniqueWords", true, "crawl"));
            


            //add logging
            EventLog eventLog = new EventLog(logPath);
            eventLog.setOnlyNetworkEvents(false);
            this.addLinkListener(eventLog);
            this.addCrawlListener(eventLog);

            try {
                domain = new Link(conf.getProperty("baseDomain", "", "crawl")).getHost();

            } catch (MalformedURLException ex) {
                Logger.getLogger(LangCrawler.class.getName()).log(Level.SEVERE, null, ex);
            }
        } catch (IOException ex) {
            Logger.getLogger(LangCrawler.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    @Override
    public void visit(Page page) {
        parse(page);
    }

    @Override
    public boolean shouldVisit(Link l){

      if (getPagesVisited() < maxPages && parsedWords < maxWords){

        if (domain.isEmpty() || domain.equals(l.getHost())){
            return true;
        }

        return false;
      }else{
          stop();
          return false;
      }

    }

    /** parse a page
    *
    * @param page the page to parse
    */

    protected void parse(Page page) {
        URL url = page.getURL();
        int tmpWordsParsedInPage = 0;
        try {
            Text [] words = page.getWords();

            if (words == null){
                return;
            }

            for (int i=0; i < words.length; i++){
                String word = words[i].toText();

                //stop parsing the page if it doesn't have necessary ammount of words
                if (i == Math.round(words.length/2)){
                    if (parsedWords/words.length < getMaxBadWords()){
                        System.out.println("Successful word rate in the page("+(parsedWords/words.length)+") is below the required("+getMaxBadWords() +"). Opting out parsing the page");
                        return;
                    }
                }

                //strip unwanted characters
                if (this.getStripChars() != null){
                    word = stripChars(word);
                }

                //check unicode range
                if (isValidWord(word) && !ommitWords.contains(word)){

                    buf.append(word + System.getProperty("line.separator"));
                    parsedWords++;
                    tmpWordsParsedInPage++;
                    //System.out.println(word);

                    if ((parsedWords % maxWordsPerUpdate) == 0){

                        this.observerble.setCrawlerChanged();
                        this.observerble.notifyObservers(buf.toString());
                        
                        buf.delete(0, buf.length());
                    }
                }

                page.discardContent();
            }

        } catch (Exception e) {
          System.err.println("Could not download url:" + url.toString());
          e.printStackTrace();
        }
    }

    /**
     * Parse a string and check if it's within the given char range
     *
     * @param t input text
     * @return if the text is valid
     */
    public boolean isValidWord(String t){

        if (isUniqueWords() && getUniqueHashMap() != null && getUniqueHashMap().containsKey(t)){
            return false;
        }

        if ((maxCharRange | minCharRange) != 0){            

            for (int i=0; i < t.length(); i++){
                if (!exceptionalChars.contains(new Character(t.charAt(i))) && (maxCharRange > 0 && t.charAt(i) > maxCharRange) ||
                        (minCharRange > 0 && t.charAt(i) < minCharRange) ){
                        return false;
                }
            }
        }

        return true;
    }

    /**
     * Strip all chars from the word (a very bad implementation, hope to visit back later)
     *
     * @param word input word
     * @return modified word
     */
    public String stripChars(String word){

        StringBuffer strbuf = new StringBuffer(word);

        for (int i=0; i < stripChars.length; i++){

            for (int j = strbuf.length(); j > 0; j-- ){
                if (strbuf.charAt(j-1) == stripChars[i]){
                    strbuf.deleteCharAt(j-1);
                }
            }
        }

        return word;
    }

    /**
     * Add an observer to the crawler
     *
     * @param observer an observer to listen to events
     */
    public void addObserver(Observer observer){
        this.observerble.addObserver(observer);
    }

    /**
     * Remove an registered observer
     *
     * @param observer the observer object to be removed
     */
    public void removeObserver(Observer observer){
        this.observerble.deleteObserver(observer);
    }

    /**
     * Set the maximum pages to crawl
     *
     * @param maxPages the maximum number of pages to crawl
     */
    public void setMaxPages(int maxPages) {
        this.maxPages = maxPages;
    }

    /**
     * The maximum number of words to crawl
     *
     * @param maxWords maximum number of words
     */
    public void setMaxWords(int maxWords) {
        this.maxWords = maxWords;
    }

    /**
     * The valid unicode range of words to collect
     *
     * @param minCharRange minimum unicode character
     * @param maxCharRange maximum unicode character
     */
    public void setCharRange(char minCharRange, char maxCharRange, String chars) {
        this.minCharRange = minCharRange;
        this.maxCharRange = maxCharRange;

        char[] temp = chars.toCharArray();
        this.exceptionalChars.clear();

        for (char c: temp){
            this.exceptionalChars.add(c);
        }

        
    }

    /**
     * Set the character set of the crawler
     *
     * @param charset the caracter set
     */
    public void setCharset(String charset) {
        this.charset = charset;
    }

    /**
     * string a given character sequence
     *
     * @param stripChars a set of characters to be removed
     */
    public void setStripChars(char[] stripChars) {
        this.stripChars = stripChars;
    }

    /**
     * A converence method for setStripChars(char[] stripChars)
     *
     * @param charString
     */
    public void setStripChars(String charString) {
        this.setStripChars(charString.toCharArray());
    }

    /**
     * Set the maximum percentage of bad words
     *
     * @param maxBadWords the percentage of max bad words
     */
    public void setMaxBadWords(double maxBadWords) {
        this.maxBadWords = maxBadWords;
    }

    /**
     * Set the log file path
     *
     * @param logPath the file path of the log
     */
    public void setLogPath(String logPath) {
        this.logPath = logPath;
    }

    /**
     * Set words to be ommited from the crawler
     *
     * @param filepath the file path containing a list of words per each line
     */
    public void setOmitWords(String filepath){
        BufferedReader reader = null;
        
        if (maxCharRange > 256){
            this.ommitWords = new Trie(maxCharRange);
        }

        try {
            reader = new BufferedReader(new FileReader(filepath));
            String line = null;
            while ((line = reader.readLine()) != null) {
                String word = line.trim();
                if (!word.isEmpty()){
                    ommitWords.add(word);
                }
            }

        } catch (FileNotFoundException ex) {
            Logger.getLogger(LangCrawler.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IOException ex) {
            Logger.getLogger(LangCrawler.class.getName()).log(Level.SEVERE, null, ex);
        } finally {
            try {
                reader.close();
            } catch (IOException ex) {
                Logger.getLogger(LangCrawler.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

    /**
     * Set words to be ommited from the crawler
     *
     * @param words array of words
     */
    public void setOmitWords(String[] words){

        for (int i=0; i < words.length; i++){
            ommitWords.add(words[i]);
        }
    }


    public int[] getCharRange() {
        return new int[]{this.minCharRange, this.maxCharRange };
    }

    public int getMaxPages() {
        return maxPages;
    }

    public int getMaxWords() {
        return maxWords;
    }

    public String getCharset() {
        return charset;
    }

    public double getMaxBadWords() {
        return maxBadWords;
    }

    public char[] getStripChars() {
        return stripChars;
    }

    public String getLogPath() {
        return logPath;
    }

    public int getParsedWords() {
        return parsedWords;
    }

    public CrawlObservable getObserverble() {
        return observerble;
    }

    public LinkedHashMap<String, Integer> getUniqueHashMap() {
        return uniqueHashMap;
    }

    public void setUniqueHashMap(LinkedHashMap<String, Integer> uniqueHashMap) {
        this.uniqueHashMap = uniqueHashMap;
    }

    public boolean isUniqueWords() {
        return uniqueWords;
    }

    public void setUniqueWords(boolean uniqueWords) {
        this.uniqueWords = uniqueWords;
    }

    public int getMaxWordsPerUpdate() {
        return maxWordsPerUpdate;
    }

    public void setMaxWordsPerUpdate(int maxWordsPerUpdate) {
        this.maxWordsPerUpdate = maxWordsPerUpdate;
    }

    //refersh rate
    int maxWordsPerUpdate = 1000;

    //max pages to parse
    private int maxPages = 500;

    //max word to collect
    private int maxWords = 10000;

    //domain to crawl
    private String domain = "";

    //singleton object
    private static LangCrawler crawler = null;

    private int parsedWords = 0;

    private char maxCharRange, minCharRange;

    private String charset = "UTF-8";

    private char[] stripChars = null;

    private double maxBadWords = 0.5;    

    private String logPath = "logs/crawler.log";

    private StringBuffer buf = new StringBuffer(maxPages*2);

    private Trie ommitWords = new Trie();

    private CrawlObservable observerble = new CrawlObservable();

    private HashSet<Character> exceptionalChars = new HashSet<Character>();

    private boolean uniqueWords = false;

    private LinkedHashMap<String, Integer> uniqueHashMap = null;

}
