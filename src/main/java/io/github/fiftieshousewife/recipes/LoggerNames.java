package io.github.fiftieshousewife.recipes;

/**
 * Fully-qualified type names referenced across the recipes. Centralised so
 * a rename (unlikely) or a typo fix stays confined to one file.
 */
final class LoggerNames {

    static final String LOMBOK_SLF4J = "lombok.extern.slf4j.Slf4j";
    static final String SLF4J_LOGGER = "org.slf4j.Logger";
    static final String SLF4J_LOGGER_FACTORY = "org.slf4j.LoggerFactory";

    static final String LOG4J2_LOGGER = "org.apache.logging.log4j.Logger";
    static final String LOG4J2_LOG_MANAGER = "org.apache.logging.log4j.LogManager";

    private LoggerNames() {
    }
}
