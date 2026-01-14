/*
 * SPDX-License-Identifier: MPL-2.0
 * Copyright (c) 2025 NovaMap Health Limited <https://novamap.health>
 */

package org.openintegrationengine.tlsmanager.client.panel;

import com.mirth.connect.client.core.ClientException;
import com.mirth.connect.client.ui.AbstractConnectorPropertiesPanel;
import com.mirth.connect.client.ui.ConnectorTypeDecoration;
import com.mirth.connect.client.ui.Frame;
import com.mirth.connect.client.ui.PlatformUI;
import com.mirth.connect.client.ui.UIConstants;
import com.mirth.connect.client.ui.components.MirthComboBox;
import com.mirth.connect.client.ui.components.MirthEditableComboBox;
import com.mirth.connect.client.ui.components.MirthRadioButton;
import com.mirth.connect.client.ui.components.MirthTextField;
import com.mirth.connect.client.ui.panels.connectors.ConnectorSettingsPanel;
import com.mirth.connect.client.ui.panels.connectors.ResponseHandler;
import com.mirth.connect.connectors.http.HttpDispatcherProperties;
import com.mirth.connect.connectors.http.HttpListener;
import com.mirth.connect.connectors.http.HttpSender;
import com.mirth.connect.connectors.tcp.TcpDispatcherProperties;
import com.mirth.connect.connectors.tcp.TcpListener;
import com.mirth.connect.connectors.tcp.TcpSender;
import com.mirth.connect.connectors.ws.DefinitionServiceMap;
import com.mirth.connect.connectors.ws.WebServiceDispatcherProperties;
import com.mirth.connect.connectors.ws.WebServiceListener;
import com.mirth.connect.connectors.ws.WebServiceSender;
import com.mirth.connect.donkey.model.channel.ConnectorPluginProperties;
import com.mirth.connect.donkey.model.channel.ConnectorProperties;
import com.mirth.connect.model.Connector;
import com.mirth.connect.util.MirthSSLUtil;
import lombok.extern.slf4j.Slf4j;
import net.miginfocom.swing.MigLayout;
import org.openintegrationengine.tlsmanager.client.dialog.MultiSelectDialog;
import org.openintegrationengine.tlsmanager.client.dialog.SingleSelectDialog;
import org.openintegrationengine.tlsmanager.client.misc.DisplayTextEnumModeComboBoxRenderer;
import org.openintegrationengine.tlsmanager.client.misc.SwingMagic;
import org.openintegrationengine.tlsmanager.shared.models.ClientAuthMode;
import org.openintegrationengine.tlsmanager.shared.models.ConnectionTestResult;
import org.openintegrationengine.tlsmanager.shared.models.RevocationMode;
import org.openintegrationengine.tlsmanager.shared.models.SubjectDnValidationMode;
import org.openintegrationengine.tlsmanager.shared.properties.TLSConnectorProperties;
import org.openintegrationengine.tlsmanager.shared.servlet.TLSServletInterface;

import javax.swing.ButtonGroup;
import javax.swing.DefaultComboBoxModel;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.SwingWorker;
import java.awt.Color;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Slf4j
public class TLSConnectorPanel extends AbstractConnectorPropertiesPanel {

    /*
    Base UI components
     */
    protected JLabel managerEnabledLabel;
    protected MirthRadioButton managerEnabledRadioYes;
    protected MirthRadioButton managerEnabledRadioNo;

    protected JLabel subjectDnValidationLabel;
    protected MirthComboBox<SubjectDnValidationMode> subjectDnValidationModeComboBox;
    protected MirthTextField subjectDnValidationFilterTextField;

    protected JLabel crlModeLabel;
    protected MirthComboBox<RevocationMode> crlModeComboBox;

    protected JLabel ocspModeLabel;
    protected MirthComboBox<RevocationMode> ocspModeComboBox;

    protected JLabel protocolsLabel;
    protected JButton protocolsButton;
    protected JLabel protocolsText;

    protected JLabel ciphersLabel;
    protected JButton ciphersButton;
    protected JLabel ciphersText;

    /*
    Client mode UI components
     */
    private JLabel trustedServerCertsLabel;
    private JButton trustedServerCertsButton;
    private JLabel trustedServerCertsText;

    private JLabel hostnameValidationLabel;
    private MirthRadioButton hostnameValidationRadioYes;
    private MirthRadioButton hostnameValidationRadioNo;

    private JLabel clientCertLabel;
    private JButton clientCertButton;
    private JLabel clientCertText;

    /*
    Server mode UI components
     */
    private JLabel clientAuthLabel;
    private MirthRadioButton clientAuthRadioNone;
    private MirthRadioButton clientAuthRadioRequested;
    private MirthRadioButton clientAuthRadioRequired;

    private JLabel trustedClientCertsLabel;
    private JButton trustedClientCertsButton;
    private JLabel trustedClientCertsText;

    private JLabel serverCertificateLabel;
    private JButton serverCertificateButton;
    private JLabel serverCertificateText;

    /*
    Other crap
     */
    private TLSConnectorProperties properties;
    private boolean isServerMode;
    private Transport transport;
    private final ResponseHandler responseHandler;

    protected final ImageIcon wrenchIcon;
    protected final Frame parentFrame;

    protected Set<String> supportedProtocols;
    protected Set<String> supportedCiphers;

    protected List<Component> generalLayoutComponents;
    protected List<Component> serverModeLayoutComponents;
    protected List<Component> clientModeLayoutComponents;

    private enum Transport {
        HTTP, TCP, WS
    }

    public TLSConnectorPanel() {
        this.wrenchIcon = new ImageIcon(Frame.class.getResource("images/wrench.png"));
        this.parentFrame = PlatformUI.MIRTH_FRAME;

        this.supportedProtocols = new HashSet<>();
        this.supportedCiphers = new HashSet<>();

        this.generalLayoutComponents = new ArrayList<>();
        this.serverModeLayoutComponents = new ArrayList<>();
        this.clientModeLayoutComponents = new ArrayList<>();

        this.properties = getDefaults();
        this.isServerMode = false;

        this.responseHandler = new ResponseHandler() {
            @Override
            public void handle(Object response) {
                var result = (ConnectionTestResult) response;

                if (result == null) {
                    parentFrame.alertError(parentFrame, "Failed to invoke service.");
                } else {
                    new ConnectionTestResultPanel(PlatformUI.MIRTH_FRAME, result);
                }
            }
        };

        initComponents();
        initTooltips();
        initLayout();
        fetchData();
    }

