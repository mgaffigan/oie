package com.mirth.connect.client.ui;

/**
 * Factory for obtaining the application's LoginPanel implementation.
 */
public class LoginPanelFactory {

    private static AbstractLoginPanel provider = null;

    public static synchronized AbstractLoginPanel getInstance() {
        if (provider == null) {
            provider = new DefaultLoginPanel();
        }
        return provider;
    }

    /**
     * Replace the current provider. This is used to switch between login implementations at runtime.
     */
    public static synchronized void setProvider(AbstractLoginPanel newProvider) {
        provider = newProvider;
    }
}
