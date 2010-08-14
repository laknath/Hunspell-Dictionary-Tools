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
    private String[] columnNames = {"Option", "Strip", "Append", "Condition"};


    public HunspellTableModel(HashMap<Character, Vector<String[]>> affRules, char index) {

        if (affRules != null){
            this.model = affRules.get(index);
        }
        
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
        return columnNames[column];
    }

    public int getRowCount() {

        if (model != null){
            return model.size();
        }else{
            return 0;
        }
        
    }

    public int getColumnCount() {
        return 4;
    }

    public Object getValueAt(int rowIndex, int columnIndex) {

        //don't show the FLAG field
        if (columnIndex > 0){
            columnIndex++;
        }
        return model.get(rowIndex)[columnIndex];
    }

    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex){
        return true;
    }


    @Override
    public void setValueAt(Object value, int row, int col) {

        if (row < getRowCount() && (col+1) < getColumnCount()){
            String [] temp = model.get(row);

            if (col > 0){
                col++;
            }
            
            temp[col] = (String)value;
            model.set(row, temp);

            fireTableCellUpdated(row, col);
        }

    }


}
