package com.mirth.connect.server.launcher;

import java.io.IOException;
import java.io.File;
import java.util.Properties;
import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.nio.file.StandardOpenOption;

public class ParseVmOptions {
    private static final String INITIAL_VMOPTIONS_PATH = "./oieserver.vmoptions";

    public static void main(String[] args) {
        // args[0] == source file
        // args[1] == destination file
        String sourceFile;
        String destinationFile;
        if (args.length == 0) {
            sourceFile = INITIAL_VMOPTIONS_PATH;
            try {
                destinationFile = getLaunchArgsPath();
            } catch (IOException e) {
                System.err.println("Error getting launch arguments path: " + e.getMessage());
                System.exit(1);
                return;
            }
        } else if (args.length == 2) {
            sourceFile = args[0];
            destinationFile = args[1];
        } else {
            System.err.println("Usage: java ... <source file> <destination file>\nExit:\n\t\t0 - No updates\n\t75 - File updated\n\t1 - Error");
            System.exit(1);
            return;
        }

        try {
            if (update(sourceFile, destinationFile)) {
                System.err.println("VM options file updated.");
                System.exit(75);
            } else {
                System.exit(0);
            }
        } catch (VmOptionsParseException e) {
            System.err.println("Error parsing VM options file: " + e.getMessage());
            System.exit(1);
        } catch (Exception e) {
            System.err.println("Unexpected error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    public static boolean update() throws VmOptionsParseException, IOException {
        return update(INITIAL_VMOPTIONS_PATH, getLaunchArgsPath());
    }

    public static boolean update(String sourceFile, String destinationFile) throws VmOptionsParseException, IOException {
        // Calculate
        ParsedVmOptions parsedOptions = new ParsedVmOptions();
        parsedOptions.addFile(sourceFile);
        List<String> result = parsedOptions.getResultantArgs();

        // Reconcile
        List<String> existing;
        try {
            existing = Files.readAllLines(Paths.get(destinationFile));
        } catch (IOException e) {
            existing = Collections.emptyList();
        }
        if (existing.equals(result)) {
            return false; // No updates needed
        }

        // Update
        Files.write(Paths.get(destinationFile), result, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        return true; // File was updated
    }

    private static String getLaunchArgsPath() throws IOException {
        Properties properties = MirthPropertiesExtensions.getMirthProperties();
        File appDataDir = new File(MirthPropertiesExtensions.getAppdataDir(properties));

        // Create appData directory if it does not exist
        if (!appDataDir.exists()) {
            if (!appDataDir.mkdirs()) {
                throw new IOException("Failed to create appdata directory: " + appDataDir.getAbsolutePath());
            }
        }

        return new File(appDataDir, "launch.args").getAbsolutePath();
    }
}
