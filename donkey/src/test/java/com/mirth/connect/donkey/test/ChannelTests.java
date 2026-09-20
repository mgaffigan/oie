/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 * 
 * http://www.mirthcorp.com
 * 
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.donkey.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.mirth.connect.donkey.model.channel.DeployedState;
import com.mirth.connect.donkey.model.channel.MetaDataColumn;
import com.mirth.connect.donkey.model.channel.MetaDataColumnException;
import com.mirth.connect.donkey.model.channel.MetaDataColumnType;
import com.mirth.connect.donkey.model.message.Message;
import com.mirth.connect.donkey.server.Donkey;
import com.mirth.connect.donkey.server.StartException;
import com.mirth.connect.donkey.server.channel.Channel;
import com.mirth.connect.donkey.server.channel.ChannelException;
import com.mirth.connect.donkey.server.channel.DestinationChainProvider;
import com.mirth.connect.donkey.server.channel.DestinationConnector;
import com.mirth.connect.donkey.server.controllers.ChannelController;
import com.mirth.connect.donkey.test.util.TestChannel;
import com.mirth.connect.donkey.test.util.TestDestinationConnector;
import com.mirth.connect.donkey.test.util.TestSourceConnector;
import com.mirth.connect.donkey.test.util.TestUtils;

public class ChannelTests {
    final public static int TEST_SIZE = 50;

    private static String channelId = TestUtils.DEFAULT_CHANNEL_ID;
    private static String channelName = TestUtils.DEFAULT_CHANNEL_ID;
    private static String serverId = TestUtils.DEFAULT_SERVER_ID;
    private static String testMessage = TestUtils.TEST_HL7_MESSAGE;

    @BeforeClass
    final public static void beforeClass() throws StartException {
        Donkey donkey = Donkey.getInstance();
        donkey.startEngine(TestUtils.getDonkeyTestConfiguration());
    }

    @AfterClass
    final public static void afterClass() throws StartException {
        Donkey.getInstance().stopEngine();
    }

    /*
     * Deploys a channel, asserts that: - The channel was successfully deployed - The channel is not
     * running - The source connector was successfully deployed - The source connector is not
     * running - Each destination connector was successfully deployed - Each destination connector
     * is not running Then sends messages and asserts that: - Each message is not received by the
     * source connector
     */
    @Test
    public final void testDeployChannel() throws Exception {
        TestChannel channel = (TestChannel) TestUtils.createDefaultChannel(channelId, serverId, false, 1, 1);
        TestSourceConnector sourceConnector = (TestSourceConnector) channel.getSourceConnector();

        channel.deploy();

        assertTrue(channel.isDeployed());
        assertFalse(channel.getCurrentState() == DeployedState.STARTED);
        assertTrue(sourceConnector.isDeployed());
        assertFalse(sourceConnector.getCurrentState() != DeployedState.STOPPED);

        for (DestinationChainProvider chain : channel.getDestinationChainProviders()) {
            for (DestinationConnector destinationConnector : chain.getDestinationConnectors().values()) {
                if (destinationConnector.isEnabled()) {
                    assertTrue(((TestDestinationConnector) destinationConnector).isDeployed());
                    assertFalse(destinationConnector.getCurrentState() != DeployedState.STOPPED);
                }
            }
        }

        channel.undeploy();
    }

    /*
     * Deploys and undeploys a channel, asserts that: - The channel was successfully undeployed -
     * The channel is not running - The source connector was successfully undeployed - The source
     * connector is not running - Each destination connector was successfully undeployed - Each
     * destination connector is not running Then sends messages and asserts that: - Each message is
     * not received by the source connector
     */
    @Test
    public final void testUndeployChannel() throws Exception {
        TestChannel channel = (TestChannel) TestUtils.createDefaultChannel(channelId, serverId, false, 1, 1);
        TestSourceConnector sourceConnector = (TestSourceConnector) channel.getSourceConnector();

        channel.deploy();
        channel.undeploy();

        assertFalse(channel.isDeployed());
        assertFalse(channel.getCurrentState() == DeployedState.STARTED);
        assertFalse(sourceConnector.isDeployed());
        assertFalse(sourceConnector.getCurrentState() != DeployedState.STOPPED);

        for (DestinationChainProvider chain : channel.getDestinationChainProviders()) {
            for (DestinationConnector destinationConnector : chain.getDestinationConnectors().values()) {
                if (destinationConnector.isEnabled()) {
                    assertFalse(((TestDestinationConnector) destinationConnector).isDeployed());
                    assertFalse(destinationConnector.getCurrentState() != DeployedState.STOPPED);
                }
            }
        }
    }

