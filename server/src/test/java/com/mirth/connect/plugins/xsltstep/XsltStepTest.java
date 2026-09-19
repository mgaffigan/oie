// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: Mitch Gaffigan <mitch@gaffigan.net>
package com.mirth.connect.plugins.xsltstep;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import java.io.File;
import java.util.HashMap;

import org.apache.commons.io.FileUtils;
import org.apache.commons.text.StringEscapeUtils;
import org.junit.BeforeClass;
import org.junit.Test;

import com.mirth.connect.donkey.model.message.ConnectorMessage;
import com.mirth.connect.util.JavaScriptTestUtil;

/**
 * Covers the script {@link XsltStep} generates for a plain transformer step. The iterator form is
 * covered by FilterTransformerIterableTest#testIteratorXsltStep.
 */
public class XsltStepTest {

    /** Copies the whole source document to the result, so anything leaked shows up in the output. */
    private static final String TEXT_XSLT = "<xsl:stylesheet version=\"1.0\" xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\">"
            + "<xsl:output method=\"text\"/><xsl:template match=\"/\"><xsl:value-of select=\".\"/></xsl:template></xsl:stylesheet>";

    private static File secret;

    @BeforeClass
    public static void setup() throws Exception {
        JavaScriptTestUtil.setup();

        secret = File.createTempFile("xxe", ".txt");
        secret.deleteOnExit();
        FileUtils.write(secret, "canary", UTF_8);
    }

    /** Dropping the entity and rejecting the message are both fine; leaking the file is not. */
    @Test
    public void externalEntityInSourceIsNotResolved() throws Exception {
        String xml = "<!DOCTYPE r [ <!ENTITY xxe SYSTEM \"" + secret.toURI() + "\"> ]><r>&xxe;</r>";
        String outcome;

        try {
            outcome = transform(xml, TEXT_XSLT);
        } catch (Exception e) {
            outcome = String.valueOf(e);
        }

        assertFalse(outcome, outcome.contains("canary"));
    }

    /** Guards the payload shape: a DOCTYPE is not rejected outright, so the parser really reads it. */
    @Test
    public void internalEntityInSourceIsResolved() throws Exception {
        assertEquals("canary", transform("<!DOCTYPE r [ <!ENTITY ok \"canary\"> ]><r>&ok;</r>", TEXT_XSLT));
    }

    @Test
    public void benignDocumentIsTransformed() throws Exception {
        assertEquals("hello", transform("<?xml version=\"1.0\"?><r>hello</r>", TEXT_XSLT));
    }

    /** The step restricts the message, not the stylesheet. */
    @Test
    public void stylesheetMayImportFromAFileUri() throws Exception {
        File imported = File.createTempFile("imported", ".xsl");
        imported.deleteOnExit();
        FileUtils.write(imported, "<xsl:stylesheet version=\"1.0\" xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\">"
                + "<xsl:template match=\"/\">imported</xsl:template></xsl:stylesheet>", UTF_8);

        String xslt = "<xsl:stylesheet version=\"1.0\" xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\">"
                + "<xsl:import href=\"" + imported.toURI() + "\"/><xsl:output method=\"text\"/></xsl:stylesheet>";
        assertEquals("imported", transform("<r>hello</r>", xslt));
    }

    @Test
    public void customFactoryIsUsed() throws Exception {
        XsltStep step = step("<r>hello</r>", TEXT_XSLT);
        step.setUseCustomFactory(true);
        step.setCustomFactory("com.sun.org.apache.xalan.internal.xsltc.trax.TransformerFactoryImpl");
        assertEquals("hello", transform(step));
    }

    private static String transform(String sourceXml, String xslt) throws Exception {
        return transform(step(sourceXml, xslt));
    }

    private static XsltStep step(String sourceXml, String xslt) {
        XsltStep step = new XsltStep();
        step.setSourceXml("'" + StringEscapeUtils.escapeEcmaScript(sourceXml) + "'");
        step.setTemplate("'" + StringEscapeUtils.escapeEcmaScript(xslt) + "'");
        step.setResultVariable("xsltResult");
        return step;
    }

    private static String transform(XsltStep step) throws Exception {
        ConnectorMessage connectorMessage = new ConnectorMessage();
        connectorMessage.setMetaDataId(0);
        connectorMessage.setChannelMap(new HashMap<String, Object>());

        JavaScriptTestUtil.testTransformerStep(step, connectorMessage);

        return String.valueOf(connectorMessage.getChannelMap().get("xsltResult"));
    }
}
