package com.mirth.connect.server;

import java.io.File;

public class MirthHome {
    /**
     * Gets the absolute path of the Mirth Connect home directory.
     *
     * @return The absolute path of the Mirth Connect home directory.
     */
    public static String getMirthHome() {
        String mirthHome = System.getProperty("mirth.home");
        if (mirthHome == null || mirthHome.isEmpty()) {
            mirthHome = System.getProperty("user.dir");
        }
        return mirthHome;
    }
}