    /*
     * Deploys and starts a channel, asserts that: - The channel is running - The source queue is
     * created - The source connector is running - Each destination connector is running Then sends
     * messages and asserts that: - Each message is received by the destination connectors
     */
    @Test
    public final void testStartChannel() throws Exception {
        Channel channel = TestUtils.createDefaultChannel(channelId, serverId, false, 1, 1);
        TestSourceConnector sourceConnector = (TestSourceConnector) channel.getSourceConnector();

        channel.deploy();
        channel.start(null);

        assertTrue(channel.getCurrentState() == DeployedState.STARTED);
        assertNotNull(channel.getSourceQueue());
        assertTrue(sourceConnector.getCurrentState() != DeployedState.STOPPED);

        for (DestinationChainProvider chain : channel.getDestinationChainProviders()) {
            for (DestinationConnector destinationConnector : chain.getDestinationConnectors().values()) {
                if (destinationConnector.isEnabled()) {
                    assertTrue(destinationConnector.getCurrentState() != DeployedState.STOPPED);
                }
            }
        }

        for (int i = 1; i <= TEST_SIZE; i++) {
            sourceConnector.readTestMessage(testMessage);
        }

        Thread.sleep(1000);

        for (DestinationChainProvider chain : channel.getDestinationChainProviders()) {
            for (DestinationConnector destinationConnector : chain.getDestinationConnectors().values()) {
                if (destinationConnector.isEnabled()) {
                    assertEquals(((TestDestinationConnector) destinationConnector).getMessageIds().size(), TEST_SIZE);
                }
            }
        }

        channel.stop();
        channel.undeploy();
    }

    /*
     * Deploys, starts and pauses a channel, asserts that: - The source connector is not running
     * Then sends messages and asserts that: - Each message is not received by the destination
     * connectors
     */
    @Test
    public final void testPauseChannel() throws Exception {
        Channel channel = TestUtils.createDefaultChannel(channelId, serverId, false, 1, 1);
        TestSourceConnector sourceConnector = (TestSourceConnector) channel.getSourceConnector();

        channel.deploy();
        channel.start(null);
        channel.pause();

        assertFalse(sourceConnector.getCurrentState() != DeployedState.STOPPED);

        ChannelException exception = null;

        try {
            sourceConnector.readTestMessage(testMessage);
        } catch (ChannelException e) {
            exception = e;
        }

        assertNotNull(exception);
        Thread.sleep(1000);

        for (DestinationChainProvider chain : channel.getDestinationChainProviders()) {
            for (DestinationConnector destinationConnector : chain.getDestinationConnectors().values()) {
                if (destinationConnector.isEnabled()) {
                    assertEquals(((TestDestinationConnector) destinationConnector).getMessageIds().size(), 0);
                }
            }
        }

        channel.stop();
        channel.undeploy();
    }

