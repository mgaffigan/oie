/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 * 
 * http://www.mirthcorp.com
 * 
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.server.util;

import java.lang.reflect.Method;
import java.util.List;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.binary.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.mirth.connect.donkey.model.message.ConnectorMessage;
import com.mirth.connect.donkey.model.message.attachment.Attachment;
import com.mirth.connect.donkey.util.Base64Util;
import com.mirth.connect.server.controllers.MessageController;
import com.mirth.connect.userutil.ImmutableConnectorMessage;

public class MessageAttachmentUtil {
    private static Logger logger = LogManager.getLogger(MessageAttachmentUtil.class);

    public static String getDICOMRawData(ImmutableConnectorMessage message) {
        String mergedMessage = null;

        List<Attachment> attachments = MessageController.getInstance().getMessageAttachment(message.getChannelId(), message.getMessageId(), false);

        if (attachments != null && attachments.size() > 0) {
            try {
                if (attachments.get(0).getType().equals("DICOM")) {
                    byte[] mergedMessageBytes = mergeHeaderAttachments(message, attachments);

                    // Replace the raw binary with the encoded binary to free up the memory
                    mergedMessageBytes = Base64Util.encodeBase64(mergedMessageBytes);

                    mergedMessage = StringUtils.newStringUsAscii(mergedMessageBytes);
                } else {
                    mergedMessage = message.getRaw().getContent();
                }
            } catch (Exception e) {
                logger.error("Error merging DICOM data", e);
                mergedMessage = message.getRaw().getContent();
            }
        } else {
            mergedMessage = message.getRaw().getContent();
        }

        return mergedMessage;
    }

    public static String getDICOMRawData(ConnectorMessage message) {
        return getDICOMRawData(new ImmutableConnectorMessage(message));
    }

    public static byte[] getDICOMRawBytes(ImmutableConnectorMessage message) {
        byte[] mergedMessage = null;

        List<Attachment> attachments = MessageController.getInstance().getMessageAttachment(message.getChannelId(), message.getMessageId(), false);

        if (attachments != null && attachments.size() > 0) {
            try {
                if (attachments.get(0).getType().equals("DICOM")) {
                    mergedMessage = mergeHeaderAttachments(message, attachments);
                } else {
                    mergedMessage = Base64.decodeBase64(StringUtils.getBytesUsAscii(message.getRaw().getContent()));
                }
            } catch (Exception e) {
                logger.error("Error merging DICOM data", e);
                mergedMessage = Base64.decodeBase64(StringUtils.getBytesUsAscii(message.getRaw().getContent()));
            }
        } else {
            mergedMessage = Base64.decodeBase64(StringUtils.getBytesUsAscii(message.getRaw().getContent()));
        }

        return mergedMessage;
    }

    public static byte[] getDICOMRawBytes(ConnectorMessage message) {
        return getDICOMRawBytes(new ImmutableConnectorMessage(message));
    }

    private static byte[] mergeHeaderAttachments(ImmutableConnectorMessage message, List<Attachment> attachments) throws Exception {
        Class<?> dicomUtilClass = Class.forName("com.mirth.connect.server.util.DICOMMessageUtil");
        Method mergeMethod = dicomUtilClass.getMethod("mergeHeaderAttachments", ImmutableConnectorMessage.class, List.class);
        return (byte[]) mergeMethod.invoke(null, message, attachments);
    }
}
