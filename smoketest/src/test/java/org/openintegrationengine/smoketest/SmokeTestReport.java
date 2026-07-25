// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: 2026 Mitch Gaffigan

package org.openintegrationengine.smoketest;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.UniqueId;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.TestPlan;

/**
 * Writes a JUnit XML report in which each test is named after the fixture that
 * produced it.
 *
 * <p>The platform's own legacy XML writer names dynamic tests from their unique id, so
 * a fixture failure arrives in CI as {@code fixtures()[2][1][3]} — unidentifiable
 * without opening the log. This listener uses the display names instead, so the
 * report reads {@code 110-hl7-no-op/01-hl7-no-op/01-adt-a01}, matching the directory
 * the fixture lives in.
 *
 * <p>Registered through {@code META-INF/services}, so the JUnit Platform picks it up
 * automatically. The output path comes from the {@code oie.reportFile} system
 * property; without it the listener does nothing.
 */
public class SmokeTestReport implements TestExecutionListener {

    private final Map<String, Long> startedAt = new ConcurrentHashMap<>();
    private final List<Case> cases = new ArrayList<>();
    private TestPlan testPlan;
    private long suiteStartedAt;

    private record Case(String classname, String name, double seconds, String failureMessage,
            String failureDetail, String skipReason) {
    }

    @Override
    public void testPlanExecutionStarted(TestPlan testPlan) {
        this.testPlan = testPlan;
        this.suiteStartedAt = System.nanoTime();
    }

    @Override
    public void executionStarted(TestIdentifier identifier) {
        if (identifier.isTest()) {
            startedAt.put(identifier.getUniqueId(), System.nanoTime());
        }
    }

    @Override
    public void executionSkipped(TestIdentifier identifier, String reason) {
        if (identifier.isTest()) {
            add(identifier, 0, null, null, reason == null ? "skipped" : reason);
        }
    }

    @Override
    public void executionFinished(TestIdentifier identifier, TestExecutionResult result) {
        if (!identifier.isTest()) {
            return;
        }

        Long started = startedAt.remove(identifier.getUniqueId());
        double seconds = started == null ? 0 : (System.nanoTime() - started) / 1_000_000_000.0;

        switch (result.getStatus()) {
            case SUCCESSFUL -> add(identifier, seconds, null, null, null);
            // An aborted test is a failed assumption, which is a skip, not a failure.
            case ABORTED -> add(identifier, seconds, null, null,
                    result.getThrowable().map(Throwable::getMessage).orElse("aborted"));
            case FAILED -> {
                Optional<Throwable> throwable = result.getThrowable();
                add(identifier, seconds,
                        throwable.map(t -> t.getMessage() == null ? t.toString() : t.getMessage())
                                .orElse("Test failed"),
                        throwable.map(SmokeTestReport::stackTrace).orElse(null),
                        null);
            }
        }
    }

    @Override
    public void testPlanExecutionFinished(TestPlan testPlan) {
        String target = System.getProperty("oie.reportFile");
        if (target == null || target.isBlank()) {
            return;
        }
        write(Path.of(target), (System.nanoTime() - suiteStartedAt) / 1_000_000_000.0);
    }

    private synchronized void add(TestIdentifier identifier, double seconds, String failureMessage,
            String failureDetail, String skipReason) {
        List<String> path = fixturePath(identifier);
        String name;
        String classname;
        if (path.isEmpty()) {
            // A plain @Test rather than a fixture-driven dynamic test.
            classname = identifier.getSource().map(Object::toString).orElse("smoketest");
            name = identifier.getDisplayName();
        } else {
            name = String.join("/", path);
            classname = path.size() == 1 ? "smoketest" : String.join(".", path.subList(0, path.size() - 1));
        }
        cases.add(new Case(classname, name, seconds, failureMessage, failureDetail, skipReason));
    }

    /**
     * Display names of the dynamic nodes enclosing this test, outermost first. Only
     * dynamic nodes are included, which drops the engine, class and factory-method
     * levels and leaves exactly the fixture directories.
     */
    private List<String> fixturePath(TestIdentifier identifier) {
        Deque<String> path = new ArrayDeque<>();
        TestIdentifier current = identifier;
        while (current != null && isDynamic(current)) {
            path.addFirst(current.getDisplayName());
            current = testPlan == null ? null : testPlan.getParent(current).orElse(null);
        }
        return new ArrayList<>(path);
    }

    private static boolean isDynamic(TestIdentifier identifier) {
        return UniqueId.parse(identifier.getUniqueId()).getLastSegment().getType().startsWith("dynamic-");
    }

    private void write(Path target, double suiteSeconds) {
        long failures = cases.stream().filter(c -> c.failureMessage() != null).count();
        long skipped = cases.stream().filter(c -> c.skipReason() != null).count();

        try {
            Files.createDirectories(target.toAbsolutePath().getParent());
            try (Writer writer = Files.newBufferedWriter(target, StandardCharsets.UTF_8)) {
                writer.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
                writer.write(String.format(
                        "<testsuite name=\"%s\" tests=\"%d\" failures=\"%d\" errors=\"0\" skipped=\"%d\" time=\"%.3f\">%n",
                        escape(System.getProperty("oie.configuration", "smoketest")),
                        cases.size(), failures, skipped, suiteSeconds));
                for (Case testCase : cases) {
                    writer.write(String.format("  <testcase classname=\"%s\" name=\"%s\" time=\"%.3f\">%n",
                            escape(testCase.classname()), escape(testCase.name()), testCase.seconds()));
                    if (testCase.failureMessage() != null) {
                        writer.write(String.format("    <failure message=\"%s\">%s</failure>%n",
                                escape(testCase.failureMessage()),
                                escape(testCase.failureDetail() == null
                                        ? testCase.failureMessage() : testCase.failureDetail())));
                    } else if (testCase.skipReason() != null) {
                        writer.write(String.format("    <skipped message=\"%s\"/>%n",
                                escape(testCase.skipReason())));
                    }
                    writer.write("  </testcase>\n");
                }
                writer.write("</testsuite>\n");
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Could not write the test report to " + target, e);
        }
    }

    private static String stackTrace(Throwable throwable) {
        var out = new java.io.StringWriter();
        throwable.printStackTrace(new java.io.PrintWriter(out));
        return out.toString();
    }

    /** Escapes for both attribute and element content, and strips characters XML forbids. */
    private static String escape(String value) {
        StringBuilder escaped = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '&' -> escaped.append("&amp;");
                case '<' -> escaped.append("&lt;");
                case '>' -> escaped.append("&gt;");
                case '"' -> escaped.append("&quot;");
                case '\'' -> escaped.append("&apos;");
                default -> {
                    if (character == '\t' || character == '\n' || character == '\r' || character >= 0x20) {
                        escaped.append(character);
                    }
                }
            }
        }
        return escaped.toString();
    }
}
