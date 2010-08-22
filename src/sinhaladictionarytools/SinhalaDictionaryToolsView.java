/*
 * SinhalaDictionaryToolsView.java
 */

package sinhaladictionarytools;

import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ItemEvent;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.event.TableModelEvent;
import org.jconfig.ConfigurationManagerException;
import org.jdesktop.application.Action;
import org.jdesktop.application.ResourceMap;
import org.jdesktop.application.SingleFrameApplication;
import org.jdesktop.application.FrameView;
import org.jdesktop.application.TaskMonitor;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import javax.swing.Timer;
import javax.swing.Icon;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Vector;
import javax.swing.DefaultCellEditor;
import javax.swing.DefaultListModel;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JOptionPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.UIManager;
import javax.swing.event.TableModelListener;
import javax.swing.plaf.FontUIResource;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;
import org.jconfig.Configuration;
import org.jconfig.ConfigurationManager;
import org.jconfig.handler.XMLFileHandler;
import sinhaladictionarytools.lib.FileOutput;
import sinhaladictionarytools.lib.JavaSystemCaller.Exec;
import sinhaladictionarytools.lib.JavaSystemCaller.StreamGobbler;
import sinhaladictionarytools.lib.crawler.CrawlObserver;
import sinhaladictionarytools.lib.crawler.LangAction;
import sinhaladictionarytools.lib.crawler.LangCrawler;
import sinhaladictionarytools.lib.crawler.LangCrawlerListener;
import sinhaladictionarytools.lib.table.HunspellTableCellEditor;
import sinhaladictionarytools.lib.table.HunspellTableModel;
import sinhaladictionarytools.lib.table.TableModel;
import websphinx.CrawlEvent;
import websphinx.DownloadParameters;
import websphinx.Link;
import com.stibocatalog.hunspell.*;
import java.awt.Color;
import java.util.List;
import sinhaladictionarytools.lib.table.TableCellRenderer;

/**
 * The application's main frame.
 */
public class SinhalaDictionaryToolsView extends FrameView {

