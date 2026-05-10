package io.github.fiftieshousewife.cleanlogging;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openrewrite.config.Environment;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.TypeValidation;

import static org.openrewrite.gradle.Assertions.buildGradleKts;
import static org.openrewrite.gradle.toolingapi.Assertions.withToolingApi;
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.test.SourceSpecs.text;

@DisplayName("SystemOutToSlf4jRecipe end-to-end with real GradleProject marker")
class SystemOutToSlf4jRecipeIntegrationTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.beforeRecipe(withToolingApi())
                .typeValidationOptions(TypeValidation.none())
                .recipe(Environment.builder()
                        .scanRuntimeClasspath("io.github.fiftieshousewife")
                        .build()
                        .activateRecipes("io.github.fiftieshousewife.cleanlogging.SystemOutToSlf4jRecipe"));
    }

    @Test
    @DisplayName("System.out only: java migrated, deps added, log4j2 configs seeded")
    void systemOutOnly_endToEnd() {
        rewriteRun(
                java(
                        """
                                package com.example;

                                public class Greeter {
                                    public void hello() {
                                        System.out.println("hi");
                                    }
                                }
                                """,
                        """
                                package com.example;

                                import lombok.extern.slf4j.Slf4j;

                                @Slf4j
                                public class Greeter {
                                    public void hello() {
                                        log.info("hi");
                                    }
                                }
                                """
                ),
                buildGradleKts(
                        """
                                plugins {
                                    java
                                }
                                repositories {
                                    mavenCentral()
                                }
                                """,
                        """
                                plugins {
                                    java
                                }
                                repositories {
                                    mavenCentral()
                                }

                                dependencies {
                                    annotationProcessor("org.projectlombok:lombok:1.18.46")

                                    compileOnly("org.projectlombok:lombok:1.18.46")

                                    implementation("org.slf4j:slf4j-api:2.0.17")

                                    runtimeOnly("org.apache.logging.log4j:log4j-core:2.25.4")
                                    runtimeOnly("org.apache.logging.log4j:log4j-slf4j2-impl:2.25.4")
                                }
                                """
                ),
                text(null, MAIN_LOG4J2_XML, spec -> spec.path("src/main/resources/log4j2.xml")),
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
