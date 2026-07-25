// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: 2026 Mitch Gaffigan

package org.openintegrationengine.smoketest;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Filesystem discovery of the test fixtures under {@code ci/tests}. Tests are data, not
 * registrations: a test case is any directory containing
 * {@code channels/<channel>/channel.xml}, and everything runs in lexicographic order so
 * execution is deterministic without extra metadata.
 */
final class Fixtures {

    /** One test case: a directory under ci/tests holding one or more channels. */
    record TestCase(Path dir, String name, List<ChannelFixture> channels) {
    }

    /** One channel within a test case, plus the messages to push through it. */
    record ChannelFixture(Path dir, String name, Path channelXml, List<MessageFixture> messages) {
    }

    /** One message pushed into a channel, plus the assertion files beside it. */
    record MessageFixture(Path dir, String name, Path source, Path sourceMap) {
    }

    private Fixtures() {
    }

    /**
     * @param configuration compose configuration name; when blank, every test case is
     *                      returned regardless of its {@code configurations} file
     */
    static List<TestCase> discover(Path testsRoot, String configuration) {
        if (!Files.isDirectory(testsRoot)) {
            throw new IllegalStateException("Tests root does not exist: " + testsRoot.toAbsolutePath());
        }

        // testsRoot/<test>/channels/<channel>/channel.xml
        Map<Path, List<ChannelFixture>> byTestDir = new LinkedHashMap<>();
        try (Stream<Path> walk = Files.walk(testsRoot)) {
            walk.filter(path -> path.getFileName().toString().equals("channel.xml"))
                    .filter(path -> path.getParent().getParent().getFileName().toString().equals("channels"))
                    .sorted()
                    .forEach(channelXml -> {
                        Path channelDir = channelXml.getParent();
                        Path testDir = channelDir.getParent().getParent();
                        byTestDir.computeIfAbsent(testDir, key -> new ArrayList<>())
                                .add(new ChannelFixture(channelDir, channelDir.getFileName().toString(),
                                        channelXml, discoverMessages(channelDir)));
                    });
        } catch (IOException e) {
            throw new UncheckedIOException("Could not scan " + testsRoot, e);
        }

        List<TestCase> cases = new ArrayList<>();
        byTestDir.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .filter(entry -> runsInConfiguration(entry.getKey(), configuration))
                .forEach(entry -> cases.add(new TestCase(entry.getKey(),
                        entry.getKey().getFileName().toString(), entry.getValue())));
        return cases;
    }

    private static List<MessageFixture> discoverMessages(Path channelDir) {
        Path messagesDir = channelDir.resolve("messages");
        if (!Files.isDirectory(messagesDir)) {
            return List.of();
        }

        List<MessageFixture> messages = new ArrayList<>();
        try (Stream<Path> children = Files.list(messagesDir)) {
            children.filter(Files::isDirectory)
                    .sorted(Comparator.comparing(Path::getFileName))
                    .forEach(messageDir -> {
                        Path source = messageDir.resolve("source");
                        if (!Files.isRegularFile(source)) {
                            throw new IllegalStateException(
                                    "Message fixture is missing its source payload: " + messageDir);
                        }
                        Path sourceMap = messageDir.resolve("source_sourcemap.yml");
                        messages.add(new MessageFixture(messageDir, messageDir.getFileName().toString(),
                                source, Files.isRegularFile(sourceMap) ? sourceMap : null));
                    });
        } catch (IOException e) {
            throw new UncheckedIOException("Could not scan " + messagesDir, e);
        }
        return messages;
    }

    /**
     * A test directory may carry a {@code configurations} file of newline-separated
     * configuration names. A missing file means the test runs everywhere.
     */
    private static boolean runsInConfiguration(Path testDir, String configuration) {
        if (configuration == null || configuration.isBlank()) {
            return true;
        }
        Path configurations = testDir.resolve("configurations");
        if (!Files.isRegularFile(configurations)) {
            return true;
        }
        try {
            return Files.readAllLines(configurations, StandardCharsets.UTF_8).stream()
                    .map(String::trim)
                    .filter(line -> !line.isEmpty())
                    .anyMatch(configuration::equals);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + configurations, e);
        }
    }
}
