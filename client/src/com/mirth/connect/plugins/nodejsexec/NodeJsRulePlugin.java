// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: 2025 Mitch Gaffigan

package com.mirth.connect.plugins.nodejsexec;

import com.mirth.connect.model.Rule;
import com.mirth.connect.plugins.FilterRulePlugin;

public class NodeJsRulePlugin extends FilterRulePlugin {

    private NodeJsRulePanel panel;

    public NodeJsRulePlugin(String name) {
        super(name);
        panel = new NodeJsRulePanel();
    }

    @Override
    public NodeJsRulePanel getPanel() {
        return panel;
    }

    @Override
    public boolean includesScrollPane() {
        return true;
    }

    @Override
    public Rule newObject(String variable, String mapping) {
        return new NodeJsRule();
    }

    @Override
    public boolean isNameEditable() {
        return true;
    }

    @Override
    public String getPluginPointName() {
        return NodeJsRule.PLUGIN_POINT;
    }
}
