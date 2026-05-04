package io.github.fiftieshousewife.recipes;

enum LoggerNames {

    LOG4J2_LOGGER("org.apache.logging.log4j.Logger"),
    LOG4J2_LOG_MANAGER("org.apache.logging.log4j.LogManager"),
    JUL_LOGGER("java.util.logging.Logger"),
    SLF4J_LOGGER("org.slf4j.Logger"),
    SLF4J_LOGGER_FACTORY("org.slf4j.LoggerFactory");

    private final String qualifiedName;

    LoggerNames(final String qualifiedName) {
        this.qualifiedName = qualifiedName;
    }

    String fqn() {
        return qualifiedName;
    }
}
