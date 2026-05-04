package io.github.fiftieshousewife.recipes;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JulToSlf4jVisitorMethodTest {

    @Test
    void isJulLoggerFqn_matchesJulLogger() {
        assertThat(JulToSlf4jVisitor.isJulLoggerFqn("java.util.logging.Logger")).isTrue();
    }

    @Test
    void isJulLoggerFqn_rejectsSlf4jLogger() {
        assertThat(JulToSlf4jVisitor.isJulLoggerFqn("org.slf4j.Logger")).isFalse();
    }

    @Test
    void isJulLoggerFqn_rejectsLog4jLogger() {
        assertThat(JulToSlf4jVisitor.isJulLoggerFqn("org.apache.logging.log4j.Logger")).isFalse();
    }

    @Test
    void isJulLoggerFqn_rejectsNull() {
        assertThat(JulToSlf4jVisitor.isJulLoggerFqn(null)).isFalse();
    }

    @Test
    void isJulLoggerFqn_rejectsEmptyString() {
        assertThat(JulToSlf4jVisitor.isJulLoggerFqn("")).isFalse();
    }

    @Test
    void isJulLoggerFqn_rejectsPartialMatch() {
        assertThat(JulToSlf4jVisitor.isJulLoggerFqn("java.util.logging.Handler")).isFalse();
    }
}
