package io.github.fiftieshousewife.cleanlogging;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JulToSlf4jVisitorMethodTest {

    @Test
    void isJulImportMatchesJulLogger() {
        assertThat(JulToSlf4jVisitor.isJulImport("java.util.logging.Logger")).isTrue();
    }

    @Test
    void isJulImportMatchesJulLevel() {
        assertThat(JulToSlf4jVisitor.isJulImport("java.util.logging.Level")).isTrue();
    }

    @Test
    void isJulImportRejectsSlf4jLogger() {
        assertThat(JulToSlf4jVisitor.isJulImport("org.slf4j.Logger")).isFalse();
    }

    @Test
    void isJulImportRejectsLog4jLogger() {
        assertThat(JulToSlf4jVisitor.isJulImport("org.apache.logging.log4j.Logger")).isFalse();
    }

    @Test
    void isJulImportRejectsNull() {
        assertThat(JulToSlf4jVisitor.isJulImport(null)).isFalse();
    }

    @Test
    void isJulImportRejectsEmptyString() {
        assertThat(JulToSlf4jVisitor.isJulImport("")).isFalse();
    }
}
