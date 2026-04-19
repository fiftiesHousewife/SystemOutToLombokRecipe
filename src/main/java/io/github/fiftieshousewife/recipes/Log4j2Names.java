package io.github.fiftieshousewife.recipes;

/**
 * Fully-qualified type names referenced across the recipes. Centralised so
 * a rename (unlikely) or a typo fix stays confined to one file.
 */
final class Log4j2Names {

    static final String LOMBOK_LOG4J2 = "lombok.extern.log4j.Log4j2";
    static final String LOG4J2_LOGGER = "org.apache.logging.log4j.Logger";
    static final String LOG4J2_LOG_MANAGER = "org.apache.logging.log4j.LogManager";

    private Log4j2Names() {
    }
}
