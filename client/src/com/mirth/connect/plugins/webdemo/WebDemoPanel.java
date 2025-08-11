package com.mirth.connect.plugins.webdemo;

import com.mirth.connect.client.ui.AbstractSettingsPanel;
import com.mirth.connect.client.ui.UIConstants;
import com.mirth.connect.plugins.SettingsPanelPlugin;
import net.miginfocom.swing.MigLayout;

import javax.swing.SwingWorker;
import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import javafx.scene.Scene;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.concurrent.Worker;
import netscape.javascript.JSObject;

/**
 * Minimal settings panel that embeds a JavaFX WebView pointing to the static demo page
 * served by the backend using the custom oieserver:/// scheme (resolved via PlatformUI.SERVER_URL).
 */
public class WebDemoPanel extends AbstractSettingsPanel {

    private JFXPanel jfxPanel;
    private WebEngine webEngine;

    public WebDemoPanel() {
        super("Web Demo");
        initLayout();
    }

    private void initLayout() {
        setBackground(UIConstants.BACKGROUND_COLOR);
        jfxPanel = new JFXPanel();
        setLayout(new MigLayout("fill", "[grow]", "[grow]"));
        add(jfxPanel, "grow");
        Platform.runLater(() -> {
            WebView webView = new WebView();
            webEngine = webView.getEngine();
            webEngine.setJavaScriptEnabled(true);
            jfxPanel.setScene(new Scene(webView));
            webEngine.load("oieserver:///webdemo/index.html");
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
