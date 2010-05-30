/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package sinhaladictionarytools.lib.crawler;

import javax.swing.JButton;
import websphinx.CrawlAdapter;
import websphinx.CrawlEvent;

/**
 *
 * @author buddhika
 */
public class LangCrawlerListener extends CrawlAdapter{

    JButton startButton, pauseButton;

    public LangCrawlerListener(JButton start, JButton pause) {
        startButton = start;
        pauseButton = pause;
    }

    @Override
    public void paused(CrawlEvent event) {
        super.paused(event);

        pauseButton.setText("Resume");
        startButton.setText("Start");
    }

    @Override
    public void stopped(CrawlEvent event) {
        super.stopped(event);

        startButton.setText("Start");
        pauseButton.setText("Pause");
        pauseButton.setEnabled(false);
    }

    @Override
    public void timedOut(CrawlEvent event) {
        super.timedOut(event);

        startButton.setText("Start");
        pauseButton.setText("Pause");
        pauseButton.setEnabled(false);
    }

    @Override
    public void started(CrawlEvent event) {
        super.started(event);

        startButton.setText("Stop");
        pauseButton.setText("Pause");
        pauseButton.setEnabled(true);
    }
    
}
