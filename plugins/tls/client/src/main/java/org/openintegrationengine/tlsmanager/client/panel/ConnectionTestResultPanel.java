/*
 * SPDX-License-Identifier: MPL-2.0
 * Copyright (c) 2023 Phosphor Icons
 * Copyright (c) 2025 NovaMap Health Limited <https://novamap.health>
 *
 * This file uses Phosphor Icons (https://github.com/phosphor-icons)
 * The Phosphor Icons portion is licensed under the MIT License:
 *   https://github.com/phosphor-icons/phosphor-icons/blob/master/LICENSE
 */

package org.openintegrationengine.tlsmanager.client.panel;

import com.mirth.connect.client.ui.MirthDialog;
import net.miginfocom.swing.MigLayout;
import org.openintegrationengine.tlsmanager.shared.models.ConnectionTestResult;

import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.WindowConstants;
import javax.swing.border.TitledBorder;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Window;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

public class ConnectionTestResultPanel extends MirthDialog {

    private JLabel iconLabel;
    private JLabel messageLabel;

    private JScrollPane scrollPane;
    private JTextArea resultArea;
    private JButton okButton;

    private static final Color RED = new Color(179, 0, 0);
    private static final Color GREEN = new Color(76, 174, 79);

    // https://github.com/phosphor-icons/core/blob/main/raw/duotone/seal-check-duotone.svg
    private static final String CHECK_ICON_PATH = "images/tls_plugin_check.png";

    // https://github.com/phosphor-icons/core/blob/main/raw/duotone/seal-warning-duotone.svg
    private static final String ERROR_ICON_PATH = "images/tls_plugin_error.png";

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.RFC_1123_DATE_TIME.withZone(ZoneOffset.systemDefault());

    private final ConnectionTestResult result;

    public ConnectionTestResultPanel(Window owner, ConnectionTestResult result) {
        super(owner, "Connection Test Result", true);

        this.result = result;

        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

        initComponents();
        initLayout();

        if (result.getSuccess()) {
            setPreferredSize(new Dimension(900, 1000));
            resultArea.setText(renderSuccess());
        } else {
            setPreferredSize(new Dimension(600, 400));
            resultArea.setText(renderFailure());
        }

        pack();
        setLocationRelativeTo(getOwner());
        setVisible(true);
    }

