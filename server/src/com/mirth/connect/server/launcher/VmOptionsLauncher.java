package com.mirth.connect.server.launcher;

public class VmOptionsLauncher {
    public static void main(String[] args) {
        // Exit codes:
        // 0 - Normal exit
        // 1 - Error
        // 75 - VM options file updated, restart required
        try {
            if (ParseVmOptions.update()) {
                System.err.println("Launch arguments changed.  Please restart the application.");
                System.exit(75);
            }
        } catch (VmOptionsParseException e) {
            System.err.println("Error parsing VM options file: " + e.getMessage());
            System.exit(1);
        } catch (Exception e) {
            System.err.println("Unexpected error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }

        MirthLauncher.main(args);
    }
}