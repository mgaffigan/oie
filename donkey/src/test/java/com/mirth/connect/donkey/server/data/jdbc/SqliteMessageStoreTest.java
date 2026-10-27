// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: 2026 Mitch Gaffigan

package com.mirth.connect.donkey.server.data.jdbc;

import static org.junit.Assert.assertEquals;

import java.io.Closeable;
import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.mirth.connect.donkey.server.DonkeyConnectionPools;

/**
 * SQLite relies on ON DELETE CASCADE rather than the manual cascade queries Derby uses. A missing
 * foreign_keys setting on the pooled connections or a wrong cascade in sqlite.xml would silently
 * leave orphaned rows.
 */
public class SqliteMessageStoreTest {
    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private ConnectionPool pool;
    private XmlQuerySource querySource;
    private Map<String, Object> values;

    @Before
    public void before() throws Exception {
        String url = "jdbc:sqlite:" + new File(tempFolder.getRoot(), "mirthdb.sqlite").getAbsolutePath();
        pool = DonkeyConnectionPools.getInstance().createConnectionPool("sqlite", "org.sqlite.JDBC", url, null, null, null, true, "SELECT 1", 1, false);

        querySource = new XmlQuerySource();
        querySource.load("default.xml");
        querySource.load("sqlite.xml");

        values = new HashMap<String, Object>();
        values.put("localChannelId", 1L);

        try (Connection connection = pool.getConnection().getConnection(); Statement statement = connection.createStatement()) {
            for (String queryId : new String[] { "createMessageTable", "createConnectorMessageTable",
                    "createMessageContentTable", "createMessageCustomMetaDataTable",
                    "createMessageAttachmentTable" }) {
                statement.execute(querySource.getQuery(queryId, values));
            }

            for (long messageId : new long[] { 1, 2 }) {
                statement.execute("INSERT INTO D_M1 (ID, SERVER_ID, PROCESSED) VALUES (" + messageId + ", 's', 0)");
                statement.execute("INSERT INTO D_MA1 (ID, MESSAGE_ID, SEGMENT_ID, ATTACHMENT_SIZE) VALUES ('a', " + messageId + ", 0, 0)");

                for (int metaDataId : new int[] { 0, 1 }) {
                    statement.execute("INSERT INTO D_MM1 (ID, MESSAGE_ID, SERVER_ID, STATUS, CHAIN_ID, ORDER_ID) VALUES (" + metaDataId + ", " + messageId + ", 's', 'R', 0, 0)");
                    statement.execute("INSERT INTO D_MC1 (METADATA_ID, MESSAGE_ID, CONTENT_TYPE, IS_ENCRYPTED) VALUES (" + metaDataId + ", " + messageId + ", 1, 0)");
                    statement.execute("INSERT INTO D_MCM1 (METADATA_ID, MESSAGE_ID) VALUES (" + metaDataId + ", " + messageId + ")");
                }
            }

            connection.commit();
        }
    }

    @After
    public void after() throws Exception {
        ((Closeable) pool.getDataSource()).close();
    }

    @Test
    public void deleteMessageCascadesToDependentTables() throws Exception {
        try (Connection connection = pool.getConnection().getConnection(); PreparedStatement statement = connection.prepareStatement(querySource.getQuery("deleteMessage", values))) {
            statement.setLong(1, 1);
            statement.executeUpdate();
            connection.commit();

            for (String table : new String[] { "D_MM1", "D_MC1", "D_MCM1", "D_MA1" }) {
                assertEquals(table, 0, count(connection, table, "MESSAGE_ID = 1"));
                assertEquals(table, table.equals("D_MA1") ? 1 : 2, count(connection, table, "MESSAGE_ID = 2"));
            }
        }
    }

    @Test
    public void deleteConnectorMessagesCascadesToContentAndMetaData() throws Exception {
        values.put("metaDataIds", "1");

        try (Connection connection = pool.getConnection().getConnection(); PreparedStatement statement = connection.prepareStatement(querySource.getQuery("deleteConnectorMessagesByMetaDataIds", values))) {
            statement.setLong(1, 1);
            statement.executeUpdate();
            connection.commit();

            for (String table : new String[] { "D_MC1", "D_MCM1" }) {
                assertEquals(table, 0, count(connection, table, "MESSAGE_ID = 1 AND METADATA_ID = 1"));
                assertEquals(table, 1, count(connection, table, "MESSAGE_ID = 1 AND METADATA_ID = 0"));
                assertEquals(table, 2, count(connection, table, "MESSAGE_ID = 2"));
            }
        }
    }

    private int count(Connection connection, String table, String where) throws Exception {
        try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery("SELECT COUNT(*) FROM " + table + " WHERE " + where)) {
            result.next();
            return result.getInt(1);
        }
    }
}
