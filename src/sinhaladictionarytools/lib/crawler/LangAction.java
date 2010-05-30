/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package sinhaladictionarytools.lib.crawler;

import javax.swing.JLabel;
import websphinx.Action;
import websphinx.Crawler;
import websphinx.Page;

/**
 *
 * @author buddhika
 */
public class LangAction implements Action{

    public LangAction(JLabel label) {
        this.label = label;
    }


    public void connected(Crawler arg0) {
        //throw new UnsupportedOperationException("Not supported yet.");
    }

    public void disconnected(Crawler arg0) {
        //throw new UnsupportedOperationException("Not supported yet.");
    }

    public void visit(Page page) {
        label.setText("Processing url " + page.getURL().toString());
    }

    JLabel label = null;
}
