/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package sinhaladictionarytools.lib.table;

import java.awt.Color;
import java.awt.Component;
import java.util.HashMap;
import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.table.DefaultTableCellRenderer;
import sinhaladictionarytools.SinhalaDictionaryToolsView;

/**
 *
 * @author buddhika
 */
public class TableCellRenderer extends DefaultTableCellRenderer{

    Color ignoredWordColor, bannedWordColor;
    HashMap<String, Integer> wordlists;

    public TableCellRenderer(HashMap<String, Integer> wordlists, Color bannedWordColor, Color ignoredWordColor) {
        super();
        this.bannedWordColor = bannedWordColor;
        this.ignoredWordColor = ignoredWordColor;
        this.wordlists = wordlists;
        setOpaque(true);

    }

    
    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                               boolean hasFocus, int row, int column){
        // allow default preparation
        Component comp = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

        if (wordlists.containsKey(value) && !hasFocus){
            int type = wordlists.get(value);
            
            if ((type & SinhalaDictionaryToolsView.IGNORED_WORD) > 0){
                comp.setBackground(ignoredWordColor);
            }            
            if ((type & SinhalaDictionaryToolsView.BLOCKED_WORD) > 0){
                comp.setBackground(bannedWordColor);
            }
            if (type == (SinhalaDictionaryToolsView.BLOCKED_WORD|SinhalaDictionaryToolsView.IGNORED_WORD)){
                comp.setBackground(new Color(bannedWordColor.getRGB()|ignoredWordColor.getRGB()));
            }
        }else{
            comp.setBackground(Color.lightGray);
        }

        if (isSelected || hasFocus){
            comp = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
        }

        return comp;
    }



}