    public SinhalaDictionaryToolsView(SingleFrameApplication app) {
        super(app);

        initComponents();
        getConfigs();
        setTableCellRenderers();

        // status bar initialization - message timeout, idle icon and busy animation, etc
        ResourceMap resourceMap = getResourceMap();
        int messageTimeout = resourceMap.getInteger("StatusBar.messageTimeout");
        messageTimer = new Timer(messageTimeout, new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                statusMessageLabel.setText("");
            }
        });
        messageTimer.setRepeats(false);
        int busyAnimationRate = resourceMap.getInteger("StatusBar.busyAnimationRate");
        for (int i = 0; i < busyIcons.length; i++) {
            busyIcons[i] = resourceMap.getIcon("StatusBar.busyIcons[" + i + "]");
        }
        busyIconTimer = new Timer(busyAnimationRate, new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                busyIconIndex = (busyIconIndex + 1) % busyIcons.length;
                statusAnimationLabel.setIcon(busyIcons[busyIconIndex]);
            }
        });
        idleIcon = resourceMap.getIcon("StatusBar.idleIcon");
        statusAnimationLabel.setIcon(idleIcon);

        // connecting action tasks to status bar via TaskMonitor
        TaskMonitor taskMonitor = new TaskMonitor(getApplication().getContext());
        taskMonitor.addPropertyChangeListener(new java.beans.PropertyChangeListener() {
            public void propertyChange(java.beans.PropertyChangeEvent evt) {
                String propertyName = evt.getPropertyName();
                if ("started".equals(propertyName)) {
                    if (!busyIconTimer.isRunning()) {
                        statusAnimationLabel.setIcon(busyIcons[0]);
                        busyIconIndex = 0;
                        busyIconTimer.start();
                    }

                } else if ("done".equals(propertyName)) {
                    busyIconTimer.stop();
                    statusAnimationLabel.setIcon(idleIcon);
                } else if ("message".equals(propertyName)) {
                    String text = (String)(evt.getNewValue());
                    statusMessageLabel.setText((text == null) ? "" : text);
                    messageTimer.restart();
                } else if ("progress".equals(propertyName)) {
                    int value = (Integer)(evt.getNewValue());
                }
            }
        });
    }

    private void setTableCellRenderers(){
        
        for (int i=0; i< jTable3.getColumnCount(); i++){
            jTable3.getColumnModel().getColumn(i).setCellRenderer(
                    new TableCellRenderer(wordlists, new Color(Integer.parseInt(conf.getProperty("bannedColour", "ff0000", "wordlists"), 16)),
                                                     new Color(Integer.parseInt(conf.getProperty("ignoredColour", "ff", "wordlists"), 16))));
        }

        for (int i=0; i< jTable4.getColumnCount(); i++){
            jTable4.getColumnModel().getColumn(i).setCellRenderer(
                    new TableCellRenderer(wordlists, new Color(Integer.parseInt(conf.getProperty("bannedColour", "ff0000", "wordlists"), 16)),
                                                     new Color(Integer.parseInt(conf.getProperty("ignoredColour", "ff", "wordlists"), 16))));
        }        
    }

   /**
     * Compresses a given wordlist into hunspell dic/aff file pair
     *
     * @param wordlist the input wordlist to be compressed into hunspell file format
     */
    protected void affixcompress(File f){
        String affixcompress = getHunspellCommand("affixcompress");
        String filePath = f.getAbsolutePath();
        String maxAffixRules = conf.getProperty("maxAffixRules", "-1");
        if (maxAffixRules.equals("-1")) {
            Exec.execute(affixcompress, null, filePath);
        } else {
            Exec.execute(affixcompress, null, filePath, maxAffixRules);
        }
        f.delete();
    }

    /**
     * generate all the words from a given dic and aff file
     *
     * @param dicFile Dictionary file
     * @param affFile Affix file
     * @param output The output file
     */
    protected String unmunch(String dicFile, String affFile, String output){

        String unmunch = getHunspellCommand("unmunch");

        try {

            if (dicFile.endsWith(".dic") && new File(affFile).exists() ){

                File tmpDicTFile = new File(output);
                tmpDicTFile.delete();
                tmpDicTFile.createNewFile();

                return Exec.execute(unmunch, tmpDicTFile, dicFile, affFile);

            }else{
                setStatusMessage("The file doesn't exist or not a dictionary file", true);
                JOptionPane.showMessageDialog(null, "The file doesn't exist or not a dictionary file");
            }

        } catch (Exception ex) {
            ex.printStackTrace();
            Logger.getLogger(SinhalaDictionaryToolsView.class.getName()).log(Level.SEVERE, null, ex);
        }

        return null;
    }

    /**
     * get hunspell command depending on the configurations
     *     
     */
    protected String getHunspellCommand(String command){

        if (conf.getIntProperty("installed", 0, "hunspell") == 0) {
            String sep = System.getProperty("file.separator");
            String base = conf.getProperty("path", "lib/hunspell-1.2.10", "hunspell");

            if (!base.endsWith(sep)){
                base = base.concat(sep);
            }

            command = base.concat("src").concat(sep).concat("tools").concat(sep).concat(command);
        }

        return command;
    }

    /**
     * load hashtable
     *
     * @param file the file to loaded into the hash table
     * @param table the table which the hashtable should be associated with
     */    
    protected void loadToHashTable(String file, JTable table){

        try {
            File wordfile = new File(file);
            LinkedHashMap<String, Integer> hashMap = new LinkedHashMap<String, Integer>(70000);

            final StreamGobbler outputGobbler = new StreamGobbler(new FileInputStream(wordfile), "OUTPUT", hashMap);
            outputGobbler.start();
            outputGobbler.join();

            table.setModel(new TableModel(hashMap));
            System.out.println(hashMap.size());

            
        } catch (FileNotFoundException ex) {
            setStatusMessage("File is not found", true);            
            Logger.getLogger(SinhalaDictionaryToolsView.class.getName()).log(Level.SEVERE, null, ex);
        } catch (InterruptedException ie){
            setStatusMessage("Process is interrupted");            
            Logger.getLogger(SinhalaDictionaryToolsView.class.getName()).log(Level.SEVERE, null, ie);
        }
    }

    /**
     * Moves to analyze tab
     *
     * @param filepath the file path to move to the analyser
     */
    protected void moveToAnalyze(String filepath){

        File file = new File(filepath);
        filepath = file.isAbsolute() ? filepath : file.getAbsolutePath();

        jTextField14.setText(filepath);
        jTabbedPane1.setSelectedIndex(2);
    }

    /**
     * clear status message
     */
    protected void clearStatusMessage(){
        statusMessageLabel.setText("");        
    }

    /**
     * set status messge
     * @param com the JComponent from which the tooltip will be take as a status message
     */
    protected void setStatusMessage(JComponent com){
        setStatusMessage(com.getToolTipText());
    }

    /**
     * set status message
     * @param s the string to be displayed
     * @param error is this an error ?
     */
    protected void setStatusMessage(String s, boolean error){
        setStatusMessage(s);

        if (error){
            System.err.println(s);
        }else{
            System.out.println(s);
        }
    }

    /**
     * set status message
     * @param s the string to be displayed
     */
    protected void setStatusMessage(String s){
        this.statusMessageLabel.setText(s);
    }


    /**
     * Save a file to selected output location
     *
     * @param tmpFile the tmpfile location
     */
    protected void saveToTmpFile(String tmpFile){
        int returnVal = fileChooser.showSaveDialog(this.getFrame());
        if (returnVal == JFileChooser.APPROVE_OPTION) {
            try {
                File file = fileChooser.getSelectedFile();
                
                if (file.getAbsolutePath().toLowerCase().endsWith(".dic")){
                    file = new File(file.getAbsolutePath().substring(0, file.getAbsolutePath().lastIndexOf('.')));
                }

                FileOutput.copyFile(new File(tmp.concat(tmpFile)), file);
                if (fileChooser.getFileFilter().getDescription().equals("Dic File")) {
                    affixcompress(file);
                }

                setStatusMessage(file.getAbsolutePath() + " was saved.");
            } catch (IOException ex) {
                Logger.getLogger(SinhalaDictionaryToolsView.class.getName()).log(Level.SEVERE, null, ex);
            }

        } else {
            setStatusMessage("File save cancelled by user.", true);
        }
    }

    /**
     * Merge rows from table1 to table 2
     * If anyrows from table1 selected they will be merged
     * If not whole table will be merged on confirmation
     *
     * @param table1
     * @param table2
     */
    protected void mergeTables(JTable table1, JTable table2){

        try{
            TableModel model1 = (TableModel)table1.getModel();
            TableModel model2 = (TableModel)table2.getModel();

            if (table1.getSelectedRowCount() == 0){
                if (JOptionPane.showConfirmDialog(this.getFrame(), "You haven't selected any rows. Do you really want to merge the whole table ?",
                        "Merge tables?", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION){
                    model2.addRows(model1);
                }
            }else{
                int[] rows = table1.getSelectedRows();
                HashSet<String> set = new HashSet<String>(rows.length);

                for (int i=0; i < rows.length; i++){
                    set.add((String)model1.getValueAt(rows[i], 0));
                }

                model2.addRows(set);
            }

        }catch(ClassCastException cce){
            setStatusMessage("A word list hasn't been loaded to the table", true);
            JOptionPane.showMessageDialog(null, "A word list hasn't been loaded to the table");
        }

    }


    /**
     * Diff table1 from table2 selected values
     *
     * @param table1
     * @param table2
     */
    protected void diffTables(JTable table1, JTable table2){

       try{
            TableModel model1 = (TableModel)table1.getModel();
            TableModel model2 = (TableModel)table2.getModel();

            int[] rows = table2.getSelectedRows();            

            for (int i=0; i < rows.length; i++){
                model1.removeRow((String)model2.getValueAt(rows[i], 0));
            }
            model1.fireTableDataChanged();

        }catch(ClassCastException cce){
            setStatusMessage("A word list hasn't been loaded to the table", true);
            JOptionPane.showMessageDialog(null, "A word list hasn't been loaded to the table");
        }

    }

    private void setFont(String font){
        Font f = new Font(font, Font.PLAIN, 12);
        jTextField1.setFont(f);
        jTextField2.setFont(f);
        jTextField3.setFont(f);
        jTextField4.setFont(f);
        jTextField5.setFont(f);
        jTextField6.setFont(f);
        jTextField7.setFont(f);
        jTextField8.setFont(f);
        jTextField9.setFont(f);
        jTextField10.setFont(f);
        jTextField11.setFont(f);
        jTextField12.setFont(f);
        jTextField13.setFont(f);
        jTextField14.setFont(f);
        jTextField15.setFont(f);
        jTextField16.setFont(f);
        jTextField17.setFont(f);
        jTextField18.setFont(f);
        jTextField19.setFont(f);

        jTable1.setFont(f);
        jTable3.setFont(f);
        jTable4.setFont(f);

        jTextArea1.setFont(f);
        jTextArea2.setFont(f);
        jTextArea3.setFont(f);

        jList1.setFont(f);
        jList2.setFont(f);
        jList3.setFont(f);

        UIManager.put("OptionPane.messageFont", new FontUIResource(f));
        UIManager.put("OptionPane.font", new FontUIResource(f));
        UIManager.put("TextField.font", new FontUIResource(f));
    }

    /**
     * Bind  a table model with a combo box
     *
     * @param table the table to listen to
     * @param box the combobox to be binded
     */
    protected void addTableListener(JTable table, final JComboBox box){

        isTableLoading = true;
        try{
            final TableModel model = (TableModel) table.getModel();

            model.addTableModelListener(new TableModelListener() {

                public void tableChanged(TableModelEvent e) {

                    int prevIndex = box.getSelectedIndex();
                    int prevCount = box.getItemCount();

                    Iterator<Integer> it = model.getUniqueValues().iterator();
                    box.removeAllItems();
                    box.addItem("All");

                    while (it.hasNext()){
                        int i = it.next();

                        box.addItem("<" + i);
                        box.addItem("=" + i);
                        box.addItem(">" + i);
                    }

                    if (box.getItemCount() == prevCount){
                        box.setSelectedIndex(prevIndex);
                    }
                }
            });

            model.getTableModelListeners()[0].tableChanged(null);
            
        }catch(ClassCastException cce){
            setStatusMessage("A word list hasn't been loaded to the table", true);
            JOptionPane.showMessageDialog(null, "A word list hasn't been loaded to the table");
        }
        isTableLoading = false;

    }

    @Action
    public void showAboutBox() {
        if (aboutBox == null) {
            JFrame mainFrame = SinhalaDictionaryToolsApp.getApplication().getMainFrame();
            aboutBox = new SinhalaDictionaryToolsAboutBox(mainFrame);
            aboutBox.setLocationRelativeTo(mainFrame);
        }
        SinhalaDictionaryToolsApp.getApplication().show(aboutBox);
    }

    /**
     * Read and set settings
     */
    protected void getConfigs() {

        ResourceMap resourceMap = getResourceMap();
        this.getFrame().setIconImage(resourceMap.getImageIcon("Application.icon").getImage());

        jComboBox4.removeAllItems();
        Font[] allFonts = GraphicsEnvironment.getLocalGraphicsEnvironment().getAllFonts();

        for (int i=0; i < allFonts.length; i++){
            jComboBox4.addItem(allFonts[i].getFontName());
        }
        setFont(conf.getProperty("font", "Arial", "general"));

        jComboBox4.setSelectedItem(conf.getProperty("font", "Arial", "general"));
        jTextField4.setText(conf.getProperty("maxAffixRules", "-1", "general"));
        jCheckBox1.setSelected(conf.getBooleanProperty("sortBeforeSave", true, "general"));
        jTextField21.setText(conf.getProperty("baseDic", "", "general"));


        jTextField5.setText(conf.getProperty("maxDepth", "-1", "crawl"));
        jTextField6.setText(conf.getProperty("maxPages", "-1", "crawl"));
        jTextField7.setText(conf.getProperty("maxWords", "10000", "crawl"));
        jTextField9.setText(conf.getProperty("baseDomain", "", "crawl"));
        jTextField17.setText(conf.getProperty("name", "LangCrawler", "crawl"));

        jTextField10.setText(conf.getProperty("bannedWordsPath", "", "wordlists"));
        jTextField15.setText(conf.getProperty("stripChars", "", "parsing"));
        jTextField16.setText(conf.getProperty("charset", "UTF-8", "parsing"));
        jTextField13.setText(conf.getProperty("maxBadWordPercentage", "0.5", "parsing"));
        jTextField11.setText(conf.getProperty("charRangeMin", "a", "parsing"));
        jTextField12.setText(conf.getProperty("charRangeMax", "Z", "parsing"));

        jComboBox5.removeAllItems();
        String[] categories = vconf.getPropertyNames("categories");
        for (String category: categories){
            jComboBox5.addItem(category);
        }

        this.getFrame().setResizable(false);

        //load vocabularies
        loadVocabularies();

        //load lists
        loadWordLists(conf.getProperty("bannedWordsPath", "config/banned.txt", "wordlists"), BLOCKED_WORD, true);
        loadWordLists(conf.getProperty("ignoredWordsPath", "config/ignored.txt", "wordlists"), IGNORED_WORD, false);
        fillWordlists();
    }

    /**
     * Save settings
     */
    protected void setConfigs(){
        try {

            XMLFileHandler handler = new XMLFileHandler("config/config.xml");

            conf.setProperty("font", jComboBox4.getSelectedItem().toString(), "general");        
            conf.setProperty("maxAffixRules", jTextField4.getText(), "general");
            conf.setBooleanProperty("sortBeforeSave", jCheckBox1.isSelected(), "general");
            conf.setProperty("baseDic", jTextField21.getText(), "general");
            setFont(conf.getProperty("font", "Arial", "general"));
            

            conf.setProperty("maxDepth", jTextField5.getText(), "crawl");
            conf.setProperty("maxPages", jTextField6.getText(), "crawl");
            conf.setProperty("maxWords", jTextField7.getText(), "crawl");
            conf.setProperty("baseDomain", jTextField9.getText(), "crawl");
            conf.setProperty("name", jTextField17.getText(), "crawl");

            conf.setProperty("bannedWordsPath", jTextField10.getText(), "wordlists");
            conf.setProperty("stripChars", jTextField15.getText(), "parsing");
            conf.setProperty("charset", jTextField16.getText(), "parsing");
            conf.setProperty("maxBadWordPercentage", jTextField13.getText(), "parsing");
            conf.setProperty("charRangeMin", jTextField11.getText(), "parsing");
            conf.setProperty("charRangeMax", jTextField12.getText(), "parsing");

            setTableCellRenderers();

            SinhalaDictionaryToolsApp.getConfiguration().save(handler, conf);

            saveWordList(vconf.getProperty("bannedWordsPath", "config/banned.txt", "wordlists"),
                    vconf.getProperty("ignoredWordsPath", "config/ignored.txt", "wordlists"), (BLOCKED_WORD | IGNORED_WORD) );
            
        } catch (ConfigurationManagerException ex) {
            setStatusMessage("Couldn't save settings.", true);
            Logger.getLogger(SinhalaDictionaryToolsView.class.getName()).log(Level.SEVERE, null, ex);
        }

    }

    /**
     * Load the global aff file to internal structures
     */
    private void loadVocabularies(){

        this.vocClasses = new HashMap<String, String[]>();
        this.vocRules = new HashMap<String, Vector<String[]>>();

        String vocPath = vconf.getProperty("affpath", "general");
        File globalAff = new File(vocPath);

        try{
            BufferedReader bf = new BufferedReader(new FileReader(globalAff));

            String line = null;
            while ((line = bf.readLine()) != null){
                String[] chunks = line.split(" ");

                //add classes to one table
                if (chunks.length == 4 && (chunks[0].equals("PFX") || chunks[0].endsWith("SFX"))){
                    vocClasses.put(chunks[1], chunks);

                //add individual affix rules 
                }else if (chunks.length > 4 && (chunks[0].equals("PFX") || chunks[0].endsWith("SFX"))){

                    String index = chunks[1];
                    Vector v = null;

                    if (vocRules.containsKey(index)){
                        v = vocRules.get(index);
                        v.add(chunks);
                    }else{
                        v = new Vector();
                        v.add(chunks);
                        vocRules.put(index, v);
                    }                   
                }
            }
        }catch (Exception ex){
            setStatusMessage("Error in loading vocabulary file", true);
            Logger.getLogger(SinhalaDictionaryToolsView.class.getName()).log(Level.SEVERE, null, ex);
        }

    }

    /**
     *
     * @param dicText a single dictionary word
     * @return the wordlist created
     * @throws IOException
     */
    protected String unmunchSingleWord(String dicText, String affText, String outputFile) throws IOException{

        setStatusMessage("Creating temp files");
        File dicFile = new File(tmp.concat(outputFile + ".dic"));
        File affFile = new File(tmp.concat(outputFile + ".aff"));
        FileWriter fw1 = new FileWriter(dicFile);
        FileWriter fw2 = new FileWriter(affFile);

        fw1.write("1" + System.getProperty("line.separator") + dicText);
        fw2.write(affText);

        fw1.close();
        fw2.close();

        setStatusMessage("Processing...");
        String wordlist = unmunch(dicFile.getPath(), affFile.getPath(), tmp.concat(outputFile));

        setStatusMessage("Wordlist generated");

        return wordlist;
    }

    /**
     *
     * @param dicText a single dictionary word
     * @return the wordlist created
     * @throws IOException
     */
    protected String generateAllAddWords(String dicText, File affFile, String outputFile) throws IOException{

        setStatusMessage("Creating temp files");
        File dicFile = new File(tmp.concat(outputFile + ".dic"));
        File tmpAffFile = new File(tmp.concat(outputFile + ".aff"));

        FileWriter fw1 = new FileWriter(dicFile);

        fw1.write("1" + System.getProperty("line.separator") + dicText);
        FileOutput.copyFile(affFile, tmpAffFile);

        fw1.close();        

        setStatusMessage("Processing...");
        String wordlist = unmunch(dicFile.getPath(), tmpAffFile.getPath(), tmp.concat(outputFile));

        setStatusMessage("Wordlist generated");

        return wordlist;
    }

    /**
     *
     * @param path the path of the file to read
     * @return
     */
    protected String readFile(String path){
        BufferedReader reader = null;
        String output = "";

        try {
            reader = new BufferedReader(new FileReader(path));
            String line = null;
            while ((line = reader.readLine()) != null) {
                output += line;
            }

        } catch (FileNotFoundException ex) {
            Logger.getLogger(SinhalaDictionaryToolsView.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IOException ex) {
            Logger.getLogger(SinhalaDictionaryToolsView.class.getName()).log(Level.SEVERE, null, ex);
        } finally {
            try {
                reader.close();
            } catch (IOException ex) {
                Logger.getLogger(SinhalaDictionaryToolsView.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        
        return output;
    }

    /**
     * Add a new word
     * 
     * @param tableId The ID of the table selected
     *
     */
     private void addNewWord(int tableId){

        //a nasty hack
        this.jCheckBox2.setVisible(false);
        TableModel model = null;

        try{
            if (tableId == 1){
                model = (TableModel)jTable4.getModel();
                this.jCheckBox2.setSelected(true);
            }else{
                model = (TableModel)jTable3.getModel();
                this.jCheckBox2.setSelected(false);
            }
            
            this.addWordsDialog.setVisible(true);
            
        }catch(ClassCastException cce){
            this.addWordsDialog.setVisible(false);
            setStatusMessage("A word list hasn't been loaded to the table", true);
            JOptionPane.showMessageDialog(null, "A word list hasn't been loaded to the table");
        }

     }

    /**
     * Popup menu helper
     *
     * @param tableId The Table ID of the Table
     * @param field The Textfield to which the selected text will be copied
     */
     private void popupHelper(int tableId, JTextField field){

        TableModel model = null;
        JTable table = null;

        if (tableId == 1){
            table = jTable4;
            model = (TableModel)jTable4.getModel();            
        }else{
            table = jTable3;
            model = (TableModel)jTable3.getModel();
        }

        String selectedWord = (String) model.getValueAt(table.getSelectedRow(), table.getSelectedColumn());
        field.setText(selectedWord);
     }

    /**
     *
     * Remove selected words
     *
     * @param tableId The ID of the table selected
     *
     **/
    private void removeWords(int tableId){

        TableModel model;
        try{
            if (tableId == 1){
                model = (TableModel)jTable4.getModel();
                model.removeRows(jTable4.getSelectedRows());
            }else{
                model = (TableModel)jTable3.getModel();
                model.removeRows(jTable3.getSelectedRows());
            }
            
        }catch(ClassCastException cce){
            setStatusMessage("A word list hasn't been loaded to the table", true);
            JOptionPane.showMessageDialog(null, "A word list hasn't been loaded to the table");
        }

    }

    /**
     *
     * Modify selected words
     *
     * @param tableId The ID of the table selected
     *
     **/
    private void modifyWords(int tableId){

        String oldWord, newWord;
        int selectedRow ;
        
        JTable table;
        TableModel model;
        
        try{
            if (tableId == 1){
                table = jTable4;
            }else{
                table = jTable3;
            }

            if (table.getSelectedRowCount() == 0){
                return;
            }

            model = (TableModel)table.getModel();
            selectedRow = table.getSelectedRow();
            oldWord = (String)table.getValueAt(selectedRow, 0);

            if ((newWord = JOptionPane.showInputDialog(this.getFrame(), "Change the word.", oldWord)) != null){                
                model.removeRow(selectedRow);
                model.addRow(newWord);
            }
        }catch(ClassCastException cce){
            setStatusMessage("A word list hasn't been loaded to the table", true);
            JOptionPane.showMessageDialog(null, "A word list hasn't been loaded to the table");
        }

    }

    /**
     * Load a word to the table
     *
     * @param tableId The ID of the table selected     
     */
    private void loadFile(int tableId){

        String s = jTextField14.getText();
        JTable table = null;
        JComboBox comboBox = null;

        if (!s.isEmpty()){

            if (tableId == 1){
                table = jTable4;
                comboBox = jComboBox1;                
            }else{
                table = jTable3;
                comboBox = jComboBox2;
            }
            
            setStatusMessage("Loading the wordlist");
            loadToHashTable(s, table);
            addTableListener(table, comboBox);
        }else{
            setStatusMessage("No file to load", true);
            JOptionPane.showMessageDialog(null, "No file to load");
        }
    }

    /**
     * Save table to an external file
     *
     * @param tableId The ID of the table selected
     */
    private void saveTable(int tableId){

        TableModel model;
        String tmpFile;

        try{
            if (tableId == 1){
                model = (TableModel)jTable4.getModel();
                tmpFile = "tmp3";
            }else{
                model = (TableModel)jTable3.getModel();
                tmpFile = "tmp4";
            }

            new FileOutput(new File(tmp.concat(tmpFile)), model,
                    conf.getBooleanProperty("sortBeforeSave", true, "general")).start();
            saveToTmpFile(tmpFile);
        }catch(ClassCastException cce){
            setStatusMessage("A word list hasn't been loaded to the table", true);
            JOptionPane.showMessageDialog(null, "A word list hasn't been loaded to the table");
        }
    }

    /**
     * Save settings to an external file
     *
     * @param filePath The file path to write
     * @param conf the configuration settings
     */
    private void saveSettings(String filePath, Configuration conf){
        XMLFileHandler handler = new XMLFileHandler(filePath);
        try {
            SinhalaDictionaryToolsApp.getConfiguration().save(handler, conf);
        } catch (ConfigurationManagerException ex) {
            Logger.getLogger(SinhalaDictionaryToolsView.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    /** This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        mainPanel = new javax.swing.JPanel();
        jTabbedPane1 = new javax.swing.JTabbedPane();
        jPanel1 = new javax.swing.JPanel();
        jPanel4 = new javax.swing.JPanel();
        jLabel1 = new javax.swing.JLabel();
        jTextField1 = new javax.swing.JTextField();
        jLabel2 = new javax.swing.JLabel();
        jButton2 = new javax.swing.JButton();
        jScrollPane1 = new javax.swing.JScrollPane();
        jTextArea1 = new javax.swing.JTextArea();
        jPanel5 = new javax.swing.JPanel();
        jLabel3 = new javax.swing.JLabel();
        jTextField3 = new javax.swing.JTextField();
        jButton3 = new javax.swing.JButton();
        jButton4 = new javax.swing.JButton();
        jButton1 = new javax.swing.JButton();
        jButton8 = new javax.swing.JButton();
        jScrollPane5 = new javax.swing.JScrollPane();
        jTextArea2 = new javax.swing.JTextArea();
        jLabel21 = new javax.swing.JLabel();
        jPanel2 = new javax.swing.JPanel();
        jPanel7 = new javax.swing.JPanel();
        jLabel8 = new javax.swing.JLabel();
        jTextField8 = new javax.swing.JTextField();
        jButton7 = new javax.swing.JButton();
        jButton9 = new javax.swing.JButton();
        jButton10 = new javax.swing.JButton();
        jButton11 = new javax.swing.JButton();
        jScrollPane2 = new javax.swing.JScrollPane();
        jTextArea3 = new javax.swing.JTextArea();
        jLabel10 = new javax.swing.JLabel();
        jPanel10 = new javax.swing.JPanel();
        jPanel11 = new javax.swing.JPanel();
        jTextField14 = new javax.swing.JTextField();
        jLabel13 = new javax.swing.JLabel();
        jButton5 = new javax.swing.JButton();
        jScrollPane6 = new javax.swing.JScrollPane();
        jTable3 = new javax.swing.JTable();
        jScrollPane7 = new javax.swing.JScrollPane();
        jTable4 = new javax.swing.JTable();
        jPanel13 = new javax.swing.JPanel();
        jButton50 = new javax.swing.JButton();
        jLabel17 = new javax.swing.JLabel();
        jButton26 = new javax.swing.JButton();
        jComboBox2 = new javax.swing.JComboBox();
        jButton28 = new javax.swing.JButton();
        jButton27 = new javax.swing.JButton();
        jPanel14 = new javax.swing.JPanel();
        jButton20 = new javax.swing.JButton();
        jButton24 = new javax.swing.JButton();
        jButton18 = new javax.swing.JButton();
        jComboBox1 = new javax.swing.JComboBox();
        jButton47 = new javax.swing.JButton();
        jLabel16 = new javax.swing.JLabel();
        jPanel15 = new javax.swing.JPanel();
        jButton30 = new javax.swing.JButton();
        jButton29 = new javax.swing.JButton();
        jButton22 = new javax.swing.JButton();
        jButton21 = new javax.swing.JButton();
        jLabel19 = new javax.swing.JLabel();
        menuBar = new javax.swing.JMenuBar();
        javax.swing.JMenu fileMenu = new javax.swing.JMenu();
        jMenuItem2 = new javax.swing.JMenuItem();
        jMenuItem1 = new javax.swing.JMenuItem();
        jMenuItem3 = new javax.swing.JMenuItem();
        jSeparator1 = new javax.swing.JSeparator();
        javax.swing.JMenuItem exitMenuItem = new javax.swing.JMenuItem();
        jMenu1 = new javax.swing.JMenu();
        jMenuItem5 = new javax.swing.JMenuItem();
        jMenuItem6 = new javax.swing.JMenuItem();
        jMenuItem7 = new javax.swing.JMenuItem();
        jSeparator2 = new javax.swing.JSeparator();
        jMenuItem4 = new javax.swing.JMenuItem();
        javax.swing.JMenu helpMenu = new javax.swing.JMenu();
        javax.swing.JMenuItem aboutMenuItem = new javax.swing.JMenuItem();
        statusPanel = new javax.swing.JPanel();
        javax.swing.JSeparator statusPanelSeparator = new javax.swing.JSeparator();
        statusMessageLabel = new javax.swing.JLabel();
        statusAnimationLabel = new javax.swing.JLabel();
        fileChooser = new javax.swing.JFileChooser();
        fileChooser2 = new javax.swing.JFileChooser();
        fileSaver = new javax.swing.JFileChooser();
        addWordsDialog = new javax.swing.JDialog();
        jPanel3 = new javax.swing.JPanel();
        jTextField2 = new javax.swing.JTextField();
        jLabel11 = new javax.swing.JLabel();
        jLabel9 = new javax.swing.JLabel();
        jLabel4 = new javax.swing.JLabel();
        jComboBox3 = new javax.swing.JComboBox();
        jLabel28 = new javax.swing.JLabel();
        jSeparator5 = new javax.swing.JSeparator();
        jScrollPane4 = new javax.swing.JScrollPane();
        jList1 = new javax.swing.JList();
        jButton35 = new javax.swing.JButton();
        jButton36 = new javax.swing.JButton();
        jButton37 = new javax.swing.JButton();
        jButton38 = new javax.swing.JButton();
        jButton40 = new javax.swing.JButton();
        jCheckBox2 = new javax.swing.JCheckBox();
        jButton12 = new javax.swing.JButton();
        jButton13 = new javax.swing.JButton();
        jButton39 = new javax.swing.JButton();
        settingsDialog = new javax.swing.JDialog();
        jButton14 = new javax.swing.JButton();
        jButton15 = new javax.swing.JButton();
        jTabbedPane2 = new javax.swing.JTabbedPane();
        jPanel12 = new javax.swing.JPanel();
        jPanel16 = new javax.swing.JPanel();
        jLabel12 = new javax.swing.JLabel();
        jTextField4 = new javax.swing.JTextField();
        jCheckBox1 = new javax.swing.JCheckBox();
        jLabel26 = new javax.swing.JLabel();
        jComboBox4 = new javax.swing.JComboBox();
        jTextField21 = new javax.swing.JTextField();
        jButton41 = new javax.swing.JButton();
        jLabel31 = new javax.swing.JLabel();
        jPanel9 = new javax.swing.JPanel();
        jPanel6 = new javax.swing.JPanel();
        jLabel5 = new javax.swing.JLabel();
        jTextField5 = new javax.swing.JTextField();
        jTextField6 = new javax.swing.JTextField();
        jLabel6 = new javax.swing.JLabel();
        jLabel7 = new javax.swing.JLabel();
        jTextField7 = new javax.swing.JTextField();
        jLabel18 = new javax.swing.JLabel();
        jTextField9 = new javax.swing.JTextField();
        jLabel25 = new javax.swing.JLabel();
        jTextField17 = new javax.swing.JTextField();
        jPanel8 = new javax.swing.JPanel();
        jPanel18 = new javax.swing.JPanel();
        jTextField10 = new javax.swing.JTextField();
        jLabel14 = new javax.swing.JLabel();
        jLabel15 = new javax.swing.JLabel();
        jTextField11 = new javax.swing.JTextField();
        jLabel20 = new javax.swing.JLabel();
        jLabel22 = new javax.swing.JLabel();
        jLabel23 = new javax.swing.JLabel();
        jTextField13 = new javax.swing.JTextField();
        jLabel24 = new javax.swing.JLabel();
        jTextField15 = new javax.swing.JTextField();
        jTextField16 = new javax.swing.JTextField();
        jTextField12 = new javax.swing.JTextField();
        jPanel20 = new javax.swing.JPanel();
        jScrollPane3 = new javax.swing.JScrollPane();
        jTable1 = new javax.swing.JTable();
        jTable1.setModel(new HunspellTableModel(null,"1"));
        jTable1.getColumnModel().getColumn(0).setCellEditor(new DefaultCellEditor(new JComboBox(new String[]{"PFX", "SFX"} ) ));
        jComboBox5 = new javax.swing.JComboBox();
        jLabel27 = new javax.swing.JLabel();
        jTextField20 = new javax.swing.JTextField();
        jButton6 = new javax.swing.JButton();
        jButton25 = new javax.swing.JButton();
        jButton31 = new javax.swing.JButton();
        jButton32 = new javax.swing.JButton();
        jButton33 = new javax.swing.JButton();
        jButton34 = new javax.swing.JButton();
        jSeparator4 = new javax.swing.JSeparator();
        jPanel22 = new javax.swing.JPanel();
        jPanel23 = new javax.swing.JPanel();
        jComboBox6 = new javax.swing.JComboBox();
        jLabel32 = new javax.swing.JLabel();
        jLabel33 = new javax.swing.JLabel();
        jLabel34 = new javax.swing.JLabel();
        jScrollPane9 = new javax.swing.JScrollPane();
        jList3 = new javax.swing.JList();
        jButton44 = new javax.swing.JButton();
        jButton45 = new javax.swing.JButton();
        jButton46 = new javax.swing.JButton();
        findWordsDialog = new javax.swing.JDialog();
        jButton16 = new javax.swing.JButton();
        jButton17 = new javax.swing.JButton();
        jPanel17 = new javax.swing.JPanel();
        jTextField18 = new javax.swing.JTextField();
        jLabel29 = new javax.swing.JLabel();
        filterWordsDialog = new javax.swing.JDialog(this.getFrame());
        jButton19 = new javax.swing.JButton();
        jButton23 = new javax.swing.JButton();
        jPanel19 = new javax.swing.JPanel();
        jTextField19 = new javax.swing.JTextField();
        jLabel30 = new javax.swing.JLabel();
        tablePopupMenu = new javax.swing.JPopupMenu();
        jMenuItem13 = new javax.swing.JMenuItem();
        jMenuItem8 = new javax.swing.JMenuItem();
        jMenuItem14 = new javax.swing.JMenuItem();
        jMenuItem9 = new javax.swing.JMenuItem();
        jSeparator3 = new javax.swing.JSeparator();
        jMenuItem10 = new javax.swing.JMenuItem();
        jMenuItem11 = new javax.swing.JMenuItem();
        jMenuItem12 = new javax.swing.JMenuItem();
        jSeparator6 = new javax.swing.JSeparator();
        jMenuItem15 = new javax.swing.JMenuItem();
        jMenuItem16 = new javax.swing.JMenuItem();
        suggestionDialog = new javax.swing.JDialog(this.getFrame());
        jButton42 = new javax.swing.JButton();
        jButton43 = new javax.swing.JButton();
        jPanel21 = new javax.swing.JPanel();
        jScrollPane8 = new javax.swing.JScrollPane();
        jList2 = new javax.swing.JList();
        jColorChooser1 = new javax.swing.JColorChooser();

        mainPanel.setMinimumSize(new java.awt.Dimension(500, 500));
        mainPanel.setName("mainPanel"); // NOI18N

        jTabbedPane1.setName("jTabbedPane1"); // NOI18N

        jPanel1.setName("jPanel1"); // NOI18N

        org.jdesktop.application.ResourceMap resourceMap = org.jdesktop.application.Application.getInstance(sinhaladictionarytools.SinhalaDictionaryToolsApp.class).getContext().getResourceMap(SinhalaDictionaryToolsView.class);
        jPanel4.setBorder(javax.swing.BorderFactory.createTitledBorder(resourceMap.getString("jPanel4.border.title"))); // NOI18N
        jPanel4.setName("jPanel4"); // NOI18N

        jLabel1.setText(resourceMap.getString("jLabel1.text")); // NOI18N
        jLabel1.setToolTipText(resourceMap.getString("jLabel1.toolTipText")); // NOI18N
        jLabel1.setName("jLabel1"); // NOI18N

        jTextField1.setFont(resourceMap.getFont("jTextField1.font")); // NOI18N
        jTextField1.setToolTipText(resourceMap.getString("jTextField1.toolTipText")); // NOI18N
        jTextField1.setName("jTextField1"); // NOI18N
        jTextField1.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseEntered(java.awt.event.MouseEvent evt) {
                jTextField1MouseEntered(evt);
            }
            public void mouseExited(java.awt.event.MouseEvent evt) {
                jTextField1MouseExited(evt);
            }
        });

        jLabel2.setText(resourceMap.getString("jLabel2.text")); // NOI18N
        jLabel2.setToolTipText(resourceMap.getString("jLabel2.toolTipText")); // NOI18N
        jLabel2.setName("jLabel2"); // NOI18N

        jButton2.setText(resourceMap.getString("jButton2.text")); // NOI18N
        jButton2.setToolTipText(resourceMap.getString("jButton2.toolTipText")); // NOI18N
        jButton2.setName("jButton2"); // NOI18N
        jButton2.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton2ActionPerformed(evt);
            }
        });

        jScrollPane1.setName("jScrollPane1"); // NOI18N

        jTextArea1.setColumns(20);
        jTextArea1.setFont(resourceMap.getFont("jTextArea1.font")); // NOI18N
        jTextArea1.setRows(5);
        jTextArea1.setToolTipText(resourceMap.getString("jTextArea1.toolTipText")); // NOI18N
        jTextArea1.setName("jTextArea1"); // NOI18N
        jTextArea1.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseEntered(java.awt.event.MouseEvent evt) {
                jTextArea1MouseEntered(evt);
            }
            public void mouseExited(java.awt.event.MouseEvent evt) {
                jTextArea1MouseExited(evt);
            }
        });
        jScrollPane1.setViewportView(jTextArea1);

        javax.swing.GroupLayout jPanel4Layout = new javax.swing.GroupLayout(jPanel4);
        jPanel4.setLayout(jPanel4Layout);
        jPanel4Layout.setHorizontalGroup(
            jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel4Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jLabel1)
                    .addComponent(jLabel2))
                .addGap(30, 30, 30)
                .addGroup(jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 410, Short.MAX_VALUE)
                    .addComponent(jTextField1, javax.swing.GroupLayout.DEFAULT_SIZE, 410, Short.MAX_VALUE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jButton2, javax.swing.GroupLayout.PREFERRED_SIZE, 100, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(95, 95, 95))
        );
        jPanel4Layout.setVerticalGroup(
            jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel4Layout.createSequentialGroup()
                .addGroup(jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel1)
                    .addComponent(jTextField1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jButton2))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jLabel2)
                    .addComponent(jScrollPane1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        jPanel5.setBorder(javax.swing.BorderFactory.createTitledBorder(resourceMap.getString("jPanel5.border.title"))); // NOI18N
        jPanel5.setName("jPanel5"); // NOI18N

        jLabel3.setText(resourceMap.getString("jLabel3.text")); // NOI18N
        jLabel3.setToolTipText(resourceMap.getString("jLabel3.toolTipText")); // NOI18N
        jLabel3.setName("jLabel3"); // NOI18N

        jTextField3.setToolTipText(resourceMap.getString("jTextField3.toolTipText")); // NOI18N
        jTextField3.setName("jTextField3"); // NOI18N
        jTextField3.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseEntered(java.awt.event.MouseEvent evt) {
                jTextField3MouseEntered(evt);
            }
            public void mouseExited(java.awt.event.MouseEvent evt) {
                jTextField3MouseExited(evt);
            }
        });

        jButton3.setText(resourceMap.getString("jButton3.text")); // NOI18N
        jButton3.setToolTipText(resourceMap.getString("jButton3.toolTipText")); // NOI18N
        jButton3.setName("jButton3"); // NOI18N
        jButton3.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                fileSave(evt);
            }
        });

        jButton4.setText(resourceMap.getString("jButton4.text")); // NOI18N
        jButton4.setToolTipText(resourceMap.getString("jButton4.toolTipText")); // NOI18N
        jButton4.setName("jButton4"); // NOI18N
        jButton4.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton4fileSave(evt);
            }
        });

        javax.swing.GroupLayout jPanel5Layout = new javax.swing.GroupLayout(jPanel5);
        jPanel5.setLayout(jPanel5Layout);
        jPanel5Layout.setHorizontalGroup(
            jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel5Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel5Layout.createSequentialGroup()
                        .addComponent(jLabel3)
                        .addGap(18, 18, 18)
                        .addComponent(jTextField3, javax.swing.GroupLayout.DEFAULT_SIZE, 578, Short.MAX_VALUE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jButton4, javax.swing.GroupLayout.PREFERRED_SIZE, 100, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addComponent(jButton3, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.PREFERRED_SIZE, 100, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap())
        );
        jPanel5Layout.setVerticalGroup(
            jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel5Layout.createSequentialGroup()
                .addGroup(jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel3)
                    .addComponent(jTextField3, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jButton4))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jButton3))
        );

        jButton1.setText(resourceMap.getString("jButton1.text")); // NOI18N
        jButton1.setToolTipText(resourceMap.getString("jButton1.toolTipText")); // NOI18N
        jButton1.setName("jButton1"); // NOI18N
        jButton1.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton1ActionPerformed(evt);
            }
        });

        jButton8.setText(resourceMap.getString("jButton8.text")); // NOI18N
        jButton8.setToolTipText(resourceMap.getString("jButton8.toolTipText")); // NOI18N
        jButton8.setName("jButton8"); // NOI18N
        jButton8.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton8ActionPerformed(evt);
            }
        });

        jScrollPane5.setName("jScrollPane5"); // NOI18N

        jTextArea2.setColumns(20);
        jTextArea2.setEditable(false);
        jTextArea2.setFont(resourceMap.getFont("jTextArea2.font")); // NOI18N
        jTextArea2.setRows(5);
        jTextArea2.setName("jTextArea2"); // NOI18N
        jScrollPane5.setViewportView(jTextArea2);

        jLabel21.setText(resourceMap.getString("jLabel21.text")); // NOI18N
        jLabel21.setName("jLabel21"); // NOI18N

        javax.swing.GroupLayout jPanel1Layout = new javax.swing.GroupLayout(jPanel1);
        jPanel1.setLayout(jPanel1Layout);
        jPanel1Layout.setHorizontalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jPanel4, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(jPanel5, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(jLabel21, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.PREFERRED_SIZE, 783, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jScrollPane5, javax.swing.GroupLayout.DEFAULT_SIZE, 783, Short.MAX_VALUE)
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel1Layout.createSequentialGroup()
                        .addComponent(jButton8, javax.swing.GroupLayout.PREFERRED_SIZE, 100, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jButton1, javax.swing.GroupLayout.PREFERRED_SIZE, 100, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addContainerGap())
        );

        jPanel1Layout.linkSize(javax.swing.SwingConstants.HORIZONTAL, new java.awt.Component[] {jPanel4, jPanel5, jScrollPane5});

        jPanel1Layout.setVerticalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel1Layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jLabel21)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jPanel4, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jPanel5, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jScrollPane5, javax.swing.GroupLayout.PREFERRED_SIZE, 163, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jButton1)
                    .addComponent(jButton8))
                .addContainerGap(17, Short.MAX_VALUE))
        );

        jTabbedPane1.addTab(resourceMap.getString("jPanel1.TabConstraints.tabTitle"), jPanel1); // NOI18N

        jPanel2.setName("jPanel2"); // NOI18N

        jPanel7.setBorder(javax.swing.BorderFactory.createTitledBorder(resourceMap.getString("jPanel7.border.title"))); // NOI18N
        jPanel7.setName("jPanel7"); // NOI18N

        jLabel8.setText(resourceMap.getString("jLabel8.text")); // NOI18N
        jLabel8.setToolTipText(resourceMap.getString("jLabel8.toolTipText")); // NOI18N
        jLabel8.setName("jLabel8"); // NOI18N

        jTextField8.setToolTipText(resourceMap.getString("jTextField8.toolTipText")); // NOI18N
        jTextField8.setName("jTextField8"); // NOI18N

        jButton7.setText(resourceMap.getString("jButton7.text")); // NOI18N
        jButton7.setToolTipText(resourceMap.getString("jButton7.toolTipText")); // NOI18N
        jButton7.setName("jButton7"); // NOI18N
        jButton7.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton7ActionPerformed(evt);
            }
        });

        jButton9.setText(resourceMap.getString("jButton9.text")); // NOI18N
        jButton9.setToolTipText(resourceMap.getString("jButton9.toolTipText")); // NOI18N
        jButton9.setName("jButton9"); // NOI18N
        jButton9.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton9ActionPerformed(evt);
            }
        });

        jButton10.setText(resourceMap.getString("jButton10.text")); // NOI18N
        jButton10.setToolTipText(resourceMap.getString("jButton10.toolTipText")); // NOI18N
        jButton10.setName("jButton10"); // NOI18N
        jButton10.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton10ActionPerformed(evt);
            }
        });

        jButton11.setText(resourceMap.getString("jButton11.text")); // NOI18N
        jButton11.setToolTipText(resourceMap.getString("jButton11.toolTipText")); // NOI18N
        jButton11.setEnabled(false);
        jButton11.setName("jButton11"); // NOI18N
        jButton11.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton11ActionPerformed(evt);
            }
        });

        jScrollPane2.setName("jScrollPane2"); // NOI18N

        jTextArea3.setColumns(20);
        jTextArea3.setFont(resourceMap.getFont("jTextArea3.font")); // NOI18N
        jTextArea3.setRows(5);
        jTextArea3.setName("jTextArea3"); // NOI18N
        jScrollPane2.setViewportView(jTextArea3);

        javax.swing.GroupLayout jPanel7Layout = new javax.swing.GroupLayout(jPanel7);
        jPanel7.setLayout(jPanel7Layout);
        jPanel7Layout.setHorizontalGroup(
            jPanel7Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel7Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel7Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(jScrollPane2, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, 747, Short.MAX_VALUE)
                    .addGroup(javax.swing.GroupLayout.Alignment.LEADING, jPanel7Layout.createSequentialGroup()
                        .addComponent(jLabel8, javax.swing.GroupLayout.PREFERRED_SIZE, 61, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(18, 18, 18)
                        .addComponent(jTextField8, javax.swing.GroupLayout.DEFAULT_SIZE, 496, Short.MAX_VALUE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jButton7, javax.swing.GroupLayout.PREFERRED_SIZE, 80, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jButton11, javax.swing.GroupLayout.PREFERRED_SIZE, 80, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addGroup(jPanel7Layout.createSequentialGroup()
                        .addComponent(jButton10, javax.swing.GroupLayout.PREFERRED_SIZE, 100, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jButton9, javax.swing.GroupLayout.PREFERRED_SIZE, 100, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addContainerGap())
        );
        jPanel7Layout.setVerticalGroup(
            jPanel7Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel7Layout.createSequentialGroup()
                .addGroup(jPanel7Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel8)
                    .addComponent(jTextField8, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jButton11)
                    .addComponent(jButton7))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jScrollPane2, javax.swing.GroupLayout.DEFAULT_SIZE, 342, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(jPanel7Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jButton9)
                    .addComponent(jButton10))
                .addContainerGap())
        );

        jLabel10.setText(resourceMap.getString("jLabel10.text")); // NOI18N
        jLabel10.setName("jLabel10"); // NOI18N

        javax.swing.GroupLayout jPanel2Layout = new javax.swing.GroupLayout(jPanel2);
        jPanel2.setLayout(jPanel2Layout);
        jPanel2Layout.setHorizontalGroup(
            jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel2Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jPanel7, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(jLabel10, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, 783, Short.MAX_VALUE))
                .addContainerGap())
        );
        jPanel2Layout.setVerticalGroup(
            jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel2Layout.createSequentialGroup()
                .addGap(6, 6, 6)
                .addComponent(jLabel10, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jPanel7, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(30, 30, 30))
        );

        jTabbedPane1.addTab(resourceMap.getString("jPanel2.TabConstraints.tabTitle"), jPanel2); // NOI18N

        jPanel10.setName("jPanel10"); // NOI18N

        jPanel11.setBorder(javax.swing.BorderFactory.createTitledBorder(resourceMap.getString("jPanel11.border.title"))); // NOI18N
        jPanel11.setName("jPanel11"); // NOI18N

        jTextField14.setName("jTextField14"); // NOI18N

        jLabel13.setText(resourceMap.getString("jLabel13.text")); // NOI18N
        jLabel13.setName("jLabel13"); // NOI18N

        jButton5.setText(resourceMap.getString("jButton5.text")); // NOI18N
        jButton5.setToolTipText(resourceMap.getString("jButton5.toolTipText")); // NOI18N
        jButton5.setName("jButton5"); // NOI18N
        jButton5.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton5fileSave(evt);
            }
        });

        jScrollPane6.setName("jScrollPane6"); // NOI18N

        jTable3.setFont(resourceMap.getFont("jTable4.font")); // NOI18N
        jTable3.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {

            },
            new String [] {
                "Word", "Frequency"
            }
        ) {
            boolean[] canEdit = new boolean [] {
                false, false
            };

            public boolean isCellEditable(int rowIndex, int columnIndex) {
                return canEdit [columnIndex];
            }
        });
        jTable3.setCellSelectionEnabled(true);
        jTable3.setName("jTable3"); // NOI18N
        jTable3.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mousePressed(java.awt.event.MouseEvent evt) {
                jTable3MouseClicked(evt);
            }
            public void mouseReleased(java.awt.event.MouseEvent evt) {
                jTable3MouseClicked(evt);
            }
        });
        jTable3.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusGained(java.awt.event.FocusEvent evt) {
                jTable3FocusGained(evt);
            }
        });
        jTable3.addKeyListener(new java.awt.event.KeyAdapter() {
            public void keyPressed(java.awt.event.KeyEvent evt) {
                jTable3KeyPressed(evt);
            }
        });
        jScrollPane6.setViewportView(jTable3);

        jScrollPane7.setName("jScrollPane7"); // NOI18N

        jTable4.setFont(resourceMap.getFont("jTable4.font")); // NOI18N
        jTable4.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {

            },
            new String [] {
                "Word", "Frequency"
            }
        ) {
            boolean[] canEdit = new boolean [] {
                false, false
            };

            public boolean isCellEditable(int rowIndex, int columnIndex) {
                return canEdit [columnIndex];
            }
        });
        jTable4.setCellSelectionEnabled(true);
        jTable4.setName("jTable4"); // NOI18N
        jTable4.setUpdateSelectionOnSort(false);
        jTable4.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mousePressed(java.awt.event.MouseEvent evt) {
                jTable4MouseClicked(evt);
            }
            public void mouseReleased(java.awt.event.MouseEvent evt) {
                jTable4MouseClicked(evt);
            }
        });
        jTable4.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusGained(java.awt.event.FocusEvent evt) {
                jTable4FocusGained(evt);
            }
        });
        jTable4.addKeyListener(new java.awt.event.KeyAdapter() {
            public void keyPressed(java.awt.event.KeyEvent evt) {
                jTable4KeyPressed(evt);
            }
        });
        jScrollPane7.setViewportView(jTable4);

        jPanel13.setName("jPanel13"); // NOI18N

        jButton50.setText(resourceMap.getString("jButton50.text")); // NOI18N
        jButton50.setToolTipText(resourceMap.getString("jButton50.toolTipText")); // NOI18N
        jButton50.setName("jButton50"); // NOI18N
        jButton50.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton50ActionPerformed(evt);
            }
        });

        jLabel17.setText(resourceMap.getString("jLabel17.text")); // NOI18N
        jLabel17.setToolTipText(resourceMap.getString("jLabel17.toolTipText")); // NOI18N
        jLabel17.setName("jLabel17"); // NOI18N

        jButton26.setText(resourceMap.getString("jButton26.text")); // NOI18N
        jButton26.setToolTipText(resourceMap.getString("jButton26.toolTipText")); // NOI18N
        jButton26.setName("jButton26"); // NOI18N
        jButton26.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton26ActionPerformed(evt);
            }
        });

        jComboBox2.setToolTipText(resourceMap.getString("jComboBox2.toolTipText")); // NOI18N
        jComboBox2.setName("jComboBox2"); // NOI18N
        jComboBox2.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jComboBox2ActionPerformed(evt);
            }
        });

        jButton28.setText(resourceMap.getString("jButton28.text")); // NOI18N
        jButton28.setToolTipText(resourceMap.getString("jButton28.toolTipText")); // NOI18N
        jButton28.setName("jButton28"); // NOI18N
        jButton28.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton28ActionPerformed(evt);
            }
        });

        jButton27.setText(resourceMap.getString("jButton27.text")); // NOI18N
        jButton27.setToolTipText(resourceMap.getString("jButton27.toolTipText")); // NOI18N
        jButton27.setName("jButton27"); // NOI18N
        jButton27.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton27ActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout jPanel13Layout = new javax.swing.GroupLayout(jPanel13);
        jPanel13.setLayout(jPanel13Layout);
        jPanel13Layout.setHorizontalGroup(
            jPanel13Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel13Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel13Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jButton50, javax.swing.GroupLayout.DEFAULT_SIZE, 98, Short.MAX_VALUE)
                    .addComponent(jLabel17, javax.swing.GroupLayout.PREFERRED_SIZE, 80, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel13Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jComboBox2, javax.swing.GroupLayout.PREFERRED_SIZE, 78, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jButton26, javax.swing.GroupLayout.PREFERRED_SIZE, 88, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel13Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jButton27, javax.swing.GroupLayout.DEFAULT_SIZE, 100, Short.MAX_VALUE)
                    .addComponent(jButton28, javax.swing.GroupLayout.DEFAULT_SIZE, 100, Short.MAX_VALUE))
                .addContainerGap())
        );
        jPanel13Layout.setVerticalGroup(
            jPanel13Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel13Layout.createSequentialGroup()
                .addGap(12, 12, 12)
                .addGroup(jPanel13Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jButton50)
                    .addComponent(jButton26)
                    .addComponent(jButton27))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel13Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jButton28)
                    .addComponent(jComboBox2, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel17))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        jPanel14.setName("jPanel14"); // NOI18N

        jButton20.setText(resourceMap.getString("jButton20.text")); // NOI18N
        jButton20.setToolTipText(resourceMap.getString("jButton20.toolTipText")); // NOI18N
        jButton20.setName("jButton20"); // NOI18N
        jButton20.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton20ActionPerformed(evt);
            }
        });

        jButton24.setText(resourceMap.getString("jButton24.text")); // NOI18N
        jButton24.setToolTipText(resourceMap.getString("jButton24.toolTipText")); // NOI18N
        jButton24.setName("jButton24"); // NOI18N
        jButton24.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton24ActionPerformed(evt);
            }
        });

        jButton18.setText(resourceMap.getString("jButton18.text")); // NOI18N
        jButton18.setToolTipText(resourceMap.getString("jButton18.toolTipText")); // NOI18N
        jButton18.setName("jButton18"); // NOI18N
        jButton18.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton18ActionPerformed(evt);
            }
        });

        jComboBox1.setToolTipText(resourceMap.getString("jComboBox1.toolTipText")); // NOI18N
        jComboBox1.setName("jComboBox1"); // NOI18N
        jComboBox1.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jComboBox1ActionPerformed(evt);
            }
        });

        jButton47.setText(resourceMap.getString("jButton47.text")); // NOI18N
        jButton47.setToolTipText(resourceMap.getString("jButton47.toolTipText")); // NOI18N
        jButton47.setName("jButton47"); // NOI18N
        jButton47.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton47ActionPerformed(evt);
            }
        });

        jLabel16.setText(resourceMap.getString("jLabel16.text")); // NOI18N
        jLabel16.setToolTipText(resourceMap.getString("jLabel16.toolTipText")); // NOI18N
        jLabel16.setName("jLabel16"); // NOI18N

        javax.swing.GroupLayout jPanel14Layout = new javax.swing.GroupLayout(jPanel14);
        jPanel14.setLayout(jPanel14Layout);
        jPanel14Layout.setHorizontalGroup(
            jPanel14Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel14Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel14Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addGroup(jPanel14Layout.createSequentialGroup()
                        .addComponent(jButton47, javax.swing.GroupLayout.DEFAULT_SIZE, 80, Short.MAX_VALUE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jButton18, javax.swing.GroupLayout.PREFERRED_SIZE, 88, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addGroup(jPanel14Layout.createSequentialGroup()
                        .addComponent(jLabel16)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jComboBox1, javax.swing.GroupLayout.PREFERRED_SIZE, 82, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel14Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jButton24, javax.swing.GroupLayout.DEFAULT_SIZE, 118, Short.MAX_VALUE)
                    .addComponent(jButton20, javax.swing.GroupLayout.DEFAULT_SIZE, 118, Short.MAX_VALUE))
                .addContainerGap())
        );
        jPanel14Layout.setVerticalGroup(
            jPanel14Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel14Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel14Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jButton18)
                    .addComponent(jButton47)
                    .addComponent(jButton20))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel14Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jComboBox1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel16)
                    .addComponent(jButton24))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        jPanel15.setName("jPanel15"); // NOI18N

        jButton30.setText(resourceMap.getString("jButton30.text")); // NOI18N
        jButton30.setToolTipText(resourceMap.getString("jButton30.toolTipText")); // NOI18N
        jButton30.setMaximumSize(new java.awt.Dimension(50, 33));
        jButton30.setMinimumSize(new java.awt.Dimension(50, 33));
        jButton30.setName("jButton30"); // NOI18N
        jButton30.setPreferredSize(new java.awt.Dimension(50, 33));
        jButton30.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton30ActionPerformed(evt);
            }
        });

        jButton29.setText(resourceMap.getString("jButton29.text")); // NOI18N
        jButton29.setToolTipText(resourceMap.getString("jButton29.toolTipText")); // NOI18N
        jButton29.setMaximumSize(new java.awt.Dimension(50, 33));
        jButton29.setMinimumSize(new java.awt.Dimension(50, 33));
        jButton29.setName("jButton29"); // NOI18N
        jButton29.setPreferredSize(new java.awt.Dimension(50, 33));
        jButton29.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton29ActionPerformed(evt);
            }
        });

        jButton22.setText(resourceMap.getString("jButton22.text")); // NOI18N
        jButton22.setToolTipText(resourceMap.getString("jButton22.toolTipText")); // NOI18N
        jButton22.setMaximumSize(new java.awt.Dimension(50, 33));
        jButton22.setMinimumSize(new java.awt.Dimension(50, 33));
        jButton22.setName("jButton22"); // NOI18N
        jButton22.setPreferredSize(new java.awt.Dimension(50, 33));
        jButton22.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton22ActionPerformed(evt);
            }
        });

        jButton21.setText(resourceMap.getString("jButton21.text")); // NOI18N
        jButton21.setToolTipText(resourceMap.getString("jButton21.toolTipText")); // NOI18N
        jButton21.setMaximumSize(new java.awt.Dimension(50, 33));
        jButton21.setMinimumSize(new java.awt.Dimension(50, 33));
        jButton21.setName("jButton21"); // NOI18N
        jButton21.setPreferredSize(new java.awt.Dimension(50, 33));
        jButton21.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton21ActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout jPanel15Layout = new javax.swing.GroupLayout(jPanel15);
        jPanel15.setLayout(jPanel15Layout);
        jPanel15Layout.setHorizontalGroup(
            jPanel15Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel15Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.CENTER)
                .addComponent(jButton30, javax.swing.GroupLayout.DEFAULT_SIZE, 94, Short.MAX_VALUE)
                .addComponent(jButton29, javax.swing.GroupLayout.DEFAULT_SIZE, 94, Short.MAX_VALUE)
                .addComponent(jButton22, javax.swing.GroupLayout.DEFAULT_SIZE, 94, Short.MAX_VALUE)
                .addComponent(jButton21, javax.swing.GroupLayout.DEFAULT_SIZE, 94, Short.MAX_VALUE))
        );

        jPanel15Layout.linkSize(javax.swing.SwingConstants.HORIZONTAL, new java.awt.Component[] {jButton21, jButton22, jButton29, jButton30});

        jPanel15Layout.setVerticalGroup(
            jPanel15Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel15Layout.createSequentialGroup()
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(jButton21, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jButton22, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jButton29, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jButton30, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap())
        );

        jPanel15Layout.linkSize(javax.swing.SwingConstants.VERTICAL, new java.awt.Component[] {jButton21, jButton22, jButton29, jButton30});

        javax.swing.GroupLayout jPanel11Layout = new javax.swing.GroupLayout(jPanel11);
        jPanel11.setLayout(jPanel11Layout);
        jPanel11Layout.setHorizontalGroup(
            jPanel11Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel11Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel11Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel11Layout.createSequentialGroup()
                        .addComponent(jLabel13, javax.swing.GroupLayout.PREFERRED_SIZE, 67, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(55, 55, 55)
                        .addComponent(jTextField14, javax.swing.GroupLayout.DEFAULT_SIZE, 514, Short.MAX_VALUE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(jButton5, javax.swing.GroupLayout.PREFERRED_SIZE, 86, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(26, 26, 26))
                    .addGroup(jPanel11Layout.createSequentialGroup()
                        .addGroup(jPanel11Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jPanel14, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(jScrollPane7, javax.swing.GroupLayout.PREFERRED_SIZE, 304, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(jPanel15, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(jPanel11Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                            .addComponent(jPanel13, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addGroup(jPanel11Layout.createSequentialGroup()
                                .addComponent(jScrollPane6, javax.swing.GroupLayout.PREFERRED_SIZE, 318, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addGap(4, 4, 4)))))
                .addContainerGap())
        );

        jPanel11Layout.linkSize(javax.swing.SwingConstants.HORIZONTAL, new java.awt.Component[] {jPanel13, jPanel14, jScrollPane6, jScrollPane7});

        jPanel11Layout.setVerticalGroup(
            jPanel11Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel11Layout.createSequentialGroup()
                .addGroup(jPanel11Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel13)
                    .addComponent(jTextField14, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jButton5))
                .addGroup(jPanel11Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel11Layout.createSequentialGroup()
                        .addGap(8, 8, 8)
                        .addGroup(jPanel11Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                            .addComponent(jScrollPane7, javax.swing.GroupLayout.PREFERRED_SIZE, 301, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(jScrollPane6, javax.swing.GroupLayout.PREFERRED_SIZE, 301, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addGap(6, 6, 6)
                        .addGroup(jPanel11Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jPanel14, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(jPanel13, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)))
                    .addGroup(jPanel11Layout.createSequentialGroup()
                        .addGap(55, 55, 55)
                        .addComponent(jPanel15, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        jPanel11Layout.linkSize(javax.swing.SwingConstants.VERTICAL, new java.awt.Component[] {jPanel13, jPanel14});

        jPanel11Layout.linkSize(javax.swing.SwingConstants.VERTICAL, new java.awt.Component[] {jScrollPane6, jScrollPane7});

        jLabel19.setText(resourceMap.getString("jLabel19.text")); // NOI18N
        jLabel19.setName("jLabel19"); // NOI18N

        javax.swing.GroupLayout jPanel10Layout = new javax.swing.GroupLayout(jPanel10);
        jPanel10.setLayout(jPanel10Layout);
        jPanel10Layout.setHorizontalGroup(
            jPanel10Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel10Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel10Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jPanel11, javax.swing.GroupLayout.DEFAULT_SIZE, 783, Short.MAX_VALUE)
                    .addComponent(jLabel19, javax.swing.GroupLayout.PREFERRED_SIZE, 708, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap())
        );
        jPanel10Layout.setVerticalGroup(
            jPanel10Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel10Layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jLabel19, javax.swing.GroupLayout.PREFERRED_SIZE, 20, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jPanel11, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addContainerGap())
        );

        jTabbedPane1.addTab(resourceMap.getString("jPanel10.TabConstraints.tabTitle"), jPanel10); // NOI18N

        javax.swing.GroupLayout mainPanelLayout = new javax.swing.GroupLayout(mainPanel);
        mainPanel.setLayout(mainPanelLayout);
        mainPanelLayout.setHorizontalGroup(
            mainPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(mainPanelLayout.createSequentialGroup()
                .addComponent(jTabbedPane1, javax.swing.GroupLayout.PREFERRED_SIZE, 823, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        mainPanelLayout.setVerticalGroup(
            mainPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(mainPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jTabbedPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 567, Short.MAX_VALUE))
        );

        menuBar.setName("menuBar"); // NOI18N

        fileMenu.setMnemonic('F');
        fileMenu.setText(resourceMap.getString("fileMenu.text")); // NOI18N
        fileMenu.setName("fileMenu"); // NOI18N

        jMenuItem2.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_1, java.awt.event.InputEvent.CTRL_MASK));
        jMenuItem2.setMnemonic('G');
        jMenuItem2.setText(resourceMap.getString("jMenuItem2.text")); // NOI18N
        jMenuItem2.setName("jMenuItem2"); // NOI18N
        jMenuItem2.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jMenuItem2ActionPerformed(evt);
            }
        });
        fileMenu.add(jMenuItem2);

        jMenuItem1.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_2, java.awt.event.InputEvent.CTRL_MASK));
        jMenuItem1.setMnemonic('C');
        jMenuItem1.setText(resourceMap.getString("jMenuItem1.text")); // NOI18N
        jMenuItem1.setName("jMenuItem1"); // NOI18N
        jMenuItem1.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jMenuItem1ActionPerformed(evt);
            }
        });
        fileMenu.add(jMenuItem1);

        jMenuItem3.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_3, java.awt.event.InputEvent.CTRL_MASK));
        jMenuItem3.setMnemonic('A');
        jMenuItem3.setText(resourceMap.getString("jMenuItem3.text")); // NOI18N
        jMenuItem3.setName("jMenuItem3"); // NOI18N
        jMenuItem3.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jMenuItem3ActionPerformed(evt);
            }
        });
        fileMenu.add(jMenuItem3);

        jSeparator1.setName("jSeparator1"); // NOI18N
        fileMenu.add(jSeparator1);

        javax.swing.ActionMap actionMap = org.jdesktop.application.Application.getInstance(sinhaladictionarytools.SinhalaDictionaryToolsApp.class).getContext().getActionMap(SinhalaDictionaryToolsView.class, this);
        exitMenuItem.setAction(actionMap.get("quit")); // NOI18N
        exitMenuItem.setText(resourceMap.getString("exitMenuItem.text")); // NOI18N
        exitMenuItem.setName("exitMenuItem"); // NOI18N
        fileMenu.add(exitMenuItem);

        menuBar.add(fileMenu);

        jMenu1.setMnemonic('E');
        jMenu1.setText(resourceMap.getString("jMenu1.text")); // NOI18N
        jMenu1.setName("jMenu1"); // NOI18N

        jMenuItem5.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_F, java.awt.event.InputEvent.CTRL_MASK));
        jMenuItem5.setMnemonic('F');
        jMenuItem5.setText(resourceMap.getString("jMenuItem5.text")); // NOI18N
        jMenuItem5.setToolTipText(resourceMap.getString("jMenuItem5.toolTipText")); // NOI18N
        jMenuItem5.setEnabled(false);
        jMenuItem5.setName("jMenuItem5"); // NOI18N
        jMenuItem5.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jMenuItem5ActionPerformed(evt);
            }
        });
        jMenu1.add(jMenuItem5);

        jMenuItem6.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_L, java.awt.event.InputEvent.CTRL_MASK));
        jMenuItem6.setMnemonic('L');
        jMenuItem6.setText(resourceMap.getString("jMenuItem6.text")); // NOI18N
        jMenuItem6.setToolTipText(resourceMap.getString("jMenuItem6.toolTipText")); // NOI18N
        jMenuItem6.setEnabled(false);
        jMenuItem6.setName("jMenuItem6"); // NOI18N
        jMenuItem6.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jMenuItem6ActionPerformed(evt);
            }
        });
        jMenu1.add(jMenuItem6);

        jMenuItem7.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_N, java.awt.event.InputEvent.CTRL_MASK));
        jMenuItem7.setText(resourceMap.getString("jMenuItem7.text")); // NOI18N
        jMenuItem7.setEnabled(false);
        jMenuItem7.setName("jMenuItem7"); // NOI18N
        jMenuItem7.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jMenuItem7ActionPerformed(evt);
            }
        });
        jMenu1.add(jMenuItem7);

        jSeparator2.setName("jSeparator2"); // NOI18N
        jMenu1.add(jSeparator2);

        jMenuItem4.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_P, java.awt.event.InputEvent.CTRL_MASK));
        jMenuItem4.setMnemonic('P');
        jMenuItem4.setText(resourceMap.getString("jMenuItem4.text")); // NOI18N
        jMenuItem4.setToolTipText(resourceMap.getString("jMenuItem4.toolTipText")); // NOI18N
        jMenuItem4.setName("jMenuItem4"); // NOI18N
        jMenuItem4.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jMenuItem4ActionPerformed(evt);
            }
        });
        jMenu1.add(jMenuItem4);

        menuBar.add(jMenu1);

        helpMenu.setMnemonic('H');
        helpMenu.setText(resourceMap.getString("helpMenu.text")); // NOI18N
        helpMenu.setName("helpMenu"); // NOI18N

        aboutMenuItem.setAction(actionMap.get("showAboutBox")); // NOI18N
        aboutMenuItem.setText(resourceMap.getString("aboutMenuItem.text")); // NOI18N
        aboutMenuItem.setName("aboutMenuItem"); // NOI18N
        helpMenu.add(aboutMenuItem);

        menuBar.add(helpMenu);

        statusPanel.setToolTipText(resourceMap.getString("statusPanel.toolTipText")); // NOI18N
        statusPanel.setName("statusPanel"); // NOI18N

        statusPanelSeparator.setName("statusPanelSeparator"); // NOI18N

        statusMessageLabel.setName("statusMessageLabel"); // NOI18N

        statusAnimationLabel.setHorizontalAlignment(javax.swing.SwingConstants.LEFT);
        statusAnimationLabel.setName("statusAnimationLabel"); // NOI18N

        javax.swing.GroupLayout statusPanelLayout = new javax.swing.GroupLayout(statusPanel);
        statusPanel.setLayout(statusPanelLayout);
        statusPanelLayout.setHorizontalGroup(
            statusPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(statusPanelSeparator, javax.swing.GroupLayout.DEFAULT_SIZE, 835, Short.MAX_VALUE)
            .addGroup(statusPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(statusMessageLabel)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 811, Short.MAX_VALUE)
                .addComponent(statusAnimationLabel)
                .addContainerGap())
        );
        statusPanelLayout.setVerticalGroup(
            statusPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(statusPanelLayout.createSequentialGroup()
                .addComponent(statusPanelSeparator, javax.swing.GroupLayout.PREFERRED_SIZE, 2, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 29, Short.MAX_VALUE)
                .addGroup(statusPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(statusMessageLabel)
                    .addComponent(statusAnimationLabel))
                .addGap(12, 12, 12))
        );

        fileChooser.setApproveButtonText(resourceMap.getString("FileChooser.approveButtonText")); // NOI18N
        fileChooser.setApproveButtonToolTipText(resourceMap.getString("FileChooser.approveButtonToolTipText")); // NOI18N
        fileChooser.setDialogTitle(resourceMap.getString("FileChooser.dialogTitle")); // NOI18N
        fileChooser.setFileFilter(new FileNameExtensionFilter("Dic File","dic"));
        fileChooser.setToolTipText(resourceMap.getString("FileChooser.toolTipText")); // NOI18N
        fileChooser.setMaximumSize(new java.awt.Dimension(647, 847));
        fileChooser.setMinimumSize(new java.awt.Dimension(500, 500));
        fileChooser.setName("FileChooser"); // NOI18N
        fileChooser.setPreferredSize(new java.awt.Dimension(535, 527));

        fileChooser2.setName("fileChooser2"); // NOI18N

        fileSaver.setName("fileSaver"); // NOI18N

        addWordsDialog.setTitle(resourceMap.getString("addWordsDialog.title")); // NOI18N
        addWordsDialog.setAlwaysOnTop(true);
        addWordsDialog.setMinimumSize(new java.awt.Dimension(623, 420));
        addWordsDialog.setName("addWordsDialog"); // NOI18N
        addWordsDialog.setResizable(false);
        addWordsDialog.addComponentListener(new java.awt.event.ComponentAdapter() {
            public void componentHidden(java.awt.event.ComponentEvent evt) {
                addWordsDialogComponentHidden(evt);
            }
            public void componentShown(java.awt.event.ComponentEvent evt) {
                addWordsDialogComponentShown(evt);
            }
        });

        jPanel3.setBorder(javax.swing.BorderFactory.createTitledBorder(resourceMap.getString("jPanel3.border.title"))); // NOI18N
        jPanel3.setName("jPanel3"); // NOI18N

        jTextField2.setText(resourceMap.getString("jTextField2.text")); // NOI18N
        jTextField2.setToolTipText(resourceMap.getString("jTextField2.toolTipText")); // NOI18N
        jTextField2.setName("jTextField2"); // NOI18N

        jLabel11.setText(resourceMap.getString("jLabel11.text")); // NOI18N
        jLabel11.setName("jLabel11"); // NOI18N

        jLabel9.setForeground(resourceMap.getColor("jLabel9.foreground")); // NOI18N
        jLabel9.setText(resourceMap.getString("jLabel9.text")); // NOI18N
        jLabel9.setName("jLabel9"); // NOI18N

        jLabel4.setForeground(resourceMap.getColor("jLabel4.foreground")); // NOI18N
        jLabel4.setText(resourceMap.getString("jLabel4.text")); // NOI18N
        jLabel4.setName("jLabel4"); // NOI18N

        jComboBox3.setToolTipText(resourceMap.getString("jComboBox3.toolTipText")); // NOI18N
        jComboBox3.setName("jComboBox3"); // NOI18N

        jLabel28.setFont(resourceMap.getFont("jLabel28.font")); // NOI18N
        jLabel28.setForeground(resourceMap.getColor("jLabel28.foreground")); // NOI18N
        jLabel28.setText(resourceMap.getString("jLabel28.text")); // NOI18N
        jLabel28.setToolTipText(resourceMap.getString("jLabel28.toolTipText")); // NOI18N
        jLabel28.setName("jLabel28"); // NOI18N
        jLabel28.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                jLabel28MouseClicked(evt);
            }
        });

        jSeparator5.setName("jSeparator5"); // NOI18N

        jScrollPane4.setName("jScrollPane4"); // NOI18N

        jList1.setName("jList1"); // NOI18N
        jScrollPane4.setViewportView(jList1);

        jButton35.setText(resourceMap.getString("jButton35.text")); // NOI18N
        jButton35.setToolTipText(resourceMap.getString("jButton35.toolTipText")); // NOI18N
        jButton35.setName("jButton35"); // NOI18N
        jButton35.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton35ActionPerformed(evt);
            }
        });

        jButton36.setText(resourceMap.getString("jButton36.text")); // NOI18N
        jButton36.setToolTipText(resourceMap.getString("jButton36.toolTipText")); // NOI18N
        jButton36.setName("jButton36"); // NOI18N
        jButton36.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton36ActionPerformed(evt);
            }
        });

        jButton37.setText(resourceMap.getString("jButton37.text")); // NOI18N
        jButton37.setToolTipText(resourceMap.getString("jButton37.toolTipText")); // NOI18N
        jButton37.setName("jButton37"); // NOI18N
        jButton37.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton37ActionPerformed(evt);
            }
        });

        jButton38.setText(resourceMap.getString("jButton38.text")); // NOI18N
        jButton38.setToolTipText(resourceMap.getString("jButton38.toolTipText")); // NOI18N
        jButton38.setName("jButton38"); // NOI18N
        jButton38.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton38ActionPerformed(evt);
            }
        });

        jButton40.setText(resourceMap.getString("jButton40.text")); // NOI18N
        jButton40.setToolTipText(resourceMap.getString("jButton40.toolTipText")); // NOI18N
        jButton40.setName("jButton40"); // NOI18N
        jButton40.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton40ActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout jPanel3Layout = new javax.swing.GroupLayout(jPanel3);
        jPanel3.setLayout(jPanel3Layout);
        jPanel3Layout.setHorizontalGroup(
            jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel3Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jSeparator5, javax.swing.GroupLayout.DEFAULT_SIZE, 559, Short.MAX_VALUE)
                    .addGroup(jPanel3Layout.createSequentialGroup()
                        .addComponent(jScrollPane4, javax.swing.GroupLayout.PREFERRED_SIZE, 389, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jButton38, javax.swing.GroupLayout.DEFAULT_SIZE, 164, Short.MAX_VALUE)
                            .addComponent(jButton36, javax.swing.GroupLayout.DEFAULT_SIZE, 164, Short.MAX_VALUE)
                            .addComponent(jButton37, javax.swing.GroupLayout.DEFAULT_SIZE, 164, Short.MAX_VALUE)))
                    .addGroup(jPanel3Layout.createSequentialGroup()
                        .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jLabel4)
                            .addComponent(jLabel9))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(jPanel3Layout.createSequentialGroup()
                                .addComponent(jComboBox3, javax.swing.GroupLayout.PREFERRED_SIZE, 208, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(jButton35, javax.swing.GroupLayout.DEFAULT_SIZE, 108, Short.MAX_VALUE))
                            .addComponent(jTextField2, javax.swing.GroupLayout.DEFAULT_SIZE, 322, Short.MAX_VALUE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jLabel28, javax.swing.GroupLayout.DEFAULT_SIZE, 136, Short.MAX_VALUE)
                            .addComponent(jButton40, javax.swing.GroupLayout.PREFERRED_SIZE, 126, javax.swing.GroupLayout.PREFERRED_SIZE)))
                    .addComponent(jLabel11, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, 559, Short.MAX_VALUE))
                .addContainerGap())
        );
        jPanel3Layout.setVerticalGroup(
            jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel3Layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jLabel11)
                .addGap(21, 21, 21)
                .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jTextField2, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel4)
                    .addComponent(jButton40))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel9)
                    .addComponent(jComboBox3, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel28)
                    .addComponent(jButton35))
                .addGap(18, 18, 18)
                .addComponent(jSeparator5, javax.swing.GroupLayout.PREFERRED_SIZE, 10, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addGroup(jPanel3Layout.createSequentialGroup()
                        .addGap(6, 6, 6)
                        .addComponent(jButton37)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jButton36)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jButton38))
                    .addGroup(jPanel3Layout.createSequentialGroup()
                        .addGap(2, 2, 2)
                        .addComponent(jScrollPane4, 0, 0, Short.MAX_VALUE)))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        jCheckBox2.setText(resourceMap.getString("jCheckBox2.text")); // NOI18N
        jCheckBox2.setEnabled(false);
        jCheckBox2.setName("jCheckBox2"); // NOI18N

        jButton12.setText(resourceMap.getString("jButton12.text")); // NOI18N
        jButton12.setToolTipText(resourceMap.getString("jButton12.toolTipText")); // NOI18N
        jButton12.setName("jButton12"); // NOI18N
        jButton12.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton12ActionPerformed(evt);
            }
        });

        jButton13.setText(resourceMap.getString("jButton13.text")); // NOI18N
        jButton13.setToolTipText(resourceMap.getString("jButton13.toolTipText")); // NOI18N
        jButton13.setName("jButton13"); // NOI18N
        jButton13.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton13ActionPerformed(evt);
            }
        });

        jButton39.setText(resourceMap.getString("jButton39.text")); // NOI18N
        jButton39.setToolTipText(resourceMap.getString("jButton39.toolTipText")); // NOI18N
        jButton39.setName("jButton39"); // NOI18N
        jButton39.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton39ActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout addWordsDialogLayout = new javax.swing.GroupLayout(addWordsDialog.getContentPane());
        addWordsDialog.getContentPane().setLayout(addWordsDialogLayout);
        addWordsDialogLayout.setHorizontalGroup(
            addWordsDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(addWordsDialogLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(addWordsDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jPanel3, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addGroup(addWordsDialogLayout.createSequentialGroup()
                        .addComponent(jCheckBox2)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 142, Short.MAX_VALUE)
                        .addComponent(jButton13, javax.swing.GroupLayout.PREFERRED_SIZE, 124, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jButton39, javax.swing.GroupLayout.PREFERRED_SIZE, 124, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jButton12, javax.swing.GroupLayout.PREFERRED_SIZE, 97, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addContainerGap())
        );
        addWordsDialogLayout.setVerticalGroup(
            addWordsDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(addWordsDialogLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jPanel3, javax.swing.GroupLayout.PREFERRED_SIZE, 312, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(addWordsDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(jCheckBox2)
                    .addGroup(addWordsDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                        .addComponent(jButton12)
                        .addComponent(jButton39)
                        .addComponent(jButton13)))
                .addGap(16, 16, 16))
        );

        settingsDialog.setTitle(resourceMap.getString("settingsDialog.title")); // NOI18N
        settingsDialog.setAlwaysOnTop(true);
        settingsDialog.setMinimumSize(new java.awt.Dimension(840, 650));
        settingsDialog.setModalityType(java.awt.Dialog.ModalityType.APPLICATION_MODAL);
        settingsDialog.setName("settingsDialog"); // NOI18N
        settingsDialog.setResizable(false);

        jButton14.setText(resourceMap.getString("jButton14.text")); // NOI18N
        jButton14.setToolTipText(resourceMap.getString("jButton14.toolTipText")); // NOI18N
        jButton14.setName("jButton14"); // NOI18N
        jButton14.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton14ActionPerformed(evt);
            }
        });

        jButton15.setText(resourceMap.getString("jButton15.text")); // NOI18N
        jButton15.setToolTipText(resourceMap.getString("jButton15.toolTipText")); // NOI18N
        jButton15.setName("jButton15"); // NOI18N
        jButton15.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton15ActionPerformed(evt);
            }
        });

        jTabbedPane2.setName("jTabbedPane2"); // NOI18N

        jPanel12.setName("jPanel12"); // NOI18N

        jPanel16.setBorder(javax.swing.BorderFactory.createTitledBorder(resourceMap.getString("jPanel16.border.title"))); // NOI18N
        jPanel16.setName("jPanel16"); // NOI18N

        jLabel12.setText(resourceMap.getString("jLabel12.text")); // NOI18N
        jLabel12.setName("jLabel12"); // NOI18N

        jTextField4.setText(resourceMap.getString("jTextField4.text")); // NOI18N
        jTextField4.setToolTipText(resourceMap.getString("jTextField4.toolTipText")); // NOI18N
        jTextField4.setName("jTextField4"); // NOI18N

        jCheckBox1.setText(resourceMap.getString("jCheckBox1.text")); // NOI18N
        jCheckBox1.setToolTipText(resourceMap.getString("jCheckBox1.toolTipText")); // NOI18N
        jCheckBox1.setName("jCheckBox1"); // NOI18N

        jLabel26.setText(resourceMap.getString("jLabel26.text")); // NOI18N
        jLabel26.setName("jLabel26"); // NOI18N

        jComboBox4.setToolTipText(resourceMap.getString("jComboBox4.toolTipText")); // NOI18N
        jComboBox4.setName("jComboBox4"); // NOI18N

        jTextField21.setToolTipText(resourceMap.getString("jTextField21.toolTipText")); // NOI18N
        jTextField21.setName("jTextField21"); // NOI18N

        jButton41.setText(resourceMap.getString("jButton41.text")); // NOI18N
        jButton41.setToolTipText(resourceMap.getString("jButton41.toolTipText")); // NOI18N
        jButton41.setName("jButton41"); // NOI18N
        jButton41.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton41fileSave(evt);
            }
        });

        jLabel31.setText(resourceMap.getString("jLabel31.text")); // NOI18N
        jLabel31.setName("jLabel31"); // NOI18N

        javax.swing.GroupLayout jPanel16Layout = new javax.swing.GroupLayout(jPanel16);
        jPanel16.setLayout(jPanel16Layout);
        jPanel16Layout.setHorizontalGroup(
            jPanel16Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel16Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel16Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jCheckBox1)
                    .addGroup(jPanel16Layout.createSequentialGroup()
                        .addGroup(jPanel16Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jLabel12, javax.swing.GroupLayout.PREFERRED_SIZE, 108, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(jLabel26, javax.swing.GroupLayout.PREFERRED_SIZE, 108, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addGap(76, 76, 76)
                        .addGroup(jPanel16Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jComboBox4, javax.swing.GroupLayout.PREFERRED_SIZE, 216, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(jTextField4, javax.swing.GroupLayout.DEFAULT_SIZE, 552, Short.MAX_VALUE)))
                    .addGroup(jPanel16Layout.createSequentialGroup()
                        .addComponent(jLabel31, javax.swing.GroupLayout.PREFERRED_SIZE, 108, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(76, 76, 76)
                        .addComponent(jTextField21, javax.swing.GroupLayout.DEFAULT_SIZE, 446, Short.MAX_VALUE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jButton41, javax.swing.GroupLayout.PREFERRED_SIZE, 100, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addContainerGap())
        );
        jPanel16Layout.setVerticalGroup(
            jPanel16Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel16Layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jCheckBox1)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(jPanel16Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel12)
                    .addComponent(jTextField4, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel16Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(jLabel26)
                    .addComponent(jComboBox4, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel16Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jTextField21, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel31)
                    .addComponent(jButton41))
                .addContainerGap(250, Short.MAX_VALUE))
        );

        javax.swing.GroupLayout jPanel12Layout = new javax.swing.GroupLayout(jPanel12);
        jPanel12.setLayout(jPanel12Layout);
        jPanel12Layout.setHorizontalGroup(
            jPanel12Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel12Layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jPanel16, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addContainerGap())
        );
        jPanel12Layout.setVerticalGroup(
            jPanel12Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel12Layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jPanel16, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addContainerGap())
        );

        jTabbedPane2.addTab(resourceMap.getString("jPanel12.TabConstraints.tabTitle"), jPanel12); // NOI18N

        jPanel9.setName("jPanel9"); // NOI18N

        jPanel6.setBorder(javax.swing.BorderFactory.createTitledBorder(resourceMap.getString("jPanel6.border.title"))); // NOI18N
        jPanel6.setName("jPanel6"); // NOI18N

        jLabel5.setText(resourceMap.getString("jLabel5.text")); // NOI18N
        jLabel5.setToolTipText(resourceMap.getString("jLabel5.toolTipText")); // NOI18N
        jLabel5.setName("jLabel5"); // NOI18N

        jTextField5.setText(resourceMap.getString("jTextField5.text")); // NOI18N
        jTextField5.setToolTipText(resourceMap.getString("jTextField5.toolTipText")); // NOI18N
        jTextField5.setName("jTextField5"); // NOI18N

        jTextField6.setText(resourceMap.getString("jTextField6.text")); // NOI18N
        jTextField6.setToolTipText(resourceMap.getString("jTextField6.toolTipText")); // NOI18N
        jTextField6.setName("jTextField6"); // NOI18N

        jLabel6.setText(resourceMap.getString("jLabel6.text")); // NOI18N
        jLabel6.setToolTipText(resourceMap.getString("jLabel6.toolTipText")); // NOI18N
        jLabel6.setName("jLabel6"); // NOI18N

        jLabel7.setText(resourceMap.getString("jLabel7.text")); // NOI18N
        jLabel7.setToolTipText(resourceMap.getString("jLabel7.toolTipText")); // NOI18N
        jLabel7.setName("jLabel7"); // NOI18N

        jTextField7.setText(resourceMap.getString("jTextField7.text")); // NOI18N
        jTextField7.setToolTipText(resourceMap.getString("jTextField7.toolTipText")); // NOI18N
        jTextField7.setName("jTextField7"); // NOI18N

        jLabel18.setText(resourceMap.getString("jLabel18.text")); // NOI18N
        jLabel18.setToolTipText(resourceMap.getString("jLabel18.toolTipText")); // NOI18N
        jLabel18.setName("jLabel18"); // NOI18N

        jTextField9.setToolTipText(resourceMap.getString("jTextField9.toolTipText")); // NOI18N
        jTextField9.setName("jTextField9"); // NOI18N

        jLabel25.setText(resourceMap.getString("jLabel25.text")); // NOI18N
        jLabel25.setToolTipText(resourceMap.getString("jLabel25.toolTipText")); // NOI18N
        jLabel25.setName("jLabel25"); // NOI18N

        jTextField17.setToolTipText(resourceMap.getString("jTextField17.toolTipText")); // NOI18N
        jTextField17.setName("jTextField17"); // NOI18N

        javax.swing.GroupLayout jPanel6Layout = new javax.swing.GroupLayout(jPanel6);
        jPanel6.setLayout(jPanel6Layout);
        jPanel6Layout.setHorizontalGroup(
            jPanel6Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel6Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel6Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel6Layout.createSequentialGroup()
                        .addGroup(jPanel6Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jLabel18)
                            .addComponent(jLabel5))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 49, Short.MAX_VALUE)
                        .addGroup(jPanel6Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jTextField5, javax.swing.GroupLayout.PREFERRED_SIZE, 159, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(jTextField9, javax.swing.GroupLayout.PREFERRED_SIZE, 629, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addContainerGap())
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel6Layout.createSequentialGroup()
                        .addGroup(jPanel6Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jLabel25)
                            .addComponent(jLabel7, javax.swing.GroupLayout.PREFERRED_SIZE, 69, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(jLabel6))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 28, Short.MAX_VALUE)
                        .addGroup(jPanel6Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                            .addComponent(jTextField6)
                            .addComponent(jTextField7)
                            .addComponent(jTextField17, javax.swing.GroupLayout.DEFAULT_SIZE, 159, Short.MAX_VALUE))
                        .addGap(482, 482, 482))))
        );
        jPanel6Layout.setVerticalGroup(
            jPanel6Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel6Layout.createSequentialGroup()
                .addGroup(jPanel6Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jTextField9, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel18))
                .addGroup(jPanel6Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel6Layout.createSequentialGroup()
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(jTextField5, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addGroup(jPanel6Layout.createSequentialGroup()
                        .addGap(14, 14, 14)
                        .addComponent(jLabel5)))
                .addGroup(jPanel6Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel6Layout.createSequentialGroup()
                        .addGap(14, 14, 14)
                        .addComponent(jLabel6))
                    .addGroup(jPanel6Layout.createSequentialGroup()
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(jTextField6, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addGap(12, 12, 12)
                .addGroup(jPanel6Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jLabel7, javax.swing.GroupLayout.PREFERRED_SIZE, 23, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jTextField7, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(12, 12, 12)
                .addGroup(jPanel6Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jTextField17, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel25, javax.swing.GroupLayout.PREFERRED_SIZE, 23, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap(204, Short.MAX_VALUE))
        );

        javax.swing.GroupLayout jPanel9Layout = new javax.swing.GroupLayout(jPanel9);
        jPanel9.setLayout(jPanel9Layout);
        jPanel9Layout.setHorizontalGroup(
            jPanel9Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 796, Short.MAX_VALUE)
            .addGroup(jPanel9Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addGroup(jPanel9Layout.createSequentialGroup()
                    .addGap(6, 6, 6)
                    .addComponent(jPanel6, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addGap(7, 7, 7)))
        );
        jPanel9Layout.setVerticalGroup(
            jPanel9Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 450, Short.MAX_VALUE)
            .addGroup(jPanel9Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addGroup(jPanel9Layout.createSequentialGroup()
                    .addContainerGap()
                    .addComponent(jPanel6, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addContainerGap()))
        );

        jTabbedPane2.addTab(resourceMap.getString("jPanel9.TabConstraints.tabTitle"), jPanel9); // NOI18N

        jPanel8.setName("jPanel8"); // NOI18N

        jPanel18.setBorder(javax.swing.BorderFactory.createTitledBorder(resourceMap.getString("jPanel18.border.title"))); // NOI18N
        jPanel18.setName("jPanel18"); // NOI18N

        jTextField10.setText(resourceMap.getString("jTextField10.text")); // NOI18N
        jTextField10.setToolTipText(resourceMap.getString("jTextField10.toolTipText")); // NOI18N
        jTextField10.setMaximumSize(new java.awt.Dimension(12, 29));
        jTextField10.setName("jTextField10"); // NOI18N

        jLabel14.setText(resourceMap.getString("jLabel14.text")); // NOI18N
        jLabel14.setToolTipText(resourceMap.getString("jLabel14.toolTipText")); // NOI18N
        jLabel14.setName("jLabel14"); // NOI18N

        jLabel15.setText(resourceMap.getString("jLabel15.text")); // NOI18N
        jLabel15.setToolTipText(resourceMap.getString("jLabel15.toolTipText")); // NOI18N
        jLabel15.setName("jLabel15"); // NOI18N

        jTextField11.setFont(resourceMap.getFont("jTextField11.font")); // NOI18N
        jTextField11.setText(resourceMap.getString("jTextField11.text")); // NOI18N
        jTextField11.setToolTipText(resourceMap.getString("jTextField11.toolTipText")); // NOI18N
        jTextField11.setName("jTextField11"); // NOI18N

        jLabel20.setText(resourceMap.getString("jLabel20.text")); // NOI18N
        jLabel20.setToolTipText(resourceMap.getString("jLabel20.toolTipText")); // NOI18N
        jLabel20.setName("jLabel20"); // NOI18N

        jLabel22.setText(resourceMap.getString("jLabel22.text")); // NOI18N
        jLabel22.setToolTipText(resourceMap.getString("jLabel22.toolTipText")); // NOI18N
        jLabel22.setName("jLabel22"); // NOI18N

        jLabel23.setText(resourceMap.getString("jLabel23.text")); // NOI18N
        jLabel23.setToolTipText(resourceMap.getString("jLabel23.toolTipText")); // NOI18N
        jLabel23.setName("jLabel23"); // NOI18N

        jTextField13.setText(resourceMap.getString("jTextField13.text")); // NOI18N
        jTextField13.setToolTipText(resourceMap.getString("jTextField13.toolTipText")); // NOI18N
        jTextField13.setName("jTextField13"); // NOI18N

        jLabel24.setText(resourceMap.getString("jLabel24.text")); // NOI18N
        jLabel24.setToolTipText(resourceMap.getString("jLabel24.toolTipText")); // NOI18N
        jLabel24.setName("jLabel24"); // NOI18N

        jTextField15.setToolTipText(resourceMap.getString("jTextField15.toolTipText")); // NOI18N
        jTextField15.setName("jTextField15"); // NOI18N

        jTextField16.setToolTipText(resourceMap.getString("jTextField16.toolTipText")); // NOI18N
        jTextField16.setName("jTextField16"); // NOI18N

        jTextField12.setFont(resourceMap.getFont("jTextField11.font")); // NOI18N
        jTextField12.setToolTipText(resourceMap.getString("jTextField12.toolTipText")); // NOI18N
        jTextField12.setName("jTextField12"); // NOI18N

        javax.swing.GroupLayout jPanel18Layout = new javax.swing.GroupLayout(jPanel18);
        jPanel18.setLayout(jPanel18Layout);
        jPanel18Layout.setHorizontalGroup(
            jPanel18Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel18Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel18Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jLabel20)
                    .addComponent(jLabel15, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, 174, Short.MAX_VALUE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel18Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jTextField11, javax.swing.GroupLayout.PREFERRED_SIZE, 119, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jTextField12, javax.swing.GroupLayout.PREFERRED_SIZE, 119, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(443, 443, 443))
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel18Layout.createSequentialGroup()
                .addGroup(jPanel18Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel18Layout.createSequentialGroup()
                        .addGap(12, 12, 12)
                        .addComponent(jLabel14))
                    .addGroup(jPanel18Layout.createSequentialGroup()
                        .addContainerGap()
                        .addComponent(jLabel24, javax.swing.GroupLayout.DEFAULT_SIZE, 174, Short.MAX_VALUE)))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel18Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(jTextField15, javax.swing.GroupLayout.DEFAULT_SIZE, 550, Short.MAX_VALUE)
                    .addComponent(jTextField10, javax.swing.GroupLayout.PREFERRED_SIZE, 550, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap())
            .addGroup(jPanel18Layout.createSequentialGroup()
                .addGap(12, 12, 12)
                .addComponent(jLabel22, javax.swing.GroupLayout.DEFAULT_SIZE, 174, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jTextField16, javax.swing.GroupLayout.PREFERRED_SIZE, 119, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(443, Short.MAX_VALUE))
            .addGroup(jPanel18Layout.createSequentialGroup()
                .addGap(12, 12, 12)
                .addComponent(jLabel23, javax.swing.GroupLayout.DEFAULT_SIZE, 174, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jTextField13, javax.swing.GroupLayout.PREFERRED_SIZE, 119, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(443, Short.MAX_VALUE))
        );
        jPanel18Layout.setVerticalGroup(
            jPanel18Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel18Layout.createSequentialGroup()
                .addGroup(jPanel18Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel14, javax.swing.GroupLayout.PREFERRED_SIZE, 29, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jTextField10, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel18Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jTextField15, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel24, javax.swing.GroupLayout.PREFERRED_SIZE, 23, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel18Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE, false)
                    .addComponent(jTextField16, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel22, javax.swing.GroupLayout.PREFERRED_SIZE, 23, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(12, 12, 12)
                .addGroup(jPanel18Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(jTextField13, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel23, javax.swing.GroupLayout.PREFERRED_SIZE, 23, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(12, 12, 12)
                .addGroup(jPanel18Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(jTextField11, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel15))
                .addGap(12, 12, 12)
                .addGroup(jPanel18Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE, false)
                    .addComponent(jLabel20)
                    .addComponent(jTextField12, javax.swing.GroupLayout.PREFERRED_SIZE, 31, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(153, 153, 153))
        );

        javax.swing.GroupLayout jPanel8Layout = new javax.swing.GroupLayout(jPanel8);
        jPanel8.setLayout(jPanel8Layout);
        jPanel8Layout.setHorizontalGroup(
            jPanel8Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel8Layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jPanel18, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addContainerGap())
        );
        jPanel8Layout.setVerticalGroup(
            jPanel8Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel8Layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jPanel18, javax.swing.GroupLayout.DEFAULT_SIZE, 426, Short.MAX_VALUE)
                .addContainerGap())
        );

        jTabbedPane2.addTab(resourceMap.getString("jPanel8.TabConstraints.tabTitle"), jPanel8); // NOI18N

        jPanel20.setName("jPanel20"); // NOI18N

        jScrollPane3.setName("jScrollPane3"); // NOI18N

        jTable1.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {

            },
            new String [] {
                "Type", "Class", "Replacement", "Append", "Pattern"
            }
        ) {
            Class[] types = new Class [] {
                java.lang.String.class, java.lang.String.class, java.lang.String.class, java.lang.String.class, java.lang.String.class
            };

            public Class getColumnClass(int columnIndex) {
                return types [columnIndex];
            }
        });
        jTable1.setCellSelectionEnabled(true);
        jTable1.setName("jTable1"); // NOI18N
        jTable1.setRowHeight(25);
        jScrollPane3.setViewportView(jTable1);

        jComboBox5.setToolTipText(resourceMap.getString("jComboBox5.toolTipText")); // NOI18N
        jComboBox5.setName("jComboBox5"); // NOI18N
        jComboBox5.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jComboBox5ActionPerformed(evt);
            }
        });

        jLabel27.setFont(resourceMap.getFont("jLabel27.font")); // NOI18N
        jLabel27.setText(resourceMap.getString("jLabel27.text")); // NOI18N
        jLabel27.setName("jLabel27"); // NOI18N

        jTextField20.setText(resourceMap.getString("jTextField20.text")); // NOI18N
        jTextField20.setToolTipText(resourceMap.getString("jTextField20.toolTipText")); // NOI18N
        jTextField20.setName("jTextField20"); // NOI18N

        jButton6.setText(resourceMap.getString("jButton6.text")); // NOI18N
        jButton6.setToolTipText(resourceMap.getString("jButton6.toolTipText")); // NOI18N
        jButton6.setName("jButton6"); // NOI18N
        jButton6.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton6ActionPerformed(evt);
            }
        });

        jButton25.setText(resourceMap.getString("jButton25.text")); // NOI18N
        jButton25.setToolTipText(resourceMap.getString("jButton25.toolTipText")); // NOI18N
        jButton25.setName("jButton25"); // NOI18N
        jButton25.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton25ActionPerformed(evt);
            }
        });

        jButton31.setText(resourceMap.getString("jButton31.text")); // NOI18N
        jButton31.setToolTipText(resourceMap.getString("jButton31.toolTipText")); // NOI18N
        jButton31.setName("jButton31"); // NOI18N
        jButton31.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton31ActionPerformed(evt);
            }
        });

        jButton32.setText(resourceMap.getString("jButton32.text")); // NOI18N
        jButton32.setToolTipText(resourceMap.getString("jButton32.toolTipText")); // NOI18N
        jButton32.setName("jButton32"); // NOI18N
        jButton32.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton32ActionPerformed(evt);
            }
        });

        jButton33.setText(resourceMap.getString("jButton33.text")); // NOI18N
        jButton33.setToolTipText(resourceMap.getString("jButton33.toolTipText")); // NOI18N
        jButton33.setName("jButton33"); // NOI18N
        jButton33.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton33ActionPerformed(evt);
            }
        });

        jButton34.setText(resourceMap.getString("jButton34.text")); // NOI18N
        jButton34.setToolTipText(resourceMap.getString("jButton34.toolTipText")); // NOI18N
        jButton34.setName("jButton34"); // NOI18N
        jButton34.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton34ActionPerformed(evt);
            }
        });

        jSeparator4.setOrientation(javax.swing.SwingConstants.VERTICAL);
        jSeparator4.setName("jSeparator4"); // NOI18N

        javax.swing.GroupLayout jPanel20Layout = new javax.swing.GroupLayout(jPanel20);
        jPanel20.setLayout(jPanel20Layout);
        jPanel20Layout.setHorizontalGroup(
            jPanel20Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel20Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel20Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(jScrollPane3, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, 772, Short.MAX_VALUE)
                    .addGroup(javax.swing.GroupLayout.Alignment.LEADING, jPanel20Layout.createSequentialGroup()
                        .addGap(6, 6, 6)
                        .addComponent(jLabel27)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 18, Short.MAX_VALUE)
                        .addComponent(jComboBox5, javax.swing.GroupLayout.PREFERRED_SIZE, 189, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(jButton25, javax.swing.GroupLayout.PREFERRED_SIZE, 69, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jButton31, javax.swing.GroupLayout.PREFERRED_SIZE, 70, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(18, 18, 18)
                        .addComponent(jSeparator4, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(18, 18, 18)
                        .addComponent(jTextField20, javax.swing.GroupLayout.PREFERRED_SIZE, 162, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jButton6))
                    .addGroup(jPanel20Layout.createSequentialGroup()
                        .addComponent(jButton34, javax.swing.GroupLayout.PREFERRED_SIZE, 137, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jButton33, javax.swing.GroupLayout.PREFERRED_SIZE, 137, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jButton32, javax.swing.GroupLayout.PREFERRED_SIZE, 137, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addContainerGap())
        );
        jPanel20Layout.setVerticalGroup(
            jPanel20Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel20Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel20Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel20Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                        .addComponent(jLabel27)
                        .addComponent(jButton31)
                        .addComponent(jButton25)
                        .addComponent(jComboBox5, javax.swing.GroupLayout.DEFAULT_SIZE, 33, Short.MAX_VALUE))
                    .addGroup(jPanel20Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                        .addComponent(jButton6)
                        .addComponent(jTextField20, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addComponent(jSeparator4, javax.swing.GroupLayout.PREFERRED_SIZE, 30, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jScrollPane3, javax.swing.GroupLayout.DEFAULT_SIZE, 348, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel20Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jButton32)
                    .addComponent(jButton33)
                    .addComponent(jButton34))
                .addContainerGap())
        );

        jPanel20Layout.linkSize(javax.swing.SwingConstants.VERTICAL, new java.awt.Component[] {jButton25, jButton31, jButton6, jComboBox5, jLabel27, jTextField20});

        jTabbedPane2.addTab(resourceMap.getString("jPanel20.TabConstraints.tabTitle"), jPanel20); // NOI18N

        jPanel22.setName("jPanel22"); // NOI18N

        jPanel23.setBorder(javax.swing.BorderFactory.createTitledBorder(resourceMap.getString("jPanel23.border.title"))); // NOI18N
        jPanel23.setName("jPanel23"); // NOI18N

        jComboBox6.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "Blocked Word List", "Ignored Word List" }));
        jComboBox6.setName("jComboBox6"); // NOI18N
        jComboBox6.addItemListener(new java.awt.event.ItemListener() {
            public void itemStateChanged(java.awt.event.ItemEvent evt) {
                jComboBox6ItemStateChanged(evt);
            }
        });

        jLabel32.setText(resourceMap.getString("jLabel32.text")); // NOI18N
        jLabel32.setName("jLabel32"); // NOI18N

        jLabel33.setText(resourceMap.getString("jLabel33.text")); // NOI18N
        jLabel33.setName("jLabel33"); // NOI18N

        jLabel34.setBackground(resourceMap.getColor("jLabel34.background")); // NOI18N
        jLabel34.setText(resourceMap.getString("jLabel34.text")); // NOI18N
        jLabel34.setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(0, 0, 0)));
        jLabel34.setName("jLabel34"); // NOI18N
        jLabel34.setOpaque(true);
        jLabel34.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                jLabel34MouseClicked(evt);
            }
        });

        jScrollPane9.setName("jScrollPane9"); // NOI18N

        jList3.setModel(new DefaultListModel());
        jList3.setName("jList3"); // NOI18N
        jScrollPane9.setViewportView(jList3);

        jButton44.setText(resourceMap.getString("jButton44.text")); // NOI18N
        jButton44.setName("jButton44"); // NOI18N
        jButton44.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton44ActionPerformed(evt);
            }
        });

        jButton45.setText(resourceMap.getString("jButton45.text")); // NOI18N
        jButton45.setName("jButton45"); // NOI18N
        jButton45.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton45ActionPerformed(evt);
            }
        });

        jButton46.setText(resourceMap.getString("jButton46.text")); // NOI18N
        jButton46.setName("jButton46"); // NOI18N
        jButton46.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton46ActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout jPanel23Layout = new javax.swing.GroupLayout(jPanel23);
        jPanel23.setLayout(jPanel23Layout);
        jPanel23Layout.setHorizontalGroup(
            jPanel23Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel23Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel23Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel23Layout.createSequentialGroup()
                        .addComponent(jScrollPane9, javax.swing.GroupLayout.DEFAULT_SIZE, 736, Short.MAX_VALUE)
                        .addContainerGap())
                    .addGroup(jPanel23Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                        .addGroup(jPanel23Layout.createSequentialGroup()
                            .addGroup(jPanel23Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                                .addComponent(jLabel32, javax.swing.GroupLayout.PREFERRED_SIZE, 164, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addComponent(jLabel33, javax.swing.GroupLayout.PREFERRED_SIZE, 164, javax.swing.GroupLayout.PREFERRED_SIZE))
                            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                            .addGroup(jPanel23Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                                .addComponent(jLabel34, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                .addComponent(jComboBox6, javax.swing.GroupLayout.PREFERRED_SIZE, 180, javax.swing.GroupLayout.PREFERRED_SIZE))
                            .addGap(392, 392, 392))
                        .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel23Layout.createSequentialGroup()
                            .addComponent(jButton45)
                            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                            .addComponent(jButton46, javax.swing.GroupLayout.PREFERRED_SIZE, 106, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                            .addComponent(jButton44)
                            .addContainerGap()))))
        );
        jPanel23Layout.setVerticalGroup(
            jPanel23Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel23Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel23Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jComboBox6, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel33, javax.swing.GroupLayout.PREFERRED_SIZE, 26, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel23Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING, false)
                    .addComponent(jLabel32, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(jLabel34, javax.swing.GroupLayout.PREFERRED_SIZE, 34, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(jScrollPane9, javax.swing.GroupLayout.DEFAULT_SIZE, 247, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(jPanel23Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jButton45, javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(jButton46, javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(jButton44, javax.swing.GroupLayout.Alignment.TRAILING))
                .addGap(14, 14, 14))
        );

        javax.swing.GroupLayout jPanel22Layout = new javax.swing.GroupLayout(jPanel22);
        jPanel22.setLayout(jPanel22Layout);
        jPanel22Layout.setHorizontalGroup(
            jPanel22Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel22Layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jPanel23, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addContainerGap())
        );
        jPanel22Layout.setVerticalGroup(
            jPanel22Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel22Layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jPanel23, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addContainerGap())
        );

        jTabbedPane2.addTab(resourceMap.getString("jPanel22.TabConstraints.tabTitle"), jPanel22); // NOI18N

        javax.swing.GroupLayout settingsDialogLayout = new javax.swing.GroupLayout(settingsDialog.getContentPane());
        settingsDialog.getContentPane().setLayout(settingsDialogLayout);
        settingsDialogLayout.setHorizontalGroup(
            settingsDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, settingsDialogLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(settingsDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(jTabbedPane2, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, 812, Short.MAX_VALUE)
                    .addGroup(settingsDialogLayout.createSequentialGroup()
                        .addComponent(jButton15, javax.swing.GroupLayout.PREFERRED_SIZE, 131, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jButton14, javax.swing.GroupLayout.PREFERRED_SIZE, 97, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addContainerGap())
        );
        settingsDialogLayout.setVerticalGroup(
            settingsDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, settingsDialogLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jTabbedPane2)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(settingsDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jButton14)
                    .addComponent(jButton15))
                .addContainerGap())
        );

        findWordsDialog.setTitle(resourceMap.getString("findWordsDialog.title")); // NOI18N
        findWordsDialog.setAlwaysOnTop(true);
        findWordsDialog.setMinimumSize(new java.awt.Dimension(430, 200));
        findWordsDialog.setName("findWordsDialog"); // NOI18N
        findWordsDialog.setResizable(false);
        findWordsDialog.addComponentListener(new java.awt.event.ComponentAdapter() {
            public void componentHidden(java.awt.event.ComponentEvent evt) {
                findWordsDialogComponentHidden(evt);
            }
        });
        findWordsDialog.addKeyListener(new java.awt.event.KeyAdapter() {
            public void keyPressed(java.awt.event.KeyEvent evt) {
                findWordsDialogKeyPressed(evt);
            }
        });

        jButton16.setText(resourceMap.getString("jButton16.text")); // NOI18N
        jButton16.setName("jButton16"); // NOI18N
        jButton16.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton16ActionPerformed(evt);
            }
        });

        jButton17.setText(resourceMap.getString("jButton17.text")); // NOI18N
        jButton17.setName("jButton17"); // NOI18N
        jButton17.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton17ActionPerformed(evt);
            }
        });

        jPanel17.setBorder(javax.swing.BorderFactory.createTitledBorder(resourceMap.getString("jPanel17.border.title"))); // NOI18N
        jPanel17.setName("jPanel17"); // NOI18N

        jTextField18.setToolTipText(resourceMap.getString("jTextField18.toolTipText")); // NOI18N
        jTextField18.setName("jTextField18"); // NOI18N

        jLabel29.setText(resourceMap.getString("jLabel29.text")); // NOI18N
        jLabel29.setName("jLabel29"); // NOI18N

        javax.swing.GroupLayout jPanel17Layout = new javax.swing.GroupLayout(jPanel17);
        jPanel17.setLayout(jPanel17Layout);
        jPanel17Layout.setHorizontalGroup(
            jPanel17Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel17Layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jLabel29)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jTextField18, javax.swing.GroupLayout.DEFAULT_SIZE, 331, Short.MAX_VALUE)
                .addContainerGap())
        );
        jPanel17Layout.setVerticalGroup(
            jPanel17Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel17Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel17Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jTextField18, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel29))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        javax.swing.GroupLayout findWordsDialogLayout = new javax.swing.GroupLayout(findWordsDialog.getContentPane());
        findWordsDialog.getContentPane().setLayout(findWordsDialogLayout);
        findWordsDialogLayout.setHorizontalGroup(
            findWordsDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(findWordsDialogLayout.createSequentialGroup()
                .addGroup(findWordsDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(findWordsDialogLayout.createSequentialGroup()
                        .addContainerGap()
                        .addComponent(jPanel17, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, findWordsDialogLayout.createSequentialGroup()
                        .addGap(217, 217, 217)
                        .addComponent(jButton17, javax.swing.GroupLayout.PREFERRED_SIZE, 97, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(jButton16, javax.swing.GroupLayout.PREFERRED_SIZE, 97, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addContainerGap())
        );
        findWordsDialogLayout.setVerticalGroup(
            findWordsDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(findWordsDialogLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jPanel17, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 8, Short.MAX_VALUE)
                .addGroup(findWordsDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jButton16)
                    .addComponent(jButton17))
                .addContainerGap())
        );

        filterWordsDialog.setTitle(resourceMap.getString("filterWordsDialog.title")); // NOI18N
        filterWordsDialog.setAlwaysOnTop(true);
        filterWordsDialog.setMinimumSize(new java.awt.Dimension(430, 200));
        filterWordsDialog.setName("filterWordsDialog"); // NOI18N
        filterWordsDialog.setResizable(false);
        filterWordsDialog.addComponentListener(new java.awt.event.ComponentAdapter() {
            public void componentHidden(java.awt.event.ComponentEvent evt) {
                filterWordsDialogComponentHidden(evt);
            }
        });

        jButton19.setText(resourceMap.getString("jButton19.text")); // NOI18N
        jButton19.setName("jButton19"); // NOI18N
        jButton19.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton19ActionPerformed(evt);
            }
        });

        jButton23.setText(resourceMap.getString("jButton23.text")); // NOI18N
        jButton23.setName("jButton23"); // NOI18N
        jButton23.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton23ActionPerformed(evt);
            }
        });

        jPanel19.setBorder(javax.swing.BorderFactory.createTitledBorder(resourceMap.getString("jPanel19.border.title"))); // NOI18N
        jPanel19.setName("jPanel19"); // NOI18N

        jTextField19.setToolTipText(resourceMap.getString("jTextField19.toolTipText")); // NOI18N
        jTextField19.setName("jTextField19"); // NOI18N

        jLabel30.setText(resourceMap.getString("jLabel30.text")); // NOI18N
        jLabel30.setName("jLabel30"); // NOI18N

        javax.swing.GroupLayout jPanel19Layout = new javax.swing.GroupLayout(jPanel19);
        jPanel19.setLayout(jPanel19Layout);
        jPanel19Layout.setHorizontalGroup(
            jPanel19Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel19Layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jLabel30)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jTextField19, javax.swing.GroupLayout.DEFAULT_SIZE, 331, Short.MAX_VALUE)
                .addContainerGap())
        );
        jPanel19Layout.setVerticalGroup(
            jPanel19Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel19Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel19Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jTextField19, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel30))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        javax.swing.GroupLayout filterWordsDialogLayout = new javax.swing.GroupLayout(filterWordsDialog.getContentPane());
        filterWordsDialog.getContentPane().setLayout(filterWordsDialogLayout);
        filterWordsDialogLayout.setHorizontalGroup(
            filterWordsDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(filterWordsDialogLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(filterWordsDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jPanel19, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, filterWordsDialogLayout.createSequentialGroup()
                        .addComponent(jButton23, javax.swing.GroupLayout.PREFERRED_SIZE, 97, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jButton19, javax.swing.GroupLayout.PREFERRED_SIZE, 97, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addContainerGap())
        );
        filterWordsDialogLayout.setVerticalGroup(
            filterWordsDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(filterWordsDialogLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jPanel19, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 8, Short.MAX_VALUE)
                .addGroup(filterWordsDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jButton19)
                    .addComponent(jButton23))
                .addContainerGap())
        );

        tablePopupMenu.setName("tablePopupMenu"); // NOI18N

        jMenuItem13.setText(resourceMap.getString("jMenuItem13.text")); // NOI18N
        jMenuItem13.setToolTipText(resourceMap.getString("jMenuItem13.toolTipText")); // NOI18N
        jMenuItem13.setName("jMenuItem13"); // NOI18N
        jMenuItem13.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jMenuItem13ActionPerformed(evt);
            }
        });
        tablePopupMenu.add(jMenuItem13);

        jMenuItem8.setText(resourceMap.getString("jMenuItem8.text")); // NOI18N
        jMenuItem8.setName("jMenuItem8"); // NOI18N
        jMenuItem8.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jMenuItem8ActionPerformed(evt);
            }
        });
        tablePopupMenu.add(jMenuItem8);

        jMenuItem14.setText(resourceMap.getString("jMenuItem14.text")); // NOI18N
        jMenuItem14.setName("jMenuItem14"); // NOI18N
        jMenuItem14.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jMenuItem14jMenuItem7ActionPerformed(evt);
            }
        });
        tablePopupMenu.add(jMenuItem14);

        jMenuItem9.setText(resourceMap.getString("jMenuItem9.text")); // NOI18N
        jMenuItem9.setName("jMenuItem9"); // NOI18N
        jMenuItem9.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jMenuItem9ActionPerformed(evt);
            }
        });
        tablePopupMenu.add(jMenuItem9);

        jSeparator3.setName("jSeparator3"); // NOI18N
        tablePopupMenu.add(jSeparator3);

        jMenuItem10.setText(resourceMap.getString("jMenuItem10.text")); // NOI18N
        jMenuItem10.setName("jMenuItem10"); // NOI18N
        jMenuItem10.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jMenuItem10ActionPerformed(evt);
            }
        });
        tablePopupMenu.add(jMenuItem10);

        jMenuItem11.setText(resourceMap.getString("jMenuItem11.text")); // NOI18N
        jMenuItem11.setName("jMenuItem11"); // NOI18N
        jMenuItem11.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jMenuItem11ActionPerformed(evt);
            }
        });
        tablePopupMenu.add(jMenuItem11);

        jMenuItem12.setText(resourceMap.getString("jMenuItem12.text")); // NOI18N
        jMenuItem12.setName("jMenuItem12"); // NOI18N
        jMenuItem12.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jMenuItem7ActionPerformed(evt);
            }
        });
        tablePopupMenu.add(jMenuItem12);

        jSeparator6.setName("jSeparator6"); // NOI18N
        tablePopupMenu.add(jSeparator6);

        jMenuItem15.setText(resourceMap.getString("jMenuItem15.text")); // NOI18N
        jMenuItem15.setToolTipText(resourceMap.getString("jMenuItem15.toolTipText")); // NOI18N
        jMenuItem15.setName("jMenuItem15"); // NOI18N
        jMenuItem15.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jMenuItem15jMenuItem7ActionPerformed(evt);
            }
        });
        tablePopupMenu.add(jMenuItem15);

        jMenuItem16.setText(resourceMap.getString("jMenuItem16.text")); // NOI18N
        jMenuItem16.setToolTipText(resourceMap.getString("jMenuItem16.toolTipText")); // NOI18N
        jMenuItem16.setName("jMenuItem16"); // NOI18N
        jMenuItem16.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jMenuItem16jMenuItem7ActionPerformed(evt);
            }
        });
        tablePopupMenu.add(jMenuItem16);

        suggestionDialog.setTitle(resourceMap.getString("suggestionsDialog.title")); // NOI18N
        suggestionDialog.setAlwaysOnTop(true);
        suggestionDialog.setMinimumSize(new java.awt.Dimension(430, 450));
        suggestionDialog.setName("suggestionsDialog"); // NOI18N
        suggestionDialog.setResizable(false);
        suggestionDialog.addComponentListener(new java.awt.event.ComponentAdapter() {
            public void componentHidden(java.awt.event.ComponentEvent evt) {
                suggestionDialogComponentHidden(evt);
            }
        });

        jButton42.setText(resourceMap.getString("jButton42.text")); // NOI18N
        jButton42.setName("jButton42"); // NOI18N
        jButton42.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton42ActionPerformed(evt);
            }
        });

        jButton43.setText(resourceMap.getString("jButton43.text")); // NOI18N
        jButton43.setName("jButton43"); // NOI18N
        jButton43.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton43ActionPerformed(evt);
            }
        });

        jPanel21.setBorder(javax.swing.BorderFactory.createTitledBorder(resourceMap.getString("jPanel21.border.title"))); // NOI18N
        jPanel21.setName("jPanel21"); // NOI18N

        jScrollPane8.setName("jScrollPane8"); // NOI18N

        jList2.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        jList2.setName("jList2"); // NOI18N
        jList2.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                jList2MouseClicked(evt);
            }
        });
        jScrollPane8.setViewportView(jList2);

        javax.swing.GroupLayout jPanel21Layout = new javax.swing.GroupLayout(jPanel21);
        jPanel21.setLayout(jPanel21Layout);
        jPanel21Layout.setHorizontalGroup(
            jPanel21Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel21Layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jScrollPane8, javax.swing.GroupLayout.DEFAULT_SIZE, 298, Short.MAX_VALUE)
                .addContainerGap())
        );
        jPanel21Layout.setVerticalGroup(
            jPanel21Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel21Layout.createSequentialGroup()
                .addComponent(jScrollPane8, javax.swing.GroupLayout.DEFAULT_SIZE, 306, Short.MAX_VALUE)
                .addContainerGap())
        );

        javax.swing.GroupLayout suggestionDialogLayout = new javax.swing.GroupLayout(suggestionDialog.getContentPane());
        suggestionDialog.getContentPane().setLayout(suggestionDialogLayout);
        suggestionDialogLayout.setHorizontalGroup(
            suggestionDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(suggestionDialogLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(suggestionDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, suggestionDialogLayout.createSequentialGroup()
                        .addComponent(jButton43, javax.swing.GroupLayout.PREFERRED_SIZE, 97, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jButton42, javax.swing.GroupLayout.PREFERRED_SIZE, 97, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addContainerGap())
                    .addGroup(suggestionDialogLayout.createSequentialGroup()
                        .addComponent(jPanel21, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addGap(12, 12, 12))))
        );
        suggestionDialogLayout.setVerticalGroup(
            suggestionDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, suggestionDialogLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jPanel21, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(suggestionDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jButton42)
                    .addComponent(jButton43))
                .addContainerGap())
        );

        suggestionDialogLayout.linkSize(javax.swing.SwingConstants.VERTICAL, new java.awt.Component[] {jButton42, jButton43});

        jColorChooser1.setName("jColorChooser1"); // NOI18N

        setComponent(mainPanel);
        setMenuBar(menuBar);
        setStatusBar(statusPanel);
    }// </editor-fold>//GEN-END:initComponents

    //Process a given dic file and generate all words
    private void fileSave(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_fileSave

        String dicFile = jTextField3.getText();

        if (dicFile.isEmpty()){
            setStatusMessage("No file to load", true);
            JOptionPane.showMessageDialog(null, "No file to load");
            return;
        }

        String affFile = dicFile.replace(".dic", ".aff");

        setStatusMessage("Processing dic/aff files");
        String wordlist = unmunch(dicFile, affFile, tmp.concat("tmp1"));
        System.out.println(wordlist);

        setStatusMessage("Processing was completed & wordlist generated.");
        this.jTextArea2.setText(wordlist);

    }//GEN-LAST:event_fileSave

    /**
     *
     * @param filePath the path of the file to load
     * @param wordCategory the word cateogry to be used in the hash
     * @param shouldReset if the wordset should be remove 
     */
    private void loadWordLists(String filePath, int wordCategory, boolean shouldReset){

        if (shouldReset){
            wordlists.clear();
        }
        
        try{
            BufferedReader bf = new BufferedReader(new FileReader(filePath));

            String line = null;
            while ((line = bf.readLine()) != null){                
                wordlistAddWord(line.trim(), wordCategory);
            }
        }catch (Exception ex){
            setStatusMessage("Error in loading wordlist category " + wordCategory, true);
            Logger.getLogger(SinhalaDictionaryToolsView.class.getName()).log(Level.SEVERE, null, ex);
        }

    }

    /**
     * @param key the key in the wordlist hash
     * @param value the value to identify the category of a word
     */
    private void wordlistAddWord(String key, int value){
        if (!wordlists.containsKey(key)){
            wordlists.put(key, value);
        }else{
            wordlists.put(key, wordlists.get(key) + value);
        }
    }

    private void fillWordlists(){
        DefaultListModel model = (DefaultListModel) jList3.getModel();

        model.clear();

        Iterator<String> it = wordlists.keySet().iterator();

        while (it.hasNext()){
            String next = it.next();

            if ((jComboBox6.getSelectedIndex() == 0) && (wordlists.get(next) & BLOCKED_WORD) > 0){
                model.addElement(next);
            } else if ((jComboBox6.getSelectedIndex() == 1) && (wordlists.get(next) & IGNORED_WORD) > 0){
                model.addElement(next);
            }
        }

        //if blocked list selected, disable colour selection
        if (jComboBox6.getSelectedIndex() == 0){
            jLabel34.setBackground(new Color(Integer.parseInt(
                    conf.getProperty("bannedColour", "ff0000", "wordlists"),16)));
        }else if (jComboBox6.getSelectedIndex() == 1){
            jLabel34.setBackground(new Color(Integer.parseInt(
                    conf.getProperty("ignoredColour", "0000ff", "wordlists"),16)));
        }

    }


    /**
     * Remove a value from the wordlists
     *
     * @param value the value to remove from the list
     */
    private void removeWordFromList(String value, int wordType){
        Integer val = wordlists.get(value);

        if (val != null){
            if (wordType == BLOCKED_WORD || wordType == IGNORED_WORD){
                val -= wordType;
            }

            if (val > 0){
                wordlists.put((String)value, val);
            }else{
                wordlists.remove(value);
            }
        }
    }

    /**
     * Add a word to the wordlist of
     *
     * @param value The word to add
     * @param wordType The word type to add
     */
    private void addWordToList(String value, int wordType){

        Integer val = wordlists.get(value);

        if (val == null){
            val = 0;
        }

        if (wordType == BLOCKED_WORD && (val & BLOCKED_WORD) == 0){
            val += BLOCKED_WORD;
        }else if (wordType == IGNORED_WORD && (val & IGNORED_WORD) == 0){
            val += IGNORED_WORD;
        }

        if (val > 0){
            wordlists.put((String)value, val);
        }
    }

    /**
     * Save wordlists 
     */
    private void saveWordList(String blockedPath, String ignoredPath, int saveType){

        BufferedWriter out1 = null;
        BufferedWriter out2 = null;
        
        try {

            if ((saveType & BLOCKED_WORD) > 0 ){
                out1 = new BufferedWriter(new FileWriter(blockedPath));
            }
            if ((saveType & IGNORED_WORD) > 0){
                out2 = new BufferedWriter(new FileWriter(ignoredPath));
            }
                                    
            Iterator<String> it = wordlists.keySet().iterator();
            while (it.hasNext()) {
                String word = it.next();
                if ((saveType & BLOCKED_WORD) > 0 && (wordlists.get(word) & BLOCKED_WORD) > 0) {
                    out1.write(word + System.getProperty("line.separator"));
                }
                if ((saveType & IGNORED_WORD) > 0 && (wordlists.get(word) & IGNORED_WORD) > 0) {
                    out2.write(word + System.getProperty("line.separator"));
                }
            }
            
        } catch (IOException ex) {
            Logger.getLogger(SinhalaDictionaryToolsView.class.getName()).log(Level.SEVERE, null, ex);
            setStatusMessage("Couldn't save the wordlist");
        } finally {
            try {
                if ((saveType & BLOCKED_WORD) > 0){
                    out1.close();
                }
                if ((saveType & IGNORED_WORD) > 0){
                    out2.close();
                }
            } catch (IOException ex) {
                Logger.getLogger(SinhalaDictionaryToolsView.class.getName()).log(Level.SEVERE, null, ex);
            }
        }

    }

    /*************************************************************************
     *  Actions *
     *************************************************************************/

    //Select a dic file
    private void jButton4fileSave(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton4fileSave

        int returnVal = fileChooser.showOpenDialog(this.getFrame());
        if (returnVal == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            jTextField3.setText(file.getAbsolutePath());
            
        } else {
            setStatusMessage("Couldn't create temp dic/aff files.", true);
        }

    }//GEN-LAST:event_jButton4fileSave

    //Generate a tmp dic and aff file and generate a wordlist
    private void jButton2ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton2ActionPerformed

        try {

            String dicText = this.jTextField1.getText();

            if (dicText.isEmpty()){
                setStatusMessage("No dictionary text to process.", true);
                JOptionPane.showMessageDialog(null, "No dictionary text to process.");
                return;
            }

            String wordlist = unmunchSingleWord(dicText, this.jTextArea1.getText(), "tmp1");

            this.jTextArea2.setText(wordlist);

        } catch (IOException ex) {
            
            setStatusMessage("Couldn't create temp dic/aff files.", true);
            Logger.getLogger(SinhalaDictionaryToolsView.class.getName()).log(Level.SEVERE, null, ex);
        }

    }//GEN-LAST:event_jButton2ActionPerformed

    private void jTextField1MouseEntered(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_jTextField1MouseEntered
        setStatusMessage(this.jTextField1);
    }//GEN-LAST:event_jTextField1MouseEntered

    private void jTextField1MouseExited(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_jTextField1MouseExited
        clearStatusMessage();
    }//GEN-LAST:event_jTextField1MouseExited

    private void jTextArea1MouseEntered(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_jTextArea1MouseEntered
        setStatusMessage(this.jTextArea1);
    }//GEN-LAST:event_jTextArea1MouseEntered

    private void jTextArea1MouseExited(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_jTextArea1MouseExited
        clearStatusMessage();
    }//GEN-LAST:event_jTextArea1MouseExited

    private void jTextField3MouseEntered(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_jTextField3MouseEntered
        setStatusMessage(this.jTextField3);
    }//GEN-LAST:event_jTextField3MouseEntered

    private void jTextField3MouseExited(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_jTextField3MouseExited
        clearStatusMessage();
    }//GEN-LAST:event_jTextField3MouseExited

    //load a file to analyzer
    private void jButton5fileSave(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton5fileSave
        int returnVal = fileChooser2.showOpenDialog(this.getFrame());
        if (returnVal == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser2.getSelectedFile();
            jTextField14.setText(file.getAbsolutePath());

        } else {
            setStatusMessage("File access cancelled by user.", true);
        }
    }//GEN-LAST:event_jButton5fileSave


    //load a file to table1
    private void jButton47ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton47ActionPerformed

        loadFile(1);
        setTableCellRenderers();
    }//GEN-LAST:event_jButton47ActionPerformed

    //load a file to table2
    private void jButton50ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton50ActionPerformed
        
        loadFile(2);
        setTableCellRenderers();
    }//GEN-LAST:event_jButton50ActionPerformed

    //Analyze button - tab1
    private void jButton8ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton8ActionPerformed
        
        if (!jTextArea2.getText().isEmpty()){
            moveToAnalyze(tmp.concat("tmp1"));
        }else{
            JOptionPane.showMessageDialog(null, "You haven't processed any dictionary file yet.");
        }
    }//GEN-LAST:event_jButton8ActionPerformed

    //save the firts text area to file out
    private void jButton1ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton1ActionPerformed

        saveToTmpFile("tmp1");
    }//GEN-LAST:event_jButton1ActionPerformed

    //start crawling a given url
    private void jButton7ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton7ActionPerformed

        LangCrawler crawler = LangCrawler.getCrawler();        


        if (crawler.getState() != CrawlEvent.STARTED){
            try {

                if (jTextField8.getText().isEmpty()){
                    JOptionPane.showMessageDialog(this.getFrame(), "Enter a url as the start point for crawling");
                    return;
                }

                crawler.setRoot(new Link(jTextField8.getText()));
                crawler.configure(conf);
                crawler.addCrawlListener(new LangCrawlerListener(jButton7, jButton11));
                crawler.setAction(new LangAction(this.statusMessageLabel));
                crawler.addObserver(new CrawlObserver(this.jTextArea3));

                DownloadParameters dp = new DownloadParameters();
                dp.changeObeyRobotExclusion(true);
                dp.changeUserAgent(conf.getProperty("name", "LangCrawler", "crawl") + " Mozilla/5.0 (X11; U; "+System.getProperty("os.name")
                           +System.getProperty("os.arch") + "; en-US; rv:1.8.1.4) WebSPHINX 0.5");
                crawler.setDownloadParameters(dp);
                this.jTextArea3.setText("");

                new Thread(crawler).start();

            }   catch (MalformedURLException ex) {
                Logger.getLogger(SinhalaDictionaryToolsView.class.getName()).log(Level.SEVERE, null, ex);
            } catch (IOException ex) {
                Logger.getLogger(SinhalaDictionaryToolsView.class.getName()).log(Level.SEVERE, null, ex);
            }
        }else{
            crawler.stop();
        }
    }//GEN-LAST:event_jButton7ActionPerformed

    //save crawl settings
    //pause crawling
    private void jButton11ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton11ActionPerformed
        LangCrawler crawler = LangCrawler.getCrawler();

        if (crawler.getState() == CrawlEvent.PAUSED){
            new Thread(crawler).start();
        }else{
            crawler.pause();
        }
    }//GEN-LAST:event_jButton11ActionPerformed

    //move to analyzer
    private void jButton10ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton10ActionPerformed

        File f = new File(tmp.concat("tmp2"));
        new FileOutput(f, jTextArea3.getText(),
                conf.getBooleanProperty("sortBeforeSave", true, "general")).start();
        moveToAnalyze(f.getPath());
        jButton47.doClick();
    }//GEN-LAST:event_jButton10ActionPerformed

    //save to output file - dic/aff or txt
    private void jButton9ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton9ActionPerformed

        new FileOutput(new File(tmp.concat("tmp2")), jTextArea3.getText(),
                conf.getBooleanProperty("sortBeforeSave", true, "general")).start();
        saveToTmpFile("tmp2");        
    }//GEN-LAST:event_jButton9ActionPerformed

    //remove selected rows from the table 1
    private void jButton18ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton18ActionPerformed
       
        removeWords(1);

    }//GEN-LAST:event_jButton18ActionPerformed

    //add words to the table1
    private void jButton20ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton20ActionPerformed

        addNewWord(1);
    }//GEN-LAST:event_jButton20ActionPerformed

    //save words from table 1
    private void jButton24ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton24ActionPerformed

        saveTable(1);

    }//GEN-LAST:event_jButton24ActionPerformed

    //remove words from table 2
    private void jButton26ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton26ActionPerformed

        removeWords(2);
    }//GEN-LAST:event_jButton26ActionPerformed

    //add words to table 2
    private void jButton27ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton27ActionPerformed

        addNewWord(2);
    }//GEN-LAST:event_jButton27ActionPerformed

    //save words from table 2
    private void jButton28ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton28ActionPerformed

        saveTable(2);
    }//GEN-LAST:event_jButton28ActionPerformed

    //move table1 list to table 2
    private void jButton21ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton21ActionPerformed

        mergeTables(jTable4, jTable3);

    }//GEN-LAST:event_jButton21ActionPerformed


    //move table2 list to table1
    private void jButton22ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton22ActionPerformed

        mergeTables(jTable3, jTable4);

    }//GEN-LAST:event_jButton22ActionPerformed

    //Table 2 - Table 1
    private void jButton29ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton29ActionPerformed

        diffTables(jTable3, jTable4);

    }//GEN-LAST:event_jButton29ActionPerformed

    //Table 1 - Table 2
    private void jButton30ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton30ActionPerformed

        diffTables(jTable4, jTable3);
    }//GEN-LAST:event_jButton30ActionPerformed

    //select 1st tab
    private void jMenuItem2ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuItem2ActionPerformed

        jTabbedPane1.setSelectedIndex(0);

    }//GEN-LAST:event_jMenuItem2ActionPerformed

    //select 2nd tab
    private void jMenuItem1ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuItem1ActionPerformed

        jTabbedPane1.setSelectedIndex(1);
    }//GEN-LAST:event_jMenuItem1ActionPerformed

    //select 3rd tab
    private void jMenuItem3ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuItem3ActionPerformed
        jTabbedPane1.setSelectedIndex(2);

    }//GEN-LAST:event_jMenuItem3ActionPerformed

    //save configurations
    private void jButton15ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton15ActionPerformed
        setConfigs();
        settingsDialog.setVisible(false);
    }//GEN-LAST:event_jButton15ActionPerformed

    private void jButton14ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton14ActionPerformed
        settingsDialog.setVisible(false);
    }//GEN-LAST:event_jButton14ActionPerformed

    private void jMenuItem4ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuItem4ActionPerformed
        getConfigs();
        settingsDialog.setVisible(true);
    }//GEN-LAST:event_jMenuItem4ActionPerformed

    //change 1st combo box item selection
    private void jComboBox1ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jComboBox1ActionPerformed
        TableModel model = (TableModel)jTable4.getModel();        

        if (!isTableLoading && jComboBox1.getSelectedItem() != null){
            model.setFilter(jComboBox1.getSelectedItem().toString());            
        }
                
    }//GEN-LAST:event_jComboBox1ActionPerformed

    //change 2nd combo box item selection
    private void jComboBox2ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jComboBox2ActionPerformed

        TableModel model = (TableModel)jTable3.getModel();

        if (!isTableLoading && jComboBox2.getSelectedItem() != null){
            model.setFilter(jComboBox2.getSelectedItem().toString());            
        }                
    }//GEN-LAST:event_jComboBox2ActionPerformed

    /**
     *
     * @param baseword the base dictionary word
     * @param affixes the set of affix keys to be appended
     * @return the word appended with affixes
     */
    private String addAffixes(String baseword, String[] affixes){

        for (int i=0; i < affixes.length; i++){

            if (i==0){
                baseword += "/";
            }else{
                baseword += ",";
            }

            baseword += affixes[i];
            
        }
        
        return baseword;
    }

    //Add a word to the table
    private void jButton13ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton13ActionPerformed

        //confirm if there are words generated in the list
        if (jList1.getModel().getSize() > 0){
            if (JOptionPane.showConfirmDialog(addWordsDialog,
                    "The word list already has some generated words. " +
                    "Do you really want to add all possible word in this category (ignoring words in the list) ?",
                    "Really want to proceed ?", JOptionPane.YES_NO_OPTION) == JOptionPane.NO_OPTION){
                return;
            }
        }

        String dicText = jTextField2.getText();

        if (!dicText.isEmpty()){

            try {
                String[] arrayWordList = null;
                TableModel model = null;

                if (jComboBox3.getSelectedIndex() == 0){
                    arrayWordList = new String[]{dicText};
                }else{
                    dicText = addAffixes(dicText, vconf.getArray(jComboBox3.getSelectedItem().toString(),
                                                new String[]{}, "categories"));

                    File affFile = new File(vconf.getProperty("affpath", "config/global.aff", "general"));
                    String wordlist = generateAllAddWords(dicText, affFile, "tmp5");
                    arrayWordList = wordlist.split(System.getProperty("line.separator"));
                }
               
                if (jCheckBox2.isSelected()) {
                    model = (TableModel) jTable4.getModel();
                } else {
                    model = (TableModel) jTable3.getModel();                    
                }

                for (String word: arrayWordList){
                    model.addRow(word);
                }
                
            } catch (IOException ex) {
                Logger.getLogger(SinhalaDictionaryToolsView.class.getName()).log(Level.SEVERE, null, ex);
            }
        }

        addWordsDialog.setVisible(false);
    }//GEN-LAST:event_jButton13ActionPerformed

    //cancel add words dialog
    private void jButton12ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton12ActionPerformed
        addWordsDialog.setVisible(false);
    }//GEN-LAST:event_jButton12ActionPerformed

    //show add words dialog
    private void addWordsDialogComponentShown(java.awt.event.ComponentEvent evt) {//GEN-FIRST:event_addWordsDialogComponentShown

        jComboBox3.removeAllItems();

        jComboBox3.addItem("None");
        for (String cat: vconf.getPropertyNames("categories")){
            jComboBox3.addItem(cat);
        }

        jList1.setModel(new DefaultListModel());
    }//GEN-LAST:event_addWordsDialogComponentShown

    //key press on table1
    private void jTable4KeyPressed(java.awt.event.KeyEvent evt) {//GEN-FIRST:event_jTable4KeyPressed

        if (evt.getKeyCode() == 127){
            jButton18.doClick();
        }

    }//GEN-LAST:event_jTable4KeyPressed

    //key press on table2
    private void jTable3KeyPressed(java.awt.event.KeyEvent evt) {//GEN-FIRST:event_jTable3KeyPressed

        if (evt.getKeyCode() == 127){
            jButton26.doClick();
        }
    }//GEN-LAST:event_jTable3KeyPressed

    //cancel find words dialog
    private void jButton16ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton16ActionPerformed

        findWordsDialog.setVisible(false);
    }//GEN-LAST:event_jButton16ActionPerformed

    //find word button
    private void jButton17ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton17ActionPerformed
        TableModel model = (TableModel)lastFocusedTable.getModel();

        if (lastRow != 0){
            lastRow += 1;
        }

        while ((lastRow = model.findKey(jTextField18.getText(), lastRow)) != -1){
            lastFocusedTable.setRowSelectionInterval(lastRow, lastRow);
            lastFocusedTable.scrollRectToVisible(lastFocusedTable.getCellRect(lastRow, 0, true));
            break;
        }

        if (lastRow == -1){
            JOptionPane.showMessageDialog(null, "Reached the end of the list and word not found. Re-starting from the beginning.");
            lastRow = 0;
        }

    }//GEN-LAST:event_jButton17ActionPerformed

    //show word find dialog
    private void jMenuItem5ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuItem5ActionPerformed

        if (lastFocusedTable != null){
            findWordsDialog.setVisible(true);
            lastRow = 0;
        }else{
            JOptionPane.showMessageDialog(null, "No wordlist loaded.");
        }
        
    }//GEN-LAST:event_jMenuItem5ActionPerformed

    //jTable focus gained
    private void jTable4FocusGained(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_jTable4FocusGained
        jMenuItem5.setEnabled(true);
        jMenuItem6.setEnabled(true);
        jMenuItem7.setEnabled(true);
        lastFocusedTable = jTable4;
    }//GEN-LAST:event_jTable4FocusGained

    //jTable focus lost
    private void jTable3FocusGained(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_jTable3FocusGained
        jMenuItem5.setEnabled(true);
        jMenuItem6.setEnabled(true);
        jMenuItem7.setEnabled(true);
        lastFocusedTable = jTable3;
    }//GEN-LAST:event_jTable3FocusGained

    //show filter table dialog
    private void jMenuItem6ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuItem6ActionPerformed

        if (lastFocusedTable != null){
            filterWordsDialog.setVisible(true);            
        }else{
            JOptionPane.showMessageDialog(null, "No wordlist loaded.");
        }
    }//GEN-LAST:event_jMenuItem6ActionPerformed

    //Filter Cancel
    private void jButton19ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton19ActionPerformed

        filterWordsDialog.setVisible(false);
    }//GEN-LAST:event_jButton19ActionPerformed

    //Filter OK
    private void jButton23ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton23ActionPerformed

        isTableLoading = true;
        TableModel model = (TableModel)lastFocusedTable.getModel();
        String word = jTextField19.getText().trim();
        model.setFilter(word);        
        isTableLoading = false;
    }//GEN-LAST:event_jButton23ActionPerformed

    private void findWordsDialogKeyPressed(java.awt.event.KeyEvent evt) {//GEN-FIRST:event_findWordsDialogKeyPressed

        System.out.println(evt.getKeyCode());
        if (evt.getKeyCode() == KeyEvent.VK_ESCAPE){
            jButton16ActionPerformed(null);
        }

    }//GEN-LAST:event_findWordsDialogKeyPressed

    //filter dialog hidden
    private void filterWordsDialogComponentHidden(java.awt.event.ComponentEvent evt) {//GEN-FIRST:event_filterWordsDialogComponentHidden
        TableModel model = (TableModel)lastFocusedTable.getModel();
        model.setFilter("All");

        jTextField19.setText("");
    }//GEN-LAST:event_filterWordsDialogComponentHidden

    private void jMenuItem7ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuItem7ActionPerformed
        TableModel model = (TableModel)lastFocusedTable.getModel();
        String msg = "";
        int rowcount = lastFocusedTable.getSelectedRowCount();

        if (rowcount != 0){
            msg = rowcount + " rows selected.";
        }else{
            rowcount = model.getRowCount();
            msg = "Total row count is " + rowcount;
        }

        JOptionPane.showMessageDialog(this.getFrame(), msg);
    }//GEN-LAST:event_jMenuItem7ActionPerformed

    //show popupmenu
    private void jTable4MouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_jTable4MouseClicked

        if (evt.isPopupTrigger()){
            tablePopupMenu.show(evt.getComponent(), evt.getX(), evt.getY());
            currentTableId = 1;
        }
        
    }//GEN-LAST:event_jTable4MouseClicked

    //add a word popupmenu
    private void jMenuItem8ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuItem8ActionPerformed

        popupHelper(currentTableId, jTextField2);
        addNewWord(currentTableId);
    }//GEN-LAST:event_jMenuItem8ActionPerformed

    private void jTable3MouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_jTable3MouseClicked

        if (evt.isPopupTrigger()){
            tablePopupMenu.show(evt.getComponent(), evt.getX(), evt.getY());
            currentTableId = 2;
        }
    }//GEN-LAST:event_jTable3MouseClicked

    private void jMenuItem9ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuItem9ActionPerformed
        removeWords(currentTableId);
    }//GEN-LAST:event_jMenuItem9ActionPerformed

    private void jMenuItem13ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuItem13ActionPerformed

        if (lastFocusedTable != null){
            String word = (String) lastFocusedTable.getValueAt(lastFocusedTable.getSelectedRow(), lastFocusedTable.getSelectedColumn());
            
            StringSelection stringSelection = new StringSelection( word );
            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            clipboard.setContents( stringSelection, (TableModel)lastFocusedTable.getModel() );

        }
    }//GEN-LAST:event_jMenuItem13ActionPerformed

    private void jMenuItem10ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuItem10ActionPerformed

        popupHelper(currentTableId, jTextField18);
        jMenuItem5ActionPerformed(evt);
    }//GEN-LAST:event_jMenuItem10ActionPerformed

    private void jMenuItem11ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuItem11ActionPerformed

        popupHelper(currentTableId, jTextField19);
        jMenuItem6ActionPerformed(evt);
    }//GEN-LAST:event_jMenuItem11ActionPerformed

    private void addWordsDialogComponentHidden(java.awt.event.ComponentEvent evt) {//GEN-FIRST:event_addWordsDialogComponentHidden

        jTextField2.setText("");

        DefaultListModel listModel = (DefaultListModel)jList1.getModel();
        listModel.removeAllElements();

    }//GEN-LAST:event_addWordsDialogComponentHidden

    private void findWordsDialogComponentHidden(java.awt.event.ComponentEvent evt) {//GEN-FIRST:event_findWordsDialogComponentHidden

        jTextField18.setText("");
    }//GEN-LAST:event_findWordsDialogComponentHidden

    //delete a category
    private void jButton31ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton31ActionPerformed
        
        if (JOptionPane.showConfirmDialog(settingsDialog, "Are you sure want to delete this category ? This action is irreversible.", "Confirm action", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION){
              
            vconf.removeProperty((String)jComboBox5.getSelectedItem(), "categories");
            saveSettings("config/vocabulary_catagory.xml", vconf);            
            getConfigs();
        }
        
    }//GEN-LAST:event_jButton31ActionPerformed

    //add a new category
    private void jButton6ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton6ActionPerformed

        Set<String> properties =  vconf.getProperties("categories").stringPropertyNames();
        int max = 0;
        for (String property: properties){
            int temp = vconf.getIntProperty(property, max, "categories");
            if (max < temp){
                max = temp;
            }
        }

        vconf.setIntProperty(jTextField20.getText(), max+1, "categories");
        saveSettings("config/vocabulary_catagory.xml", vconf);

        jTextField20.setText("");
        JOptionPane.showMessageDialog(settingsDialog, "New category added");

        getConfigs();
    }//GEN-LAST:event_jButton6ActionPerformed

    //modify a category
    private void jButton25ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton25ActionPerformed

        String value = vconf.getProperty((String)jComboBox5.getSelectedItem(), "1", "categories");

        String categoryName = JOptionPane.showInputDialog(settingsDialog, "Enter the modified category name",
                (String)jComboBox5.getSelectedItem());

        if (categoryName != null){
            
            //remove current value
            vconf.removeProperty((String)jComboBox5.getSelectedItem(), "categories");
            //set the new property name
            vconf.setProperty(categoryName, value, "categories");

            jComboBox5.removeItem(jComboBox5.getSelectedItem());
            jComboBox5.addItem(categoryName);

            //save settings
            saveSettings("config/vocabulary_catagory.xml", vconf);

            JOptionPane.showMessageDialog(settingsDialog, "The category was modified");
        }

    }//GEN-LAST:event_jButton25ActionPerformed

    //when categories combobox changes
    private void jComboBox5ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jComboBox5ActionPerformed
        String index = vconf.getProperty((String)jComboBox5.getSelectedItem(), "1", "categories");

        if (index.length() > 0){
            jTable1.setModel(new HunspellTableModel(vocRules, index));
            TableColumnModel columnModel = jTable1.getColumnModel();

            for (int i = 0; i < columnModel.getColumnCount(); i++) {
                TableColumn column = columnModel.getColumn(i);

                if (i == 0){
                    column.setCellEditor(new HunspellTableCellEditor(conf.getProperty("font", "Arial", "general"),
                            new JComboBox(new String[]{"PFX", "SFX"} )));
                }else{

                    JTextField textCell = new JTextField();
                    textCell.setFont(new Font(conf.getProperty("font", "Arial", "general"), Font.PLAIN, 12));
                    column.setCellEditor(new HunspellTableCellEditor(conf.getProperty("font", "Arial", "general"),
                            textCell));

                }

            }

        }
    }//GEN-LAST:event_jComboBox5ActionPerformed

    //delete the selected row
    private void jButton34ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton34ActionPerformed
        
        HunspellTableModel tmpModel = (HunspellTableModel)jTable1.getModel();
        tmpModel.removeRow(jTable1.getSelectedRow(), this.vocClasses);

    }//GEN-LAST:event_jButton34ActionPerformed

    //add row to hunspell rules table
    private void jButton33ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton33ActionPerformed

        HunspellTableModel tmpModel = (HunspellTableModel)jTable1.getModel();
        String index = vconf.getProperty((String)jComboBox5.getSelectedItem(), "1", "categories");

        tmpModel.addRow(new String[]{"SFX", index, "0", "", "."}, this.vocClasses);
    }//GEN-LAST:event_jButton33ActionPerformed

    //save the category rules
    private void jButton32ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton32ActionPerformed
        String globalAffPath = vconf.getProperty("affpath", "config/global.aff", "general");
        String lineSep = System.getProperty("line.separator");

        BufferedWriter out = null;

        try{
            // Create file
            FileWriter fstream = new FileWriter(globalAffPath);
            out = new BufferedWriter(fstream);

            out.write("SET " + conf.getProperty("charset", "UTF-8", "parsing") + lineSep);
            out.write("FLAG " + conf.getProperty("flagType", "num", "general") + lineSep);

            Iterator<String> it = vocClasses.keySet().iterator();

            while (it.hasNext()){
                String key = it.next();
                String[] ruleClass = vocClasses.get(key);

                if (ruleClass.length == 4){
                    out.write(ruleClass[0] + " " + ruleClass[1] + " " + ruleClass[2] + " " + ruleClass[3] + lineSep);

                    Vector<String[]> rules = vocRules.get(key);

                    if (rules != null){
                        Iterator<String[]> it1 =  rules.iterator();

                        while (it1.hasNext()){
                            String[] rule = it1.next();

                            if (rule.length >= 5){
                                out.write(rule[0] + " " + rule[1] + " " + rule[2] + " " + rule[3] + " " + rule[4] +  lineSep);
                            }
                        }
                    }
                    
                }
            }
            
            JOptionPane.showMessageDialog(settingsDialog, "Settings were saved");

        }catch (Exception e){
            e.printStackTrace();
            System.err.println("Error: " + e.getMessage());
        }finally{
            try {
                out.flush();
                out.close();
            } catch (IOException ex) {
                Logger.getLogger(SinhalaDictionaryToolsView.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }//GEN-LAST:event_jButton32ActionPerformed

    private void jLabel28MouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_jLabel28MouseClicked
        getConfigs();
        settingsDialog.setVisible(true);
        jTabbedPane2.setSelectedIndex(3);
    }//GEN-LAST:event_jLabel28MouseClicked

    //add word from the generated list to the table
    private void jButton39ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton39ActionPerformed

        DefaultListModel listModel = (DefaultListModel)jList1.getModel();
        TableModel model = null;

        if (jCheckBox2.isSelected()) {
            model = (TableModel) jTable4.getModel();
        } else {
            model = (TableModel) jTable3.getModel();                    
        }

        Object[] arrayWordList = listModel.toArray();
        if (arrayWordList.length > 0){
            for (Object word: arrayWordList){
                model.addRow((String)word);
            }

            JOptionPane.showMessageDialog(addWordsDialog, "New word(s) were added");
            addWordsDialog.setVisible(false);
        }else{
            JOptionPane.showMessageDialog(addWordsDialog, "There are no words in the list. No word added.");
        }
                
    }//GEN-LAST:event_jButton39ActionPerformed

    //generate words and list them before adding
    private void jButton35ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton35ActionPerformed

        String dicText = jTextField2.getText();

        if (!dicText.isEmpty()){

            try {
                jButton35.setText("Processing...");
                jButton35.setEnabled(false);
                
                String[] arrayWordList = null;

                if (jComboBox3.getSelectedIndex() == 0){
                    arrayWordList = new String[]{dicText};
                }else{
                    
                    dicText = addAffixes(dicText, vconf.getArray(jComboBox3.getSelectedItem().toString(),
                                                new String[]{}, "categories"));

                    File affFile = new File(vconf.getProperty("affpath", "config/global.aff", "general"));
                    String wordlist = generateAllAddWords(dicText, affFile, "tmp5");
                    arrayWordList = wordlist.split(System.getProperty("line.separator"));
                }

                DefaultListModel model = new DefaultListModel();

                for (String word: arrayWordList){
                    model.addElement(word);
                }

                jList1.setModel(model);
                
            } catch (IOException ex) {
                Logger.getLogger(SinhalaDictionaryToolsView.class.getName()).log(Level.SEVERE, null, ex);
            } finally{
                jButton35.setText("Generate");
                jButton35.setEnabled(true);
            }
        }
    }//GEN-LAST:event_jButton35ActionPerformed

    //remove selected items from the list
    private void jButton38ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton38ActionPerformed
        Object [] values = jList1.getSelectedValues();
        DefaultListModel model = (DefaultListModel)jList1.getModel();

        for (Object value: values){
            model.removeElement((String)value);
        }        
    }//GEN-LAST:event_jButton38ActionPerformed

    //add a new word to the list
    private void jButton37ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton37ActionPerformed
        String word = JOptionPane.showInputDialog(addWordsDialog, "The new word to add to the list.");

        if (word != null){
            DefaultListModel model = (DefaultListModel)jList1.getModel();

            if (!model.contains(word)){
                model.addElement(word);
            }else{
                JOptionPane.showMessageDialog(addWordsDialog, "The word is already there. So not added.");
            }

        }
        
    }//GEN-LAST:event_jButton37ActionPerformed

    //modify a given word
    private void jButton36ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton36ActionPerformed

        String currentWord = (String)jList1.getSelectedValue();

        if (currentWord != null){
            String newWord = JOptionPane.showInputDialog(addWordsDialog, "Change the word.", currentWord);

            if (newWord != null){
                DefaultListModel model = (DefaultListModel)jList1.getModel();

                if (!model.contains(newWord)){
                    model.removeElement(currentWord);
                    model.addElement(newWord);
                }else{
                    JOptionPane.showMessageDialog(addWordsDialog, "The word is already there. Word not modified.");
                }
            }
        }
        
    }//GEN-LAST:event_jButton36ActionPerformed

    //Suggest words that can be created from the wordlist
    private void jButton40ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton40ActionPerformed

        String dicPath = conf.getProperty("baseDic","","general");
        dicPath = dicPath.substring(0, dicPath.length() - 4);
        System.out.println(dicPath);

        if (!dicPath.isEmpty() ){
            try {
                Hunspell.Dictionary d = Hunspell.getInstance().getDictionary(dicPath);
                List<String> stems = d.stem(jTextField2.getText());
                stems.addAll(d.suggest(jTextField2.getText()));

                DefaultListModel model = new DefaultListModel();                

                Iterator<String> it = stems.iterator();

                while (it.hasNext()){
                    String temp = it.next();
                    if (!model.contains(temp)){
                        model.addElement(temp);
                    }                    
                }

                model.removeElement(jTextField2.getText());

                jList2.setModel(model);
                suggestionDialog.setVisible(true);

            } catch (FileNotFoundException ex) {
                Logger.getLogger(SinhalaDictionaryToolsView.class.getName()).log(Level.SEVERE, null, ex);
            } catch (UnsupportedEncodingException ex) {
                Logger.getLogger(SinhalaDictionaryToolsView.class.getName()).log(Level.SEVERE, null, ex);
            }

        }else{
            JOptionPane.showMessageDialog(addWordsDialog, "No word given. Type a word first and then try.");
        }

    }//GEN-LAST:event_jButton40ActionPerformed

    //modify a selected table element
    private void jMenuItem14jMenuItem7ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuItem14jMenuItem7ActionPerformed
        modifyWords(currentTableId);
    }//GEN-LAST:event_jMenuItem14jMenuItem7ActionPerformed

    //browse base dic/aff
    private void jButton41fileSave(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton41fileSave
        int returnVal = fileChooser.showOpenDialog(settingsDialog);
        if (returnVal == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            jTextField21.setText(file.getAbsolutePath());

        } else {
            setStatusMessage("Couldn't create temp dic/aff files.", true);
        }
    }//GEN-LAST:event_jButton41fileSave

    //hide suggestion box
    private void jButton42ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton42ActionPerformed
        suggestionDialog.setVisible(false);
    }//GEN-LAST:event_jButton42ActionPerformed

    //select a suggestion and enter it to add words dialog
    private void jButton43ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton43ActionPerformed
        jTextField2.setText((String)jList2.getSelectedValue());
        suggestionDialog.setVisible(false);
    }//GEN-LAST:event_jButton43ActionPerformed

    private void suggestionDialogComponentHidden(java.awt.event.ComponentEvent evt) {//GEN-FIRST:event_suggestionDialogComponentHidden
        DefaultListModel model = (DefaultListModel)jList2.getModel();
        model.clear();
        addWordsDialog.toFront();
    }//GEN-LAST:event_suggestionDialogComponentHidden

    //mouse double clicked on suggest menu
    private void jList2MouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_jList2MouseClicked
        if (evt.getClickCount() == 2){
            jButton43ActionPerformed(null);
        }
    }//GEN-LAST:event_jList2MouseClicked

    //add a selected word to blocked list
    private void jMenuItem15jMenuItem7ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuItem15jMenuItem7ActionPerformed
        
        JTable currentTable = (currentTableId == 1) ? jTable4 : jTable3;
        TableModel model = (TableModel) currentTable.getModel();

        String word = (String)model.getValueAt(currentTable.getSelectedRow(), 0);

        if (word != null){
            addWordToList(word, BLOCKED_WORD);
        }

        saveWordList(vconf.getProperty("bannedWordsPath", "config/banned.txt", "wordlists"),
                    vconf.getProperty("ignoredWordsPath", "config/ignored.txt", "wordlists"), BLOCKED_WORD);
    }//GEN-LAST:event_jMenuItem15jMenuItem7ActionPerformed

    //add a selected word to ignored list
    private void jMenuItem16jMenuItem7ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuItem16jMenuItem7ActionPerformed

        JTable currentTable = (currentTableId == 1) ? jTable4 : jTable3;
        TableModel model = (TableModel) currentTable.getModel();

        String word = (String)model.getValueAt(currentTable.getSelectedRow(), 0);

        if (word != null){
            addWordToList(word, IGNORED_WORD);
        }

        saveWordList(vconf.getProperty("bannedWordsPath", "config/banned.txt", "wordlists"),
                    vconf.getProperty("ignoredWordsPath", "config/ignored.txt", "wordlists"), IGNORED_WORD);
    }//GEN-LAST:event_jMenuItem16jMenuItem7ActionPerformed

    //change the blocked words menu items
    private void jComboBox6ItemStateChanged(java.awt.event.ItemEvent evt) {//GEN-FIRST:event_jComboBox6ItemStateChanged
        if (ItemEvent.SELECTED == evt.getStateChange()){
            fillWordlists();
        }
    }//GEN-LAST:event_jComboBox6ItemStateChanged

    //color pick for word lists
    private void jLabel34MouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_jLabel34MouseClicked
        
        Color colour = null;
        
        if ((colour = jColorChooser1.showDialog(settingsDialog, "Select the colour for the wordlist", 
                jLabel34.getBackground())) != null){

            if (jComboBox6.getSelectedIndex() == 0 ){
                conf.setProperty("bannedColour", Integer.toHexString( (colour.getRGB() & 0x00FFFFFF)), "wordlists");
            }else if (jComboBox6.getSelectedIndex() == 1 ){
                conf.setProperty("ignoredColour", Integer.toHexString( (colour.getRGB() & 0x00FFFFFF)), "wordlists");
            }
            jLabel34.setBackground(colour);
            
            try {
                XMLFileHandler handler = new XMLFileHandler("config/config.xml");
                SinhalaDictionaryToolsApp.getConfiguration().save(handler, conf);
            } catch (ConfigurationManagerException ex) {
                Logger.getLogger(SinhalaDictionaryToolsView.class.getName()).log(Level.SEVERE, null, ex);
                setStatusMessage("Couldn't save settings", true);
            }
        }
    }//GEN-LAST:event_jLabel34MouseClicked

    //add a new word to wordlists
    private void jButton45ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton45ActionPerformed
        String word = JOptionPane.showInputDialog(settingsDialog, "The new word to add to the list.");

        if (word != null){
            DefaultListModel model = (DefaultListModel)jList3.getModel();

            if (!model.contains(word)){
                int wordType = (jComboBox6.getSelectedIndex() == 0) ? BLOCKED_WORD : IGNORED_WORD;
                addWordToList(word, wordType);
                fillWordlists();
                //model.addElement(word);
            }else{
                JOptionPane.showMessageDialog(settingsDialog, "The word is already there. So not added.");
            }

        }        
    }//GEN-LAST:event_jButton45ActionPerformed

    //modify a word in wordlist
    private void jButton46ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton46ActionPerformed
        String currentWord = (String)jList3.getSelectedValue();

        if (currentWord != null){
            String newWord = JOptionPane.showInputDialog(settingsDialog, "Change the word.", currentWord);

            if (newWord != null){
                DefaultListModel model = (DefaultListModel)jList3.getModel();

                if (!model.contains(newWord)){
                    int wordType = (jComboBox6.getSelectedIndex() == 0) ? BLOCKED_WORD : IGNORED_WORD;
                    removeWordFromList((String)currentWord, wordType);
                    addWordToList(newWord, wordType);
                    fillWordlists();
                    //model.removeElement(currentWord);
                    //model.addElement(newWord);
                }else{
                    JOptionPane.showMessageDialog(settingsDialog, "The word is already there. So not modified.");
                }
            }
        }
    }//GEN-LAST:event_jButton46ActionPerformed

    //delete a word from the wordlist
    private void jButton44ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton44ActionPerformed
        Object [] values = jList3.getSelectedValues();
        DefaultListModel model = (DefaultListModel)jList3.getModel();
        int wordType = (jComboBox6.getSelectedIndex() == 0) ? BLOCKED_WORD : IGNORED_WORD;

        for (Object value: values){
            removeWordFromList((String)value, wordType);
            fillWordlists();
            //model.removeElement((String)value);
        }
    }//GEN-LAST:event_jButton44ActionPerformed


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JDialog addWordsDialog;
    private javax.swing.JFileChooser fileChooser;
    private javax.swing.JFileChooser fileChooser2;
    private javax.swing.JFileChooser fileSaver;
    private javax.swing.JDialog filterWordsDialog;
    private javax.swing.JDialog findWordsDialog;
    private javax.swing.JButton jButton1;
    private javax.swing.JButton jButton10;
    private javax.swing.JButton jButton11;
    private javax.swing.JButton jButton12;
    private javax.swing.JButton jButton13;
    private javax.swing.JButton jButton14;
    private javax.swing.JButton jButton15;
    private javax.swing.JButton jButton16;
    private javax.swing.JButton jButton17;
    private javax.swing.JButton jButton18;
    private javax.swing.JButton jButton19;
    private javax.swing.JButton jButton2;
    private javax.swing.JButton jButton20;
    private javax.swing.JButton jButton21;
    private javax.swing.JButton jButton22;
    private javax.swing.JButton jButton23;
    private javax.swing.JButton jButton24;
    private javax.swing.JButton jButton25;
    private javax.swing.JButton jButton26;
    private javax.swing.JButton jButton27;
    private javax.swing.JButton jButton28;
    private javax.swing.JButton jButton29;
    private javax.swing.JButton jButton3;
    private javax.swing.JButton jButton30;
    private javax.swing.JButton jButton31;
    private javax.swing.JButton jButton32;
    private javax.swing.JButton jButton33;
    private javax.swing.JButton jButton34;
    private javax.swing.JButton jButton35;
    private javax.swing.JButton jButton36;
    private javax.swing.JButton jButton37;
    private javax.swing.JButton jButton38;
    private javax.swing.JButton jButton39;
    private javax.swing.JButton jButton4;
    private javax.swing.JButton jButton40;
    private javax.swing.JButton jButton41;
    private javax.swing.JButton jButton42;
    private javax.swing.JButton jButton43;
    private javax.swing.JButton jButton44;
    private javax.swing.JButton jButton45;
    private javax.swing.JButton jButton46;
    private javax.swing.JButton jButton47;
    private javax.swing.JButton jButton5;
    private javax.swing.JButton jButton50;
    private javax.swing.JButton jButton6;
    private javax.swing.JButton jButton7;
    private javax.swing.JButton jButton8;
    private javax.swing.JButton jButton9;
    private javax.swing.JCheckBox jCheckBox1;
    private javax.swing.JCheckBox jCheckBox2;
    private javax.swing.JColorChooser jColorChooser1;
    private javax.swing.JComboBox jComboBox1;
    private javax.swing.JComboBox jComboBox2;
    private javax.swing.JComboBox jComboBox3;
    private javax.swing.JComboBox jComboBox4;
    private javax.swing.JComboBox jComboBox5;
    private javax.swing.JComboBox jComboBox6;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel10;
    private javax.swing.JLabel jLabel11;
    private javax.swing.JLabel jLabel12;
    private javax.swing.JLabel jLabel13;
    private javax.swing.JLabel jLabel14;
    private javax.swing.JLabel jLabel15;
    private javax.swing.JLabel jLabel16;
    private javax.swing.JLabel jLabel17;
    private javax.swing.JLabel jLabel18;
    private javax.swing.JLabel jLabel19;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel20;
    private javax.swing.JLabel jLabel21;
    private javax.swing.JLabel jLabel22;
    private javax.swing.JLabel jLabel23;
    private javax.swing.JLabel jLabel24;
    private javax.swing.JLabel jLabel25;
    private javax.swing.JLabel jLabel26;
    private javax.swing.JLabel jLabel27;
    private javax.swing.JLabel jLabel28;
    private javax.swing.JLabel jLabel29;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JLabel jLabel30;
    private javax.swing.JLabel jLabel31;
    private javax.swing.JLabel jLabel32;
    private javax.swing.JLabel jLabel33;
    private javax.swing.JLabel jLabel34;
    private javax.swing.JLabel jLabel4;
    private javax.swing.JLabel jLabel5;
    private javax.swing.JLabel jLabel6;
    private javax.swing.JLabel jLabel7;
    private javax.swing.JLabel jLabel8;
    private javax.swing.JLabel jLabel9;
    private javax.swing.JList jList1;
    private javax.swing.JList jList2;
    private javax.swing.JList jList3;
    private javax.swing.JMenu jMenu1;
    private javax.swing.JMenuItem jMenuItem1;
    private javax.swing.JMenuItem jMenuItem10;
    private javax.swing.JMenuItem jMenuItem11;
    private javax.swing.JMenuItem jMenuItem12;
    private javax.swing.JMenuItem jMenuItem13;
    private javax.swing.JMenuItem jMenuItem14;
    private javax.swing.JMenuItem jMenuItem15;
    private javax.swing.JMenuItem jMenuItem16;
    private javax.swing.JMenuItem jMenuItem2;
    private javax.swing.JMenuItem jMenuItem3;
    private javax.swing.JMenuItem jMenuItem4;
    private javax.swing.JMenuItem jMenuItem5;
    private javax.swing.JMenuItem jMenuItem6;
    private javax.swing.JMenuItem jMenuItem7;
    private javax.swing.JMenuItem jMenuItem8;
    private javax.swing.JMenuItem jMenuItem9;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JPanel jPanel10;
    private javax.swing.JPanel jPanel11;
    private javax.swing.JPanel jPanel12;
    private javax.swing.JPanel jPanel13;
    private javax.swing.JPanel jPanel14;
    private javax.swing.JPanel jPanel15;
    private javax.swing.JPanel jPanel16;
    private javax.swing.JPanel jPanel17;
    private javax.swing.JPanel jPanel18;
    private javax.swing.JPanel jPanel19;
    private javax.swing.JPanel jPanel2;
    private javax.swing.JPanel jPanel20;
    private javax.swing.JPanel jPanel21;
    private javax.swing.JPanel jPanel22;
    private javax.swing.JPanel jPanel23;
    private javax.swing.JPanel jPanel3;
    private javax.swing.JPanel jPanel4;
    private javax.swing.JPanel jPanel5;
    private javax.swing.JPanel jPanel6;
    private javax.swing.JPanel jPanel7;
    private javax.swing.JPanel jPanel8;
    private javax.swing.JPanel jPanel9;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JScrollPane jScrollPane2;
    private javax.swing.JScrollPane jScrollPane3;
    private javax.swing.JScrollPane jScrollPane4;
    private javax.swing.JScrollPane jScrollPane5;
    private javax.swing.JScrollPane jScrollPane6;
    private javax.swing.JScrollPane jScrollPane7;
    private javax.swing.JScrollPane jScrollPane8;
    private javax.swing.JScrollPane jScrollPane9;
    private javax.swing.JSeparator jSeparator1;
    private javax.swing.JSeparator jSeparator2;
    private javax.swing.JSeparator jSeparator3;
    private javax.swing.JSeparator jSeparator4;
    private javax.swing.JSeparator jSeparator5;
    private javax.swing.JSeparator jSeparator6;
    private javax.swing.JTabbedPane jTabbedPane1;
    private javax.swing.JTabbedPane jTabbedPane2;
    private javax.swing.JTable jTable1;
    private javax.swing.JTable jTable3;
    private javax.swing.JTable jTable4;
    private javax.swing.JTextArea jTextArea1;
    private javax.swing.JTextArea jTextArea2;
    private javax.swing.JTextArea jTextArea3;
    private javax.swing.JTextField jTextField1;
    private javax.swing.JTextField jTextField10;
    private javax.swing.JTextField jTextField11;
    private javax.swing.JTextField jTextField12;
    private javax.swing.JTextField jTextField13;
    private javax.swing.JTextField jTextField14;
    private javax.swing.JTextField jTextField15;
    private javax.swing.JTextField jTextField16;
    private javax.swing.JTextField jTextField17;
    private javax.swing.JTextField jTextField18;
    private javax.swing.JTextField jTextField19;
    private javax.swing.JTextField jTextField2;
    private javax.swing.JTextField jTextField20;
    private javax.swing.JTextField jTextField21;
    private javax.swing.JTextField jTextField3;
    private javax.swing.JTextField jTextField4;
    private javax.swing.JTextField jTextField5;
    private javax.swing.JTextField jTextField6;
    private javax.swing.JTextField jTextField7;
    private javax.swing.JTextField jTextField8;
    private javax.swing.JTextField jTextField9;
    private javax.swing.JPanel mainPanel;
    private javax.swing.JMenuBar menuBar;
    private javax.swing.JDialog settingsDialog;
    private javax.swing.JLabel statusAnimationLabel;
    private javax.swing.JLabel statusMessageLabel;
    private javax.swing.JPanel statusPanel;
    private javax.swing.JDialog suggestionDialog;
    private javax.swing.JPopupMenu tablePopupMenu;
    // End of variables declaration//GEN-END:variables

    private final Timer messageTimer;
    private final Timer busyIconTimer;
    private final Icon idleIcon;
    private final Icon[] busyIcons = new Icon[15];
    private int busyIconIndex = 0;

    private JDialog aboutBox;
    private Configuration conf = ConfigurationManager.getConfiguration("config");
    private Configuration vconf = ConfigurationManager.getConfiguration("voconfig");
    private String tmp = "tmp".concat(System.getProperty("file.separator"));

    //for word find
    private JTable lastFocusedTable = null;
    private int lastRow = 0;
    private int currentTableId = 1;

    //a combo box lock
    private boolean isTableLoading = false;

    //hunspell rules
    private HashMap<String, String[]> vocClasses = new HashMap<String, String[]>();
    private HashMap<String, Vector<String[]>> vocRules = new HashMap<String, Vector<String[]>>();

    //wordlists
    private HashMap<String, Integer> wordlists = new HashMap<String, Integer>();
    final public static int BLOCKED_WORD = 1;
    final public static int IGNORED_WORD = 2;

}
