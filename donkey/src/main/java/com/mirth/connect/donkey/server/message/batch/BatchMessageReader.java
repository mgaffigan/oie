/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 * 
 * http://www.mirthcorp.com
 * 
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.donkey.server.message.batch;

import java.io.Reader;
import java.io.StringReader;

public class BatchMessageReader implements BatchMessageSource {

    private Reader reader;
    private String message;

    public BatchMessageReader(Reader reader) {
        this.setReader(reader);
    }

    public BatchMessageReader(String message) {
        this.message = message;
        reader = new StringReader(message);
    }

    /**
     * The payload this reader was built from, or null when it wraps a stream. Reading the reader
     * consumes it, so this is the only way to report the payload back after a failure.
     */
    public String getMessage() {
        return message;
    }

    public Reader getReader() {
        return reader;
    }

    public void setReader(Reader reader) {
        this.reader = reader;
    }
}
