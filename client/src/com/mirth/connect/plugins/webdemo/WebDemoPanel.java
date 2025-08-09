package com.mirth.connect.plugins.webdemo;

import com.mirth.connect.client.ui.AbstractSettingsPanel;
import com.mirth.connect.client.ui.UIConstants;
import com.mirth.connect.plugins.SettingsPanelPlugin;
import net.miginfocom.swing.MigLayout;

import javax.swing.JLabel;
import javax.swing.SwingWorker;
import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import javafx.scene.Scene;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import com.mirth.connect.plugins.webdemo.net.OieServerURLStreamHandlerFactory;

/**
 * Minimal settings panel that embeds a JavaFX WebView pointing to the static demo page served
 * by the backend at https://localhost:8443/webdemo/index.html. URL intentionally hard-coded for POC.
 */
public class WebDemoPanel extends AbstractSettingsPanel {

    private final SettingsPanelPlugin plugin;
    private JFXPanel jfxPanel;
    private WebEngine webEngine;
    private JLabel statusLabel;
    private static final String DEMO_URL = "oieserver:///webdemo/index.html";

    public WebDemoPanel(String tabName, SettingsPanelPlugin plugin) {
        super(tabName);
        this.plugin = plugin;
        // Register oieserver:// URL handler once
        try {
            java.net.URL.setURLStreamHandlerFactory(new OieServerURLStreamHandlerFactory());
            System.out.println("[WebDemo] Registered URL handler for oieserver://");
        } catch (Error | Exception e) {
            // Factory can only be set once per JVM; log if already set
            System.out.println("[WebDemo] URLStreamHandlerFactory registration skipped/failed: " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
        initComponents();
        initLayout();
        loadPageAsync();
    }

    private void initComponents() {
        setBackground(UIConstants.BACKGROUND_COLOR);
        jfxPanel = new JFXPanel();
        statusLabel = new JLabel("Loading: " + DEMO_URL);
    }

    private void initLayout() {
        setLayout(new MigLayout("insets 12, fill", "[grow]", "[grow][]"));
        add(jfxPanel, "grow, wrap");
        add(statusLabel, "growx");
    }

    private void loadPageAsync() {
        Platform.runLater(() -> {
            WebView webView = new WebView();
            webEngine = webView.getEngine();
            // Update status when location changes
            webEngine.locationProperty().addListener((obs, o, n) -> statusLabel.setText("Loaded: " + n));
            // Add diagnostics for load success/failure
            webEngine.getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
                switch (newState) {
                    case SUCCEEDED:
                        statusLabel.setText("Loaded: " + webEngine.getLocation());
                        break;
                    case FAILED:
                        Throwable ex = webEngine.getLoadWorker().getException();
                        String detail = "";
                        if (ex != null) {
                            String msg = ex.getMessage();
                            Throwable cause = ex.getCause();
                            detail = " (" + ex.getClass().getSimpleName() + (msg != null ? ": " + msg : "") +
                                    (cause != null ? "; cause=" + cause.getClass().getSimpleName() + (cause.getMessage() != null ? ": " + cause.getMessage() : "") : "") + ")";
                        }
                        statusLabel.setText("Failed to load: " + webEngine.getLocation() + detail);
                        System.out.println("[WebDemo] WebView FAILED: " + detail);
                        break;
                    default:
                        // no-op
                }
            });
            jfxPanel.setScene(new Scene(webView));
            webEngine.load(DEMO_URL);
        });
    }

    @Override
    public void doRefresh() {
        // Simply reload page
    SwingWorker<Void, Void> worker = new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() {
                if (webEngine != null) {
                    Platform.runLater(() -> webEngine.reload());
                }
                return null;
            }
        };
        worker.execute();
    }

    @Override
    public boolean doSave() {
        // Nothing to save
        return true;
    }
}
