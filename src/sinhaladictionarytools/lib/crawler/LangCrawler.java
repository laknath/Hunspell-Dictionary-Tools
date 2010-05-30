package sinhaladictionarytools.lib.crawler;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Observable;
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


/**
 *
 * @author buddhika
 */
public class LangCrawler extends Crawler{    

    int MAX_WORDS_PER_UPDATE = 200;

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
            this.setCharRange(conf.getIntProperty("charRangeMin", 10000, "parsing"), conf.getIntProperty("charRangeMax", 10000, "parsing"));
            this.setCharset(conf.getProperty("charset", "UTF-8", "parsing"));
            this.setMaxBadWords(conf.getDoubleProperty("maxBadWordPercentage", 0.5, "parsing"));

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
        try {
            Text [] words = page.getWords();

            for (int i=0; i < words.length; i++){
                Text t = words[i];

                buf.append(t.toText() + System.getProperty("line.separator"));                
                parsedWords++;

                if ((parsedWords % MAX_WORDS_PER_UPDATE) == 0){                    
                    
                    System.out.println(t.toText());

                    this.observerble.setCrawlerChanged();
                    this.observerble.notifyObservers(buf.toString());
                    buf = new StringBuffer();
                }

                page.discardContent();
            }

        } catch (Exception e) {
          System.err.println("Could not download url:" + url.toString());
          e.printStackTrace();
        }
    }

    public void addObserver(Observer observer){
        this.observerble.addObserver(observer);
    }

    public void removeObserver(Observer observer){
        this.observerble.deleteObserver(observer);
    }


    public void setMaxPages(int maxPages) {
        this.maxPages = maxPages;
    }

    public void setMaxWords(int maxWords) {
        this.maxWords = maxWords;
    }

    public void setCharRange(int minCharRange, int maxCharRange) {
        this.minCharRange = minCharRange;
        this.maxCharRange = maxCharRange;
    }

    public void setCharset(String charset) {
        this.charset = charset;
    }

    public void setStripChars(char[] stripChars) {
        this.stripChars = stripChars;
    }

    public void setStripChars(String charString) {
        this.setStripChars(charString.toCharArray());
    }


    public void setMaxBadWords(double maxBadWords) {
        this.maxBadWords = maxBadWords;
    }

    public void setLogPath(String logPath) {
        this.logPath = logPath;
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


    //max pages to parse
    private int maxPages = 500;

    //max word to collect
    private int maxWords = 10000;

    //domain to crawl
    private String domain = "";

    //singleton object
    private static LangCrawler crawler = null;

    private int parsedWords = 0;

    private int maxCharRange, minCharRange;

    private String charset = "UTF-8";

    private char[] stripChars = null;

    private double maxBadWords = 0.5;    

    private String logPath = "logs/crawler.log";

    private StringBuffer buf = new StringBuffer(maxPages*2);

    private CrawlObservable observerble = new CrawlObservable();
}