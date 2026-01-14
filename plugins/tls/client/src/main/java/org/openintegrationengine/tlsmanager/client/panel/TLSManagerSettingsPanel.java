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

import com.mirth.connect.client.core.ClientException;
import com.mirth.connect.client.ui.AbstractSettingsPanel;
import com.mirth.connect.client.ui.BareBonesBrowserLaunch;
import com.mirth.connect.client.ui.MirthHeadingPanel;
import com.mirth.connect.client.ui.PlatformUI;
import com.mirth.connect.client.ui.UIConstants;
import com.mirth.connect.plugins.SettingsPanelPlugin;
import net.miginfocom.swing.MigLayout;

import javax.swing.BorderFactory;
import javax.swing.GroupLayout;
import javax.swing.ImageIcon;
import javax.swing.JEditorPane;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingWorker;
import javax.swing.UIManager;
import javax.swing.border.TitledBorder;
import javax.swing.event.HyperlinkEvent;
import java.awt.Color;
import java.awt.Font;
import java.util.concurrent.ExecutionException;

public class TLSManagerSettingsPanel extends AbstractSettingsPanel {

    // https://github.com/phosphor-icons/core/blob/main/raw/duotone/gear-duotone.svg
    private static final String SETTINGS_ICON_PATH = "images/tls_plugin_settings.png";

    private MirthHeadingPanel mirthHeadingPanel;
    private JPanel infoPanel;
    private JPanel aboutPanel;
    private JPanel teamPanel;
    private JLabel copyright;

    private JLabel versionLabel;

    private final SettingsPanelPlugin plugin;

    private static final String tlsManagerUrl = PlatformUI.SERVER_URL + "/tls-manager";

    public TLSManagerSettingsPanel(String tabName, SettingsPanelPlugin plugin) {
        super(tabName);

        this.plugin = plugin;

        setVisibleTasks(0, 1, false);
        addTask(
            "openManagerInBrowser",
            "Open TLS Manager",
            "Launch the Web TLS Manager inside your system browser",
            "",
            new ImageIcon(this.getClass().getClassLoader().getResource(SETTINGS_ICON_PATH)));
        initComponents();
        initLayout();

        fetchData();
    }

