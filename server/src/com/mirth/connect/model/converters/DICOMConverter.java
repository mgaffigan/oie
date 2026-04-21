/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 *
 * http://www.mirthcorp.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.model.converters;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;

import javax.xml.XMLConstants;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.sax.SAXTransformerFactory;
import javax.xml.transform.sax.TransformerHandler;
import javax.xml.transform.stream.StreamResult;

import org.apache.commons.codec.binary.Base64InputStream;
import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.ElementDictionary;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.data.VR;
import org.dcm4che3.io.ContentHandlerAdapter;
import org.dcm4che3.io.DicomInputStream;
import org.dcm4che3.io.DicomOutputStream;
import org.dcm4che3.io.SAXWriter;
import org.xml.sax.InputSource;

public class DICOMConverter {

    private static final Logger logger = LogManager.getLogger(DICOMConverter.class);

    private DICOMConverter() {}

    public static Attributes createDicomObject() {
        return new Attributes();
    }

    public static Attributes byteArrayToDicomObject(byte[] bytes, boolean decodeBase64) throws IOException {
        DicomInputStream dis = null;

        try {
            ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
            InputStream inputStream;
            if (decodeBase64) {
                inputStream = new BufferedInputStream(new Base64InputStream(bais));
            } else {
                inputStream = bais;
            }
            dis = new DicomInputStream(inputStream);
            Attributes fmi = dis.readFileMetaInformation();
            Attributes dataset = dis.readDataset(-1, -1);
            if (fmi != null) {
                copyFileMetaInformation(fmi, dataset);
            }
            return dataset;
        } catch (IOException e) {
            throw e;
        } finally {
            IOUtils.closeQuietly(dis);
        }
    }

    public static byte[] dicomObjectToByteArray(Attributes dicomObject) throws IOException {
        Attributes dataset = new Attributes(dicomObject);
        Attributes fmi = createFileMetaInformation(dataset);

        DicomOutputStream dos = null;

        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();

            if (fmi != null && !fmi.isEmpty()) {
                dos = new DicomOutputStream(baos, UID.ExplicitVRLittleEndian);
                dos.writeDataset(fmi, dataset);
            } else {
                dos = new DicomOutputStream(baos, UID.ImplicitVRLittleEndian);
                dos.writeDataset(null, dataset);
            }

            dicomObject.clear();

            return baos.toByteArray();
        } catch (IOException e) {
            throw e;
        } catch (Throwable t) {
            logger.error("Error serializing DICOM object to byte array", t);
            return null;
        } finally {
            IOUtils.closeQuietly(dos);
        }
    }

    public static String dicomBytesToXml(byte[] encodedDicomBytes) throws Exception {
        DicomInputStream dis = new DicomInputStream(new BufferedInputStream(new Base64InputStream(new ByteArrayInputStream(encodedDicomBytes))));

        try {
            dis.readFileMetaInformation();
            Attributes dataset = dis.readDataset(-1, -1);

            StringWriter output = new StringWriter();
            TransformerFactory tf = TransformerFactory.newInstance();
            tf.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            tf.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
            SAXTransformerFactory factory = (SAXTransformerFactory) tf;
            TransformerHandler handler = factory.newTransformerHandler();
            handler.getTransformer().setOutputProperty(OutputKeys.INDENT, "no");
            handler.setResult(new StreamResult(output));

            SAXWriter writer = new SAXWriter(handler);
            writer.setIncludeKeyword(true);
            writer.write(dataset);

            return output.toString();
        } finally {
            IOUtils.closeQuietly(dis);
        }
    }

    public static Attributes xmlToDicomObject(String xml, String charset) throws Exception {
        SAXParserFactory factory = SAXParserFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        SAXParser parser = factory.newSAXParser();
        Attributes dataset = new Attributes();
        ContentHandlerAdapter contentHandler = new ContentHandlerAdapter(dataset);
        byte[] documentBytes = xml.trim().getBytes(charset);
        parser.parse(new InputSource(new ByteArrayInputStream(documentBytes)), contentHandler);
        return dataset;
    }

    public static String getElementName(int tag) {
        try {
            String keyword = ElementDictionary.keywordOf(tag, null);
            return keyword != null ? keyword : "";
        } catch (Exception e) {
            return "";
        }
    }

    private static void copyFileMetaInformation(Attributes fmi, Attributes dataset) {
        copyFileMetaValue(fmi, dataset, Tag.MediaStorageSOPClassUID);
        copyFileMetaValue(fmi, dataset, Tag.MediaStorageSOPInstanceUID);
        copyFileMetaValue(fmi, dataset, Tag.TransferSyntaxUID);
    }

    private static void copyFileMetaValue(Attributes fmi, Attributes dataset, int tag) {
        String value = fmi.getString(tag);
        if (value != null) {
            dataset.setString(tag, VR.UI, value);
        }
    }

    private static Attributes createFileMetaInformation(Attributes dataset) {
        String transferSyntaxUid = dataset.getString(Tag.TransferSyntaxUID, UID.ExplicitVRLittleEndian);
        String sopClassUid = dataset.getString(Tag.MediaStorageSOPClassUID, dataset.getString(Tag.SOPClassUID));
        String sopInstanceUid = dataset.getString(Tag.MediaStorageSOPInstanceUID, dataset.getString(Tag.SOPInstanceUID));

        removeInjectedFileMetaInformation(dataset);

        if (sopClassUid == null || sopInstanceUid == null) {
            return null;
        }

        return Attributes.createFileMetaInformation(sopInstanceUid, sopClassUid, transferSyntaxUid);
    }

    private static void removeInjectedFileMetaInformation(Attributes dataset) {
        dataset.remove(Tag.MediaStorageSOPClassUID);
        dataset.remove(Tag.MediaStorageSOPInstanceUID);
        dataset.remove(Tag.TransferSyntaxUID);
    }
}
