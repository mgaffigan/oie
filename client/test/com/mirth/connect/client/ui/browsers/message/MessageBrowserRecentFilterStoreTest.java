/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 * 
 * http://www.mirthcorp.com
 * 
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.client.ui.browsers.message;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.prefs.Preferences;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.mirth.connect.client.ui.Mirth;
import com.mirth.connect.model.filters.MessageFilter;

public class MessageBrowserRecentFilterStoreTest {
    private static final String RECENT_FILTERS_PREFERENCE_PREFIX = "messageBrowserRecentFilters.";

    private String channelId;
    private MessageBrowserRecentFilterStore store;

    @Before
    public void setup() {
        channelId = UUID.randomUUID().toString();
        store = new MessageBrowserRecentFilterStore(channelId);
        clearPreferences();
    }

    @After
    public void teardown() {
        clearPreferences();
    }

    @Test
    public void testNewestFirstAndDeduplication() {
        MessageFilter filterA = createFilter("a");
        MessageFilter filterB = createFilter("b");
        MessageFilter filterC = createFilter("c");

        store.addRecentFilter(filterA);
        store.addRecentFilter(filterB);
        store.addRecentFilter(filterC);

        assertEquals(List.of("c", "b", "a"), getTextSearchValues(store.getRecentFilters()));

        store.addRecentFilter(filterB);

        assertEquals(List.of("b", "c", "a"), getTextSearchValues(store.getRecentFilters()));
    }

    @Test
    public void testEvictsOldestWhenMaxReached() {
        for (int i = 0; i <= 10; i++) {
            store.addRecentFilter(createFilter("f" + i));
        }

        List<String> recentFilterValues = getTextSearchValues(store.getRecentFilters());

        assertEquals(10, recentFilterValues.size());
        assertEquals(List.of("f10", "f9", "f8", "f7", "f6", "f5", "f4", "f3", "f2", "f1"), recentFilterValues);
        assertFalse(recentFilterValues.contains("f0"));
    }

    private MessageFilter createFilter(String textSearch) {
        MessageFilter filter = new MessageFilter();
        filter.setTextSearch(textSearch);
        return filter;
    }

    private List<String> getTextSearchValues(List<MessageFilter> filters) {
        List<String> values = new ArrayList<String>();
        for (MessageFilter filter : filters) {
            values.add(filter.getTextSearch());
        }
        return values;
    }

    private void clearPreferences() {
        Preferences.userNodeForPackage(Mirth.class).remove(RECENT_FILTERS_PREFERENCE_PREFIX + channelId);
    }
}
