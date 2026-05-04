package io.github.fiftieshousewife.recipes;

import java.util.Arrays;

enum LombokLoggingAnnotation {

    SLF4J("Slf4j", "lombok.extern.slf4j.Slf4j"),
    LOG4J("Log4j", "lombok.extern.log4j.Log4j"),
    LOG4J2("Log4j2", "lombok.extern.log4j.Log4j2"),
    JAVA_LOG("Log", "lombok.extern.java.Log"),
    COMMONS_LOG("CommonsLog", "lombok.extern.apachecommons.CommonsLog"),
    FLOGGER("Flogger", "lombok.extern.flogger.Flogger"),
    JBOSS_LOG("JBossLog", "lombok.extern.jbosslog.JBossLog"),
    CUSTOM_LOG("CustomLog", "lombok.CustomLog");

    private final String simpleName;
    private final String qualifiedName;

    LombokLoggingAnnotation(final String simpleName, final String qualifiedName) {
        this.simpleName = simpleName;
        this.qualifiedName = qualifiedName;
    }

    String fqn() {
        return qualifiedName;
    }

    static boolean matchesSimpleName(final String name) {
        return Arrays.stream(values()).anyMatch(a -> a.simpleName.equals(name));
    }

    static boolean matchesTypeFragment(final String typeName) {
        return Arrays.stream(values()).anyMatch(a -> typeName.contains(a.qualifiedName));
    }
}
