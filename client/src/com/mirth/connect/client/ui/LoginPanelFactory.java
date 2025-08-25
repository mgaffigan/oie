package com.mirth.connect.client.ui;

/**
 * Factory for obtaining the application's LoginPanel implementation.
 */
public class LoginPanelFactory {

    private static AbstractLoginPanel provider = null;

    public static synchronized AbstractLoginPanel getInstance() {
        if (provider == null) {
            provider = PlatformUI.WEB_LOGIN_URL == null ? new LoginPanel() : new WebLoginPanel(PlatformUI.WEB_LOGIN_URL);
        }
        return provider;
    }
}
