// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: 2025 Mitch Gaffigan

package com.mirth.connect.plugins.nodejsexec;

import java.awt.event.ActionListener;

import javax.swing.BorderFactory;

import net.miginfocom.swing.MigLayout;

import com.mirth.connect.client.ui.UIConstants;
import com.mirth.connect.client.ui.components.rsta.MirthRTextScrollPane;
import com.mirth.connect.client.ui.editors.EditorPanel;
import com.mirth.connect.model.Rule;
import com.mirth.connect.model.codetemplates.ContextType;

public class NodeJsRulePanel extends EditorPanel<Rule> {

    public NodeJsRulePanel() {
        initComponents();
        initLayout();
    }

    @Override
    public Rule getDefaults() {
        return new NodeJsRule();
    }

    @Override
    public Rule getProperties() {
        NodeJsRule props = new NodeJsRule();
        props.setScript(scriptTextArea.getText().trim());

        return props;
    }

    @Override
    public void setProperties(Rule properties) {
        NodeJsRule props = (NodeJsRule) properties;
        scriptTextArea.setText(props.getScript());
    }

    @Override
    public String checkProperties(Rule properties, boolean highlight) {
        //NodeJsRule props = (NodeJsRule) properties;
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
