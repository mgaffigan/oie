package com.mirth.connect.server.launcher;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.junit.Test;

public class MirthLauncherTest {

    @Test
    public void testSanitizeLog4jRemovesDirLogs() throws Exception {
        File file = File.createTempFile("log4j2", ".properties");
        file.deleteOnExit();
        Files.writeString(file.toPath(), String.join(System.lineSeparator(),
                "rootLogger = ERROR,stdout,fout",
                "dir.logs   = logs",
                "property.log.dir = logs",
                "appender.rolling.fileName = ${log.dir}/mirth.log"), StandardCharsets.UTF_8);

        Method method = MirthLauncher.class.getDeclaredMethod("sanitizeLog4jConfiguration", File.class);
        method.setAccessible(true);
        method.invoke(null, file);

        String fileContents = Files.readString(file.toPath(), StandardCharsets.UTF_8);
        assertFalse(fileContents.contains("dir.logs"));
        assertTrue(fileContents.contains("property.log.dir = logs"));
        assertTrue(fileContents.contains("appender.rolling.fileName = ${log.dir}/mirth.log"));
    }
}