    private void attemptDetermineTransportAndDirectionality() {
        var settingsPanel = connectorPanel.getConnectorSettingsPanel();

        if (settingsPanel instanceof TcpSender tcpSender) {
            transport = Transport.TCP;
            isServerMode = tcpSender.modeServerRadio.isSelected();
        } else if (settingsPanel instanceof TcpListener tcpListener) {
            transport = Transport.TCP;
            isServerMode = tcpListener.modeServerRadio.isSelected();
        } else if (settingsPanel instanceof HttpSender) {
            transport = Transport.HTTP;
            isServerMode = false;
        } else if (settingsPanel instanceof HttpListener) {
            transport = Transport.HTTP;
            isServerMode = true;
        } else if (settingsPanel instanceof WebServiceSender) {
            transport = Transport.WS;
            isServerMode = false;
        } else if (settingsPanel instanceof WebServiceListener) {
            transport = Transport.WS;
            isServerMode = true;
        } else {
            throw new IllegalArgumentException("Unsupported settings panel type: " + settingsPanel.getClass().getName());
        }

        log.info("Determined transport: {}; isServerMode {}", transport, isServerMode);
    }

    protected void handleManagerEnabledButton(boolean managerEnabled) {
        properties.setTlsManagerEnabled(managerEnabled);

        generalLayoutComponents.forEach(component -> component.setEnabled(managerEnabled));

        subjectDnValidationFilterTextField.setEnabled(
            managerEnabled && properties.getSubjectDnValidationMode() != SubjectDnValidationMode.NONE
        );

        if (isServerMode) {
            handleManagerEnabledButtonClientMode(false);
            handleManagerEnabledButtonServerMode(managerEnabled);
        } else {
            handleManagerEnabledButtonClientMode(managerEnabled);
            handleManagerEnabledButtonServerMode(false);
        }
    }

    private void handleManagerEnabledButtonClientMode(boolean managerEnabled) {
        clientModeLayoutComponents.forEach(component -> component.setEnabled(managerEnabled));
    }

    private void handleManagerEnabledButtonServerMode(boolean managerEnabled) {
        serverModeLayoutComponents.forEach(component -> component.setEnabled(managerEnabled));

        if (managerEnabled) {
            handleClientAuthModeChange(properties.getClientAuthMode(), false);
        } else {
            trustedClientCertsLabel.setEnabled(false);
            trustedClientCertsButton.setEnabled(false);
            trustedClientCertsText.setEnabled(false);
        }
    }

    protected void handleCrlModeChange() {
        if (crlModeComboBox.getSelectedItem() instanceof RevocationMode revocationMode) {
            properties.setCrlMode(revocationMode);
        }
    }

    protected void handleOcspModeChange() {
        if (ocspModeComboBox.getSelectedItem() instanceof RevocationMode revocationMode) {
            properties.setOcspMode(revocationMode);
        }
    }

    protected void handleSubjectDnValidationModeChange() {
        if (subjectDnValidationModeComboBox.getSelectedItem() instanceof SubjectDnValidationMode validationMode) {
            properties.setSubjectDnValidationMode(validationMode);
            redrawState();
        }
    }

    private void handleClientAuthModeChange(ClientAuthMode authMode, boolean persistChanges) {
        if (persistChanges) {
            properties.setClientAuthMode(authMode);
        }

        var issuerSelectorEnabled = authMode != ClientAuthMode.NONE;
        trustedClientCertsLabel.setEnabled(issuerSelectorEnabled);
        trustedClientCertsButton.setEnabled(issuerSelectorEnabled);
        trustedClientCertsText.setEnabled(issuerSelectorEnabled);
    }

    protected void redrawState() {
        if (properties.isTlsManagerEnabled()) {
            managerEnabledRadioYes.setSelected(true);
        } else {
            managerEnabledRadioNo.setSelected(true);
        }

        subjectDnValidationModeComboBox.setSelectedItem(properties.getSubjectDnValidationMode());
        subjectDnValidationFilterTextField.setEnabled(properties.getSubjectDnValidationMode() != SubjectDnValidationMode.NONE);
        subjectDnValidationFilterTextField.setText(properties.getSubjectDnValidationFilter());

        crlModeComboBox.setSelectedItem(properties.getCrlMode());
        ocspModeComboBox.setSelectedItem(properties.getOcspMode());

        final var protocolsString = properties.isUseServerDefaultProtocols()
            ? "Server default: %s".formatted(supportedProtocols)
            : "%d selected".formatted(properties.getUsedProtocols().size());

        protocolsText.setText(protocolsString);

        final var ciphersString = properties.isUseServerDefaultCiphers()
            ? "Server default: %d selected".formatted(supportedCiphers.size())
            : "%d selected".formatted(properties.getUsedCiphers().size());

        ciphersText.setText(ciphersString);

        redrawClientModeState();
        redrawServerModeState();
    }

    private void redrawClientModeState() {
        trustedServerCertsText.setText(
            !isServerMode ? renderTrustTginfWhatevs() : ""
        );

        if (properties.isHostnameVerificationEnabled()) {
            hostnameValidationRadioYes.setSelected(true);
        } else {
            hostnameValidationRadioNo.setSelected(true);
        }

        clientCertText.setText(properties.getClientCertificateAlias());
    }

    private void redrawServerModeState() {
        serverCertificateText.setText(properties.getServerCertificateAlias());

        if (properties.getClientAuthMode() == ClientAuthMode.NONE) {
            clientAuthRadioNone.setSelected(true);
        } else if (properties.getClientAuthMode() == ClientAuthMode.REQUESTED) {
            clientAuthRadioRequested.setSelected(true);
        } else if (properties.getClientAuthMode() == ClientAuthMode.REQUIRED) {
            clientAuthRadioRequired.setSelected(true);
        } else {
            clientAuthRadioNone.setSelected(true);
            log.warn("Unable to determine client auth mode: {}. Using NONE", properties.getClientAuthMode());
        }

        handleClientAuthModeChange(properties.getClientAuthMode(), false);

        trustedClientCertsText.setText(
            isServerMode ? renderTrustTginfWhatevs() : ""
        );
    }

    private String renderTrustTginfWhatevs() {
        var thingsToTrust = new ArrayList<String>();
        if (properties.isTrustSystemTruststore()) {
            thingsToTrust.add("System Truststore");
        }

        if (properties.getTrustedServerCertificates() != null && !properties.getTrustedServerCertificates().isEmpty()) {
            var count = properties.getTrustedServerCertificates().size();
            var plural = (count == 1) ? "" : "s";
            thingsToTrust.add("%d certificate%s".formatted(count, plural));
        }

        return thingsToTrust.isEmpty()
            ? "None selected"
            : "Trusting %s".formatted(String.join(" and ", thingsToTrust)
        );
    }

