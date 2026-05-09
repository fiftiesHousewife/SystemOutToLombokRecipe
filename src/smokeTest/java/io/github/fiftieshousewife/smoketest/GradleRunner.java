package io.github.fiftieshousewife.smoketest;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Runs {@code ./gradlew <tasks>} in a scaffolded smoke project. Forces the
 * daemon's JAVA_HOME to a JDK 21 install (the project's wrapper is pinned to
 * Gradle 8.14.3, which can't host JDK 25). Captures combined stdout/stderr to
 * a per-cell log under {@code build/smoke-tmp/<cell>/run.log} so failures
 * surface with the actual Gradle output, not just an exit code.
 */
final class GradleRunner {

    private static final long TIMEOUT_SECONDS = 600;

    private final Path projectDir;
    private final SmokeTestConfig config;

    GradleRunner(final Path projectDir, final SmokeTestConfig config) {
        this.projectDir = projectDir;
        this.config = config;
    }

    Result run(final String... gradleTasks) {
        final List<String> command = buildCommand(gradleTasks);
        final ProcessBuilder builder = new ProcessBuilder(command)
                .directory(projectDir.toFile())
                .redirectErrorStream(true);
        builder.environment().put("JAVA_HOME", config.jdk21Home().toString());

        try {
            final Process process = builder.start();
            final byte[] output = process.getInputStream().readAllBytes();
            final boolean finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new IllegalStateException(
                        "Gradle process timed out after " + TIMEOUT_SECONDS + "s in " + projectDir);
            }
            final Path log = projectDir.resolve("run.log");
            Files.write(log, output);
            return new Result(process.exitValue(), new String(output, StandardCharsets.UTF_8), log);
        } catch (final IOException e) {
            throw new UncheckedIOException("Failed to launch gradlew in " + projectDir, e);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted waiting for gradlew", e);
        }
    }

    private List<String> buildCommand(final String[] gradleTasks) {
        final List<String> command = new ArrayList<>();
        command.add(projectDir.resolve("gradlew").toString());
        command.add("--no-daemon");
        command.add("--stacktrace");
        command.add("-Dorg.gradle.java.home=" + config.jdk21Home());
        for (final String task : gradleTasks) {
            command.add(task);
        }
        return command;
    }

    record Result(int exitCode, String output, Path logFile) {
        boolean succeeded() {
            return exitCode == 0;
        }
    }
}
