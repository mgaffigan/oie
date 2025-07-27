package com.mirth.connect.server.launcher.winnt;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.WString;

public final class Dbgview {
    public static void log(String message) {
        System.out.println(message);
        Kernel32.INSTANCE.OutputDebugStringW(new WString(message));
    }

    private interface Kernel32 extends Library {
        Kernel32 INSTANCE = Native.loadLibrary("kernel32", Kernel32.class);
        void OutputDebugStringW(WString lpOutputString);
    }
}
