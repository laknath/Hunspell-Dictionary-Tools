/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package sinhaladictionarytools.lib.crawler;

import java.util.Observable;
import java.util.Observer;
import javax.swing.JTextArea;

/**
 *
 * @author buddhika
 */
public class CrawlObserver implements Observer{

    JTextArea area = null;    

    public CrawlObserver(JTextArea area) {
        this.area = area;        
    }

    public void update(Observable o, Object arg) {
        
        area.append((String)arg);
        area.revalidate();
    }
}

class CrawlObservable extends Observable{

    public void setCrawlerChanged(){
        setChanged();
    }
    
}
