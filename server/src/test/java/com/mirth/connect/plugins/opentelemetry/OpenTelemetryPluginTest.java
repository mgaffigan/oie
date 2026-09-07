// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: Mitch Gaffigan <mitch@gaffigan.net>

package com.mirth.connect.plugins.opentelemetry;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.Test;

import com.mirth.connect.donkey.model.message.ConnectorMessage;
import com.mirth.connect.donkey.server.channel.Channel;
import com.mirth.connect.donkey.server.channel.ChannelThreadScope;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.context.Context;

public class OpenTelemetryPluginTest {

    @Test
    public void testTraceparentRoundTrip() {
        SpanContext original = SpanContext.createFromRemoteParent("0af7651916cd43dd8448eb211c80319c", "b7ad6b7169203331", TraceFlags.getSampled(), TraceState.getDefault());

        String encoded = OpenTelemetryPlugin.encodeTraceparent(original);
        assertEquals("00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01", encoded);

        Context decoded = OpenTelemetryPlugin.decodeTraceparent(encoded);
        SpanContext roundTripped = Span.fromContext(decoded).getSpanContext();

        assertEquals(original.getTraceId(), roundTripped.getTraceId());
        assertEquals(original.getSpanId(), roundTripped.getSpanId());
        assertEquals(original.getTraceFlags(), roundTripped.getTraceFlags());
    }

    @Test
    public void testDecodeWithNoExistingTraceparentReturnsRootContext() {
        assertSame(Context.root(), OpenTelemetryPlugin.decodeTraceparent(null));
    }

    @Test
    public void testDecodeWithMalformedTraceparentReturnsRootContext() {
        assertSame(Context.root(), OpenTelemetryPlugin.decodeTraceparent("not-a-traceparent"));
    }

    @Test
    public void testBeginThreadWritesTraceparentToChannelMap() {
        Channel channel = mock(Channel.class);
        when(channel.getName()).thenReturn("Test Channel");
        when(channel.getChannelId()).thenReturn("test-channel-id");

        ConnectorMessage message = new ConnectorMessage();
        assertNull(message.getChannelMap().get("traceparent"));

        OpenTelemetryPlugin plugin = new OpenTelemetryPlugin();
        try (ChannelThreadScope scope = plugin.beginThread(channel, message, "dispatch")) {
            assertNotNull(message.getChannelMap().get("traceparent"));
        }
    }
}
