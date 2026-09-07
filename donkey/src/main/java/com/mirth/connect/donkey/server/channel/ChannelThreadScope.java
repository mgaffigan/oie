// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: Mitch Gaffigan <mitch@gaffigan.net>

package com.mirth.connect.donkey.server.channel;

/**
 * Like {@link AutoCloseable}, but {@link #close()} doesn't declare a checked exception, so callers
 * don't need a try/catch just to end a unit of work.
 */
public interface ChannelThreadScope extends AutoCloseable {
    @Override
    void close();
}
