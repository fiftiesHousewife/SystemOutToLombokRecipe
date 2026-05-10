package io.github.fiftieshousewife.cleanlogging;

import org.jspecify.annotations.NullMarked;

import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toUnmodifiableMap;

/**
 * One of the seven {@code java.util.logging.Logger} level-named methods, with
 * its mapping to the SLF4J method that {@code @Slf4j} exposes and to the
 * SLF4J {@code is*Enabled} predicate that pairs with each level.
 *
 * <p>Replaces three parallel lookup tables that previously lived as
 * {@code static final} fields on {@link JulToSlf4j} (the JUL method-name
 * set, the JUL → SLF4J method map, and the JUL level-constant → SLF4J
 * {@code is*Enabled} map).
 */
@NullMarked
enum JulLevel {

    SEVERE("severe", "error", "isErrorEnabled"),
    WARNING("warning", "warn", "isWarnEnabled"),
    INFO("info", "info", "isInfoEnabled"),
    CONFIG("config", "debug", "isDebugEnabled"),
    FINE("fine", "debug", "isDebugEnabled"),
    FINER("finer", "trace", "isTraceEnabled"),
    FINEST("finest", "trace", "isTraceEnabled");

    private static final Map<String, JulLevel> BY_JUL_METHOD = Stream.of(values())
            .collect(toUnmodifiableMap(JulLevel::julMethod, identity()));

    private final String julMethod;
    private final String slf4jMethod;
    private final String slf4jIsEnabled;

    JulLevel(final String julMethod, final String slf4jMethod, final String slf4jIsEnabled) {
        this.julMethod = julMethod;
        this.slf4jMethod = slf4jMethod;
        this.slf4jIsEnabled = slf4jIsEnabled;
    }

    String julMethod() {
        return julMethod;
    }

    String slf4jMethod() {
        return slf4jMethod;
    }

    String slf4jIsEnabled() {
        return slf4jIsEnabled;
    }

    /** Look up by the JUL method name (lowercase, e.g. {@code "severe"}). */
    static Optional<JulLevel> byJulMethod(final String name) {
        return Optional.ofNullable(BY_JUL_METHOD.get(name));
    }

    /** Look up by the JUL {@code Level} constant name (uppercase, e.g. {@code "SEVERE"}). */
    static Optional<JulLevel> byLevelName(final String name) {
        try {
            return Optional.of(valueOf(name));
        } catch (final IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }
}
