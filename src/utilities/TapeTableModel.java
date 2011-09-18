/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package utilities;

import javax.swing.table.AbstractTableModel;

/**
 *
 * @author jsanchez
 */
public class TapeTableModel extends AbstractTableModel {

    private Tape tape;

    public TapeTableModel(Tape device) {
        tape = device;
    }

    @Override
    public int getRowCount() {
        return tape.getNumBlocks();
    }

    @Override
    public int getColumnCount() {
        return 3;
    }

    @Override
    public Object getValueAt(int row, int col) {
        String msg;

//        System.out.println(String.format("getValueAt row %d, col %d", row, col));
        switch (col) {
            case 0:
                return String.format("%4d", row + 1);
            case 1:
                msg = tape.getBlockType(row);
                break;
            case 2:
                msg = tape.getBlockInfo(row);
                break;
            default:
                return "NOT EXISTANT COLUMN!";
        }
        return msg;
    }

    @Override
    public String getColumnName(int col) {
        java.util.ResourceBundle bundle = java.util.ResourceBundle.getBundle("gui/Bundle"); // NOI18N

        String msg;

        switch(col) {
            case 0:
                msg = bundle.getString("JSpeccy.tapeCatalog.columnModel.title0");
                break;
            case 1:
                msg = bundle.getString("JSpeccy.tapeCatalog.columnModel.title1");
                break;
            case 2:
                msg = bundle.getString("JSpeccy.tapeCatalog.columnModel.title2");
                break;
            default:
                msg = "COLUMN ERROR!";
        }
        return msg;
    }
 }
