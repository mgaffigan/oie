package com.mirth.connect.client.ui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.util.Collections;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

import javax.swing.SwingUtilities;

import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import javafx.scene.Scene;
import javafx.concurrent.Worker;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import netscape.javascript.JSObject;

import com.mirth.connect.client.core.Client;
import com.mirth.connect.client.core.ClientException;
import com.mirth.connect.client.core.UnauthorizedException;
import com.mirth.connect.client.core.api.servlets.UserServletInterface;
import com.mirth.connect.client.ui.util.DisplayUtil;
import com.mirth.connect.client.ui.BrandingConstants;
import com.mirth.connect.model.ExtendedLoginStatus;
import com.mirth.connect.model.LoginStatus;

/**
 * A minimal login panel that displays a JavaFX WebView which loads the provided URL.
 * There is no additional UI chrome inside the window — only the OS window frame is shown.
 */
public class WebLoginPanel extends AbstractLoginPanel {

    private static final String ERROR_MESSAGE = "There was an error connecting to the server at the specified address. Please verify that the server is up and running.";

    private boolean reinitialize = false;
    private final String url;
    private JFXPanel jfxPanel;
    private WebView webView;
    private WebEngine engine;

    public WebLoginPanel(String url) {
        this.url = url;
        initUI();
    }

    private void initUI() {
        // Ensure Swing creation on EDT
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                setDefaultCloseOperation(EXIT_ON_CLOSE);
                setTitle(String.format("%s - Login", BrandingConstants.PRODUCT_NAME));
                setIconImage(BrandingConstants.FAVICON.getImage());
                
                // No internal borders or controls; use the content pane directly
                getRootPane().setBorder(null);
                getContentPane().setLayout(new BorderLayout());

                jfxPanel = new JFXPanel(); // initializes JavaFX runtime
                jfxPanel.setPreferredSize(new Dimension(500, 600));
                getContentPane().add(jfxPanel, BorderLayout.CENTER);

                pack();
                setLocationRelativeTo(null);

                reinitializeWebview();
            }
        });
    }

    private void reinitializeWebview() {
        // Create the JavaFX scene and WebView
        Platform.runLater(new Runnable() {
            @Override
            public void run() {
                webView = new WebView();
                webView.setContextMenuEnabled(false);

                engine = webView.getEngine();

                // Inject a Java bridge object into the page once it finishes loading
                engine.documentProperty().addListener((obs, oldState, newState) -> {
                    try {
                        JSObject window = (JSObject)engine.executeScript("window");
                        window.setMember("mirthlogin", new JavaBridge());
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });

                // Navigate to the pilot URL
                try {
                    engine.load(url);
                } catch (Exception e) {
                    e.printStackTrace();
                }

                Scene scene = new Scene(webView);
                jfxPanel.setScene(scene);
            }
        });
    }

    public class JavaBridge {
        public void completeLogin(final String username, final String password) {
            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    handleLogin(username, password);
                }
            });
        }
    }

    private void handleLogin(String username, String password) {
        try {
            Client client = new Client(PlatformUI.SERVER_URL, PlatformUI.HTTPS_PROTOCOLS, PlatformUI.HTTPS_CIPHER_SUITES);

            // Attempt to login
            LoginStatus loginStatus = null;
            try {
                Map<String, List<String>> customHeaders = new HashMap<String, List<String>>();
                customHeaders.put(UserServletInterface.LOGIN_SERVER_URL_HEADER, Collections.singletonList(PlatformUI.SERVER_URL));
                loginStatus = client.getServlet(UserServletInterface.class, null, customHeaders).login(username, password);
            } catch (UnauthorizedException ex) {
                ex.printStackTrace();

                if (ex.getResponse() != null && ex.getResponse() instanceof LoginStatus) {
                    loginStatus = (LoginStatus)ex.getResponse();
                } else {
                    loginStatus = new LoginStatus(LoginStatus.Status.FAIL, "Login failed");
                }
            }

            // If SUCCESS or SUCCESS_GRACE_PERIOD
            if (loginStatus.isSuccess()) {
                if (!Mirth.handleLoginSuccess(client, loginStatus, username)) {
                    loginStatus = new LoginStatus(LoginStatus.Status.FAIL, "Login failed");
                }
            }

            if (!loginStatus.isSuccess()) {
                reportStatus(true, loginStatus.getMessage());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void initialize(String mirthServer, String version, String user, String pass) {
        // Show the window when requested by the caller
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                if (reinitialize) {
                    // Reinitialize the UI components
                    reinitializeWebview();
                }
                reinitialize = true;
                setVisible(true);
            }
        });
    }

    @Override
    public void setStatus(String status) {
        reportStatus(false, status);
    }

    private void reportStatus(boolean isError, String message) {
        Platform.runLater(() -> {
            try {
                JSObject window = (JSObject)engine.executeScript("window");
                if (isError) {
                    window.call("handleError", message);
                } else {
                    window.call("handleStatus", message);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }
}
