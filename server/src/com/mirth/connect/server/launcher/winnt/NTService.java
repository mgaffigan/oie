package com.mirth.connect.server.launcher.winnt;

public interface NTService {
    String getServiceName();
    void onStart();
    void onStop();
    void logError(String action, Exception e);
}
