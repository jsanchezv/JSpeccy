package gui;

import javax.swing.table.AbstractTableModel;

import utilities.Tape;
import utilities.TapeStateListener;
import utilities.Tape.TapeState;

@SuppressWarnings("serial")
class TapeTableModel extends AbstractTableModel {

        /**
		 * 
		 */
		private final Tape tape;

		public TapeTableModel(Tape tape) {
			this.tape = tape;
			tape.addTapeChangedListener(new TapeStateListener() {
				
				@Override
				public void stateChanged(TapeState state) {
					if(state == TapeState.INSERT || state == TapeState.EJECT) {
						fireTableDataChanged();	
					}
				}
			});
        }

        @Override
        public int getRowCount() {
            return this.tape.getNumBlocks();
        }

        @Override
        public int getColumnCount() {
            return 3;
        }

        @Override
        public Object getValueAt(int row, int col) {

//            log.trace("getValueAt row {}, col {}", row, col);
            return switch (col) {
                case 0 -> String.format("%4d", row + 1);
                case 1 -> this.tape.getBlockType(row);
                case 2 -> this.tape.getBlockInfo(row);
                default -> "NON EXISTENT COLUMN!";
            };
        }

        @Override
        public String getColumnName(int col) {
            java.util.ResourceBundle bundle = java.util.ResourceBundle.getBundle("gui/Bundle"); // NOI18N

            return switch (col) {
                case 0 -> bundle.getString("JSpeccy.tapeCatalog.columnModel.title0");
                case 1 -> bundle.getString("JSpeccy.tapeCatalog.columnModel.title1");
                case 2 -> bundle.getString("JSpeccy.tapeCatalog.columnModel.title2");
                default -> "COLUMN ERROR!";
            };
        }
    }