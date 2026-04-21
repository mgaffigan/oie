/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 *
 * http://www.mirthcorp.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.connectors.dimse;

import java.io.File;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.VR;

import com.mirth.connect.connectors.dimse.dicom.DicomConstants;
import com.mirth.connect.connectors.dimse.dicom.dcm5.Dcm5DicomSender;
import com.mirth.connect.donkey.model.channel.ConnectorProperties;
import com.mirth.connect.donkey.model.event.ConnectionStatusEventType;
import com.mirth.connect.donkey.model.event.ErrorEventType;
import com.mirth.connect.donkey.model.message.ConnectorMessage;
import com.mirth.connect.donkey.model.message.Response;
import com.mirth.connect.donkey.model.message.Status;
import com.mirth.connect.donkey.server.ConnectorTaskException;
import com.mirth.connect.donkey.server.channel.DestinationConnector;
import com.mirth.connect.donkey.server.event.ConnectionStatusEvent;
import com.mirth.connect.donkey.server.event.ErrorEvent;
import com.mirth.connect.donkey.util.DonkeyElement;
import com.mirth.connect.server.controllers.ConfigurationController;
import com.mirth.connect.server.controllers.ControllerFactory;
import com.mirth.connect.server.controllers.EventController;
import com.mirth.connect.server.util.TemplateValueReplacer;
import com.mirth.connect.util.ErrorMessageBuilder;

public class DICOMDispatcher extends DestinationConnector {
    private Logger logger = LogManager.getLogger(this.getClass());
    private DICOMDispatcherProperties connectorProperties;

    private EventController eventController = ControllerFactory.getFactory().createEventController();
    private ConfigurationController configurationController = ControllerFactory.getFactory().createConfigurationController();
    private TemplateValueReplacer replacer = new TemplateValueReplacer();
    protected DICOMConfiguration configuration = null;

    @Override
    public void onDeploy() throws ConnectorTaskException {
        this.connectorProperties = (DICOMDispatcherProperties) getConnectorProperties();

        // load the default configuration
        String configurationClass = configurationController.getProperty(connectorProperties.getProtocol(), "dicomConfigurationClass");

        configuration = loadConfiguration(configurationClass);

        try {
            configuration.configureConnectorDeploy(this);
        } catch (Exception e) {
            throw new ConnectorTaskException(e);
        }
    }

    @Override
    public void onUndeploy() throws ConnectorTaskException {}

    @Override
    public void onStart() throws ConnectorTaskException {}

    @Override
    public void onStop() throws ConnectorTaskException {}

    @Override
    public void onHalt() throws ConnectorTaskException {}

    @Override
    public void replaceConnectorProperties(ConnectorProperties connectorProperties, ConnectorMessage connectorMessage) {
        DICOMDispatcherProperties dicomDispatcherProperties = (DICOMDispatcherProperties) connectorProperties;

        dicomDispatcherProperties.setHost(replacer.replaceValues(dicomDispatcherProperties.getHost(), connectorMessage));
        dicomDispatcherProperties.setPort(replacer.replaceValues(dicomDispatcherProperties.getPort(), connectorMessage));

        dicomDispatcherProperties.setLocalHost(replacer.replaceValues(dicomDispatcherProperties.getLocalHost(), connectorMessage));
        dicomDispatcherProperties.setLocalPort(replacer.replaceValues(dicomDispatcherProperties.getLocalPort(), connectorMessage));

        dicomDispatcherProperties.setApplicationEntity(replacer.replaceValues(dicomDispatcherProperties.getApplicationEntity(), connectorMessage));
        dicomDispatcherProperties.setLocalApplicationEntity(replacer.replaceValues(dicomDispatcherProperties.getLocalApplicationEntity(), connectorMessage));

        dicomDispatcherProperties.setUsername(replacer.replaceValues(dicomDispatcherProperties.getUsername(), connectorMessage));
        dicomDispatcherProperties.setPasscode(replacer.replaceValues(dicomDispatcherProperties.getPasscode(), connectorMessage));

        dicomDispatcherProperties.setTemplate(replacer.replaceValues(dicomDispatcherProperties.getTemplate(), connectorMessage));

        dicomDispatcherProperties.setKeyStore(replacer.replaceValues(dicomDispatcherProperties.getKeyStore(), connectorMessage));
        dicomDispatcherProperties.setKeyStorePW(replacer.replaceValues(dicomDispatcherProperties.getKeyStorePW(), connectorMessage));

        dicomDispatcherProperties.setTrustStore(replacer.replaceValues(dicomDispatcherProperties.getTrustStore(), connectorMessage));
        dicomDispatcherProperties.setTrustStorePW(replacer.replaceValues(dicomDispatcherProperties.getTrustStorePW(), connectorMessage));

        dicomDispatcherProperties.setKeyPW(replacer.replaceValues(dicomDispatcherProperties.getKeyPW(), connectorMessage));
    }

