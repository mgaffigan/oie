package com.mirth.connect.connectors.dimse.dicom.dcm5;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.Map;

import org.dcm4che3.net.Device;
import org.junit.Test;

import com.mirth.connect.connectors.dimse.DICOMConfiguration;
import com.mirth.connect.connectors.dimse.DICOMDispatcher;
import com.mirth.connect.connectors.dimse.DICOMDispatcherProperties;
import com.mirth.connect.connectors.dimse.DICOMReceiver;
import com.mirth.connect.connectors.dimse.DICOMReceiverProperties;
import com.mirth.connect.donkey.server.channel.Connector;

public class Dcm5DicomSenderTest {

    @Test
    public void testConstructionCreatesDevice() {
        Dcm5DicomSender sender = new Dcm5DicomSender(new TestConfig());
        assertNotNull(sender);
        assertNotNull(sender.getDevice());
        assertTrue(sender.getDevice() instanceof Device);
    }

    @Test
    public void testStorageCommitmentDefault() {
        Dcm5DicomSender sender = new Dcm5DicomSender(new TestConfig());
        assertTrue(!sender.isStorageCommitment());
    }

    @Test
    public void testSetStorageCommitment() {
        Dcm5DicomSender sender = new Dcm5DicomSender(new TestConfig());
        sender.setStorageCommitment(true);
        assertTrue(sender.isStorageCommitment());
    }

    private static class TestConfig implements DICOMConfiguration {
        @Override public void configureConnectorDeploy(Connector connector) {}
        @Override public void configureReceiver(Dcm5DicomReceiver r, DICOMReceiver c, DICOMReceiverProperties p) {}
        @Override public void configureSender(Dcm5DicomSender s, DICOMDispatcher c, DICOMDispatcherProperties p) {}
        @Override public Map<String, Object> getCStoreRequestInformation(org.dcm4che3.net.Association association) { return new java.util.HashMap<>(); }
    }
}
