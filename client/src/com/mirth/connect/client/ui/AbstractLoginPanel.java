package com.mirth.connect.client.ui;

/**
 * Public interface for the login panel so alternative implementations can be provided.
 */
public abstract class AbstractLoginPanel extends javax.swing.JFrame {

    /**
     * Initialize and show the login UI.
     */
    public abstract void initialize(String mirthServer, String version, String user, String pass);

    /**
     * Update the status text shown on the login UI.
     */
    public abstract void setStatus(String status);
}
