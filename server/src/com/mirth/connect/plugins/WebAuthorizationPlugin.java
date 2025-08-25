package com.mirth.connect.plugins;

import com.mirth.connect.client.core.ControllerException;
import com.mirth.connect.model.LoginStatus;

public interface WebAuthorizationPlugin extends ServerPlugin {

    /**
     * Checks if the plugin is configured.  If not configured, the plugin
     * should be ignored.
     * 
     * @return true if the plugin is configured, false otherwise
     */
    public boolean isConfigured();

    /**
     * Authorizes a user with the given username and password.
     * 
     * @param username the username
     * @param plainPassword the plain text password
     * @return the login status, or null if the username is unrecognized by the plugin
     */
    public LoginStatus authorizeUser(String username, String plainPassword) throws ControllerException;

    /**
     * Returns any extra launch arguments for the web start client.
     * 
     * @param baseUrl the base URL of the server
     * @return an array of extra launch arguments or null if the plugin is not configured
     */
    public String[] getExtraLaunchArgs(String baseUrl);
}
