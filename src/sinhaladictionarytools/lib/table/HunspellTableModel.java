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
    String index;
    private String[] columnNames = {"Option", "Strip", "Append", "Condition"};


    public HunspellTableModel(HashMap<String, Vector<String[]>> affRules, String index) {

        if (affRules != null){
            this.model = affRules.get(index);

            if (this.model == null){
                this.model = new Vector<String[]>();
                affRules.put(index, this.model);
            }

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

    public void removeRow(int row, HashMap<String, String[]> vocClasses){

        if (getRowCount() > 0 && row >= 0){
            String[] tmp = model.remove(row);

            if (getRowCount() == 0){
                vocClasses.remove(tmp[1]);
            }else{
                
                String[] tmpClass = vocClasses.get(tmp[1]);
                tmpClass[3] = Integer.toString(getRowCount());
                vocClasses.put(tmp[1], tmpClass);

            }
            

            fireTableRowsDeleted(row, row);
        }
        
    }

    public void addRow(String[] row, HashMap<String, String[]> vocClasses){

        if (row.length > 4){
            model.add(row);

            String[] tmpClass = vocClasses.get(row[1]);

            //not a new category
            if (tmpClass != null){
                tmpClass[3] = Integer.toString(getRowCount());
                vocClasses.put(row[1], tmpClass);

            }else{ // a new category and the first row
                vocClasses.put(row[1], new String[]{row[0], row[1], "Y", Integer.toString(getRowCount()) });
            }


            fireTableRowsDeleted(getRowCount(), getRowCount());
        }        
    }
    
}
