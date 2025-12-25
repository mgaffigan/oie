// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: 2025 Mitch Gaffigan

package com.mirth.connect.plugins.nodejsexec;

import com.mirth.connect.model.Step;
import com.mirth.connect.plugins.TransformerStepPlugin;

public class NodeJsStepPlugin extends TransformerStepPlugin {

    private NodeJsStepPanel panel;

    public NodeJsStepPlugin(String name) {
        super(name);
        panel = new NodeJsStepPanel();
    }

    @Override
    public NodeJsStepPanel getPanel() {
        return panel;
    }

    @Override
    public boolean includesScrollPane() {
        return true;
    }

    @Override
    public Step newObject(String variable, String mapping) {
        return new NodeJsStep();
    }

    @Override
    public boolean isNameEditable() {
        return true;
    }

    @Override
    public String getPluginPointName() {
        return NodeJsStep.PLUGIN_POINT;
    }
}
