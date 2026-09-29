/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 * 
 * http://www.mirthcorp.com
 * 
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.util;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import javax.xml.transform.TransformerException;

import org.junit.Test;
import org.xml.sax.SAXParseException;

public class ErrorMessageBuilderTest {

    /** The wording Xerces uses, which is all that reaches us through the wrapping below. */
    private static final String PARSER_WORDING = "DOCTYPE is disallowed when the feature \"http://apache.org/xml/features/disallow-doctype-decl\" set to true.";

    /**
     * A refused DOCTYPE is the engine enforcing a security control, but the parser only reports its
     * own feature flag. The operator-facing error has to say why the message was rejected.
     */
    @Test
    public void doctypeRefusalIsExplained() {
        Throwable wrapped = new TransformerException(new SAXParseException(PARSER_WORDING, null, null, 1, 22));

        String error = ErrorMessageBuilder.buildErrorMessage("Transformer", "Error evaluating transformer", wrapped);

        assertTrue(error, error.contains(MirthXmlUtil.DOCTYPE_REFUSED));
    }

    /** Only a DOCTYPE refusal gets the explanation; every other failure is left alone. */
    @Test
    public void unrelatedFailuresAreNotExplained() {
        String error = ErrorMessageBuilder.buildErrorMessage("Transformer", "Error evaluating transformer", new IllegalStateException("something else"));

        assertFalse(error, error.contains(MirthXmlUtil.DOCTYPE_REFUSED));
    }
}
