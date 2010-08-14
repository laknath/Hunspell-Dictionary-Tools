/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package sinhaladictionarytools.lib.table;

import java.awt.Component;
import java.awt.Font;
import javax.swing.DefaultCellEditor;
import javax.swing.JComboBox;
import javax.swing.JTable;
import javax.swing.JTextField;

/**
 *
 * @author buddhika
 */
public class HunspellTableCellEditor extends DefaultCellEditor{

    private Font font;

    public HunspellTableCellEditor(String font, JComboBox comboBox) {
        super(comboBox);
        this.font = new Font(font, Font.PLAIN, 12);
    }

    public HunspellTableCellEditor(String font, JTextField textfield) {
        super(textfield);
        this.font = new Font(font, Font.PLAIN, 12);
    }

    // override renderer preparation
    public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected,
                                                   boolean hasFocus,
                                                   int row, int column)
    {
        // allow default preparation
        Component comp = super.getTableCellEditorComponent(table, value, isSelected, row, column);

        // replace default font
        comp.setFont(font);
        return comp;
    }

}