    /*
     * Deploys and starts a channel, sends messages, stops the channel and asserts that: - The
     * channel is not running - The source connector is not running - Each destination connector is
     * not running - Each message is received by each destination connector Then sends messages and
     * asserts that: - Each dispatch returns a null MessageResponse
     */
    @Test
    public final void testStopChannel() throws Exception {
        Channel channel = TestUtils.createDefaultChannel(channelId, serverId, false, 1, 1);
        TestSourceConnector sourceConnector = (TestSourceConnector) channel.getSourceConnector();

        channel.deploy();
        channel.start(null);

        for (int i = 1; i <= TEST_SIZE; i++) {
            sourceConnector.readTestMessage(testMessage);
        }

        channel.stop();
        assertFalse(channel.getCurrentState() == DeployedState.STARTED);
        assertFalse(sourceConnector.getCurrentState() != DeployedState.STOPPED);

        for (DestinationChainProvider chain : channel.getDestinationChainProviders()) {
            for (DestinationConnector destinationConnector : chain.getDestinationConnectors().values()) {
                if (destinationConnector.isEnabled()) {
                    assertFalse(destinationConnector.getCurrentState() != DeployedState.STOPPED);
                    assertEquals(((TestDestinationConnector) destinationConnector).getMessageIds().size(), TEST_SIZE);
                }
            }
        }

        ChannelException exception = null;

        try {
            assertNull(sourceConnector.readTestMessage(testMessage));
        } catch (ChannelException e) {
            exception = e;
        }

        assertNotNull(exception);
        channel.undeploy();
    }

    /*
     * Deploys and starts a channel, sends messages, stops the channel and asserts that: - The
     * channel is not running - The source connector is not running - Each destination connector is
     * not running - Each message is received by the source connector Then sends messages and
     * asserts that: - Each dispatch returns a null MessageResponse
     */
    @Test
    public final void testHardStop() throws Exception {
        Channel channel = TestUtils.createDefaultChannel(channelId, serverId, false, 1, 1);
        TestSourceConnector sourceConnector = (TestSourceConnector) channel.getSourceConnector();

        channel.deploy();
        channel.start(null);

        for (int i = 1; i <= TEST_SIZE; i++) {
            sourceConnector.readTestMessage(testMessage);
        }

        channel.halt();
        assertFalse(channel.getCurrentState() == DeployedState.STARTED);
        assertFalse(sourceConnector.getCurrentState() != DeployedState.STOPPED);

        for (DestinationChainProvider chain : channel.getDestinationChainProviders()) {
            for (DestinationConnector destinationConnector : chain.getDestinationConnectors().values()) {
                if (destinationConnector.isEnabled()) {
                    assertFalse(destinationConnector.getCurrentState() != DeployedState.STOPPED);
                }
            }
        }

        assertEquals(sourceConnector.getMessageIds().size(), TEST_SIZE);

        ChannelException exception = null;

        try {
            assertNull(sourceConnector.readTestMessage(testMessage));
        } catch (ChannelException e) {
            exception = e;
        }

        assertNotNull(exception);
        channel.undeploy();
    }

    /*
     * Creates a channel and asserts that: - The channel exists Then removes the channel and asserts
     * that: - The channel does not exist
     */
    @Test
    public final void testControllerRemoveChannel() throws Exception {
        ChannelController channelController = ChannelController.getInstance();

        // Create Channel
        TestUtils.createDefaultChannel(channelId, serverId, false, 1, 1);
        TestUtils.assertChannelExists(channelId);

        // Delete Channel
        channelController.removeChannel(channelId);
        TestUtils.assertChannelDoesNotExist(channelId);
    }

