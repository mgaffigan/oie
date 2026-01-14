/*
 * SPDX-License-Identifier: MPL-2.0
 * Copyright (c) 2025 NovaMap Health Limited <https://novamap.health>
 */

package org.openintegrationengine.tlsmanager.shared;

import com.mirth.connect.model.converters.ObjectXMLSerializer;
import org.openintegrationengine.tlsmanager.shared.models.ConnectionTestResult;
import org.openintegrationengine.tlsmanager.shared.models.LocalCertificate;
import org.openintegrationengine.tlsmanager.shared.models.TrustedCertificate;
import org.openintegrationengine.tlsmanager.shared.properties.TLSConnectorProperties;

import java.util.List;

public class SerializationController {

    private static final List<String> types = List.of(
        TLSConnectorProperties.class.getCanonicalName(),
        TrustedCertificate.class.getCanonicalName(),
        LocalCertificate.class.getCanonicalName(),
        ConnectionTestResult.class.getCanonicalName()
    );

    private static final Class<?>[] classes = new Class[]{
        TrustedCertificate.class,
        LocalCertificate.class
    };

    private static final List<String> wildcardTypes = List.of();
    private static final List<String> typeHierarchies = List.of();

    // Register our property classes with XStream to prevent ForbiddenClassException
    public static void registerSerializableClasses() {
        ObjectXMLSerializer.getInstance().allowTypes(types, wildcardTypes, typeHierarchies);
        ObjectXMLSerializer.getInstance().processAnnotations(classes);
    }

    private SerializationController() {
    }
}