    @Override
    public Response send(ConnectorProperties connectorProperties, ConnectorMessage connectorMessage) {
        DICOMDispatcherProperties dicomDispatcherProperties = (DICOMDispatcherProperties) connectorProperties;

        String info = "Host: " + dicomDispatcherProperties.getHost();
        eventController.dispatchEvent(new ConnectionStatusEvent(getChannelId(), getMetaDataId(), getDestinationName(), ConnectionStatusEventType.WRITING, info));

        String responseData = null;
        String responseError = null;
        String responseStatusMessage = null;
        Status responseStatus = Status.QUEUED;

        File tempFile = null;
        Dcm5DicomSender dcmSnd = createDicomSender(configuration);

        try {
            tempFile = File.createTempFile("temp", "tmp");

            FileUtils.writeByteArrayToFile(tempFile, getAttachmentHandlerProvider().reAttachMessage(dicomDispatcherProperties.getTemplate(), connectorMessage, null, true, dicomDispatcherProperties.getDestinationConnectorProperties().isReattachAttachments()));

            dcmSnd.setCalledAET("DCMRCV");
            dcmSnd.setRemoteHost(dicomDispatcherProperties.getHost());
            dcmSnd.setRemotePort(NumberUtils.toInt(dicomDispatcherProperties.getPort()));

            if ((dicomDispatcherProperties.getApplicationEntity() != null) && !dicomDispatcherProperties.getApplicationEntity().equals("")) {
                dcmSnd.setCalledAET(dicomDispatcherProperties.getApplicationEntity());
            }

            if ((dicomDispatcherProperties.getLocalApplicationEntity() != null) && !dicomDispatcherProperties.getLocalApplicationEntity().equals("")) {
                dcmSnd.setCalling(dicomDispatcherProperties.getLocalApplicationEntity());
            }

            if ((dicomDispatcherProperties.getLocalHost() != null) && !dicomDispatcherProperties.getLocalHost().equals("")) {
                dcmSnd.setLocalHost(dicomDispatcherProperties.getLocalHost());
                dcmSnd.setLocalPort(NumberUtils.toInt(dicomDispatcherProperties.getLocalPort()));
            }

            dcmSnd.addFile(tempFile);

            //TODO Allow variables
            int value = NumberUtils.toInt(dicomDispatcherProperties.getAcceptTo());
            if (value != 5)
                dcmSnd.setAcceptTimeout(value);

            value = NumberUtils.toInt(dicomDispatcherProperties.getAsync());
            if (value > 0)
                dcmSnd.setMaxOpsInvoked(value);

            value = NumberUtils.toInt(dicomDispatcherProperties.getBufSize());
            if (value != 1)
                dcmSnd.setTranscoderBufferSize(value);

            value = NumberUtils.toInt(dicomDispatcherProperties.getConnectTo());
            if (value > 0)
                dcmSnd.setConnectTimeout(value);
            if (dicomDispatcherProperties.getPriority().equals("med"))
                dcmSnd.setPriority(0);
            else if (dicomDispatcherProperties.getPriority().equals("low"))
                dcmSnd.setPriority(1);
            else if (dicomDispatcherProperties.getPriority().equals("high"))
                dcmSnd.setPriority(2);
            if (dicomDispatcherProperties.getUsername() != null && !dicomDispatcherProperties.getUsername().equals("")) {
                String username = dicomDispatcherProperties.getUsername();
                String passcode = dicomDispatcherProperties.getPasscode();
                dcmSnd.setUserIdentity(username, passcode, dicomDispatcherProperties.isUidnegrsp());
            }
            dcmSnd.setPackPDV(dicomDispatcherProperties.isPdv1());

            value = NumberUtils.toInt(dicomDispatcherProperties.getRcvpdulen());
            if (value != 16)
                dcmSnd.setMaxPDULengthReceive(value);

            value = NumberUtils.toInt(dicomDispatcherProperties.getReaper());
            if (value != 10)
                dcmSnd.setAssociationReaperPeriod(value);

            value = NumberUtils.toInt(dicomDispatcherProperties.getReleaseTo());
            if (value != 5)
                dcmSnd.setReleaseTimeout(value);

            value = NumberUtils.toInt(dicomDispatcherProperties.getRspTo());
            if (value != 60)
                dcmSnd.setDimseRspTimeout(value);

            value = NumberUtils.toInt(dicomDispatcherProperties.getShutdownDelay());
            if (value != 1000)
                dcmSnd.setShutdownDelay(value);

            value = NumberUtils.toInt(dicomDispatcherProperties.getSndpdulen());
            if (value != 16)
                dcmSnd.setMaxPDULengthSend(value);

            value = NumberUtils.toInt(dicomDispatcherProperties.getSoCloseDelay());
            if (value != 50)
                dcmSnd.setSocketCloseDelay(value);

            value = NumberUtils.toInt(dicomDispatcherProperties.getSorcvbuf());
            if (value > 0)
                dcmSnd.setReceiveBufferSize(value);

            value = NumberUtils.toInt(dicomDispatcherProperties.getSosndbuf());
            if (value > 0)
                dcmSnd.setSendBufferSize(value);

            dcmSnd.setStorageCommitment(dicomDispatcherProperties.isStgcmt());
            dcmSnd.setTcpNoDelay(!dicomDispatcherProperties.isTcpDelay());

            configuration.configureSender(dcmSnd, this, dicomDispatcherProperties);

            dcmSnd.setOfferDefaultTransferSyntaxInSeparatePresentationContext(dicomDispatcherProperties.isTs1());
            dcmSnd.configureTransferCapability();
            dcmSnd.start();

            dcmSnd.open();
            Attributes responseCommand = dcmSnd.send();

            boolean storageCommitmentFailed = false;
            String storageCommitmentFailureReason = "Unknown";
            if (dcmSnd.isStorageCommitment()) {
                if (dcmSnd.commit()) {
                    Attributes cmtrslt = dcmSnd.waitForStgCmtResult();
                    if (cmtrslt != null) {
                        Sequence failedSOPSq = cmtrslt.getSequence(DicomConstants.TAG_FAILED_SOP_SEQUENCE);
                        if (failedSOPSq != null && !failedSOPSq.isEmpty()) {
                            storageCommitmentFailed = true;
                            Attributes failedSOPItem = failedSOPSq.get(0);
                            if (failedSOPItem != null) {
                                int failureReason = failedSOPItem.getInt(DicomConstants.TAG_FAILURE_REASON, 0);
                                if (failureReason != 0) {
                                    storageCommitmentFailureReason = String.valueOf(failureReason);
                                }
                            }
                        }
                    } else {
                        logger.warn("Storage commitment result was null — remote SCP may not have responded");
                        storageCommitmentFailed = true;
                    }
                } else {
                    storageCommitmentFailed = true;
                }
            }

            dcmSnd.close();

            int status = getStatus(responseCommand);

            if (status == DicomConstants.STATUS_SUCCESS) {
                responseStatusMessage = "DICOM message successfully sent";
                responseStatus = Status.SENT;
            } else if (status == DicomConstants.STATUS_WARNING_COERCION || status == DicomConstants.STATUS_WARNING_ELEMENTS_DISCARDED || status == DicomConstants.STATUS_WARNING_DATA_SET_MISMATCH) {
                // These status codes are used in DcmSnd.onDimseRSP to flag warnings
                responseStatusMessage = "DICOM message successfully sent with warning status code: 0x" + DicomConstants.shortToHex(status);
                responseStatus = Status.SENT;
            } else {
                // Any other status is considered unsuccessful
                responseStatusMessage = "Error status code received from DICOM server: 0x" + DicomConstants.shortToHex(status);
                responseStatus = Status.QUEUED;
            }

            if (storageCommitmentFailed && responseStatus == Status.SENT) {
                responseStatusMessage += " but Storage Commitment failed with reason: " + storageCommitmentFailureReason;
                responseStatus = Status.QUEUED;
            }

            responseData = getCommandData(responseCommand);
        } catch (Exception e) {
            responseStatusMessage = ErrorMessageBuilder.buildErrorResponse(e.getMessage(), e);
            responseError = ErrorMessageBuilder.buildErrorMessage(connectorProperties.getName(), e.getMessage(), null);
            eventController.dispatchEvent(new ErrorEvent(getChannelId(), getMetaDataId(), connectorMessage.getMessageId(), ErrorEventType.DESTINATION_CONNECTOR, getDestinationName(), connectorProperties.getName(), e.getMessage(), null));
        } finally {
            try {
                dcmSnd.close();
            } catch (Exception e) {
                logger.debug("Error closing DICOM sender association", e);
            }
            dcmSnd.stop();

            if (tempFile != null) {
                tempFile.delete();
            }

            eventController.dispatchEvent(new ConnectionStatusEvent(getChannelId(), getMetaDataId(), getDestinationName(), ConnectionStatusEventType.IDLE));
        }

        return new Response(responseStatus, responseData, responseStatusMessage, responseError);
    }

