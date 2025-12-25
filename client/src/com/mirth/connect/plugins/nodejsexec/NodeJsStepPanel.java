// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: 2025 Mitch Gaffigan

package com.mirth.connect.plugins.nodejsexec;

import java.awt.event.ActionListener;

import javax.swing.BorderFactory;

import net.miginfocom.swing.MigLayout;

import com.mirth.connect.client.ui.UIConstants;
import com.mirth.connect.client.ui.components.rsta.MirthRTextScrollPane;
import com.mirth.connect.client.ui.editors.EditorPanel;
import com.mirth.connect.model.Step;
import com.mirth.connect.model.codetemplates.ContextType;

public class NodeJsStepPanel extends EditorPanel<Step> {

    public NodeJsStepPanel() {
        initComponents();
        initLayout();
    }

    @Override
    public Step getDefaults() {
        return new NodeJsStep();
    }

    @Override
    public Step getProperties() {
        NodeJsStep props = new NodeJsStep();
        props.setScript(scriptTextArea.getText().trim());
        return props;
    }

    @Override
    public void setProperties(Step properties) {
        var props = (NodeJsStep) properties;
        scriptTextArea.setText(props.getScript());
    }

    @Override
    public String checkProperties(Step properties, boolean highlight) {
        //NodeJsStep props = (NodeJsStep) properties;
        return null;
    }

    @Override
    public void resetInvalidProperties() {}

    @Override
    public void setNameActionListener(ActionListener actionListener) {}

    public void setContextType(ContextType contextType) {
        scriptTextArea.setContextType(contextType);
    }

    private void initComponents() {
        setBackground(UIConstants.BACKGROUND_COLOR);

        scriptTextArea = new MirthRTextScrollPane(null, true);
        scriptTextArea.setBorder(BorderFactory.createEtchedBorder());
    }

    private void initLayout() {
        setLayout(new MigLayout("insets 0, novisualpadding, hidemode 3"));

        add(scriptTextArea, "grow, push");
    }

    private MirthRTextScrollPane scriptTextArea;
}
