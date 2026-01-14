/*
 * SPDX-License-Identifier: MPL-2.0
 * Copyright (c) 2025 NovaMap Health Limited <https://novamap.health>
 */

package org.openintegrationengine.tlsmanager.client.dialog;

import com.mirth.connect.client.ui.RefreshTableModel;
import com.mirth.connect.client.ui.UIConstants;
import net.miginfocom.swing.MigLayout;

import javax.swing.AbstractCellEditor;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableModel;
import java.awt.Component;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class SingleSelectDialog extends AbstractDialog {

    private final String selectedOption;

    private final Consumer<String> onSaveConsumer;

    private TableCellRenderer cellRenderer;

    public SingleSelectDialog(
        String windowTitle,
        String selectedOption,
        Supplier<Set<String>> dataSupplier,
        Consumer<String> onSaveConsumer
    ) {
        super(windowTitle, dataSupplier, false);

        this.selectedOption = selectedOption;
        this.onSaveConsumer = onSaveConsumer;

        initComponents();
        initLayout();

        handleDataFetchResult(Set.of("Loading data..."));
        fetchData();

        pack();
        setVisible(true);
    }

    @Override
    protected final void initComponents() {
        super.initComponents();

        cellRenderer = new RadioCellEditorRenderer();

        tableModel = new RefreshTableModel(new String[]{"", "Options"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }

            @Override
            public Class<?> getColumnClass(int column) {
                return column == SELECTED_COLUMN ? Boolean.class : String.class;
            }
        };
        optionsTable.setModel(tableModel);
        formatTable();

        optionsTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                int column = optionsTable.columnAtPoint(e.getPoint());
                int row = optionsTable.rowAtPoint(e.getPoint());

                if (row != -1 && optionsTable.convertColumnIndexToModel(column) == SELECTED_COLUMN) {
                    int modelRow = optionsTable.convertRowIndexToModel(row);
                    TableModel model = optionsTable.getModel();
                    for (int i = 0; i < model.getRowCount(); i++) {
                        model.setValueAt(i == modelRow, i, SELECTED_COLUMN);
                    }
                }
            }
        });

        optionsTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        okButton.addActionListener(evt -> {
            var selectedItem = getSelectedItem();
            onSaveConsumer.accept(selectedItem);
            dispose();
        });
    }

    @Override
    protected final void initLayout() {
        super.initLayout();
    }

    private String getSelectedItem() {
        var selectedIndex = optionsTable.getSelectedModelIndex();
        return optionsTable.getModel().getValueAt(selectedIndex, NAME_COLUMN).toString();
    }

    @Override
    protected void applyRenderers() {
        optionsTable.getColumnExt(SELECTED_COLUMN).setCellRenderer(cellRenderer);
    }

    @Override
    protected void handleDataFetchResult(Set<String> options) {
        var data = new Object[options.size()][2];

        int i = 0;
        for (var option : options) {
            data[i][SELECTED_COLUMN] = option.equals(selectedOption);
            data[i][NAME_COLUMN] = option;
            i++;
        }

        tableModel.refreshDataVector(data);
    }

    private static class RadioCellEditorRenderer extends AbstractCellEditor implements TableCellRenderer {
        private final JRadioButton radioButton;
        private final JPanel panel;

        public RadioCellEditorRenderer() {
            panel = new JPanel(new MigLayout("insets 0, novisualpadding, hidemode 3, fill"));
            this.radioButton = new JRadioButton();

            radioButton.setOpaque(false);
            panel.add(radioButton, "center");
        }

        @Override
        public Object getCellEditorValue() {
            return true;
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            radioButton.setSelected(Boolean.TRUE.equals(value));
            panel.setBackground(row % 2 == 0 ? UIConstants.HIGHLIGHTER_COLOR : UIConstants.BACKGROUND_COLOR);
            radioButton.setBackground(panel.getBackground());
            return panel;
        }
    }
}
