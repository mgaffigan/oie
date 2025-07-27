package com.mirth.connect.server.launcher.winnt;

import com.sun.jna.LastErrorException;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.Advapi32;
import com.sun.jna.platform.win32.WinError;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.platform.win32.Winsvc;

public class NTServiceHost {
    private final NTService service;
    private Winsvc.SERVICE_STATUS_HANDLE serviceStatusHandle;
    private final Object waitObject = new Object();

    public NTServiceHost(NTService service) {
        this.service = service;
    }

    public void run() {
        Winsvc.SERVICE_TABLE_ENTRY entry = new Winsvc.SERVICE_TABLE_ENTRY();
        entry.lpServiceName = service.getServiceName();
        entry.lpServiceProc = this::ServiceMain;

        if (!Advapi32.INSTANCE.StartServiceCtrlDispatcher((Winsvc.SERVICE_TABLE_ENTRY[]) entry.toArray(2))) {
            service.logError("StartServiceCtrlDispatcher", new LastErrorException(Native.getLastError()));
            return;
        }
    }

    public void ServiceMain(int dwArgc, Pointer lpszArgv) {
        serviceStatusHandle = Advapi32.INSTANCE.RegisterServiceCtrlHandlerEx(
                service.getServiceName(), this::ServiceControlHandler, null);
        if (serviceStatusHandle == null) {
            service.logError("RegisterServiceCtrlHandlerEx", new LastErrorException(Native.getLastError()));
            return;
        }

        reportStatus(Winsvc.SERVICE_START_PENDING);
        reportStatus(Winsvc.SERVICE_RUNNING);

        try {
            service.onStart();
        } catch (Exception e) {
            service.logError("onStart", e);
            reportStatus(Winsvc.SERVICE_STOPPED);
            return;
        }

        // Wait for stop signal
        try {
            synchronized(waitObject) {
                waitObject.wait();
            }
        } catch (InterruptedException ex) { /* nop */ }

        try {
            service.onStop();
        } catch (Exception e) {
            service.logError("onStop", e);
        }
        reportStatus(Winsvc.SERVICE_STOPPED);
    }

    private int ServiceControlHandler(int dwControl, int dwEventType, Pointer lpEventData, Pointer lpContext) {
        switch(dwControl) {
            case Winsvc.SERVICE_CONTROL_STOP:
            case Winsvc.SERVICE_CONTROL_SHUTDOWN:
                reportStatus(Winsvc.SERVICE_STOP_PENDING);
                synchronized(waitObject) {
                    waitObject.notifyAll();
                }
        }
        return WinError.NO_ERROR;
    }

    private void reportStatus(int status) {
        Winsvc.SERVICE_STATUS serviceStatus = new Winsvc.SERVICE_STATUS();
        serviceStatus.dwServiceType = WinNT.SERVICE_WIN32_OWN_PROCESS;
        serviceStatus.dwControlsAccepted = Winsvc.SERVICE_ACCEPT_STOP | Winsvc.SERVICE_ACCEPT_SHUTDOWN;
        serviceStatus.dwWaitHint = 30 * 1000 /* ms */;
        serviceStatus.dwCurrentState = status;
        Advapi32.INSTANCE.SetServiceStatus(serviceStatusHandle, serviceStatus);
    }
}
