/*
 * SPDX-License-Identifier: MPL-2.0
 * Copyright (c) 2025 NovaMap Health Limited <https://novamap.health>
 */

package org.openintegrationengine.tlsmanager.client.misc;

import javax.swing.JLabel;
import java.awt.Component;
import java.awt.Container;

public class SwingMagic {
    public static Component findComponentFollowingLabel(Container container, String labelText) {
        var containerComponents = container.getComponents();

        for (int i = 0; i < containerComponents.length; i++) {
            if (containerComponents[i] instanceof JLabel label) {
                if (labelText.equals(label.getText()) && i + 1 < containerComponents.length) {
                    return containerComponents[i + 1];
                }
            }

            if (containerComponents[i] instanceof Container) {
                var result = findComponentFollowingLabel((Container) containerComponents[i], labelText);
                if (result != null) return result;
            }
        }

        return null;
    }
}
