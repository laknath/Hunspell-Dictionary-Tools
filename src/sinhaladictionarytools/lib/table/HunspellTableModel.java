/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package sinhaladictionarytools.lib.table;

import java.util.HashMap;
import java.util.Vector;
import javax.swing.table.AbstractTableModel;

/**
 *
 * @author buddhika
 */
public class HunspellTableModel extends AbstractTableModel {

    Vector<String[]> model;
    char index;

    public HunspellTableModel(HashMap<Character, Vector<String[]>> affRules, char index) {
        this.model = affRules.get(index);
        this.index = index;
    }

    /**
     * return the column title
     *
     * @param column the column index
     * @return
     */
    @Override
    public String getColumnName(int column) {
        if (column == 0) {
            return "Type";
        } else if (column == 1) {
            return "Class";
        } else if (column == 1) {
            return "Replacement";
        } else if (column == 1) {
            return "Append";
        } else {
            return "Pattern";
        }
    }

    public int getRowCount() {
        return model.size();
    }

    public int getColumnCount() {
        return 5;
    }

    public Object getValueAt(int rowIndex, int columnIndex) {
        return model.get(rowIndex)[columnIndex];
    }

}
