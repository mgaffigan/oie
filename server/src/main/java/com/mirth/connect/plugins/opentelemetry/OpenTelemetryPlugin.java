// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: Mitch Gaffigan <mitch@gaffigan.net>

package com.mirth.connect.plugins.opentelemetry;

import java.util.Map;
import java.util.Properties;

import com.mirth.connect.donkey.model.message.ConnectorMessage;
import com.mirth.connect.donkey.server.channel.Channel;
import com.mirth.connect.donkey.server.channel.ChannelThreadPlugin;
import com.mirth.connect.donkey.server.channel.ChannelThreadPlugins;
import com.mirth.connect.donkey.server.channel.ChannelThreadScope;
import com.mirth.connect.model.ExtensionPermission;
import com.mirth.connect.plugins.ServicePlugin;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;

public class OpenTelemetryPlugin implements ServicePlugin, ChannelThreadPlugin {
    public static final String PLUGINPOINT = "OpenTelemetry";

    private static final String TRACEPARENT_KEY = "traceparent";

    private final Tracer tracer = GlobalOpenTelemetry.getTracer("com.mirth.connect");

    @Override
    public String getPluginPointName() {
        return PLUGINPOINT;
    }

    @Override
    public void start() {
        ChannelThreadPlugins.register(this);
    }

    @Override
    public void stop() {
        ChannelThreadPlugins.unregister(this);
    }

    @Override
    public void init(Properties properties) {}

    @Override
    public void update(Properties properties) {}

    @Override
    public Properties getDefaultProperties() {
        return new Properties();
    }

    @Override
    public ExtensionPermission[] getExtensionPermissions() {
        return new ExtensionPermission[0];
    }

    @Override
    public ChannelThreadScope beginThread(Channel channel, ConnectorMessage message, String taskName) {
        Map<String, Object> channelMap = message.getChannelMap();
        Context parentContext = decodeTraceparent((String) channelMap.get(TRACEPARENT_KEY));

        final Span span = tracer.spanBuilder(channel.getName() + " " + taskName)
                .setParent(parentContext)
                .setAttribute("mirth.channel.id", channel.getChannelId())
                .setAttribute("mirth.channel.name", channel.getName())
                .setAttribute("mirth.message.id", message.getMessageId())
                .startSpan();

        channelMap.put(TRACEPARENT_KEY, encodeTraceparent(span.getSpanContext()));

        final Scope scope = span.makeCurrent();
        return () -> {
            scope.close();
            span.end();
        };
    }

    static Context decodeTraceparent(String traceparent) {
        if (traceparent != null) {
            String[] parts = traceparent.split("-");
            if (parts.length == 4) {
                SpanContext spanContext = SpanContext.createFromRemoteParent(parts[1], parts[2], TraceFlags.fromHex(parts[3], 0), TraceState.getDefault());
                if (spanContext.isValid()) {
                    return Context.root().with(Span.wrap(spanContext));
                }
            }
        }
        return Context.root();
    }

    static String encodeTraceparent(SpanContext spanContext) {
        return "00-" + spanContext.getTraceId() + "-" + spanContext.getSpanId() + "-" + spanContext.getTraceFlags().asHex();
    }
}
