// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: 2026 Mitch Gaffigan

package org.openintegrationengine.smoketest;

import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.junit.jupiter.api.DynamicContainer.dynamicContainer;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicNode;
import org.junit.jupiter.api.TestFactory;

import com.mirth.connect.donkey.model.message.ConnectorMessage;
import com.mirth.connect.donkey.model.message.Message;
import com.mirth.connect.donkey.model.message.Status;

/**
 * Runs every fixture under {@code ci/tests} against a live server.
 *
 * <p>The fixture tree maps directly onto the dynamic-test tree, so the JUnit report mirrors
 * the directory layout: a container per test case, a container per channel, and inside it
 * the deploy step, one test per message in lexicographic order, and the undeploy step.
 *
 * <p>Fixtures are pure data. A test that needs real logic is an ordinary {@code @Test} in
 * this package using {@link OieServer}; see ci/README.md.
 */
class FixtureSmokeTest {

    /** Statuses that mean the server has not finished with the message yet. */
    private static final List<Status> PENDING_STATUSES = List.of(Status.PENDING, Status.QUEUED);

    private static OieServer server;

    @BeforeAll
    static void connect() {
        server = OieServer.connect();
    }

    @AfterAll
    static void disconnect() {
        if (server != null) {
            server.close();
        }
    }

    @TestFactory
    Stream<DynamicNode> fixtures() {
        List<Fixtures.TestCase> testCases = Fixtures.discover(HarnessConfig.TESTS_ROOT, HarnessConfig.CONFIGURATION);
        if (testCases.isEmpty()) {
            throw new AssertionError("No fixtures discovered under " + HarnessConfig.TESTS_ROOT.toAbsolutePath()
                    + " for configuration '" + HarnessConfig.CONFIGURATION + "'");
        }
        return testCases.stream().map(testCase -> dynamicContainer(testCase.name(),
                testCase.channels().stream().map(FixtureSmokeTest::channelNode)));
    }

    /**
     * Deploy, message, undeploy for one channel. The channel id is only known once deploy
     * succeeds, so it is threaded through a holder that the later nodes read; if deploy
     * failed they abort rather than pile extra failures onto one root cause.
     */
    private static DynamicNode channelNode(Fixtures.ChannelFixture channel) {
        String[] channelId = new String[1];
        List<DynamicNode> nodes = new ArrayList<>();

        nodes.add(dynamicTest("deploy", () -> channelId[0] = server.deployChannel(channel.channelXml())));

        for (Fixtures.MessageFixture message : channel.messages()) {
            nodes.add(dynamicTest(message.name(), () -> {
                assumeTrue(channelId[0] != null, "channel was not deployed");
                runMessage(channelId[0], message);
            }));
        }

        nodes.add(dynamicTest("undeploy", () -> {
            assumeTrue(channelId[0] != null, "channel was not deployed");
            server.removeChannel(channelId[0]);
        }));

        return dynamicContainer(channel.name(), nodes);
    }

    /**
     * Submits the fixture's source payload, then polls until the fixture's assertions hold.
     * Messages are written asynchronously, so an early poll can legitimately fail; only a
     * failure that persists once the message is terminal is a real failure.
     */
    private static void runMessage(String channelId, Fixtures.MessageFixture fixture) throws Exception {
        String source = Files.readString(fixture.source(), StandardCharsets.UTF_8);
        Map<String, Object> sourceMap = MessageAssertions.loadSourceMap(fixture.sourceMap());
        long messageId = server.submitMessage(channelId, source, sourceMap);

        long deadline = System.nanoTime() + HarnessConfig.TIMEOUT.toNanos();
        AssertionError lastFailure = null;
        Message lastMessage = null;

        while (System.nanoTime() < deadline) {
            Message message = server.fetchMessage(channelId, messageId);
            if (message != null) {
                lastMessage = message;
                try {
                    MessageAssertions.assertAll(fixture.dir(), message);
                    return;
                } catch (AssertionError e) {
                    lastFailure = e;
                    if (isTerminal(message)) {
                        break;
                    }
                }
            }
            Thread.sleep(500);
        }

        if (lastFailure != null) {
            throw new AssertionError(fixture.dir() + " failed: " + lastFailure.getMessage()
                    + "\n\n" + describe(lastMessage), lastFailure);
        }
        throw new AssertionError("Timed out after " + HarnessConfig.TIMEOUT.toSeconds() + "s waiting for message "
                + messageId + " for fixture " + fixture.dir() + "\n\n" + describe(lastMessage));
    }

    /** True once the server has finished processing and no connector is still pending. */
    private static boolean isTerminal(Message message) {
        if (!message.isProcessed()) {
            return false;
        }
        Map<Integer, ConnectorMessage> connectorMessages = message.getConnectorMessages();
        if (connectorMessages == null) {
            return false;
        }
        return connectorMessages.values().stream()
                .noneMatch(connectorMessage -> PENDING_STATUSES.contains(connectorMessage.getStatus()));
    }

    /** Renders the message the way a fixture author needs to see it to fix a mismatch. */
    private static String describe(Message message) {
        if (message == null) {
            return "No message was retrieved from the server.";
        }

        StringBuilder detail = new StringBuilder("Actual message ").append(message.getMessageId())
                .append(" (processed=").append(message.isProcessed()).append("):");
        Map<Integer, ConnectorMessage> connectorMessages = message.getConnectorMessages();
        if (connectorMessages == null) {
            return detail.append("\n  <no connector messages>").toString();
        }

        connectorMessages.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            ConnectorMessage connectorMessage = entry.getValue();
            detail.append("\n  [").append(entry.getKey()).append("] ").append(connectorMessage.getConnectorName())
                    .append(" status=").append(connectorMessage.getStatus());
            appendContent(detail, "raw", connectorMessage.getRaw());
            appendContent(detail, "transformed", connectorMessage.getTransformed());
            appendContent(detail, "encoded", connectorMessage.getEncoded());
            appendContent(detail, "sent", connectorMessage.getSent());
            appendContent(detail, "response", connectorMessage.getResponse());
            detail.append("\n        connectorMap=").append(connectorMessage.getConnectorMap())
                    .append("\n        metaDataMap=").append(connectorMessage.getMetaDataMap());
            if (connectorMessage.getProcessingError() != null) {
                detail.append("\n        processingError=").append(connectorMessage.getProcessingError());
            }
        });
        return detail.toString();
    }

    private static void appendContent(StringBuilder detail, String label,
            com.mirth.connect.donkey.model.message.MessageContent content) {
        if (content != null && content.getContent() != null) {
            detail.append("\n        ").append(label).append('=').append(quote(content.getContent()));
        }
    }

    private static String quote(String content) {
        String escaped = content.replace("\r\n", "\\n").replace("\r", "\\n").replace("\n", "\\n");
        return "\"" + (escaped.length() > 2000 ? escaped.substring(0, 2000) + "\"... (truncated)" : escaped + "\"");
    }
}
