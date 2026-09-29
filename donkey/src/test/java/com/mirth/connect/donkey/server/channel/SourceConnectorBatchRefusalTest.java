/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 * 
 * http://www.mirthcorp.com
 * 
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.donkey.server.channel;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import com.mirth.connect.donkey.model.channel.DeployedState;
import com.mirth.connect.donkey.model.message.BatchRawMessage;
import com.mirth.connect.donkey.server.DeployException;
import com.mirth.connect.donkey.server.UndeployException;
import com.mirth.connect.donkey.server.message.batch.BatchAdaptor;
import com.mirth.connect.donkey.server.message.batch.BatchAdaptorFactory;
import com.mirth.connect.donkey.server.message.batch.BatchMessageException;
import com.mirth.connect.donkey.server.message.batch.BatchMessageReader;
import com.mirth.connect.donkey.server.message.batch.SimpleResponseHandler;

/**
 * A batch the connector never accepted has to be reported as a failure. Returning quietly is
 * indistinguishable from an accepted batch that contained no messages, which lets a caller record
 * a dropped submission as delivered and never retry it.
 */
public class SourceConnectorBatchRefusalTest {

    @Test
    public void refusesABatchWhileTheConnectorIsStopping() throws Exception {
        SourceConnector sourceConnector = sourceConnector(DeployedState.STOPPING);

        try {
            sourceConnector.dispatchBatchMessage(batch(), new SimpleResponseHandler());
            fail("Expected a stopping connector to refuse the batch.");
        } catch (BatchMessageException e) {
            assertTrue(String.valueOf(e), String.valueOf(e).contains("stopping"));
        }
    }

    @Test
    public void refusesABatchOnceTheBatchFactoryHasStopped() throws Exception {
        SourceConnector sourceConnector = sourceConnector(DeployedState.STARTED);
        sourceConnector.getBatchAdaptorFactory().stop();

        try {
            sourceConnector.dispatchBatchMessage(batch(), new SimpleResponseHandler());
            fail("Expected a stopped batch factory to refuse the batch.");
        } catch (BatchMessageException e) {
            assertTrue(String.valueOf(e), String.valueOf(e).contains("shutting down"));
        }
    }

    private static BatchRawMessage batch() {
        return new BatchRawMessage(new BatchMessageReader("<Batch><Message>alpha</Message></Batch>"));
    }

    private static SourceConnector sourceConnector(DeployedState state) {
        Channel channel = new Channel();
        channel.setChannelId("channel1");
        channel.setName("Test Channel");

        SourceConnector sourceConnector = new RefusalTestSourceConnector();
        sourceConnector.setChannel(channel);
        sourceConnector.setChannelId(channel.getChannelId());
        sourceConnector.setCurrentState(state);
        sourceConnector.setBatchAdaptorFactory(new RefusalTestBatchAdaptorFactory(sourceConnector));
        return sourceConnector;
    }

    /** Never reached: both tests refuse before the adaptor is created. */
    private static class RefusalTestBatchAdaptorFactory extends BatchAdaptorFactory {
        RefusalTestBatchAdaptorFactory(SourceConnector sourceConnector) {
            super(sourceConnector);
        }

        @Override
        public BatchAdaptor createBatchAdaptor(BatchRawMessage batchRawMessage) {
            throw new UnsupportedOperationException("The batch should have been refused.");
        }

        @Override
        public void onDeploy() throws DeployException {}

        @Override
        public void onUndeploy() throws UndeployException {}
    }

    private static class RefusalTestSourceConnector extends SourceConnector {
        @Override
        public void onDeploy() {}

        @Override
        public void onUndeploy() {}

        @Override
        public void onStart() {}

        @Override
        public void onStop() {}

        @Override
        public void onHalt() {}

        @Override
        public void handleRecoveredResponse(DispatchResult dispatchResult) {}

        @Override
        public void start() {}

        @Override
        public void stop() {}

        @Override
        public void halt() {}
    }
}
