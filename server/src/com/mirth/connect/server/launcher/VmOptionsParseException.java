package com.mirth.connect.server.launcher;

public class VmOptionsParseException extends Exception {
    private static final long serialVersionUID = 1L;

    public VmOptionsParseException(String message) {
        super(message);
    }

    public VmOptionsParseException(String message, Throwable cause) {
        super(message, cause);
    }

    public VmOptionsParseException(Throwable cause) {
        super(cause);
    }
}