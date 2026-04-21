/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 * 
 * http://www.mirthcorp.com
 * 
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.connectors.dimse;

import org.dcm4che3.net.Association;
import org.dcm4che3.net.Connection;

import com.mirth.connect.connectors.dimse.dicom.dcm5.Dcm5DicomReceiver;
import com.mirth.connect.connectors.dimse.dicom.dcm5.Dcm5DicomSender;
import java.util.Map;
import com.mirth.connect.donkey.server.channel.Connector;

/**
 * Interface for DICOM connector configuration.
 */
public interface DICOMConfiguration {

    void configureConnectorDeploy(Connector connector) throws Exception;

    void configureReceiver(Dcm5DicomReceiver receiver, DICOMReceiver connector, DICOMReceiverProperties connectorProperties) throws Exception;

    void configureSender(Dcm5DicomSender sender, DICOMDispatcher connector, DICOMDispatcherProperties connectorProperties) throws Exception;

    /**
     * Extracts additional information from a DICOM C-STORE association request.
        * The association parameter is the active dcm4che association object.
     *
     * @param association The library-specific association object
     * @return Additional key-value pairs to add to the source map
     */
    Map<String, Object> getCStoreRequestInformation(Association association);

    /**
     * Optional factory method for creating a custom connection object.
     * If {@code null} is returned, the default connection is used.
     *
     * @return A library-specific connection object, or {@code null} for the default
     */
    default Connection createNetworkConnection() { return null; }
}