    private void initComponents() {
        var iconPath = result.getSuccess() ? CHECK_ICON_PATH : ERROR_ICON_PATH;
        var iconUrl = this.getClass().getClassLoader().getResource(iconPath);

        if (iconUrl == null) {
            System.out.printf("Could not find icon at %s%n", iconPath);
        } else {
            var imageIcon = new ImageIcon(iconUrl);
            iconLabel = new JLabel(imageIcon);
        }

        messageLabel = new JLabel(
            "TLS Connection Test %ssuccessful".formatted(result.getSuccess() ? "" : "un")
        );
        messageLabel.setFont(new Font(Font.DIALOG, Font.BOLD, 18));
        messageLabel.setForeground(result.getSuccess() ? GREEN : RED);

        resultArea = new JTextArea();
        resultArea.setEditable(false);
        resultArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14));
        resultArea.setBackground(Color.WHITE);

        scrollPane = new JScrollPane(resultArea);
        scrollPane.setBorder(new TitledBorder("TLS Connection Results"));

        okButton = new JButton("OK");
        okButton.addActionListener(e -> dispose());
    }

    private void initLayout() {
        setLayout(new MigLayout("insets 8, novisualpadding, hidemode 3, fillx", "[grow, fill]", "[][grow][]"));

        if (iconLabel != null) {
            add(iconLabel, "w 64!, split");
        }
        add(messageLabel);

        add(scrollPane, "newline, grow, push");

        add(okButton, "newline, w 50!, sx, right");
    }

    private String renderHeader() {
        return """
        === TLS Connection Test Results ===
        Host: %s
        Test time: %s
        """.formatted(
            result.getRequestedAddress(),
            DATE_FORMAT.format(result.getTimestamp())
        );
    }

    private String renderSuccess() {
        var stringBuilder = new StringBuilder();

        stringBuilder.append(renderHeader()).append("\n");

        var sessionInfo = """
        === SSL/TLS Session Information ===
        Protocol: %s
        Cipher Suite: %s
        Session ID: %s
        Peer Host: %s
        Peer Port: %s
        Session Valid: %s
        Create Time: %s
        Last Access Time: %s
        """.formatted(
            result.getProtocol(),
            result.getCipherSuite(),
            result.getSessionId(),
            result.getPeerHost(),
            result.getPeerPort(),
            result.getSessionValid(),
            DATE_FORMAT.format(result.getSessionCreationTime()),
            DATE_FORMAT.format(result.getSessionLastAccessedTime())
        );
        stringBuilder.append(sessionInfo).append("\n");

        var protocols = """
        === Supported Protocols ===
        %s

        === Enabled Protocols ===
        %s
        """.formatted(
            String.join("\n", result.getSupportedProtocols()),
            String.join("\n", result.getEnabledProtocols())
        );
        stringBuilder.append(protocols).append("\n");

        var certificates = """
        === Certificate Chain ===
        Number of certificates: %d
        """.formatted(
            result.getCertificates().size()
        );
        stringBuilder.append(certificates).append("\n");

        for (int i = 0; i < result.getCertificates().size(); i++) {
            var certificate = result.getCertificates().get(i);
            var certText = renderCertificate(certificate);
            if (certText != null) {
                stringBuilder.append("--- Certificate %d ---".formatted(i + 1)).append("\n");
                stringBuilder.append(certText).append("\n").append("\n");
            }
        }

        var summary = """
        === Connection Summary ===
        ✓ TLS connection successful
        ✓ Certificate chain retrieved (%d certificate(s))
        ✓ Using %s with %s
        """.formatted(
            result.getCertificates().size(),
            result.getChosenProtocol(),
            result.getChosenCipherSuite()
        );
        stringBuilder.append(summary);

        return stringBuilder.toString();
    }

    private String renderFailure() {
        var sb = new StringBuilder();

        sb.append(renderHeader()).append("\n");

        sb.append("=== Connection Failed ===").append("\n");

        if (result.getExceptionName() != null) {
            sb.append("Error: ").append(result.getExceptionName()).append("\n");
            sb.append("  ").append(result.getExceptionMessage());
            if (result.getCauseName() != null) {
                sb.append("\n");
                sb.append("Cause: ").append(result.getCauseName()).append("\n");
                sb.append("  ").append(result.getCauseMessage()).append("\n");
            }
        } else {
            sb.append("Message: ").append(result.getMessage());
        }

        return sb.toString();
    }

    private String renderCertificate(Certificate certificate) {
        if (certificate instanceof X509Certificate x509) {
            var certBuilder = new StringBuilder();

            certBuilder.append("Subject: ").append(x509.getSubjectX500Principal().toString()).append("\n");
            certBuilder.append("Issuer: ").append(x509.getIssuerX500Principal().toString()).append("\n");
            certBuilder.append("Serial Number: ").append(x509.getSerialNumber().toString(16).toUpperCase()).append("\n");
            certBuilder.append("Version: ").append(x509.getVersion()).append("\n");
            certBuilder.append("Not Before: ").append(DATE_FORMAT.format(x509.getNotBefore().toInstant())).append("\n");
            certBuilder.append("Not After: ").append(DATE_FORMAT.format(x509.getNotAfter().toInstant())).append("\n");
            certBuilder.append("Signature Algorithm: ").append(x509.getSigAlgName()).append("\n");
            certBuilder.append("Public Key Algorithm: ").append(x509.getPublicKey().getAlgorithm()).append("\n");
            certBuilder.append("Key Size: ").append(ConnectionTestResult.getKeySize(x509)).append(" bits\n");

            try {
                x509.checkValidity();
                certBuilder.append("Status: VALID").append("\n");
            } catch (Exception ex) {
                certBuilder.append("Status: INVALID - ").append(ex.getMessage()).append("\n");
            }

            // Subject Alternative Names
            try {
                if (x509.getSubjectAlternativeNames() != null) {
                    certBuilder.append("Subject Alternative Names:\n");
                    x509.getSubjectAlternativeNames().forEach(san -> {
                        certBuilder.append("  ").append(san.get(1)).append("\n");
                    });
                }
            } catch (Exception ex) {
                // SANs might not be available
            }

            certBuilder
                .append("SHA-256 Fingerprint:").append("\n")
                .append("  ")
                .append(ConnectionTestResult.getCertificateFingerprint(x509, "SHA-256"))
                .append("\n");

            certBuilder.append("SHA-1 Fingerprint:").append("\n")
                .append("  ")
                .append(ConnectionTestResult.getCertificateFingerprint(x509, "SHA-1"));

            return certBuilder.toString();
        } else {
            return null;
        }
    }
}
