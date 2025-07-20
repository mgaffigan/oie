package com.mirth.connect.server.launcher;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ParsedVmOptions {

    private static final Pattern envVariableRegex = Pattern.compile("\\$\\{([a-zA-Z_][a-zA-Z0-9_]*)\\}");
    private final List<String> vmOptions = new ArrayList<>();
    private final List<String> classpath = new ArrayList<>();
    private String javaCmdPath;

    private final HashSet<String> parsedFiles = new HashSet<>();

    /**
     * Substitutes ${VAR_NAME} patterns within a given string.
     *
     * @param s The input string.
     * @return The string with environment variables substituted.
     */
    private static String substituteEnvVars(String s) {
        if (s == null || !s.contains("${")) {
            return s;
        }

        return envVariableRegex.matcher(s).replaceAll(matchResult -> {
            String envValue = System.getenv(matchResult.group(1));
            return Matcher.quoteReplacement(Objects.requireNonNullElse(envValue, ""));
        });
    }

    /**
     * Recursively parses a vmoptions file and any files included via -include-options.
     * Accumulates JVM options, classpath segments, and the effective Java command path.
     *
     * @param filepath The path to the vmoptions file.
     * @throws VmOptionsParseException If an error occurs during parsing, file inclusion,
     *                        or if the command doesn't make sense.
     */
    public void addFile(String filepath) throws VmOptionsParseException {
        if (parsedFiles.contains(filepath)) {
            throw new VmOptionsParseException("Detected circular include for file: " + filepath);
        }
        parsedFiles.add(filepath);

        // Get the directory of the filepath to resolve relative includes
        String basePath = Paths.get(filepath).getParent().toString();

        List<String> lines;
        try {
            lines = Files.readAllLines(Paths.get(filepath));
        } catch (IOException e) {
            throw new VmOptionsParseException("Failed to read file: " + filepath, e);
        }

        for (String line : lines) {
            try {
                addOption(line, basePath);
            } catch (Exception e) {
                throw new VmOptionsParseException("Error parsing line in " + filepath + ": " + line, e);
            }
        }
    }

    private void addOption(String line, String basePath) throws VmOptionsParseException {
        line = line.trim();
        if (line.isEmpty() || line.startsWith("#")) {
            return;
        }

        // Split to the first and rest parts
        String[] parts = line.split(" ", 2);
        String first = parts[0];
        String rest = parts.length > 1 ? parts[1].trim() : "";

        switch (first) {
            case "-include-options":
                rest = Paths.get(basePath, substituteEnvVars(rest)).toString();
                addFile(rest);
                break;
            case "-java-cmd":
                throw new VmOptionsParseException("-java-cmd is not supported");
            case "-classpath":
                if (rest.isEmpty()) {
                    throw new VmOptionsParseException("Missing classpath value");
                }
                classpath.clear();
                classpath.add(substituteEnvVars(rest));
                break;
            case "-classpath/a":
                if (rest.isEmpty()) {
                    throw new VmOptionsParseException("Missing classpath value");
                }
                classpath.add(substituteEnvVars(rest));
                break;
            case "-classpath/p":
                if (rest.isEmpty()) {
                    throw new VmOptionsParseException("Missing classpath value");
                }
                classpath.add(0, substituteEnvVars(rest));
                break;
            default:
                vmOptions.add(substituteEnvVars(line));
                break;
        }
    }

    public List<String> getResultantArgs() {
        List<String> command = new ArrayList<>();
        command.addAll(vmOptions);
        command.add("-cp " + String.join(System.getProperty("path.separator"), classpath));
        return command;
    }
}