    @Override
    public TLSConnectorProperties getProperties() {
        return properties.clone();
    }

    @Override
    public void setProperties(ConnectorProperties connectorProperties, ConnectorPluginProperties connectorPluginProperties, Connector.Mode mode, String s) {
        if (connectorPluginProperties instanceof TLSConnectorProperties tlsConnectorProperties) {
            this.properties = tlsConnectorProperties;

            fetchData();
            redrawState();
            handleManagerEnabledButton(tlsConnectorProperties.isTlsManagerEnabled());

            attemptDetermineTransportAndDirectionality();
            generalLayoutComponents.forEach(component -> component.setVisible(true));

            if (transport == Transport.TCP) {
                serverModeLayoutComponents.forEach(component -> component.setVisible(true));
                clientModeLayoutComponents.forEach(component -> component.setVisible(true));
            } else {
                serverModeLayoutComponents.forEach(component -> component.setVisible(isServerMode));
                clientModeLayoutComponents.forEach(component -> component.setVisible(!isServerMode));
            }
        }
    }

    @Override
    public TLSConnectorProperties getDefaults() {
        return new TLSConnectorProperties();
    }

    @Override
    public boolean checkProperties(ConnectorProperties connectorProperties, ConnectorPluginProperties connectorPluginProperties, Connector.Mode mode, String s, boolean shouldHighlight) {
        boolean isValid = true;

        if (!properties.isTlsManagerEnabled()) {
            return true;
        }

        if (properties.getSubjectDnValidationMode() != SubjectDnValidationMode.NONE) {
            final var filter = properties.getSubjectDnValidationFilter();
            if (filter == null || filter.isBlank()) {
                isValid = false;

                if (shouldHighlight) {
                    subjectDnValidationFilterTextField.setBackground(UIConstants.INVALID_COLOR);
                }
            }
        }

        if (!properties.isUseServerDefaultProtocols() && properties.getUsedProtocols().isEmpty()) {
            isValid = false;

            if (shouldHighlight) {
                protocolsButton.setBackground(UIConstants.INVALID_COLOR);
            }
        }

        if (!properties.isUseServerDefaultCiphers() && properties.getUsedCiphers().isEmpty()) {
            isValid = false;

            if (shouldHighlight) {
                ciphersButton.setBackground(UIConstants.INVALID_COLOR);
            }
        }

        if (isServerMode) {

            final var serverCertAlias = properties.getServerCertificateAlias();
            if (serverCertAlias == null || serverCertAlias.isBlank()) {
                isValid = false;

                if (shouldHighlight) {
                    serverCertificateButton.setBackground(UIConstants.INVALID_COLOR);
                }
            }

            if (
                properties.getClientAuthMode() != ClientAuthMode.NONE
                && !properties.isTrustSystemTruststore()
                && properties.getTrustedServerCertificates().isEmpty()
            ) {
                isValid = false;

                if (shouldHighlight) {
                    trustedClientCertsButton.setBackground(UIConstants.INVALID_COLOR);
                }
            }
        } else {
            if (!properties.isTrustSystemTruststore() && properties.getTrustedServerCertificates().isEmpty()) {
                isValid = false;

                if (shouldHighlight) {
                    trustedServerCertsButton.setBackground(UIConstants.INVALID_COLOR);
                }
            }
        }

        return isValid;
    }

    @Override
    public void resetInvalidProperties() {
        // This method seems to be called after other panels have been initialized.
        // We need other panels to be initialized 'cause we'll be fiddling with one.
        registerActionListeners();

        // Reset validation error backgrounds for general components
        subjectDnValidationFilterTextField.setBackground(null);
        protocolsButton.setBackground(null);
        ciphersButton.setBackground(null);
        ciphersText.setBackground(null);

        // Reset validation error backgrounds for server mode components
        serverCertificateButton.setBackground(null);
        trustedClientCertsButton.setBackground(null);

        // Reset validation error backgrounds for client mode components
        trustedServerCertsButton.setBackground(null);
        clientCertButton.setBackground(null);
    }

    @Override
    public Component[][] getLayoutComponents() {
        return new Component[0][];
    }

    @Override
    public void setLayoutComponentsEnabled(boolean b) {}

    @Override
    public ConnectorTypeDecoration getConnectorTypeDecoration() {
        return new ConnectorTypeDecoration(Connector.Mode.DESTINATION);
    }

