package sinhaladictionarytools.lib.table;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Set;
import javax.swing.table.AbstractTableModel;

/**
 *
 * @author buddhika
 */
public class TableModel extends AbstractTableModel{

    private String [] keys;
    private HashMap<String, Integer> hashmap;
    
    /***
     *
     * @param hashmap the hashmap which the table model should be based on
     */
    
    public TableModel(final HashMap<String, Integer> hashmap) {
        super();
        this.hashmap = hashmap;
        this.keys = hashmap.keySet().toArray(new String[0]);
    }


    /**
     *
     * @return the row count
     */
    public int getColumnCount() {
            return 2;
    }

    /**
     *
     * @return the total row count in the table
     */
    public int getRowCount() {
            return hashmap.size();
    }

    /**
     * return the column title
     *
     * @param column the column index
     * @return
     */
    public String getColumnName(int column) {
            if (column == 0) {
                    return "Word";
            } else {
                    return "Frequency";
            }
    }

    /**
     * Return the value at a given row, column index
     *
     * @param rowIndex the row index of the table
     * @param columnIndex the column index of the table
     * @return the value at the given table row,column point
     */
    public Object getValueAt(int rowIndex, int columnIndex) {
            if (columnIndex == 0) {                    
                    return keys[rowIndex];
            } else {
                    return hashmap.get(keys[rowIndex]);
            }
    }

    /**
     * Add a key to the table and calculate the value of the key
     *
     * @param key
     */
    public void addRow(String key){

        int val = hashmap.containsKey(key) ? hashmap.get(key) + 1 : 1;
        hashmap.put(key, val);
        keys = hashmap.keySet().toArray(new String[0]);
        fireTableDataChanged();
    }

    /**
     * Add an external table model to the current model
     *
     * @param model the table model to add to the current model
     */
    public void addRows(TableModel model){

        Iterator<String> it = model.getHashMap().keySet().iterator();

        while (it.hasNext()){

            String key = it.next();
            int val = hashmap.containsKey(key) ? hashmap.get(key) + 1 : 1;
            hashmap.put(key, val);                        
        }

        keys = hashmap.keySet().toArray(new String[0]);
        fireTableDataChanged();
    }

    /**
     *
     * @param model
     */
    public void addRows(Set<String> set){

        Iterator<String> it = set.iterator();

        while (it.hasNext()){

            String key = it.next();
            int val = hashmap.containsKey(key) ? hashmap.get(key) + 1 : 1;
            hashmap.put(key, val);
        }

        keys = hashmap.keySet().toArray(new String[0]);
        fireTableDataChanged();
    }

    /**
     * Remove a given row set
     *
     * @param rows set of row indexes to be removed
     */
    public void removeRows(int[] rows){
        
        for (int i = rows.length; i > 0; i--){            
            this.removeRow(rows[i-1]);
        }        
    }


    /**
     * Remove a given row
     *
     * @param i the row index to be removed
     */
    public void removeRow(int i){
        hashmap.remove(keys[i]);
        keys = hashmap.keySet().toArray(new String[0]);
        fireTableRowsDeleted(i, i);
    }

    /**
     * Remove a given row
     *
     * @param i the row index to be removed
     * Note: You need to perform fireTableDataChanged() after an operation is over
     *
     */
    public void removeRow(String key){

        if (hashmap.containsKey(key)){
            hashmap.remove(key);
            keys = hashmap.keySet().toArray(new String[0]);
        }
        
    }

    /**
     *
     * @return the list of keys in printable format
     */

    @Override
    public String toString(){

        Iterator<String> keys = hashmap.keySet().iterator();
        String output = "";

        while (keys.hasNext()){
            output += keys.next() + System.getProperty("line.separator") ;
        }

        return output;
    }

    public HashMap<String, Integer> getHashMap(){
        return this.hashmap;
    }

}
