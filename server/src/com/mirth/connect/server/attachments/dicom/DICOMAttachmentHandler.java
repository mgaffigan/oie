/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 *
 * http://www.mirthcorp.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.server.attachments.dicom;

import org.apache.commons.codec.binary.StringUtils;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Fragments;
import org.dcm4che3.data.Tag;

import com.mirth.connect.donkey.model.message.RawMessage;
import com.mirth.connect.donkey.model.message.attachment.Attachment;
import com.mirth.connect.donkey.model.message.attachment.AttachmentException;
import com.mirth.connect.donkey.model.message.attachment.AttachmentHandler;
import com.mirth.connect.donkey.server.channel.Channel;
import com.mirth.connect.donkey.util.Base64Util;
import com.mirth.connect.model.converters.DICOMConverter;
import com.mirth.connect.server.util.ServerUUIDGenerator;

public class DICOMAttachmentHandler implements AttachmentHandler {

    private Attributes dicomObject;
    private Object dicomElement;
    private int index;
    private String attachmentId;

    @Override
    public void initialize(RawMessage message, Channel channel) throws AttachmentException {
        index = 0;
        try {
            byte[] messageBytes = null;
            boolean decode = false;

            if (message.isBinary()) {
                messageBytes = message.getRawBytes();
            } else {
                // Taking a string is much more inefficient than taking in a byte array. 
                // If the user manually sends a message, it will arrive as a base64 encoded string, so we must support Strings for DICOM still.
                // However, DICOM messages that use this initializer should be relatively small in size.
                messageBytes = StringUtils.getBytesUsAscii(message.getRawData());
                decode = true;
            }

            dicomObject = DICOMConverter.byteArrayToDicomObject(messageBytes, decode);
            dicomElement = dicomObject.remove(Tag.PixelData);
            attachmentId = ServerUUIDGenerator.getUUID();
        } catch (Throwable t) {
            throw new AttachmentException(t);
        }
    }

    @Override
    public Attachment nextAttachment() throws AttachmentException {
        try {
            if (dicomElement instanceof Fragments) {
                Fragments fragments = (Fragments) dicomElement;
                int total = fragments.size();
                if (index < total) {
                    String fragment = "F" + org.apache.commons.lang3.StringUtils.leftPad(String.valueOf(index), String.valueOf(total).length(), '0') + "-";
                    return new Attachment(fragment + attachmentId, (byte[]) fragments.get(index++), "DICOM");
                }
            } else if (dicomElement instanceof byte[]) {
                Attachment attachment = new Attachment(attachmentId, (byte[]) dicomElement, "DICOM");
                dicomElement = null;
                return attachment;
            }

            return null;
        } catch (Throwable t) {
            throw new AttachmentException(t);
        }
    }

    @Override
    public String shutdown() throws AttachmentException {
        try {
            byte[] encodedMessage = Base64Util.encodeBase64(DICOMConverter.dicomObjectToByteArray(dicomObject));
            dicomElement = null;
            dicomObject = null;
            return StringUtils.newStringUsAscii(encodedMessage);
        } catch (Throwable t) {
            throw new AttachmentException(t);
        }

    }
}