    protected Dcm5DicomSender createDicomSender(DICOMConfiguration configuration) {
        return new Dcm5DicomSender(configuration);
    }

    private DICOMConfiguration loadConfiguration(String configurationClass) {
        try {
            return (DICOMConfiguration) Class.forName(configurationClass).getDeclaredConstructor().newInstance();
        } catch (Throwable t) {
            logger.trace("could not find custom configuration class, using default", t);
            return new DefaultDICOMConfiguration();
        }
    }

    protected int getStatus(Attributes command) {
        return command != null ? command.getInt(DicomConstants.TAG_STATUS, 0) : 0;
    }

    protected String getCommandData(Attributes command) {
        if (command == null) {
            return null;
        }

        try {
            DonkeyElement dicom = new DonkeyElement("<dicom/>");
            command.accept(new Attributes.Visitor() {
                @Override
                public boolean visit(Attributes attrs, int tag, VR vr, Object value) {
                    if ((tag >>> 16) != 0x0000) {
                        return true;
                    }

                    String hexTag = DicomConstants.shortToHex(tag >> 16) + DicomConstants.shortToHex(tag);
                    DonkeyElement child = dicom.addChildElement("tag" + hexTag, attrs.getString(tag));
                    child.setAttribute("len", String.valueOf(getElementLength(attrs, tag)));
                    child.setAttribute("tag", hexTag);
                    child.setAttribute("vr", vr != null ? vr.name() : String.valueOf(attrs.getVR(tag)));
                    return true;
                }
            }, false);
            return dicom.toXml();
        } catch (Throwable t) {
            logger.error("Unable to extract DICOM command data from response", t);
            return null;
        }
    }

    private int getElementLength(Attributes attrs, int tag) {
        try {
            byte[] bytes = attrs.getBytes(tag);
            return bytes != null ? bytes.length : -1;
        } catch (Exception e) {
            return -1;
        }
    }
}
