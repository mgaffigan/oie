/*
 * SPDX-License-Identifier: MPL-2.0
 * Copyright (c) 2025 NovaMap Health Limited <https://novamap.health>
 */

package org.openintegrationengine.tlsmanager.client.dialog;

import com.mirth.connect.client.ui.Mirth;
import com.mirth.connect.client.ui.MirthDialog;
import com.mirth.connect.client.ui.PlatformUI;
import com.mirth.connect.client.ui.RefreshTableModel;
import com.mirth.connect.client.ui.UIConstants;
import com.mirth.connect.client.ui.components.MirthTable;
import net.miginfocom.swing.MigLayout;
import org.apache.commons.lang3.StringUtils;
import org.jdesktop.swingx.decorator.HighlighterFactory;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JTextField;
import javax.swing.RowFilter;
import javax.swing.SwingWorker;
import javax.swing.WindowConstants;
import javax.swing.border.TitledBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.TableModel;
import javax.swing.table.TableRowSorter;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Font;
import java.util.Comparator;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;
import java.util.prefs.Preferences;

public abstract class AbstractDialog extends MirthDialog {

    protected JPanel containerPanel;

    protected JLabel optionFilterLabel;
    protected JTextField optionFilterField;

    protected JLabel selectAllLabel;
    protected JLabel optionSelectSeparator;
    protected JLabel deselectAllLabel;

    protected JScrollPane optionsScrollPane;
    protected MirthTable optionsTable;

    protected JButton okButton;
    protected JButton cancelButton;

    protected static final int SELECTED_COLUMN = 0;
    protected static final int NAME_COLUMN = 1;

    private final boolean shouldShowAllSelects;

    protected RefreshTableModel tableModel;

    protected Supplier<Set<String>> dataSupplier;

    public AbstractDialog(
        String windowTitle,
        Supplier<Set<String>> dataSupplier,
        boolean shouldShowAllSelects
    ) {
        super(PlatformUI.MIRTH_FRAME, windowTitle, true);
        this.dataSupplier = dataSupplier;
        this.shouldShowAllSelects = shouldShowAllSelects;

        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        setLocationRelativeTo(getOwner());
    }

