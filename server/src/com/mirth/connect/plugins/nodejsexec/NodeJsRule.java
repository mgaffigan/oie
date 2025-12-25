// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: 2025 Mitch Gaffigan

package com.mirth.connect.plugins.nodejsexec;

import java.util.Collection;
import java.util.Map;

import com.mirth.connect.donkey.util.purge.PurgeUtil;
import com.mirth.connect.model.Rule;
import com.mirth.connect.util.JavaScriptSharedUtil;

public class NodeJsRule extends Rule {

    public static final String PLUGIN_POINT = "Node Javascript";

    private String script;

    public NodeJsRule() {
        script = "";
    }

    public NodeJsRule(NodeJsRule props) {
        super(props);
        script = props.getScript();
    }

    public String getScript() {
        return script;
    }

    public void setScript(String script) {
        this.script = script;
    }

    @Override
    public String getScript(boolean loadFiles) {
        return script;
    }

    @Override
    public String getType() {
        return PLUGIN_POINT;
    }

    @Override
    public Rule clone() {
        return new NodeJsRule(this);
    }

    @Override
    public Collection<String> getResponseVariables() {
        return JavaScriptSharedUtil.getResponseVariables(getScript(false));
    }

    @Override
    public Map<String, Object> getPurgedProperties() {
        Map<String, Object> purgedProperties = super.getPurgedProperties();
        purgedProperties.put("scriptLines", PurgeUtil.countLines(script));
        return purgedProperties;
    }
}