    protected void initComponents() {
        setBackground(UIConstants.BACKGROUND_COLOR);

        managerEnabledLabel = new JLabel("Use TLS Manager:");

        var managerEnabledButtonGroup = new ButtonGroup();
        managerEnabledRadioYes = new MirthRadioButton();
        managerEnabledRadioYes.setText("Yes");
        managerEnabledRadioYes.setBackground(Color.white);
        managerEnabledRadioYes.addActionListener(e -> handleManagerEnabledButton(true));
        managerEnabledButtonGroup.add(managerEnabledRadioYes);

        managerEnabledRadioNo = new MirthRadioButton();
        managerEnabledRadioNo.setText("No");
        managerEnabledRadioNo.setBackground(Color.white);
        managerEnabledRadioNo.addActionListener(e -> handleManagerEnabledButton(false));
        managerEnabledButtonGroup.add(managerEnabledRadioNo);

        var comboBoxRenderer = new DisplayTextEnumModeComboBoxRenderer();

        final var subjectDnValidationModeModel = new SubjectDnValidationMode[]{
            SubjectDnValidationMode.NONE,
            SubjectDnValidationMode.PARTIAL,
            SubjectDnValidationMode.EXACT,
        };

        subjectDnValidationLabel = new JLabel("Subject DN Validation Mode:");
        generalLayoutComponents.add(subjectDnValidationLabel);

        subjectDnValidationModeComboBox = new MirthComboBox<>();
        subjectDnValidationModeComboBox.setRenderer(comboBoxRenderer);
        subjectDnValidationModeComboBox.setModel(new DefaultComboBoxModel<>(subjectDnValidationModeModel));
        subjectDnValidationModeComboBox.addActionListener(evt -> handleSubjectDnValidationModeChange());
        generalLayoutComponents.add(subjectDnValidationModeComboBox);

        subjectDnValidationFilterTextField = new MirthTextField();
        subjectDnValidationFilterTextField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent e) {
                properties.setSubjectDnValidationFilter(subjectDnValidationFilterTextField.getText());
            }
        });
        generalLayoutComponents.add(subjectDnValidationFilterTextField);

        final var revocationModeModel = new RevocationMode[]{
            RevocationMode.DISABLED,
            RevocationMode.SOFT_FAIL,
            RevocationMode.HARD_FAIL
        };

        crlModeLabel = new JLabel("CRL Mode:");
        generalLayoutComponents.add(crlModeLabel);

        crlModeComboBox = new MirthComboBox<>();
        crlModeComboBox.setRenderer(comboBoxRenderer);
        crlModeComboBox.setModel(new DefaultComboBoxModel<>(revocationModeModel));
        crlModeComboBox.addActionListener(evt -> handleCrlModeChange());
        generalLayoutComponents.add(crlModeComboBox);

        ocspModeLabel = new JLabel("OCSP Mode:");
        generalLayoutComponents.add(ocspModeLabel);

        ocspModeComboBox = new MirthComboBox<>();
        ocspModeComboBox.setRenderer(comboBoxRenderer);
        ocspModeComboBox.setModel(new DefaultComboBoxModel<>(revocationModeModel));
        ocspModeComboBox.addActionListener(evt -> handleOcspModeChange());
        generalLayoutComponents.add(ocspModeComboBox);

        protocolsLabel = new JLabel("Enabled Protocols:");
        generalLayoutComponents.add(protocolsLabel);

        protocolsButton = new JButton(wrenchIcon);
        protocolsButton.addActionListener(e -> {
            BiConsumer<Boolean, Set<String>> completionConsumer = (trustDefaultProtocols, selectedProtocols) -> {
                properties.setUseServerDefaultProtocols(trustDefaultProtocols);
                if (trustDefaultProtocols) {
                    properties.setUsedProtocols(Collections.emptySet());
                } else {
                    properties.setUsedProtocols(selectedProtocols);
                }

                redrawState();
                PlatformUI.MIRTH_FRAME.setSaveEnabled(true);
            };

            Supplier<Set<String>> dataSupplier = () -> {
                try {
                    var cryptoMap = PlatformUI.MIRTH_FRAME.mirthClient.getProtocolsAndCipherSuites();
                    var protocolArray = cryptoMap.get(MirthSSLUtil.KEY_ENABLED_SERVER_PROTOCOLS);
                    var protocolSet = Set.of(protocolArray)
                        .stream()
                        .sorted()
                        .collect(Collectors.toCollection(LinkedHashSet::new));

                    return protocolSet;
                } catch (ClientException ex) {
                    throw new RuntimeException(ex);
                }
            };

            new MultiSelectDialog(
                "Protocols Picker",
                properties.getUsedProtocols(),
                properties.isUseServerDefaultProtocols(),
                "[Server default]",
                completionConsumer,
                dataSupplier
            );
        });
        generalLayoutComponents.add(protocolsButton);

        protocolsText = new JLabel();
        generalLayoutComponents.add(protocolsText);

        ciphersLabel = new JLabel("Enabled Ciphers:");
        generalLayoutComponents.add(ciphersLabel);

        ciphersButton = new JButton(wrenchIcon);
        ciphersButton.addActionListener(e -> {
            BiConsumer<Boolean, Set<String>> completionConsumer = (trustDefaultCiphers, selectedCiphers) -> {
                properties.setUseServerDefaultCiphers(trustDefaultCiphers);
                if (trustDefaultCiphers) {
                    properties.setUsedCiphers(Collections.emptySet());
                } else {
                    properties.setUsedCiphers(selectedCiphers);
                }

                redrawState();
                PlatformUI.MIRTH_FRAME.setSaveEnabled(true);
            };

            Supplier<Set<String>> dataSupplier = () -> {
                try {
                    var cryptoMap = PlatformUI.MIRTH_FRAME.mirthClient.getProtocolsAndCipherSuites();
                    var ciphersArray = cryptoMap.get(MirthSSLUtil.KEY_ENABLED_CIPHER_SUITES);

                    var ciphersSet = Set.of(ciphersArray)
                        .stream()
                        .sorted()
                        .collect(Collectors.toCollection(LinkedHashSet::new));

                    return ciphersSet;
                } catch (ClientException ex) {
                    throw new RuntimeException(ex);
                }
            };

            new MultiSelectDialog(
                "Ciphers Picker",
                properties.getUsedCiphers(),
                properties.isUseServerDefaultCiphers(),
                "[Server default]",
                completionConsumer,
                dataSupplier
            );
        });
        generalLayoutComponents.add(ciphersButton);

        ciphersText = new JLabel();
        generalLayoutComponents.add(ciphersText);

        initClientModeComponents();
        initServerModeComponents();

        clientModeLayoutComponents.forEach(component -> component.setVisible(false));
        serverModeLayoutComponents.forEach(component -> component.setVisible(false));
    }

    private void initClientModeComponents() {
        trustedServerCertsLabel = new JLabel("Trusted Server Certificates:");
        clientModeLayoutComponents.add(trustedServerCertsLabel);

        trustedServerCertsButton = new JButton(wrenchIcon);
        trustedServerCertsButton.addActionListener(e -> {
            BiConsumer<Boolean, Set<String>> completionConsumer = (isTrustSystemTrustStoreEnabled, selectedCerts) -> {
                properties.setTrustSystemTruststore(isTrustSystemTrustStoreEnabled);
                properties.setTrustedServerCertificates(selectedCerts);
                redrawState();
                PlatformUI.MIRTH_FRAME.setSaveEnabled(true);
            };

            Supplier<Set<String>> dataSupplier = () -> {
                var certs = PlatformUI.MIRTH_FRAME.mirthClient.getServlet(TLSServletInterface.class)
                    .getPublicCertificates()
                    .stream()
                    .sorted()
                    .collect(Collectors.toCollection(LinkedHashSet::new));
                return certs;
            };

            new MultiSelectDialog(
                "Certificate Picker",
                properties.getTrustedServerCertificates(),
                properties.isTrustSystemTruststore(),
                "[JVM Truststore]",
                completionConsumer,
                dataSupplier
            );
        });
        clientModeLayoutComponents.add(trustedServerCertsButton);

        trustedServerCertsText = new JLabel("Trusting some certs as a placeholder");
        clientModeLayoutComponents.add(trustedServerCertsText);

        hostnameValidationLabel = new JLabel("Hostname verification:");
        clientModeLayoutComponents.add(hostnameValidationLabel);

        final var hostnameValidationButtonGroup = new ButtonGroup();

        hostnameValidationRadioYes = new MirthRadioButton();
        hostnameValidationRadioYes.setBackground(Color.white);
        hostnameValidationRadioYes.setText("Enabled");
        hostnameValidationRadioYes.addActionListener(e -> properties.setHostnameVerificationEnabled(true));
        hostnameValidationButtonGroup.add(hostnameValidationRadioYes);
        clientModeLayoutComponents.add(hostnameValidationRadioYes);

        hostnameValidationRadioNo = new MirthRadioButton();
        hostnameValidationRadioNo.setBackground(Color.white);
        hostnameValidationRadioNo.setText("Disabled");
        hostnameValidationRadioNo.addActionListener(e -> properties.setHostnameVerificationEnabled(false));
        hostnameValidationButtonGroup.add(hostnameValidationRadioNo);
        clientModeLayoutComponents.add(hostnameValidationRadioNo);

        clientCertLabel = new JLabel("Client Certificate:");
        clientModeLayoutComponents.add(clientCertLabel);

        clientCertButton = new JButton(wrenchIcon);
        clientCertButton.addActionListener(e -> {
            Consumer<String> completionConsumer = (selectedAlias) -> {
                properties.setClientCertificateAlias(selectedAlias);
                redrawState();
                PlatformUI.MIRTH_FRAME.setSaveEnabled(true);
            };

            var currentCertificateAlias = properties.getClientCertificateAlias();

            Supplier<Set<String>> dataSupplier = () -> {
                var certs = PlatformUI.MIRTH_FRAME.mirthClient.getServlet(TLSServletInterface.class)
                    .getClientCertificates()
                    .stream()
                    .sorted()
                    .collect(Collectors.toCollection(LinkedHashSet::new));
                return certs;
            };

            new SingleSelectDialog(
                "Client Certificate Picker",
                currentCertificateAlias,
                dataSupplier,
                completionConsumer
            );
        });
        clientModeLayoutComponents.add(clientCertButton);

        clientCertText = new JLabel();
        clientModeLayoutComponents.add(clientCertText);
    }

    protected void initServerModeComponents() {
        serverCertificateLabel = new JLabel("Server Certificate:");
        serverModeLayoutComponents.add(serverCertificateLabel);

        serverCertificateButton = new JButton(wrenchIcon);
        serverModeLayoutComponents.add(serverCertificateButton);

        serverCertificateButton.addActionListener(e -> {
            Consumer<String> completionConsumer = (selectedAlias) -> {
                properties.setServerCertificateAlias(selectedAlias);
                redrawState();
                PlatformUI.MIRTH_FRAME.setSaveEnabled(true);
            };

            var currentCertificateAlias = properties.getServerCertificateAlias();
            Supplier<Set<String>> dataSupplier = () -> {
                var certs = PlatformUI.MIRTH_FRAME.mirthClient.getServlet(TLSServletInterface.class)
                    .getClientCertificates()
                    .stream()
                    .sorted()
                    .collect(Collectors.toCollection(LinkedHashSet::new));
                return certs;
            };

            new SingleSelectDialog(
                "Server Certificate Picker",
                currentCertificateAlias,
                dataSupplier,
                completionConsumer
            );
        });
        serverModeLayoutComponents.add(serverCertificateButton);

        serverCertificateText = new JLabel();
        serverModeLayoutComponents.add(serverCertificateText);

        clientAuthLabel = new JLabel("Client Authentication Mode");
        serverModeLayoutComponents.add(clientAuthLabel);

        final var clientAuthModeButtonGroup = new ButtonGroup();
        clientAuthRadioNone = new MirthRadioButton();
        clientAuthRadioNone.setText("None");
        clientAuthRadioNone.setBackground(Color.white);
        clientAuthRadioNone.addActionListener(e -> handleClientAuthModeChange(ClientAuthMode.NONE, true));
        clientAuthModeButtonGroup.add(clientAuthRadioNone);
        serverModeLayoutComponents.add(clientAuthRadioNone);

        clientAuthRadioRequested = new MirthRadioButton();
        clientAuthRadioRequested.setText("Requested");
        clientAuthRadioRequested.setBackground(Color.white);
        clientAuthRadioRequested.addActionListener(e -> handleClientAuthModeChange(ClientAuthMode.REQUESTED, true));
        clientAuthModeButtonGroup.add(clientAuthRadioRequested);
        serverModeLayoutComponents.add(clientAuthRadioRequested);

        clientAuthRadioRequired = new MirthRadioButton();
        clientAuthRadioRequired.setText("Required");
        clientAuthRadioRequired.setBackground(Color.white);
        clientAuthRadioRequired.addActionListener(e -> handleClientAuthModeChange(ClientAuthMode.REQUIRED, true));
        clientAuthModeButtonGroup.add(clientAuthRadioRequired);
        serverModeLayoutComponents.add(clientAuthRadioRequired);

        trustedClientCertsLabel = new JLabel("Trusted Client Certificates:");
        serverModeLayoutComponents.add(trustedClientCertsLabel);

        trustedClientCertsButton = new JButton(wrenchIcon);
        trustedClientCertsButton.addActionListener(e -> {
            BiConsumer<Boolean, Set<String>> completionConsumer = (isTrustSystemTrustStoreEnabled, selectedCertificates) -> {
                properties.setTrustSystemTruststore(isTrustSystemTrustStoreEnabled);
                if (isTrustSystemTrustStoreEnabled) {
                    properties.setTrustedServerCertificates(Collections.emptySet());
                } else {
                    properties.setTrustedServerCertificates(selectedCertificates);
                }

                redrawState();
                PlatformUI.MIRTH_FRAME.setSaveEnabled(true);
            };

            Supplier<Set<String>> dataSupplier = () -> {
                var certs = PlatformUI.MIRTH_FRAME.mirthClient.getServlet(TLSServletInterface.class)
                    .getPublicCertificates()
                    .stream()
                    .sorted()
                    .collect(Collectors.toCollection(LinkedHashSet::new));
                return certs;
            };

            new MultiSelectDialog(
                "Trusted Client Certificates Picker",
                properties.getTrustedServerCertificates(),
                properties.isTrustSystemTruststore(),
                "[Server default]",
                completionConsumer,
                dataSupplier
            );
        });
        serverModeLayoutComponents.add(trustedClientCertsButton);

        trustedClientCertsText = new JLabel();
        serverModeLayoutComponents.add(trustedClientCertsText);
    }

    private void initTooltips() {
        final var tlsRadioToolTip = """
            <html>
            Enabling the TLS Manager permits a greater degree of control over the TLS connections.<br/>
            When enabled, the connector will use the settings below for TLS connections, else the JVM settings will be used.
            </html>""";
        managerEnabledLabel.setToolTipText(tlsRadioToolTip);
        managerEnabledRadioYes.setToolTipText(tlsRadioToolTip);
        managerEnabledRadioNo.setToolTipText(tlsRadioToolTip);

        final var subjectDNToolTip = """
            <html>
            Certificate's Subject DN Validation Mode determines how the connector validates the Subject DN of incoming TLS connections.<br/>
            <b>NONE:</b> No validation is performed.<br/>
            <b>PARTIAL:</b> The certificate's Subject DN must contain all of the specified RDNs.<br/>
            <b>EXACT:</b> The certificate's Subject DN must match the specified Subject DN exactly.
            </html>""";
        subjectDnValidationLabel.setToolTipText(subjectDNToolTip);
        subjectDnValidationModeComboBox.setToolTipText(subjectDNToolTip);
        subjectDnValidationFilterTextField.setToolTipText(subjectDNToolTip);

        final var crlModeToolTip = """
            <html>
            CRL Mode determines how the connector handles Certificate Revocation Lists (CRLs) for incoming TLS connections.<br/>
            <b>NONE:</b> CRL checking is disabled.<br/>
            <b>SOFT FAIL:</b> CRL checking is enabled but if the CRL cannot be retrieved, the connection will still be established.<br/>
            <b>HARD FAIL:</b> CRL checking is enabled and must be successful for the connection to be established.
            </html>""";
        crlModeLabel.setToolTipText(crlModeToolTip);
        crlModeComboBox.setToolTipText(crlModeToolTip);

        final var ocspModeToolTip = """
            <html>
            OCSP Mode determines how the connector handles Online Certificate Status Protocol (OCSP) responses for incoming TLS connections.<br/>
            <b>NONE:</b> OCSP checking is disabled.<br/>
            <b>SOFT FAIL:</b> OCSP checking is enabled but if the response cannot be retrieved, the connection will still be established.<br/>
            <b>HARD FAIL:</b> OCSP checking is enabled and must be successful for the connection to be established.
            </html>""";
        ocspModeLabel.setToolTipText(ocspModeToolTip);
        ocspModeComboBox.setToolTipText(ocspModeToolTip);

        final var protocolsToolTip = """
            <html>
            Determines which TLS protocols the connector supports.
            </html>""";
        protocolsLabel.setToolTipText(protocolsToolTip);
        protocolsButton.setToolTipText(protocolsToolTip);
        protocolsText.setToolTipText(protocolsToolTip);

        final var cipherSuitesToolTip = """
            <html>
            Determines which cipher suites the connector supports.
            </html>""";
        ciphersLabel.setToolTipText(cipherSuitesToolTip);
        ciphersButton.setToolTipText(cipherSuitesToolTip);
        ciphersText.setToolTipText(cipherSuitesToolTip);

        final var trustedServerCertsToolTip = """
            <html>
            Determines which remote certificates to trust.
            </html>""";
        trustedServerCertsLabel.setToolTipText(trustedServerCertsToolTip);
        trustedServerCertsButton.setToolTipText(trustedServerCertsToolTip);
        trustedServerCertsText.setToolTipText(trustedServerCertsToolTip);

        final var hostnameVerificationToolTip = """
            <html>
            Determines whether the remote server's hostname is validated.
            </html>""";
        hostnameValidationLabel.setToolTipText(hostnameVerificationToolTip);
        hostnameValidationRadioYes.setToolTipText(hostnameVerificationToolTip);
        hostnameValidationRadioNo.setToolTipText(hostnameVerificationToolTip);

        final var clientCertificateToolTip = """
            <html>
            Permits the selection of the certificate to be sent when using mTLS authentication.
            </html>""";
        clientCertLabel.setToolTipText(clientCertificateToolTip);
        clientCertButton.setToolTipText(clientCertificateToolTip);
        clientCertText.setToolTipText(cipherSuitesToolTip);

        final var serverCertificateToolTip = """
            <html>
            Determines which certificate is used to serve the endpoint.
            </html>""";
        serverCertificateLabel.setToolTipText(serverCertificateToolTip);
        serverCertificateButton.setToolTipText(serverCertificateToolTip);
        serverCertificateText.setToolTipText(cipherSuitesToolTip);

        final var clientAuthModeToolTip = """
            <html>
            Determines whether client authentication is required for the endpoint.<br/>
            <b>NONE:</b> No client authentication is required.<br/>
            <b>REQUESTED:</b> Client authentication is requested but not mandatory.<br/>
            <b>REQUIRED:</b> Client authentication is required for the connection to be established.
            </html>""";
        clientAuthLabel.setToolTipText(clientAuthModeToolTip);
        clientAuthRadioNone.setToolTipText(clientAuthModeToolTip);
        clientAuthRadioRequested.setToolTipText(clientAuthModeToolTip);
        clientAuthRadioRequired.setToolTipText(clientAuthModeToolTip);

        final var trustedClientCertsToolTip = """
            <html>
            Determines which client certificates to trust.
            Determines which client certificates to trust.
            </html>""";
        trustedClientCertsLabel.setToolTipText(trustedClientCertsToolTip);
        trustedClientCertsButton.setToolTipText(trustedClientCertsToolTip);
        trustedClientCertsText.setToolTipText(trustedClientCertsToolTip);
    }

    protected void initLayout() {
        setLayout(new MigLayout("insets 0, novisualpadding, hidemode 3", "[]12[]", ""));

        add(managerEnabledLabel, "newline, right");
        add(managerEnabledRadioYes, "split");
        add(managerEnabledRadioNo);

        add(subjectDnValidationLabel, "newline, right");
        add(subjectDnValidationModeComboBox, "split");
        add(subjectDnValidationFilterTextField, "w 168!");

        add(crlModeLabel, "newline, right");
        add(crlModeComboBox);

        add(ocspModeLabel, "newline, right");
        add(ocspModeComboBox);

        add(protocolsLabel, "newline, right");
        add(protocolsButton, "h 22!, w 22!, split");
        add(protocolsText);

        add(ciphersLabel, "newline, right");
        add(ciphersButton, "h 22!, w 22!, split");
        add(ciphersText);

        initClientModeLayout();
        initServerModeLayout();
    }

    private void initClientModeLayout() {
        add(trustedServerCertsLabel, "newline, right");
        add(trustedServerCertsButton, "h 22!, w 22!, split");
        add(trustedServerCertsText);

        add(hostnameValidationLabel, "newline, right");
        add(hostnameValidationRadioYes, "split");
        add(hostnameValidationRadioNo);

        add(clientCertLabel, "newline, right");
        add(clientCertButton, "h 22!, w 22!, split");
        add(clientCertText);
    }

    private void initServerModeLayout() {
        add(serverCertificateLabel, "newline, right");
        add(serverCertificateButton, "h 22!, w 22!, split");
        add(serverCertificateText);

        add(clientAuthLabel, "newline, right");
        add(clientAuthRadioNone, "split");
        add(clientAuthRadioRequested, "split");
        add(clientAuthRadioRequired);

        add(trustedClientCertsLabel, "newline, right");
        add(trustedClientCertsButton, "h 22!, w 22!, split");
        add(trustedClientCertsText);
    }

    private void registerActionListeners() {
        var settingsPanel = connectorPanel.getConnectorSettingsPanel();

        // Register client/server mode listener
        if (settingsPanel instanceof TcpListener tcpListener) {
            tcpListener.modeServerRadio.addActionListener((event) -> handleTcpModeChange(true));
            tcpListener.modeClientRadio.addActionListener((event) -> handleTcpModeChange(false));

            this.isServerMode = tcpListener.modeServerRadio.isSelected();
            handleTcpModeChange(this.isServerMode);
        } else if (settingsPanel instanceof TcpSender tcpSender) {
            tcpSender.modeServerRadio.addActionListener((event) -> handleTcpModeChange(true));
            tcpSender.modeClientRadio.addActionListener((event) -> handleTcpModeChange(false));

            this.isServerMode = tcpSender.modeServerRadio.isSelected();
            handleTcpModeChange(this.isServerMode);
        }

        // Register Connection testing overrides
        if (settingsPanel instanceof HttpSender) {
            registerTestConnectionActionHandlers(settingsPanel, Transport.HTTP);
        } else if (settingsPanel instanceof TcpSender) {
            registerTestConnectionActionHandlers(settingsPanel, Transport.TCP);
        } else if (settingsPanel instanceof WebServiceSender) {
            registerWsTestConnectionActionHandlers(settingsPanel);
        }
    }

    private void handleTcpModeChange(boolean isServerMode) {
        this.isServerMode = isServerMode;
        var isTlsEnabled = properties.isTlsManagerEnabled();
        if (isServerMode) {
            handleManagerEnabledButtonClientMode(false);
            handleManagerEnabledButtonServerMode(isTlsEnabled);
        } else {
            handleManagerEnabledButtonClientMode(isTlsEnabled);
            handleManagerEnabledButtonServerMode(false);
        }
    }

    private void registerTestConnectionActionHandlers(ConnectorSettingsPanel settingsPanel, Transport transport) {
        var testConnectionButtons = getButtonsByText("Test Connection");
        if (!testConnectionButtons.isEmpty()) {
            var button = testConnectionButtons.get(0);

            var actionListeners = button.getActionListeners().clone();

            var previousActionListener = actionListeners[0]; // Hope it only has a single listener

            // Replace the ActionListener
            button.removeActionListener(previousActionListener);
            button.addActionListener(e -> testTlsConnection(previousActionListener, e, transport));
        } else {
            log.warn("No test connection button found in settings panel {}", settingsPanel);
        }
    }

    private void registerWsTestConnectionActionHandlers(ConnectorSettingsPanel settingsPanel) {
        var testConnectionButtons = getButtonsByText("Test Connection");
        if (!testConnectionButtons.isEmpty()) {
            // This works on the faint hope the buttons are ordered, and the order of said buttons is not messed with during processing...
            var testWsdlConnectionButton = testConnectionButtons.get(0);
            var testLocationConnectionButton = testConnectionButtons.get(1);

            var wsdlActionListeners = testWsdlConnectionButton.getActionListeners().clone();
            var locationActionListeners = testLocationConnectionButton.getActionListeners().clone();

            var previousWsdlActionListener = wsdlActionListeners[0];
            var previousLocationActionListener = locationActionListeners[0];

            testWsdlConnectionButton.removeActionListener(previousWsdlActionListener);
            testWsdlConnectionButton.addActionListener(e -> testWsTlsConnection(previousWsdlActionListener, e, true));

            testLocationConnectionButton.removeActionListener(previousLocationActionListener);
            testLocationConnectionButton.addActionListener(e -> testWsTlsConnection(previousLocationActionListener, e, false));
        } else {
            log.warn("No Test Connection button found in settings panel {}", settingsPanel);
        }

        var getOperationsButtons = getButtonsByText("Get Operations");
        if (!getOperationsButtons.isEmpty()) {
            var button = getOperationsButtons.get(0);

            var actionListeners = button.getActionListeners().clone();

            var previousActionListener = actionListeners[0]; // Hope it only has a single listener

            // Replace the ActionListener
            button.removeActionListener(previousActionListener);
            button.addActionListener(e -> getOperations(previousActionListener, e));
        } else {
            log.warn("No Get Operations button found in settings panel {}", settingsPanel);
        }
    }

    private void testWsTlsConnection(ActionListener nonTlsActionListener, ActionEvent event, boolean isWsdlUrlBeingTested) {
        if (!properties.isTlsManagerEnabled()) {
            // If TLS management is disabled, run the previous non-tls connection test
            // The <code>isWsdlUrlBeingTested</code> hopefully doesn't matter here as the listeners are already defined
            // by the sender panel.
            nonTlsActionListener.actionPerformed(event);
            return;
        }

        if (!canTestConnection(isWsdlUrlBeingTested)) {
            return;
        }

        var wsProperties = (WebServiceDispatcherProperties) connectorPanel.getProperties();

        // Blank out the other property so that it isn't tested
        if (isWsdlUrlBeingTested) {
            wsProperties.setLocationURI("");
        } else {
            wsProperties.setWsdlUrl("");
        }

        try {
            connectorPanel
                .getConnectorSettingsPanel()
                .getServlet(
                    TLSServletInterface.class,
                    "Testing connection...",
                    "Error testing Web Service connection: ",
                    this.responseHandler
                ).testWsConnection(
                    connectorPanel.getConnectorSettingsPanel().getChannelId(),
                    connectorPanel.getConnectorSettingsPanel().getChannelName(),
                    wsProperties
                );
        } catch (ClientException e) {
            // Should not happen
        }
    }

    private boolean canTestConnection(boolean isWsdlUrlBeingTested) {
        var wsProperties = (WebServiceDispatcherProperties) connectorPanel.getProperties();

        if (isWsdlUrlBeingTested) {
            if (wsProperties.getWsdlUrl() == null || wsProperties.getWsdlUrl().isBlank()) {
                parentFrame.alertError(parentFrame, "-WSDL URL is blank.");
            }
        } else if (wsProperties.getLocationURI() == null || wsProperties.getLocationURI().isBlank()) {
            parentFrame.alertError(parentFrame, "-Location URI is blank.");
            return false;
        }

        return true;
    }

    private void testTlsConnection(ActionListener nonTlsActionListener, ActionEvent event, Transport transport) {
        if (!properties.isTlsManagerEnabled()) {
            // If TLS management is disabled, run the previous non-tls connection test
            nonTlsActionListener.actionPerformed(event);
            return;
        }

        try {
            var servletInterface = connectorPanel
                .getConnectorSettingsPanel()
                .getServlet(
                    TLSServletInterface.class,
                    "Testing connection...",
                    "Error testing TLS connection",
                    this.responseHandler
                );

            if (transport == Transport.HTTP) {
                servletInterface.testHttpsConnection(
                    connectorPanel.getConnectorSettingsPanel().getChannelId(),
                    connectorPanel.getConnectorSettingsPanel().getChannelName(),
                    (HttpDispatcherProperties) connectorPanel.getProperties()
                );
            } else if (transport == Transport.TCP) {
                servletInterface.testTcpConnection(
                    connectorPanel.getConnectorSettingsPanel().getChannelId(),
                    connectorPanel.getConnectorSettingsPanel().getChannelName(),
                    (TcpDispatcherProperties) connectorPanel.getProperties()
                );
            }

        } catch (Exception e) {
            log.error("Error testing TLS connection", e);
        }
    }

    private void getOperations(ActionListener nonTlsActionListener, ActionEvent event) {
        if (!properties.isTlsManagerEnabled()) {
            // If TLS management is disabled, run the previous non-tls connection test
            // The <code>isWsdlUrlBeingTested</code> hopefully doesn't matter here as the listeners are already defined
            // by the sender panel.
            nonTlsActionListener.actionPerformed(event);
            return;
        }

        var webServiceSender = (WebServiceSender) connectorPanel.getConnectorSettingsPanel();
        if (!parentFrame.alertOkCancel(parentFrame, "This will replace your current service, port, location URI, and operation list. Press OK to continue.")) {
            return;
        }

        var wsProperties = (WebServiceDispatcherProperties) connectorPanel.getProperties();

        // wtf...
        var cacheWsdlHandler = new ResponseHandler() {
            @Override
            public void handle(Object response) {
                try {
                    var retrieveWsdlFromCacheHandler = new ResponseHandler() {
                        @Override
                        public void handle(Object response) {
                            if (response == null) {
                                return;
                            }

                            var definitionServiceMap = (DefinitionServiceMap) response;

                            var currentProperties = (WebServiceDispatcherProperties) webServiceSender.getProperties();
                            currentProperties.setWsdlDefinitionMap(definitionServiceMap);

                            // Trigger private loadServiceMap() function
                            webServiceSender.setProperties(currentProperties);

                            // Trigger population of service and port comboboxes
                            var serviceCombobox = SwingMagic.findComponentFollowingLabel(connectorPanel.getConnectorSettingsPanel(), "Service:");
                            if (serviceCombobox instanceof MirthEditableComboBox serviceEditableCombobox) {
                                serviceEditableCombobox.setSelectedItem(definitionServiceMap.getMap().keySet().iterator().next());
                            }

                            parentFrame.setSaveEnabled(true);
                        }
                    };

                    connectorPanel
                        .getConnectorSettingsPanel()
                        .getServlet(
                            TLSServletInterface.class,
                            "Retrieving cached WSDL definition map...",
                            "There was an error retrieving the cached WSDL definition map.\n\n",
                            retrieveWsdlFromCacheHandler
                        )
                        .getDefinition(
                            connectorPanel.getConnectorSettingsPanel().getChannelId(),
                            connectorPanel.getConnectorSettingsPanel().getChannelName(),
                            wsProperties.getWsdlUrl(),
                            wsProperties.getUsername(),
                            wsProperties.getPassword()
                        );
                } catch (ClientException e) {
                    log.error("Error retrieving cached WSDL definition map", e);
                }
            }
        };

        try {
            connectorPanel
                .getConnectorSettingsPanel()
                .getServlet(
                    TLSServletInterface.class,
                    "Getting operations...",
                    "Error caching WSDL. Please check the WSDL URL and authentication settings.\n\n",
                    cacheWsdlHandler
                )
                .cacheWsdlFromUrl(
                    connectorPanel.getConnectorSettingsPanel().getChannelId(),
                    connectorPanel.getConnectorSettingsPanel().getChannelName(),
                    wsProperties
                );
        } catch (ClientException e) {
            log.error("Error getting operations", e);
        }
    }

    private List<JButton> getButtonsByText(String text) {
        var settingsComponents = connectorPanel
            .getConnectorSettingsPanel()
            .getComponents();

        return Arrays
            .stream(settingsComponents)
            .filter(component -> component instanceof JButton)
            .map(component -> (JButton) component)
            .filter(button -> button.getText().equals(text))
            .toList();
    }

    protected void fetchData() {
        final var workerId = PlatformUI.MIRTH_FRAME.startWorking("Fetching data...");

        var worker = new SwingWorker<Void, Void>() {
            private Map<String, String[]> cryptoMap;

            public Void doInBackground() {
                try {cryptoMap = PlatformUI.MIRTH_FRAME.mirthClient.getProtocolsAndCipherSuites();
                } catch (Exception e) {
                    PlatformUI.MIRTH_FRAME.alertThrowable(PlatformUI.MIRTH_FRAME, e, "Fetching imported certificates failed");
                }

                return null;
            }

            public void done() {
                supportedProtocols = Set.of(
                    cryptoMap.get(MirthSSLUtil.KEY_ENABLED_SERVER_PROTOCOLS)
                );

                supportedCiphers = Set.of(
                    cryptoMap.get(MirthSSLUtil.KEY_ENABLED_CIPHER_SUITES)
                );

                PlatformUI.MIRTH_FRAME.stopWorking(workerId);
            }
        };

        worker.execute();
    }
}
