package com.mirth.connect.plugins.oidcsupplicant;

import java.awt.Color;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Properties;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingWorker;
import javax.swing.border.TitledBorder;

import net.miginfocom.swing.MigLayout;

import com.mirth.connect.client.ui.AbstractSettingsPanel;
import com.mirth.connect.client.ui.Frame;
import com.mirth.connect.client.ui.UIConstants;
import com.mirth.connect.client.ui.PlatformUI;
import com.mirth.connect.client.ui.components.MirthPasswordField;
import com.mirth.connect.client.ui.components.MirthTextField;
import com.mirth.connect.plugins.SettingsPanelPlugin;

/**
 * Simple settings panel to edit OIDC supplicant properties.
 */
public class OidcSupplicantPanel extends AbstractSettingsPanel {
    private MirthTextField clientIdField;
    private MirthPasswordField clientSecretField;
    private MirthTextField authEndpointField;
    private MirthTextField tokenEndpointField;
    private SettingsPanelPlugin plugin;

    public OidcSupplicantPanel(SettingsPanelPlugin plugin) {
        super("OIDC Supplicant");
        this.plugin = plugin;

        // match other settings panels
        setBackground(UIConstants.BACKGROUND_COLOR);

        initComponents();
        initLayout();
    }

    @Override
    public void doRefresh() {
        if (PlatformUI.MIRTH_FRAME.alertRefresh()) {
            return;
        }

        final Frame frame = getFrame();
        final String workingId = frame.startWorking("Loading " + getTabName() + " properties...");

        SwingWorker<Properties, Void> worker = new SwingWorker<Properties, Void>() {
            @Override
            protected Properties doInBackground() throws Exception {
                Properties serverProperties = new Properties();
                try {
                    Properties fromServer = plugin.getPropertiesFromServer();
                    if (fromServer != null) {
                        serverProperties.putAll(fromServer);
                    }
                } catch (Exception e) {
                    frame.alertThrowable(frame, e);
                }
                return serverProperties;
            }

            @Override
            protected void done() {
                try {
                    Properties properties = get();
                    setProperties(properties);
                } catch (Exception e) {
                    frame.alertThrowable(frame, e);
                } finally {
                    frame.stopWorking(workingId);
                }
            }
        };

        worker.execute();
    }

    @Override
    public boolean doSave() {
        if (!isSaveEnabled()) {
            return true;
        }

        final Frame frame = getFrame();
        final String workingId = frame.startWorking("Saving OIDC supplicant properties...");

        SwingWorker<Boolean, Void> worker = new SwingWorker<Boolean, Void>() {
            @Override
            protected Boolean doInBackground() throws Exception {
                try {
                    plugin.setPropertiesToServer(getProperties());
                    return true;
                } catch (Exception e) {
                    frame.alertThrowable(frame, e);
                    return false;
                }
            }

            @Override
            protected void done() {
                try {
                    boolean ok = get();
                    if (ok) {
                        setSaveEnabled(false);
                    }
                } catch (Exception e) {
                    frame.alertThrowable(frame, e);
                } finally {
                    frame.stopWorking(workingId);
                }
            }
        };

        worker.execute();
        return true;
    }

    public void setProperties(Properties properties) {
        clientIdField.setText(properties.getProperty("oidc_client_id", ""));
        clientSecretField.setText(properties.getProperty("oidc_client_secret", ""));
        authEndpointField.setText(properties.getProperty("oidc_authorization_endpoint", ""));
        tokenEndpointField.setText(properties.getProperty("oidc_token_endpoint", ""));
        setSaveEnabled(false);
    }

    public Properties getProperties() {
        Properties properties = new Properties();
        properties.setProperty("oidc_client_id", clientIdField.getText());
        properties.setProperty("oidc_client_secret", new String(clientSecretField.getPassword()));
        properties.setProperty("oidc_authorization_endpoint", authEndpointField.getText());
        properties.setProperty("oidc_token_endpoint", tokenEndpointField.getText());
        return properties;
    }

    private void initComponents() {
        clientIdField = new MirthTextField();
        clientSecretField = new MirthPasswordField();
        authEndpointField = new MirthTextField();
        tokenEndpointField = new MirthTextField();

        // mark fields dirty when edited
        ActionListener dirtyListener = new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                setSaveEnabled(true);
            }
        };

        clientIdField.addActionListener(dirtyListener);
        clientSecretField.addActionListener(dirtyListener);
        authEndpointField.addActionListener(dirtyListener);
        tokenEndpointField.addActionListener(dirtyListener);
    }

    private void initLayout() {
        // follow DataPrunerPanel conventions: top-level uses hidemode/novisualpadding and 12px inset
        setLayout(new MigLayout("hidemode 3, novisualpadding, insets 12", "[grow]"));

        // inner container uses 0 insets and column constraints similar to other settings panels
        JPanel container = new JPanel(new MigLayout("hidemode 3, novisualpadding, insets 0", "12[right]12[left, grow]", "[]8[]8[]8[]"));
        container.setBackground(UIConstants.BACKGROUND_COLOR);
        container.setBorder(BorderFactory.createTitledBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(204, 204, 204)), "OIDC Supplicant", TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION, new Font("Tahoma", Font.BOLD, 11)));

    // right-align labels and limit text box widths like DataPrunerPanel
    container.add(new JLabel("Client ID:"));
    container.add(clientIdField, "w 250!, h 22!, wrap");

    container.add(new JLabel("Client Secret:"));
    container.add(clientSecretField, "w 250!, h 22!, wrap");

    container.add(new JLabel("Authorization Endpoint:"));
    container.add(authEndpointField, "w 400!, h 22!, wrap");

    container.add(new JLabel("Token Endpoint:"));
    container.add(tokenEndpointField, "w 400!, h 22!, wrap");

    add(container, "grow, sx, wrap");
    }
}
