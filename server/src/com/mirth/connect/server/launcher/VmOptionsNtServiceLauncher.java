package com.mirth.connect.server.launcher;

import com.mirth.connect.server.launcher.winnt.Dbgview;
import com.mirth.connect.server.launcher.winnt.WindowsCwd;
import com.mirth.connect.server.launcher.winnt.NTService;
import com.mirth.connect.server.launcher.winnt.NTServiceHost;

public class VmOptionsNtServiceLauncher {
    public static void main(String[] args) {
        // Exit codes:
        // 0 - Normal exit
        // 1 - Error
        // 75 - VM options file updated, restart required
        if (args.length < 2) {
            Dbgview.log("OIE: Usage: java ... serviceName launchFile");
            System.exit(1);
            return;
        }
        String serviceName = args[0];
        String launchFile = args[1];

        try {
            // Set the launch arguments file
            if (ParseVmOptions.update(null, launchFile, true)) {
                Dbgview.log("OIE: Launch arguments changed.  Please restart the application.");
                System.exit(75);
            }
        } catch (VmOptionsParseException e) {
            Dbgview.log("OIE: Error parsing VM options file: " + e.getMessage());
            System.exit(1);
        } catch (Exception e) {
            Dbgview.log("OIE: Unexpected error: " + e.getMessage());
            for (StackTraceElement element : e.getStackTrace()) {
                Dbgview.log("OIE:   " + element.toString());
            }
            System.exit(1);
        }

        // Start the service
        try {
            NTServiceHost service = new NTServiceHost(new NTService() {
                @Override
                public String getServiceName() {
                    return serviceName;
                }

                @Override
                public void onStart() {
                    MirthLauncher.main(args);
                }

                @Override
                public void onStop() {
                    // TODO: Implement graceful shutdown
                    System.exit(0);
                }

                @Override
                public void logError(String action, Exception e) {
                    Dbgview.log("OIE: Error during " + action + ": " + e.getMessage());
                }
            });
            service.run();
        } catch (Exception e) {
            Dbgview.log("OIE: Error starting service: " + e.getMessage());
            for (StackTraceElement element : e.getStackTrace()) {
                Dbgview.log("OIE:   " + element.toString());
            }
            System.exit(1);
        }
    }
}