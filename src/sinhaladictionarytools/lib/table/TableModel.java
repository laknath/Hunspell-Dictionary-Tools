package sinhaladictionarytools.lib.table;

import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.table.AbstractTableModel;

/**
 *
 * @author buddhika
 */
public class TableModel extends AbstractTableModel{

    private String [] keys;
    private LinkedHashMap<String, Integer> hashmap;
    private String filter = "All";
    
    /***
     *
     * @param hashmap the hashmap which the table model should be based on
     */
    
    public TableModel(final LinkedHashMap<String, Integer> hashmap) {
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
        return keys.length;
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
     * 
     */
    public void removeRows(int[] rows){
        
        for (int i = rows.length; i > 0; i--){            
            hashmap.remove(keys[rows[i-1]]);
        }

        keys = hashmap.keySet().toArray(new String[0]);
        fireTableRowsDeleted(rows[0], rows[rows.length-1]);
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

        String output = "";

        for (int i=0; i < keys.length; i++){
            output += keys[i] + System.getProperty("line.separator") ;
        }


        return output;
    }

    /**
     *
     * @return the hashmap of the model
     */
    public LinkedHashMap<String, Integer> getHashMap(){
        return this.hashmap;
    }

    /**
     *
     * @return unique values from the model
     */
    public HashSet<Integer> getUniqueValues(){

        return new HashSet<Integer>(this.hashmap.values());

    }

    /**
     * 
     * @param filter the string code used for filtering table values
     */
    public synchronized void setFilter(String filter) {
        this.filter = filter;
        Iterator<String> it = hashmap.keySet().iterator();
        HashSet<String> keyset = new HashSet<String>();

        while (it.hasNext()){
            String key = it.next();
            if (filteroutElement(key)){                
                keyset.add(key);
            }
        }

        this.keys = keyset.toArray(new String[0]);

        fireTableDataChanged();
    }

    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex){
        return false;
    }

    /**
     * Fine the row of a given key
     *
     * @param word The word to search
     * @param offset The offset to start the search from (0 indexed)
     * @return the row offset of the word found or -1 if not found
     */
    public int findKey(String word, int offset){

        if (offset > -1){
            if (offset < keys.length){
                for (int i= offset; i < keys.length; i++){
                    if (keys[i].equals(word)){
                        return i;
                    }
                }
            }
        }
        

        return -1;
    }

    /**
     * Checks if the given key fits the filter
     *
     * @param key the key to be checked
     * @return true if the key fits/false if not
     */
    private boolean filteroutElement(String key){

        //System.out.println(filter);
        //System.out.println(key);

        if (key.startsWith(filter)){
            return true;
        }else if (filter.equals("All")){
            return true;
        }else if (filter.startsWith(">") || filter.startsWith("<") || filter.startsWith("=")){

            try{
                int filterKey = Integer.parseInt(filter.substring(1));

                if ((filter.startsWith(">") && hashmap.get(key).intValue() > filterKey)
                        || (filter.startsWith("<") && hashmap.get(key).intValue() < filterKey)
                        || (filter.startsWith("=") && hashmap.get(key).intValue() == filterKey)){
                    return true;
                }
            }catch(NumberFormatException nex){
                nex.printStackTrace();
                Logger.getLogger(TableModel.class.getName()).log(Level.SEVERE, null, nex);
            }
        }

        return false;
    }

}