    protected void initComponents() {
        setBackground(UIConstants.BACKGROUND_COLOR);
        getContentPane().setBackground(getBackground());

        containerPanel = new JPanel();
        containerPanel.setBackground(getBackground());
        containerPanel.setBorder(
            BorderFactory.createTitledBorder(
                BorderFactory.createMatteBorder(
                    1, 1, 1, 1,
                    new Color(204, 204, 204)
                ),
                "TLS settings",
                TitledBorder.DEFAULT_JUSTIFICATION,
                TitledBorder.DEFAULT_POSITION,
                new Font(Font.SANS_SERIF, Font.BOLD, 11)
            )
        );

        optionFilterLabel = new JLabel("Filter:");
        optionFilterField = new JTextField();
        optionFilterField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void removeUpdate(DocumentEvent evt) {
                filterChanged();
            }

            @Override
            public void insertUpdate(DocumentEvent evt) {
                filterChanged();
            }

            @Override
            public void changedUpdate(DocumentEvent evt) {
                filterChanged();
            }

            private void filterChanged() {
                optionsTable.getRowSorter().allRowsChanged();
            }
        });

        selectAllLabel = new JLabel("<html><u>Select All</u></html>");
        selectAllLabel.setForeground(Color.BLUE);
        selectAllLabel.setCursor(new Cursor(Cursor.HAND_CURSOR));
        // Add actionlisteners in subclasses

        optionSelectSeparator = new JLabel("|");

        deselectAllLabel = new JLabel("<html><u>Deselect All</u></html>");
        deselectAllLabel.setForeground(Color.BLUE);
        deselectAllLabel.setCursor(new Cursor(Cursor.HAND_CURSOR));
        // Add actionlisteners in subclasses

        tableModel = new RefreshTableModel(new String[]{"", "Option"}, 0);
        optionsTable = new MirthTable();

        optionsTable.setModel(tableModel);

        optionsTable.setDragEnabled(false);
        optionsTable.setRowSelectionAllowed(false);
        optionsTable.setRowHeight(UIConstants.ROW_HEIGHT);
        optionsTable.setFocusable(false);
        optionsTable.setOpaque(true);
        optionsTable.getTableHeader().setReorderingAllowed(false);
        optionsTable.setEditable(true);
        optionsTable.setSortable(true);

        formatTable();

        var rowSorter = new TableRowSorter<>(optionsTable.getModel());
        rowSorter.setComparator(0, (Comparator<Integer>) (o1, o2) -> {
            // 0, 2, 1
            if (Objects.equals(o1, o2)) {
                return 0;
            } else if (o1 == 0 || (o1 == 2 && o2 == 1)) {
                return -1;
            } else {
                return 1;
            }
        });
        optionsTable.setRowSorter(rowSorter);

        var rowFilter = new RowFilter<TableModel, Integer>() {
            @Override
            public boolean include(Entry<? extends TableModel, ? extends Integer> entry) {
                String name = entry.getStringValue(1);
                return StringUtils.containsIgnoreCase(name, optionFilterField.getText());
            }
        };
        rowSorter.setRowFilter(rowFilter);
        optionsTable.setRowFilter(rowFilter);

        if (Preferences.userNodeForPackage(Mirth.class).getBoolean("highlightRows", true)) {
            optionsTable.setHighlighters(HighlighterFactory.createAlternateStriping(UIConstants.HIGHLIGHTER_COLOR, UIConstants.BACKGROUND_COLOR));
        }

        optionsScrollPane = new JScrollPane(optionsTable);

        okButton = new JButton("OK");

        cancelButton = new JButton("Cancel");
        cancelButton.addActionListener(evt -> dispose());
    }

    protected void initLayout() {
        setLayout(new MigLayout("insets 8, novisualpadding, hidemode 3, fill", "", "[grow][][]"));

        containerPanel.setLayout(new MigLayout("insets 8, novisualpadding, hidemode 3, fill", "[]13[grow]", "[][][][][][][][][grow]"));

        containerPanel.add(optionFilterLabel, "right, split 5");
        containerPanel.add(optionFilterField, "w 100:350");

        if (shouldShowAllSelects) {
            containerPanel.add(selectAllLabel, "gapbefore 12");
            containerPanel.add(optionSelectSeparator);
            containerPanel.add(deselectAllLabel);
        }

        containerPanel.add(optionsScrollPane, "newline, grow 25, sx");

        add(containerPanel, "grow, push");

        add(new JSeparator(), "newline, growx, sx");

        add(okButton, "newline, w 50!, sx, right, split");
        add(cancelButton, "w 50!");
    }

    protected void formatTable() {
        optionsTable.getColumnExt(SELECTED_COLUMN).setMinWidth(20);
        optionsTable.getColumnExt(SELECTED_COLUMN).setMaxWidth(20);

        applyRenderers();
    }

    protected abstract void applyRenderers();

    protected final void fetchData() {
        final var workerId = PlatformUI.MIRTH_FRAME.startWorking("Fetching data...");

        var worker = new SwingWorker<Set<String>, Void>() {
            protected Set<String> doInBackground() {
                return dataSupplier.get();
            }

            protected void done() {
                try {
                    var allOptions = get();
                    handleDataFetchResult(allOptions);
                } catch (InterruptedException | ExecutionException e) {
                    PlatformUI.MIRTH_FRAME.alertThrowable(PlatformUI.MIRTH_FRAME, e, "Fetching failed");
                    throw new RuntimeException(e);
                }

                PlatformUI.MIRTH_FRAME.stopWorking(workerId);
            }
        };

        worker.execute();
    }

    protected abstract void handleDataFetchResult(Set<String> options);
}
