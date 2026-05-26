/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 * 
 * http://www.mirthcorp.com
 * 
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.client.ui.browsers.message;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.prefs.Preferences;

import org.apache.commons.lang3.StringUtils;
import com.google.common.collect.EvictingQueue;

import com.mirth.connect.client.ui.Mirth;
import com.mirth.connect.model.converters.ObjectXMLSerializer;
import com.mirth.connect.model.filters.MessageFilter;

class MessageBrowserRecentFilterStore {
    private static final int MAX_RECENT_FILTERS = 10;
    private static final String RECENT_FILTERS_PREFERENCE_PREFIX = "messageBrowserRecentFilters.";

    private final String prefKey;

    public MessageBrowserRecentFilterStore(String channelId) {
        this.prefKey = RECENT_FILTERS_PREFERENCE_PREFIX + channelId;
    }

    public List<MessageFilter> getRecentFilters() {
        try {
            String serialized = Preferences.userNodeForPackage(Mirth.class).get(prefKey, "");
            if (StringUtils.isBlank(serialized)) return List.of();

            var result = ObjectXMLSerializer.getInstance().deserialize(serialized, List.class);
            if (result == null) return List.of();
            
            return (List<MessageFilter>) result;
        } catch (Exception e) {
            // Fail quietly if the stored filters cannot be deserialized for any reason.
            e.printStackTrace();
            return List.of();
        }
    }

    public void addRecentFilter(MessageFilter filter) {
        if (filter == null) {
            throw new IllegalArgumentException("Filter cannot be null");
        }

        var existingFilters = new ArrayList<MessageFilter>(getRecentFilters());
        Collections.reverse(existingFilters);

        var filters = EvictingQueue.create(MAX_RECENT_FILTERS);
        filters.addAll(existingFilters);

        // Remove then re-add to avoid duplicates.
        filters.remove(filter);
        filters.add(filter);

        var filtersToStore = new ArrayList<MessageFilter>(filters);
        Collections.reverse(filtersToStore);

        try {
            var preferences = Preferences.userNodeForPackage(Mirth.class);
            preferences.put(prefKey, ObjectXMLSerializer.getInstance().serialize(filtersToStore));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}