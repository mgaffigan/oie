// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: 2025 Mitch Gaffigan

package com.mirth.connect.plugins.nodejsexec;

import java.util.Collection;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mirth.connect.donkey.util.purge.PurgeUtil;
import com.mirth.connect.model.Step;
import com.mirth.connect.util.JavaScriptSharedUtil;

public class NodeJsStep extends Step {

    public static final String PLUGIN_POINT = "Node Javascript";

    private String script;

    public NodeJsStep() {
        script = "";
    }

    public NodeJsStep(NodeJsStep props) {
        super(props);
        script = props.getScript();
    }

    @Override
    public String getScript(boolean loadFiles) {
        String escapedScript;
        try {
            var objectMapper = new ObjectMapper();
            escapedScript = objectMapper.writeValueAsString(this.script);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to escape script", e);
        }

        return "msg = JSON.parse(com.mirth.connect.plugins.nodejsexec.NodeJsExecutor.run(" + escapedScript + ", JSON.stringify({msg: msg})))";
    }

    public String getScript() {
        return this.script;
    }

    public void setScript(String script) {
        this.script = script;
    }

    @Override
    public String getType() {
        return PLUGIN_POINT;
    }

    @Override
    public Step clone() {
        return new NodeJsStep(this);
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
