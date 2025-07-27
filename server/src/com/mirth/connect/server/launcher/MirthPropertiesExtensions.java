package com.mirth.connect.server.launcher;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

public class MirthPropertiesExtensions {
    private static final String PROPERTY_APP_DATA_DIR = "dir.appdata";
    private static final String MIRTH_PROPERTIES_FILE = "./conf/mirth.properties";

    public static Properties getMirthProperties() throws IOException {
        Properties mirthProperties = new Properties();
        try (FileInputStream inputStream = new FileInputStream(new File(MIRTH_PROPERTIES_FILE))) {
            mirthProperties.load(inputStream);
        }
        return mirthProperties;
    }

    /**
     * Gets the absolute path of the appdata directory from Mirth Connect properties.
     *
     * @param mirthProperties The properties object containing Mirth Connect settings.
     * @return The absolute path of the appdata directory.
     */
    public static String getAppdataDir(Properties mirthProperties) {
        if (mirthProperties.getProperty(PROPERTY_APP_DATA_DIR) != null) {
            return new File(mirthProperties.getProperty(PROPERTY_APP_DATA_DIR)).getAbsolutePath();
        } else {
            return new File(".").getAbsolutePath();
        }
    }
}