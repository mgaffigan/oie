/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 *
 * http://www.mirthcorp.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.donkey.test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.util.Calendar;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.mirth.connect.donkey.model.DonkeyException;
import com.mirth.connect.donkey.model.message.ConnectorMessage;
import com.mirth.connect.donkey.model.message.ContentType;
import com.mirth.connect.donkey.model.message.MessageContent;
import com.mirth.connect.donkey.model.message.MessageSerializer;
import com.mirth.connect.donkey.model.message.MessageSerializerException;
import com.mirth.connect.donkey.model.message.Status;
import com.mirth.connect.donkey.server.Donkey;
import com.mirth.connect.donkey.server.StartException;
import com.mirth.connect.donkey.server.channel.FilterTransformerExecutor;
import com.mirth.connect.donkey.server.message.DataType;
import com.mirth.connect.donkey.test.util.TestAutoResponder;
import com.mirth.connect.donkey.test.util.TestDataType;
import com.mirth.connect.donkey.test.util.TestFilterTransformer;
import com.mirth.connect.donkey.test.util.TestUtils;

/*
 * What is left of this class after the rest of it was re-implemented as the ci/tests fixtures
 * 120-filter-transformer, 130-hl7-strict-serialization and 140-pre-post-processor. The one case
 * below has no fixture because it needs transformed content that the outbound serializer rejects,
 * which no data type produces from an input the harness can submit.
 */
public class FilterTransformerTests {
    private static int TEST_SIZE = 10;
    private static String channelId = TestUtils.DEFAULT_CHANNEL_ID;
    private static String channelName = TestUtils.DEFAULT_CHANNEL_ID;
    private static String serverId = TestUtils.DEFAULT_SERVER_ID;
    private static String testMessage = TestUtils.TEST_HL7_MESSAGE;

    private Logger logger = LogManager.getLogger(this.getClass());

    @BeforeClass
    final public static void beforeClass() throws StartException {
        Donkey.getInstance().startEngine(TestUtils.getDonkeyTestConfiguration());
    }

    @AfterClass
    final public static void afterClass() throws StartException {
        Donkey.getInstance().stopEngine();
    }

    /*
     * For each test, create a new connector message with some raw content
     *
     * Set the outbound data type to a FailingTestDataType which always throws a
     * SerializerException Process the connector message, and assert that: - The transformed content
     * is not null - The encoded content is null
     */
    @Test
    public void testOutboundSerializationFailure() throws Exception {
        ConnectorMessage connectorMessage;
        FilterTransformerExecutor filterTransformerExecutor;

        class FailingTestSerializer implements MessageSerializer {
            @Override
            public boolean isSerializationRequired(boolean isXml) {
                return false;
            }

            @Override
            public String transformWithoutSerializing(String message, MessageSerializer outboundSerializer) {
                return message;
            }

            @Override
            public String toXML(String message) throws MessageSerializerException {
                throw new MessageSerializerException("Inbound serialization failed.");
            }

            @Override
            public String fromXML(String message) throws MessageSerializerException {
                throw new MessageSerializerException("Outbound serialization failed.");
            }

            @Override
            public void populateMetaData(String message, Map<String, Object> map) {}

            @Override
            public String toJSON(String message) throws MessageSerializerException {
                return null;
            }

            @Override
            public String fromJSON(String message) throws MessageSerializerException {
                return null;
            }
        }

        class FailingTestDataType extends DataType {
            public FailingTestDataType() {
                super("HL7V2", new FailingTestSerializer(), new TestAutoResponder());
            }
        }

        logger.info("Testing FilterTransformerExecutor.processConnectorMessage with a failing outbound serializer...");

        filterTransformerExecutor = new FilterTransformerExecutor(new TestDataType(), new FailingTestDataType());
        filterTransformerExecutor.setFilterTransformer(new TestFilterTransformer());
        for (int i = 1; i <= TEST_SIZE; i++) {
            connectorMessage = new ConnectorMessage(channelId, channelName, 1, 1, serverId, Calendar.getInstance(), Status.RECEIVED);
            connectorMessage.setRaw(new MessageContent(channelId, 1, 1, ContentType.RAW, testMessage, "HL7V2", false));

            try {
                filterTransformerExecutor.processConnectorMessage(connectorMessage);
            } catch (DonkeyException e) {
            }

            assertNotNull(connectorMessage.getTransformed());
            assertNull(connectorMessage.getEncoded());
        }
    }
}