    /*
     * Creates and deploys a channel, and asserts that: - No extra columns exist on the custom
     * metadata table
     * 
     * Adds metadata columns (one of each type), redeploys the channel, and asserts that: - All the
     * added columns are in the database with the correct name and type
     * 
     * Removes one of the columns, redeploys, and asserts that: - The column is no longer in the
     * database
     * 
     * Alters the name of one of the columns, redeploys, and asserts that: - The old column is
     * dropped correctly - The new column is added correctly
     * 
     * Alters the type one of the columns, redeploys, and asserts that: - The old column is dropped
     * correctly - The new column is added correctly
     * 
     * Alters the name and type of one of the columns, redeploys, and asserts that: - The old column
     * is dropped correctly - The new column is added correctly
     */
    @Test
    public final void testUpdateMetaDataColumns() throws Exception {
        ChannelController.getInstance().removeChannel(channelId);

        TestChannel channel = (TestChannel) TestUtils.createDefaultChannel(channelId, serverId);

        channel.deploy();

        // Assert that there are no columns currently
        assertEquals(TestUtils.getExistingMetaDataColumns(channelId).size(), 0);

        // Add all the columns
        channel.getMetaDataColumns().add(new MetaDataColumn("stringcolumn", MetaDataColumnType.STRING, null));
        channel.getMetaDataColumns().add(new MetaDataColumn("numbercolumn", MetaDataColumnType.NUMBER, null));
        channel.getMetaDataColumns().add(new MetaDataColumn("booleancolumn", MetaDataColumnType.BOOLEAN, null));
        channel.getMetaDataColumns().add(new MetaDataColumn("timestampcolumn", MetaDataColumnType.TIMESTAMP, null));

        channel.undeploy();
        channel.deploy();

        // Assert that each column exists
        List<MetaDataColumn> columns = TestUtils.getExistingMetaDataColumns(channelId);
        assertTrue(columns.contains(new MetaDataColumn("stringcolumn", MetaDataColumnType.STRING, null)));
        assertTrue(columns.contains(new MetaDataColumn("numbercolumn", MetaDataColumnType.NUMBER, null)));
        assertTrue(columns.contains(new MetaDataColumn("booleancolumn", MetaDataColumnType.BOOLEAN, null)));
        assertTrue(columns.contains(new MetaDataColumn("timestampcolumn", MetaDataColumnType.TIMESTAMP, null)));

        // Remove the string column
        channel.getMetaDataColumns().remove(0);

        channel.undeploy();
        channel.deploy();

        // Assert that the string column doesn't exist anymore
        columns = TestUtils.getExistingMetaDataColumns(channelId);
        assertFalse(columns.contains(new MetaDataColumn("stringcolumn", MetaDataColumnType.STRING, null)));

        // Alter the long column's name
        channel.getMetaDataColumns().get(0).setName("longcolumn2");

        channel.undeploy();
        channel.deploy();

        // Assert that the long column got dropped/added correctly
        columns = TestUtils.getExistingMetaDataColumns(channelId);
        assertFalse(columns.contains(new MetaDataColumn("numbercolumn", MetaDataColumnType.NUMBER, null)));
        assertTrue(columns.contains(new MetaDataColumn("numbercolumn2", MetaDataColumnType.NUMBER, null)));

        // Alter the double column's type
        channel.getMetaDataColumns().get(1).setType(MetaDataColumnType.TIMESTAMP);

        channel.undeploy();
        channel.deploy();

        // Assert that the double column got dropped/added correctly as a timestamp column
        columns = TestUtils.getExistingMetaDataColumns(channelId);
        assertFalse(columns.contains(new MetaDataColumn("numbercolumn", MetaDataColumnType.NUMBER, null)));
        assertTrue(columns.contains(new MetaDataColumn("numbercolumn", MetaDataColumnType.TIMESTAMP, null)));

        // Alter the boolean column's name and type
        channel.getMetaDataColumns().get(2).setName("booleancolumn2");
        channel.getMetaDataColumns().get(2).setType(MetaDataColumnType.TIMESTAMP);

        channel.undeploy();
        channel.deploy();

        // Assert that the boolean column got dropped/added correctly as a time column
        columns = TestUtils.getExistingMetaDataColumns(channelId);
        assertFalse(columns.contains(new MetaDataColumn("booleancolumn", MetaDataColumnType.BOOLEAN, null)));
        assertTrue(columns.contains(new MetaDataColumn("booleancolumn2", MetaDataColumnType.TIMESTAMP, null)));

        channel.undeploy();
    }

