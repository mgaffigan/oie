package com.mirth.connect.plugins.nodejsexec;

/**
 * Exception thrown when Node.js executor operations fail.
 */
public class ExecutorException extends Exception {
    private static final long serialVersionUID = 1L;
    
    private final int errorCode;

    public ExecutorException(String message) {
        this(0, message);
    }

    public ExecutorException(String message, Throwable cause) {
        this(0, message, cause);
    }

    public ExecutorException(int errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ExecutorException(int errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public int getErrorCode() {
        return errorCode;
    }
}
