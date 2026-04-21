package com.mirth.connect.connectors.dimse;

import static org.junit.Assert.assertEquals;

import java.io.File;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.junit.Test;

import com.mirth.connect.connectors.dimse.dicom.DicomConstants;
import com.mirth.connect.connectors.dimse.dicom.dcm5.Dcm5DicomSender;
import com.mirth.connect.donkey.model.message.ConnectorMessage;
import com.mirth.connect.donkey.model.message.Response;
import com.mirth.connect.donkey.model.message.Status;
import com.mirth.connect.donkey.model.message.attachment.AttachmentHandlerProvider;
import com.mirth.connect.donkey.util.DonkeyElement;
import com.mirth.connect.donkey.util.DonkeyElement.DonkeyElementException;
import com.mirth.connect.server.attachments.dicom.DICOMAttachmentHandlerProvider;
import com.mirth.connect.server.controllers.MessageController;

public class DICOMDispatcherTest {

    @Test
    public void testSendWithStatusCodes() {
        // send message using our custom sender
        TestDICOMDispatcher dispatcher = new TestDICOMDispatcher();
        dispatcher.configuration = new DefaultDICOMConfiguration();
        DICOMDispatcherProperties props = new DICOMDispatcherProperties();
        props.setHost("host");
        props.setPort("9000");
        ConnectorMessage message = new ConnectorMessage();

        Response response = null;
        Status status = null;
        String statusMessage = null;

        TestDicomSender.setCommitSucceeded(true);
        TestDicomSender.setCmdStatus(0);
        response = dispatcher.send(props, message);
        status = response.getStatus();
        statusMessage = response.getStatusMessage();

        // check with 0 status
        assertEquals(Status.SENT, status);
        assertEquals("DICOM message successfully sent", statusMessage);

        // check with 0xB000 || 0xB006 || 0xB007 status
        TestDicomSender.setCmdStatus(0xB000);
        response = dispatcher.send(props, message);
        status = response.getStatus();
        statusMessage = response.getStatusMessage();
        assertEquals(Status.SENT, status);
        assertEquals("DICOM message successfully sent with warning status code: 0x" + DicomConstants.shortToHex(0xB000), statusMessage);

        TestDicomSender.setCmdStatus(0xB006);
        response = dispatcher.send(props, message);
        status = response.getStatus();
        statusMessage = response.getStatusMessage();
        assertEquals(Status.SENT, status);
        assertEquals("DICOM message successfully sent with warning status code: 0x" + DicomConstants.shortToHex(0xB006), statusMessage);

        TestDicomSender.setCmdStatus(0xB007);
        response = dispatcher.send(props, message);
        status = response.getStatus();
        statusMessage = response.getStatusMessage();
        assertEquals(Status.SENT, status);
        assertEquals("DICOM message successfully sent with warning status code: 0x" + DicomConstants.shortToHex(0xB007), statusMessage);

        // check other status == QUEUED
        TestDicomSender.setCmdStatus(0xB008);
        response = dispatcher.send(props, message);
        status = response.getStatus();
        statusMessage = response.getStatusMessage();
        assertEquals(Status.QUEUED, status);
        assertEquals("Error status code received from DICOM server: 0x" + DicomConstants.shortToHex(0xB008), statusMessage);
    }

    @Test
    public void testResponseData() throws DonkeyElementException {
        // send message using our custom sender
        TestDICOMDispatcher dispatcher = new TestDICOMDispatcher();
        dispatcher.configuration = new DefaultDICOMConfiguration();
        DICOMDispatcherProperties props = new DICOMDispatcherProperties();
        props.setHost("host");
        props.setPort("9000");
        ConnectorMessage message = new ConnectorMessage();

        TestDicomSender.setCmdStatus(0);
        TestDicomSender.setCommitSucceeded(true);
        Response response = dispatcher.send(props, message);
        String responseData = response.getMessage();

        String expectedResponseString = "<dicom><tag00000900 len=\"2\" tag=\"00000900\" vr=\"US\">0</tag00000900></dicom>";
        DonkeyElement dicom = new DonkeyElement(expectedResponseString);
        assertEquals(dicom.toXml(), responseData);
    }

