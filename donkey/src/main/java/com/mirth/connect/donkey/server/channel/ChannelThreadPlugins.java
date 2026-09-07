// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: Mitch Gaffigan <mitch@gaffigan.net>

package com.mirth.connect.donkey.server.channel;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import com.mirth.connect.donkey.model.message.ConnectorMessage;

/**
 * Registry of {@link ChannelThreadPlugin}s to notify when a thread begins executing a unit of
 * channel/message work.
 */
public class ChannelThreadPlugins {
    private static final List<ChannelThreadPlugin> plugins = new CopyOnWriteArrayList<ChannelThreadPlugin>();

    private ChannelThreadPlugins() {}

    /** 
     * Registers a {@link ChannelThreadPlugin} to be notified when a thread 
     * begins executing a unit of channel/message work. 
     */
    public static void register(ChannelThreadPlugin plugin) {
        plugins.add(plugin);
    }

    /** Unregisters a previously registered {@link ChannelThreadPlugin}. */
    public static void unregister(ChannelThreadPlugin plugin) {
        plugins.remove(plugin);
    }

    /**
     * Notifies all registered {@link ChannelThreadPlugin}s that a thread is beginning to
     * execute a unit of channel/message work.  Always a non-null result.
     */
    public static ChannelThreadScope beginThread(final Channel channel, final ConnectorMessage message, final String taskName) {
        if (plugins.isEmpty()) {
            return NO_OP;
        }

        final List<ChannelThreadScope> scopes = new ArrayList<ChannelThreadScope>(plugins.size());
        for (ChannelThreadPlugin plugin : plugins) {
            scopes.add(plugin.beginThread(channel, message, taskName));
        }

        return () -> {
            for (int i = scopes.size() - 1; i >= 0; i--) {
                scopes.get(i).close();
            }
        };
    }

    private static final ChannelThreadScope NO_OP = () -> {};
}
