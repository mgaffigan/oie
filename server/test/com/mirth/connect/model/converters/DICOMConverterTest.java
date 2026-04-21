package com.mirth.connect.model.converters;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayOutputStream;
import java.util.Base64;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.data.VR;
import org.dcm4che3.io.DicomOutputStream;
import org.junit.Test;

public class DICOMConverterTest {

    @Test
    public void testByteArrayRoundTripWithoutFmi() throws Exception {
        Attributes original = new Attributes();
        original.setString(Tag.PatientName, VR.PN, "Test^Patient");
        original.setString(Tag.PatientID, VR.LO, "12345");

        byte[] bytes = DICOMConverter.dicomObjectToByteArray(original);
        assertNotNull(bytes);
        assertTrue(bytes.length > 0);

        Attributes parsed = DICOMConverter.byteArrayToDicomObject(bytes, false);
        assertNotNull(parsed);
        assertEquals("Test^Patient", parsed.getString(Tag.PatientName));
        assertEquals("12345", parsed.getString(Tag.PatientID));
    }

    @Test
    public void testByteArrayRoundTripWithFmi() throws Exception {
        Attributes original = new Attributes();
        original.setString(Tag.PatientName, VR.PN, "FmiTest");
        original.setString(Tag.SOPClassUID, VR.UI, "1.2.840.10008.5.1.4.1.1.2");
        original.setString(Tag.SOPInstanceUID, VR.UI, "1.2.3.4.5");
        original.setString(Tag.TransferSyntaxUID, VR.UI, "1.2.840.10008.1.2");

        byte[] bytes = DICOMConverter.dicomObjectToByteArray(original);
        assertNotNull(bytes);

        Attributes parsed = DICOMConverter.byteArrayToDicomObject(bytes, false);
        assertNotNull(parsed);
        assertEquals("1.2.840.10008.1.2", parsed.getString(Tag.TransferSyntaxUID));
    }

    @Test
    public void testByteArrayBase64RoundTrip() throws Exception {
        Attributes original = new Attributes();
        original.setString(Tag.PatientName, VR.PN, "Base64Test");
        original.setString(Tag.SOPClassUID, VR.UI, "1.2.840.10008.5.1.4.1.1.2");
        original.setString(Tag.SOPInstanceUID, VR.UI, "1.2.3.4.5");
        original.setString(Tag.TransferSyntaxUID, VR.UI, "1.2.840.10008.1.2");
        byte[] dicomBytes = DICOMConverter.dicomObjectToByteArray(original);

        byte[] base64Bytes = Base64.getEncoder().encode(dicomBytes);

        Attributes parsed = DICOMConverter.byteArrayToDicomObject(base64Bytes, true);
        assertNotNull(parsed);
    }

    @Test
    public void testCreateDicomObject() {
        Attributes obj = DICOMConverter.createDicomObject();
        assertNotNull(obj);
        assertTrue(obj.isEmpty());
        assertFalse(obj.contains(Tag.TransferSyntaxUID));
    }

    @Test
    public void testGetElementName() {
        String name = DICOMConverter.getElementName(Tag.PatientName);
        assertNotNull(name);
        assertFalse(name.isEmpty());
        assertEquals("PatientName", name);
    }

    @Test
    public void testGetElementNameUnknown() {
        String name = DICOMConverter.getElementName(0x99999999);
        assertNotNull(name);
    }

    @Test
    public void testDicomBytesToXml() throws Exception {
        Attributes fmi = Attributes.createFileMetaInformation("1.2.3.4.5", "1.2.840.10008.5.1.4.1.1.2", "1.2.840.10008.1.2.1");
        Attributes dataset = new Attributes();
        dataset.setString(Tag.PatientName, VR.PN, "XmlTest^Patient");
        dataset.setString(Tag.PatientID, VR.LO, "XML123");

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DicomOutputStream dos = new DicomOutputStream(baos, UID.ExplicitVRLittleEndian);
        dos.writeDataset(fmi, dataset);
        dos.close();

        byte[] base64Bytes = Base64.getEncoder().encode(baos.toByteArray());

        String xml = DICOMConverter.dicomBytesToXml(base64Bytes);
        assertNotNull(xml);
        assertTrue(xml.contains("PatientName") || xml.contains("00100010"));
    }

    @Test
    public void testXmlToDicomObject() throws Exception {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<NativeDicomModel>"
                + "<DicomAttribute keyword=\"PatientName\" tag=\"00100010\" vr=\"PN\">"
                + "<PersonName number=\"1\"><Alphabetic><FamilyName>Test</FamilyName></Alphabetic></PersonName>"
                + "</DicomAttribute>"
                + "<DicomAttribute keyword=\"PatientID\" tag=\"00100020\" vr=\"LO\"><Value number=\"1\">ID123</Value></DicomAttribute>"
                + "</NativeDicomModel>";

        Attributes obj = DICOMConverter.xmlToDicomObject(xml, "UTF-8");
        assertNotNull(obj);
        assertEquals("ID123", obj.getString(Tag.PatientID));
    }

    @Test
    public void testXxePrevention() {
        String maliciousXml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<!DOCTYPE foo [<!ENTITY xxe SYSTEM \"file:///etc/passwd\">]>"
                + "<NativeDicomModel>&xxe;</NativeDicomModel>";

        try {
            DICOMConverter.xmlToDicomObject(maliciousXml, "UTF-8");
            fail("Expected exception for XXE attack");
        } catch (Exception e) {
            // Expected.
        }
    }

    @Test
    public void testDicomObjectToByteArrayClearsObject() throws Exception {
        Attributes obj = new Attributes();
        obj.setString(Tag.PatientName, VR.PN, "ClearTest");

        DICOMConverter.dicomObjectToByteArray(obj);

        assertNull(obj.getString(Tag.PatientName));
    }
}