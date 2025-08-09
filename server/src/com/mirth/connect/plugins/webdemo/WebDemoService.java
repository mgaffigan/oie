package com.mirth.connect.plugins.webdemo;

import com.mirth.connect.client.core.api.util.OperationUtil;
import com.mirth.connect.model.ExtensionPermission;
import com.mirth.connect.plugins.ServicePlugin;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Simple service plugin that on startup ensures a demo HTML file is written to the server's
 * public_html/webdemo directory so it can be accessed at: https://localhost:8443/webdemo/index.html
 */
public class WebDemoService implements ServicePlugin {

    public static final String PLUGIN_POINT = WebDemoServletInterface.PLUGIN_POINT;
    public static final String PERMISSION_USE = WebDemoServletInterface.PERMISSION_USE;
    private final Logger logger = LogManager.getLogger(getClass());

    @Override
    public String getPluginPointName() {
        return PLUGIN_POINT;
    }

    @Override
    public void start() {
        // Copy the bundled HTML resource to public_html if not already present
        try {
            installStaticPage();
        } catch (Exception e) {
            logger.error("Failed to install Web Demo static page.", e);
        }
    }

    @Override
    public void stop() {
        // Nothing to do
    }

    @Override
    public void init(Properties properties) {
        // No properties currently
    }

    @Override
    public void update(Properties properties) {
        // No updatable properties currently
    }

    @Override
    public Properties getDefaultProperties() {
        return new Properties();
    }

    @Override
    public ExtensionPermission[] getExtensionPermissions() {
    ExtensionPermission usePermission = new ExtensionPermission(PLUGIN_POINT, PERMISSION_USE,
        "Allows using the Web Demo API endpoints.",
        OperationUtil.getOperationNamesForPermission(PERMISSION_USE, WebDemoServletInterface.class),
        new String[] {});
    return new ExtensionPermission[] { usePermission };
    }

    private void installStaticPage() throws IOException {
        // Determine base directory by walking up from the classes location until we find /server
        // Then append public_html/webdemo
        String userDir = System.getProperty("user.dir");
        File baseDir = new File(userDir);
        // Heuristic: look for a child directory 'public_html'
        File publicHtml = new File(baseDir, "public_html");
        if (!publicHtml.exists()) {
            // Try parent (runtime layout differs from dev tree)
            publicHtml = new File(baseDir.getParentFile(), "public_html");
        }
        File webdemoDir = new File(publicHtml, "webdemo");
        if (!webdemoDir.exists() && !webdemoDir.mkdirs()) {
            logger.warn("Unable to create webdemo directory: " + webdemoDir.getAbsolutePath());
            return;
        }

        File targetFile = new File(webdemoDir, "index.html");
        if (targetFile.exists()) {
            return; // Don't overwrite (developer may edit)
        }

        try (InputStream in = getClass().getResourceAsStream("/com/mirth/connect/plugins/webdemo/webdemo.html")) {
            if (in == null) {
                logger.warn("Could not find embedded webdemo.html resource");
                return;
            }
            // Java 8 compatible copy (readAllBytes not available prior to Java 9)
            byte[] buffer = new byte[4096];
            int read;
            try (FileOutputStream out = new FileOutputStream(targetFile)) {
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                }
            }
            logger.info("Installed Web Demo page at " + targetFile.getAbsolutePath());
        }
    }
}
