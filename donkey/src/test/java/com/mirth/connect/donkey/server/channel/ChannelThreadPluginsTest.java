// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: Mitch Gaffigan <mitch@gaffigan.net>

package com.mirth.connect.donkey.server.channel;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;

public class ChannelThreadPluginsTest {

    @Test
    public void testBeginThreadFansOutAndClosesInReverseOrder() {
        List<String> events = new ArrayList<String>();

        ChannelThreadPlugin pluginA = (channel, message, taskName) -> {
            events.add("beginA");
            return (ChannelThreadScope) () -> events.add("closeA");
        };
        ChannelThreadPlugin pluginB = (channel, message, taskName) -> {
            events.add("beginB");
            return (ChannelThreadScope) () -> events.add("closeB");
        };

        ChannelThreadPlugins.register(pluginA);
        ChannelThreadPlugins.register(pluginB);
        try {
            ChannelThreadScope scope = ChannelThreadPlugins.beginThread(null, null, "task");
            assertEquals(Arrays.asList("beginA", "beginB"), events);

            scope.close();
            assertEquals(Arrays.asList("beginA", "beginB", "closeB", "closeA"), events);
        } finally {
            ChannelThreadPlugins.unregister(pluginA);
            ChannelThreadPlugins.unregister(pluginB);
        }
    }

    @Test
    public void testBeginThreadWithNoPluginsReturnsUsableNoOp() {
        ChannelThreadPlugins.beginThread(null, null, "task").close();
    }
}
