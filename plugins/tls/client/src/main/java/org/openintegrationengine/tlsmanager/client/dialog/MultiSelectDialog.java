/*
 * SPDX-License-Identifier: MPL-2.0
 * Copyright (c) 2025 NovaMap Health Limited <https://novamap.health>
 */

package org.openintegrationengine.tlsmanager.client.dialog;

import com.mirth.connect.client.ui.RefreshTableModel;
import com.mirth.connect.client.ui.UIConstants;
import com.mirth.connect.client.ui.components.MirthTriStateCheckBox;
import net.miginfocom.swing.MigLayout;

import javax.swing.DefaultCellEditor;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.border.TitledBorder;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Collections;
import java.util.EventObject;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

public class MultiSelectDialog extends AbstractDialog {

    private JScrollPane unknownOptionsScrollPane;
    private JTextArea unknownOptionsPanel;

    private final Set<String> selectedOptions;
    private final boolean isDefaultSelected;
    private final String defaultValue;

    private final BiConsumer<Boolean, Set<String>> onSaveConsumer;

    private TableCellRenderer tableCellRenderer;
    private TableCellEditor tableCellEditor;

    public MultiSelectDialog(
        String windowTitle,
        Set<String> selectedOptions,
        boolean isDefaultSelected,
        String defaultValue,
        BiConsumer<Boolean, Set<String>> onSaveConsumer,
        Supplier<Set<String>> dataSupplier
    ) {
        super(windowTitle, dataSupplier, true);

        this.selectedOptions = Objects.requireNonNullElseGet(selectedOptions, Collections::emptySet);

        this.isDefaultSelected = isDefaultSelected;
        this.defaultValue = defaultValue;
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

        tableCellEditor = new TagSelectionCellEditor();
        tableCellRenderer = new TagSelectionCellRenderer();

        selectAllLabel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseReleased(MouseEvent evt) {
                if (evt.getComponent().isEnabled()) {
                    setAllSelected(true);
                }
            }
        });

        deselectAllLabel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseReleased(MouseEvent evt) {
                if (evt.getComponent().isEnabled()) {
                    setAllSelected(false);
                }
            }
        });

        tableModel = new RefreshTableModel(new String[] { "", "Option" }, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return column == SELECTED_COLUMN;
            }
        };

        optionsTable.setModel(tableModel);
        formatTable();

        unknownOptionsPanel = new JTextArea();
        unknownOptionsPanel.setEditable(false);
        unknownOptionsPanel.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        unknownOptionsPanel.setBackground(Color.WHITE);

        unknownOptionsScrollPane = new JScrollPane(unknownOptionsPanel);
        unknownOptionsScrollPane.setBorder(new TitledBorder("Unknown Options"));
        unknownOptionsScrollPane.setBackground(Color.WHITE);

        okButton.addActionListener(evt -> {
            var firstValue = (int) tableModel.getValueAt(0, SELECTED_COLUMN);
            var isDefaultSelected = firstValue == MirthTriStateCheckBox.CHECKED;

            var selectedOptions = getSelectedOptions(true);
            onSaveConsumer.accept(isDefaultSelected, selectedOptions);
            dispose();
        });
    }

    @Override
    protected final void initLayout() {
        super.initLayout();


        containerPanel.add(optionsScrollPane, "newline, grow 25, sx");
        containerPanel.add(unknownOptionsScrollPane, "newline, grow 25, sx");
    }

    @Override
    protected void applyRenderers() {
        optionsTable.getColumn(SELECTED_COLUMN).setCellEditor(tableCellEditor);
        optionsTable.getColumn(SELECTED_COLUMN).setCellRenderer(tableCellRenderer);
    }

    @Override
    protected void handleDataFetchResult(Set<String> options) {
        var data = new Object[options.size() + 1][2];

        data[0][SELECTED_COLUMN] = isDefaultSelected ? MirthTriStateCheckBox.CHECKED : MirthTriStateCheckBox.UNCHECKED;
        data[0][NAME_COLUMN] = defaultValue;

        int i = 1;
        for (var option : options) {
            data[i][SELECTED_COLUMN] = selectedOptions.contains(option) ? MirthTriStateCheckBox.CHECKED : MirthTriStateCheckBox.UNCHECKED;
            data[i][NAME_COLUMN] = option;
            i++;
        }

        tableModel.refreshDataVector(data);
    }

    private Set<String> getSelectedOptions(boolean skipFirstOption) {
        var localSelectedOptions = new LinkedHashSet<String>();

        for (int row = skipFirstOption ? 1 : 0; row < optionsTable.getModel().getRowCount(); row++) {
            var state = (int) optionsTable.getModel().getValueAt(row, SELECTED_COLUMN);
            var certificateAlias = (String) optionsTable.getModel().getValueAt(row, NAME_COLUMN);

            if (state == MirthTriStateCheckBox.CHECKED) {
                localSelectedOptions.add(certificateAlias);
            }
        }

        return localSelectedOptions;
    }

    /*
     * SPDX-License-Identifier: MPL-2.0
     * Copyright (c) 2016 Mirth Corporation
     * Source: https://github.com/OpenIntegrationEngine/engine/blob/788a150f36a6bcd1db672e00d2e7ee609e2842d9/client/src/com/mirth/connect/client/ui/tag/SettingsPanelTags.java#L780-L800
     */
    private class TagSelectionCellEditor extends DefaultCellEditor {

        private MirthTriStateCheckBox checkBox;
        private JPanel panel;

        public TagSelectionCellEditor() {
            super(new MirthTriStateCheckBox());
            checkBox = (MirthTriStateCheckBox) editorComponent;
            panel = new JPanel(new MigLayout("insets 0, novisualpadding, hidemode 3, fill"));
            panel.add(checkBox, "center");
        }

        @Override
        public Object getCellEditorValue() {
            return checkBox.getState();
        }

        @Override
        public boolean isCellEditable(EventObject anEvent) {
            return true;
        }

        @Override
        public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
            super.getTableCellEditorComponent(table, value, isSelected, row, column);
            if (value != null) {
                checkBox.setState((int) value);
            }
            panel.setBackground(row % 2 == 0 ? UIConstants.HIGHLIGHTER_COLOR : UIConstants.BACKGROUND_COLOR);
            checkBox.setBackground(panel.getBackground());
            return panel;
        }
    }

    /*
     * SPDX-License-Identifier: MPL-2.0
     * Copyright (c) 2016 Mirth Corporation
     * Source: https://github.com/OpenIntegrationEngine/engine/blob/788a150f36a6bcd1db672e00d2e7ee609e2842d9/client/src/com/mirth/connect/client/ui/tag/SettingsPanelTags.java#L746-L778
     */
    private class TagSelectionCellRenderer implements TableCellRenderer {

        private MirthTriStateCheckBox checkBox;
        private JPanel panel;

        public TagSelectionCellRenderer() {
            panel = new JPanel(new MigLayout("insets 0, novisualpadding, hidemode 3, fill"));
            checkBox = new MirthTriStateCheckBox();
            panel.add(checkBox, "center");
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            if (value != null) {
                checkBox.setState((int) value);
            }
            panel.setBackground(row % 2 == 0 ? UIConstants.HIGHLIGHTER_COLOR : UIConstants.BACKGROUND_COLOR);
            checkBox.setBackground(panel.getBackground());
            return panel;
        }
    }
    private void setAllSelected(boolean isSelected) {
        for (int row = 0; row < optionsTable.getRowCount(); row++) {
            optionsTable.setValueAt(
                isSelected ? MirthTriStateCheckBox.CHECKED : MirthTriStateCheckBox.UNCHECKED,
                row,
                SELECTED_COLUMN
            );
        }
    }
}