    @Test
    public final void testMetaDataCasting() throws MetaDataColumnException {
        MetaDataColumnType columnType = MetaDataColumnType.BOOLEAN;
        Boolean booleanValue = (Boolean) columnType.castValue("TRUE");
        assertEquals(Boolean.TRUE, booleanValue);
        booleanValue = (Boolean) columnType.castValue("FALSE");
        assertEquals(Boolean.FALSE, booleanValue);

        columnType = MetaDataColumnType.NUMBER;
        BigDecimal bigDecimalValue = (BigDecimal) columnType.castValue("1.0234567890123456789");
        assertEquals(new BigDecimal(1.0234567890123456789), bigDecimalValue);

        columnType = MetaDataColumnType.STRING;
        String stringValue = (String) columnType.castValue(" test !@# String 123 ");
        assertEquals(" test !@# String 123 ", stringValue);

        columnType = MetaDataColumnType.TIMESTAMP;
        Calendar dateValue = (Calendar) columnType.castValue("2010-01-02 13:01:02");
        assertEquals("13 01 02 01 02 2010", new SimpleDateFormat("HH mm ss MM dd yyyy").format(dateValue.getTimeInMillis()));
    }

    @Test
    public final void testEncryption() throws Exception {
        //TODO UPDATE THIS TEST!
//        final String prefix = "Encrypted: ";
//        final int prefixLength = prefix.length();
//        
//        TestChannel channel = (TestChannel) TestUtils.createDefaultChannel(channelId, serverId);
//        channel.setEncryptor(new Encryptor() {
//            @Override
//            public String encrypt(String text) {
//                return prefix + text;
//            }
//            
//            @Override
//            public String decrypt(String text) {
//                return text.substring(prefixLength);
//            }
//        });
//        
//        SourceConnector sourceConnector = channel.getSourceConnector();
//
//        channel.deploy();
//        channel.start();
//        
//        DispatchResult dispatchResult = sourceConnector.dispatchRawMessage(new RawMessage(testMessage));
//        sourceConnector.finishDispatch(dispatchResult);
//
//        channel.stop();
//        channel.undeploy();
//        
//        Connection connection = null;
//        PreparedStatement statement = null;
//        ResultSet resultSet = null;
//        
//        try {
//            connection = TestUtils.getConnection();
//            
//            long messageId = dispatchResult.getProcessedMessage().getMessageId();
//            
//            statement = connection.prepareStatement("SELECT content, is_encrypted FROM d_mc" + ChannelController.getInstance().getLocalChannelId(channelId) + " WHERE message_id = ? AND metadata_id = ? AND content_type = ?");
//            statement.setLong(1, messageId);
//            
//            for (ConnectorMessage connectorMessage : dispatchResult.getProcessedMessage().getConnectorMessages().values()) {
//                int metaDataId = connectorMessage.getMetaDataId();
//                statement.setInt(2, metaDataId);
//                
//                for (ContentType contentType : ContentType.getMessageTypes()) {
//                    MessageContent messageContent = connectorMessage.getContent(contentType);
//                    
//                    if (messageContent != null) {
//                        assertNotNull(messageContent.getContent());
//                        //TODO Update this test, no longer valid
////                        assertEquals(prefix + messageContent.getContent(), messageContent.getEncryptedContent());
//                    }
//                    
//                    statement.setInt(3, contentType.getContentTypeCode());
//                    resultSet = statement.executeQuery();
//                    
//                    if (resultSet.next()) {
//                        assertEquals(prefix + messageContent.getContent(), resultSet.getString("content"));
//                        assertTrue(resultSet.getBoolean("is_encrypted"));
//                    } else if (messageContent != null && (metaDataId == 0 || !contentType.equals(ContentType.RAW))) {
//                        throw new AssertionError("Message content was not stored in the database (" + messageId + "/" + metaDataId + "/" + contentType.getContentTypeCode() + ")");
//                    }
//                    
//                    resultSet.close();
//                }
//            }
//        } finally {
//            TestUtils.close(resultSet);
//            TestUtils.close(statement);
//            TestUtils.close(connection);
//        }
    }

    /*
     * Replaced by the ci/tests/160-message-storage-levels fixtures (DEVELOPMENT, PRODUCTION, RAW
     * and METADATA), MessageStorageDisabledTest (DISABLED) and the ci/tests/170-content-removal
     * fixtures (removeContentOnCompletion, with and without a queued destination).
     */
}
