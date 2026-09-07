// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: Mitch Gaffigan <mitch@gaffigan.net>

package com.mirth.connect.donkey.server.channel;

import com.mirth.connect.donkey.model.message.ConnectorMessage;

/**
 * Allows a plugin to be notified when a thread begins executing a unit of channel/message work
 * (e.g. dispatching a message, or processing a destination), so it can attach context (such as a
 * tracing span) for the duration of that work.
 */
public interface ChannelThreadPlugin {
    /**
     * Called when a thread begins executing a unit of work for the given channel/message. The
     * returned handle is closed when that unit of work completes.
     */
    ChannelThreadScope beginThread(Channel channel, ConnectorMessage message, String taskName);
}