    @Test
    public void testStorageCommitment() throws Exception {
        TestDICOMDispatcher dispatcher = new TestDICOMDispatcher();
        dispatcher.configuration = new DefaultDICOMConfiguration();
        DICOMDispatcherProperties props = new DICOMDispatcherProperties();
        props.setHost("host");
        props.setPort("9000");
        props.setStgcmt(true);
        ConnectorMessage message = new ConnectorMessage();

        TestDicomSender.setCmdStatus(0);
        TestDicomSender.setCommitSucceeded(false);

        Response response = null;
        Status status = null;
        String statusMessage = null;

        response = dispatcher.send(props, message);
        status = response.getStatus();
        statusMessage = response.getStatusMessage();

        assertEquals(Status.QUEUED, status);
        assertEquals("DICOM message successfully sent but Storage Commitment failed with reason: Unknown", statusMessage);

        // Test the case where the stgcmt request succeeds but contains failed SOP items
        TestDicomSender.setCommitSucceeded(true);
        TestDicomSender.setFailedSOP(true);
        TestDicomSender.setFailureReason(1);

        response = dispatcher.send(props, message);
        status = response.getStatus();
        statusMessage = response.getStatusMessage();

        assertEquals(Status.QUEUED, status);
        assertEquals("DICOM message successfully sent but Storage Commitment failed with reason: 1", statusMessage);

        TestDicomSender.setCommitSucceeded(false);
        TestDicomSender.setFailedSOP(false);
        TestDicomSender.setFailureReason(0);

        // test that a failed storage commitment doesn't cause the message to fail
        // if the dispatcher isn't configured to care
        props.setStgcmt(false);
        response = dispatcher.send(props, message);
        status = response.getStatus();
        statusMessage = response.getStatusMessage();

        assertEquals(Status.SENT, status);
        assertEquals("DICOM message successfully sent", statusMessage);

        // check with 0xB000 and requesting storage commitment
        props.setStgcmt(true);
        TestDicomSender.setCmdStatus(0xB000);
        response = dispatcher.send(props, message);
        status = response.getStatus();
        statusMessage = response.getStatusMessage();
        assertEquals(Status.QUEUED, status);
        String expectedMessage = "DICOM message successfully sent with warning status code: 0x" + DicomConstants.shortToHex(0xB000) + " but Storage Commitment failed with reason: Unknown";
        assertEquals(expectedMessage, statusMessage);

        // check other status and requesting storage commitment
        TestDicomSender.setCmdStatus(0xB008);
        response = dispatcher.send(props, message);
        status = response.getStatus();
        statusMessage = response.getStatusMessage();
        assertEquals(Status.QUEUED, status);
        assertEquals("Error status code received from DICOM server: 0x" + DicomConstants.shortToHex(0xB008), statusMessage);
    }

    /**
     * Test dcm5 sender that stubs out all network operations.
     */
    private static class TestDicomSender extends Dcm5DicomSender {
        private static int cmdStatus;
        private static boolean commitSucceeded = true;
        private static boolean failedSOP = false;
        private static int failureReason = 0;

        public TestDicomSender(DICOMConfiguration configuration) {
            super(configuration);
        }

        public static void setCmdStatus(int status) {
            cmdStatus = status;
        }

        public static void setCommitSucceeded(boolean succeeded) {
            commitSucceeded = succeeded;
        }

        public static void setFailedSOP(boolean failedSOP) {
            TestDicomSender.failedSOP = failedSOP;
        }

        public static void setFailureReason(int failureReason) {
            TestDicomSender.failureReason = failureReason;
        }

        @Override
        public Attributes send() {
            Attributes cmd = new Attributes();
            cmd.setInt(Tag.Status, VR.US, cmdStatus);
            return cmd;
        }

        @Override
        public Attributes waitForStgCmtResult() throws InterruptedException {
            Attributes rsp = new Attributes();
            if (failedSOP) {
                Sequence failedSOPSq = rsp.newSequence(DicomConstants.TAG_FAILED_SOP_SEQUENCE, 1);
                Attributes failedSOPItem = new Attributes();
                failedSOPItem.setInt(DicomConstants.TAG_FAILURE_REASON, VR.US, failureReason);
                failedSOPSq.add(failedSOPItem);
            }
            return rsp;
        }

        @Override public boolean commit() { return commitSucceeded; }
        @Override public void addFile(File file) {}
        @Override public void configureTransferCapability() {}
        @Override public void start() {}
        @Override public void open() {}
        @Override public void close() {}
        @Override public void stop() {}
    }

    private class TestDICOMDispatcher extends DICOMDispatcher {
        @Override
        protected Dcm5DicomSender createDicomSender(DICOMConfiguration configuration) {
            return new TestDicomSender(configuration);
        }

        @Override
        protected AttachmentHandlerProvider getAttachmentHandlerProvider() {
            return new TestAttachmentHandlerProvider(null);
        }
    }

    private class TestAttachmentHandlerProvider extends DICOMAttachmentHandlerProvider {
        public TestAttachmentHandlerProvider(MessageController messageController) {
            super(messageController);
        }

        @Override
        public byte[] reAttachMessage(String raw, ConnectorMessage connectorMessage, String charsetEncoding, boolean binary, boolean reattach) {
            return "".getBytes();
        }
    }
}
