package io.github.fiftieshousewife.recipes;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openrewrite.config.Environment;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.test.SourceSpecs.text;

/**
 * Verifies the contract of {@code CreateLog4j2Config}: both XML files appear
 * at the expected paths, and {@code overwriteExisting: false} is honoured so
 * a project-level customisation isn't clobbered on re-run.
 *
 * <p>No {@code withToolingApi()} here — {@code CreateTextFile} doesn't read
 * any Gradle marker, so the embedded daemon would be wasted setup time.
 */
@DisplayName("CreateLog4j2Config: file creation + overwriteExisting=false honoured")
class CreateLog4j2ConfigIntegrationTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(Environment.builder()
                .scanRuntimeClasspath("io.github.fiftieshousewife")
                .build()
                .activateRecipes("io.github.fiftieshousewife.CreateLog4j2Config"));
    }

    @Test
    @DisplayName("fresh project: both production and test config files are created")
    void freshProject_createsBothFiles() {
        rewriteRun(
                text(null, MAIN_LOG4J2_XML, spec -> spec.path("src/main/resources/log4j2.xml")),
                text(null, TEST_LOG4J2_XML, spec -> spec.path("src/test/resources/log4j2-test.xml"))
        );
    }

    @Test
    @DisplayName("idempotent: an existing log4j2.xml is left alone, the test config is still created")
    void existingProductionConfig_isLeftAlone() {
        final String customConfig = """
                <?xml version="1.0" encoding="UTF-8"?>
                <Configuration status="WARN">
                    <Appenders>
                        <Console name="OurOwn" target="SYSTEM_OUT"/>
                    </Appenders>
                    <Loggers>
                        <Root level="info">
                            <AppenderRef ref="OurOwn"/>
                        </Root>
                    </Loggers>
                </Configuration>
                """;
        rewriteRun(
                text(customConfig, spec -> spec.path("src/main/resources/log4j2.xml")),
                text(null, TEST_LOG4J2_XML, spec -> spec.path("src/test/resources/log4j2-test.xml"))
        );
    }

    private static final String MAIN_LOG4J2_XML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <Configuration status="WARN">
                <Properties>
                    <Property name="logPath">logs</Property>
                    <Property name="logFile">app</Property>
                    <Property name="pattern">%d{yyyy-MM-dd HH:mm:ss.SSS} [%t] %-5level %logger{36} - %msg%n</Property>
                </Properties>
                <Appenders>
                    <Console name="StdOut" target="SYSTEM_OUT">
                        <PatternLayout pattern="${pattern}"/>
                        <ThresholdFilter level="ERROR" onMatch="DENY" onMismatch="ACCEPT"/>
                    </Console>
                    <Console name="StdErr" target="SYSTEM_ERR">
                        <PatternLayout pattern="${pattern}"/>
                        <ThresholdFilter level="ERROR" onMatch="ACCEPT" onMismatch="DENY"/>
                    </Console>
                    <RollingFile name="RollingFile"
                                 fileName="${logPath}/${logFile}.log"
                                 filePattern="${logPath}/${logFile}-%d{yyyy-MM-dd}-%i.log.gz">
                        <PatternLayout pattern="${pattern}"/>
                        <Policies>
                            <TimeBasedTriggeringPolicy interval="1" modulate="true"/>
                            <SizeBasedTriggeringPolicy size="10 MB"/>
                        </Policies>
                        <DefaultRolloverStrategy max="10"/>
                    </RollingFile>
                </Appenders>
                <Loggers>
                    <Root level="info">
                        <AppenderRef ref="StdOut"/>
                        <AppenderRef ref="StdErr"/>
                        <AppenderRef ref="RollingFile"/>
                    </Root>
                </Loggers>
            </Configuration>
            """;

    private static final String TEST_LOG4J2_XML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <Configuration status="WARN">
                <Properties>
                    <Property name="pattern">%d{yyyy-MM-dd HH:mm:ss.SSS} [%t] %-5level %logger{36} - %msg%n</Property>
                </Properties>
                <Appenders>
                    <Console name="StdOut" target="SYSTEM_OUT">
                        <PatternLayout pattern="${pattern}"/>
                        <ThresholdFilter level="ERROR" onMatch="DENY" onMismatch="ACCEPT"/>
                    </Console>
                    <Console name="StdErr" target="SYSTEM_ERR">
                        <PatternLayout pattern="${pattern}"/>
                        <ThresholdFilter level="ERROR" onMatch="ACCEPT" onMismatch="DENY"/>
                    </Console>
                </Appenders>
                <Loggers>
                    <Root level="info">
                        <AppenderRef ref="StdOut"/>
                        <AppenderRef ref="StdErr"/>
                    </Root>
                </Loggers>
            </Configuration>
            """;
}
