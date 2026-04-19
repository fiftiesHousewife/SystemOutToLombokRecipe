package io.github.fiftieshousewife.recipes;

import org.junit.jupiter.api.Test;

import static io.github.fiftieshousewife.recipes.LogCallTemplate.*;
import static org.assertj.core.api.Assertions.assertThat;

class LogCallTemplateTest {

    @Test
    void logLevel_returnsInfoForNonError() {
        assertThat(logLevel(false)).isEqualTo("info");
    }

    @Test
    void logLevel_returnsErrorForError() {
        assertThat(logLevel(true)).isEqualTo("error");
    }

    @Test
    void escape_escapesBackslashes() {
        assertThat(escape("C:\\path\\to\\file")).isEqualTo("C:\\\\path\\\\to\\\\file");
    }

    @Test
    void escape_escapesQuotes() {
        assertThat(escape("Say \"hello\"")).isEqualTo("Say \\\"hello\\\"");
    }

    @Test
    void escape_escapesBoth() {
        assertThat(escape("Path: \"C:\\test\"")).isEqualTo("Path: \\\"C:\\\\test\\\"");
    }

    @Test
    void argsOnly_singleArg() {
        assertThat(argsOnly(1, false)).isEqualTo("log.info(#{any()})");
    }

    @Test
    void argsOnly_multipleArgs() {
        assertThat(argsOnly(3, false)).isEqualTo("log.info(#{any()}, #{any()}, #{any()})");
    }

    @Test
    void argsOnly_errorLevel() {
        assertThat(argsOnly(2, true)).isEqualTo("log.error(#{any()}, #{any()})");
    }

    @Test
    void parameterized_noArgs() {
        assertThat(parameterized("Message", 0, false)).isEqualTo("log.info(\"Message\")");
    }

    @Test
    void parameterized_withArgs() {
        assertThat(parameterized("Value: {}", 1, false)).isEqualTo("log.info(\"Value: {}\", #{any()})");
    }

    @Test
    void parameterized_errorWithMultipleArgs() {
        assertThat(parameterized("x={}, y={}", 2, true))
                .isEqualTo("log.error(\"x={}, y={}\", #{any()}, #{any()})");
    }
}