    private void initComponents() {
        setBackground(UIConstants.BACKGROUND_COLOR);

        versionLabel = new JLabel();
        versionLabel.setFont(new java.awt.Font(Font.SANS_SERIF, Font.BOLD, 18));
        versionLabel.setForeground(Color.WHITE);

        mirthHeadingPanel = new MirthHeadingPanel();
        GroupLayout mirthHeadingPanelLayout = new GroupLayout(mirthHeadingPanel);
        mirthHeadingPanel.setLayout(mirthHeadingPanelLayout);
        mirthHeadingPanelLayout.setHorizontalGroup(
            mirthHeadingPanelLayout.createParallelGroup(GroupLayout.Alignment.LEADING)
                .addGroup(mirthHeadingPanelLayout.createSequentialGroup()
                    .addContainerGap()
                    .addComponent(versionLabel, GroupLayout.DEFAULT_SIZE, 353, Short.MAX_VALUE)
                    .addContainerGap())
        );
        mirthHeadingPanelLayout.setVerticalGroup(
            mirthHeadingPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addGroup(mirthHeadingPanelLayout.createSequentialGroup()
                    .addContainerGap()
                    .addComponent(versionLabel, javax.swing.GroupLayout.DEFAULT_SIZE, 29, Short.MAX_VALUE)
                    .addContainerGap())
        );

        infoPanel = new JPanel();
        infoPanel.setBackground(UIConstants.BACKGROUND_COLOR);
        infoPanel.setBorder(
            BorderFactory.createTitledBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(204, 204, 204)),
                "Where are the settings?", TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION,
                new Font(Font.SANS_SERIF, Font.BOLD, 11)
            )
        );

        aboutPanel = new JPanel();
        aboutPanel.setBackground(UIConstants.BACKGROUND_COLOR);
        aboutPanel.setBorder(
            BorderFactory.createTitledBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(204, 204, 204)),
                "About the TLS Manager Plugin", TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION,
                new Font(Font.SANS_SERIF, Font.BOLD, 11)
            )
        );

        teamPanel = new JPanel();
        teamPanel.setBackground(UIConstants.BACKGROUND_COLOR);
        teamPanel.setBorder(
            BorderFactory.createTitledBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(204, 204, 204)),
                "The team members involved", TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION,
                new Font(Font.SANS_SERIF, Font.BOLD, 11)
            )
        );

        copyright = new JLabel("© 2025 NovaMap Health Limited");
        copyright.setFont(copyright.getFont().deriveFont(Font.PLAIN, 10f));
        copyright.setForeground(new Color(120, 120, 120));
    }

    private void initLayout() {
        setLayout(new MigLayout(
            "hidemode 3, novisualpadding, insets 0",
            "[grow]",
            "[] [grow] []"
        ));

        JLabel whereText1 = new JLabel(
            """
                <html>
                Certificate management for the TLS Manager Plugin is done using a web-based user interface available at:<br/><br/>
                </html>"""
        );

        JEditorPane whereText2 = new JEditorPane();
        whereText2.setContentType("text/html");
        whereText2.setEditable(false);
        whereText2.setOpaque(false);
        whereText2.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
        Font labelFont = UIManager.getFont("Label.font");

        String style =
            "<style>" +
                "body { " +
                "  font-family: '" + labelFont.getFamily() + "';" +
                "  font-size: " + labelFont.getSize() + "pt;" +
                "  color: rgb(0,0,0);" +
                "}" +
                "</style>";

        whereText2.setText(
            "<html>" +
                style +
                "<body>" +
                "<a href=\"" + tlsManagerUrl + "\">" + tlsManagerUrl + "</a>" +
                "</body></html>"
        );

        whereText2.addHyperlinkListener(e -> {
            if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
                openManagerInBrowser();
            }
        });

        JLabel whereText3 = new JLabel(
            """
                <html>
                <br/>The access credentials are the same ones used to log in to this Administrator Client.
                </html>"""
        );

        JLabel aboutText = new JLabel(
            """
                <html>
                Jointly sponsored by NovaMap Health Limited & Diridium Technologies Inc.<br/>
                and donated to the Open Integration Engine initiative.
                </html>"""
        );

        JLabel teamList = new JLabel(
            """
                <html>
                •  Alex Frîncu<br/>
                •  Andreea Dincă<br/>
                •  Andrei Haiducu<br/>
                •  Ed Riordan<br/>
                •  Kaur Palang<br/>
                •  Paul Coyne<br/>
                •  Paul Hristea<br/>
                •  Paul Richardson<br/><br/>
                <i>Thank you!</i>
                </html>"""
        );

        infoPanel.setLayout(new MigLayout("insets 8", "[grow]"));
        aboutPanel.setLayout(new MigLayout("insets 8", "[grow]"));
        teamPanel.setLayout(new MigLayout("insets 8", "[grow]"));

        infoPanel.add(whereText1, "growx, wrap");
        infoPanel.add(whereText2, "growx, wrap");
        infoPanel.add(whereText3, "growx, wrap");
        aboutPanel.add(aboutText, "growx, wrap");
        teamPanel.add(teamList, "growx, wrap");

        JPanel contentPanel = new JPanel(
            new MigLayout("insets 12", "[grow]", "[] [] []")
        );
        contentPanel.setOpaque(true);
        contentPanel.setBackground(UIConstants.BACKGROUND_COLOR);
        contentPanel.add(infoPanel, "growx, wrap");
        contentPanel.add(aboutPanel, "growx, wrap");
        contentPanel.add(teamPanel, "growx, wrap");
        contentPanel.add(copyright, "gapy 24");

        JPanel headerWrapper = new JPanel(new MigLayout("insets 0", "[grow]", "[]"));
        headerWrapper.setOpaque(true);
        headerWrapper.setBackground(UIConstants.BACKGROUND_COLOR);

        headerWrapper.add(
            mirthHeadingPanel,
            "growx, gaptop 7, gapleft 7, gapafter 7"
        );
        add(headerWrapper, "growx, wrap");
        add(contentPanel, "grow, push, wrap");
    }

    @Override
    public void doRefresh() {

    }

    @Override
    public boolean doSave() {
        return false;
    }

    public void openManagerInBrowser() {
        BareBonesBrowserLaunch.openURL(tlsManagerUrl);
    }

    protected final void fetchData() {
        final var workerId = PlatformUI.MIRTH_FRAME.startWorking("Fetching data...");

        var worker = new SwingWorker<String, Void>() {
            protected String doInBackground() {
                try {
                    return PlatformUI.MIRTH_FRAME.mirthClient.getExtensionMetaData(plugin.getPluginName()).getPluginVersion();
                } catch (ClientException e) {
                    throw new RuntimeException(e);
                }
            }

            protected void done() {
                try {
                    var pluginVersion = get();
                    versionLabel.setText("TLS Manager Plugin " + pluginVersion);
                } catch (InterruptedException | ExecutionException e) {
                    PlatformUI.MIRTH_FRAME.alertThrowable(PlatformUI.MIRTH_FRAME, e, "Fetching failed");
                    throw new RuntimeException(e);
                }

                PlatformUI.MIRTH_FRAME.stopWorking(workerId);
            }
        };

        worker.execute();
    }
}
