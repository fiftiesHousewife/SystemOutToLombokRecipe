package io.github.fiftieshousewife.recipes;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openrewrite.config.Environment;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.TypeValidation;

import static org.openrewrite.gradle.Assertions.buildGradleKts;
import static org.openrewrite.gradle.toolingapi.Assertions.withToolingApi;
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.test.SourceSpecs.text;

@DisplayName("MigrateToSlf4jRecipe end-to-end with real GradleProject marker")
class MigrateToSlf4jRecipeIntegrationTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.beforeRecipe(withToolingApi())
                .typeValidationOptions(TypeValidation.none())
                .parser(JavaParser.fromJavaVersion()
                        .dependsOn(
                                """
                                        package org.apache.logging.log4j;
                                        public interface Logger {
                                            void info(String msg);
                                            void error(String msg);
                                            void warn(String msg);
                                            void debug(String msg);
                                            void trace(String msg);
                                        }
                                        """,
                                """
                                        package org.apache.logging.log4j;
                                        public final class LogManager {
                                            public static Logger getLogger(Class<?> c) { return null; }
                                            public static Logger getLogger(String name) { return null; }
                                        }
                                        """))
                .recipe(Environment.builder()
                        .scanRuntimeClasspath("io.github.fiftieshousewife")
                        .build()
                        .activateRecipes("io.github.fiftieshousewife.MigrateToSlf4jRecipe"));
    }

    @Test
    @DisplayName("mixed pattern in one class: System.out + manual Log4j2 field both migrated, deps added")
    void mixedPattern_systemOutAndManualLog4j2() {
        rewriteRun(
                java(
                        """
                                package com.example;

                                import org.apache.logging.log4j.LogManager;
                                import org.apache.logging.log4j.Logger;

                                public class Mixed {
                                    private static final Logger logger = LogManager.getLogger(Mixed.class);

                                    public void existing() {
                                        logger.info("structured");
                                    }

                                    public void legacy() {
                                        System.out.println("legacy");
                                    }

                                    public void boom(Exception e) {
                                        e.printStackTrace();
                                    }
                                }
                                """,
                        """
                                package com.example;

                                import lombok.extern.slf4j.Slf4j;

                                @Slf4j
                                public class Mixed {

                                    public void existing() {
                                        log.info("structured");
                                    }

                                    public void legacy() {
                                        log.info("legacy");
                                    }

                                    public void boom(Exception e) {
                                        log.error("Exception occurred", e);
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
                                    annotationProcessor("org.projectlombok:lombok:1.18.44")

                                    compileOnly("org.projectlombok:lombok:1.18.44")

                                    implementation("org.slf4j:slf4j-api:2.0.17")

                                    runtimeOnly("org.apache.logging.log4j:log4j-core:2.25.4")
                                    runtimeOnly("org.apache.logging.log4j:log4j-slf4j2-impl:2.25.4")
                                }
                                """
                ),
                // CreateLog4j2Config seeds these. Asserting they're created at the expected
                // paths with the expected content; if the YAML changes, this test will catch it.
                text(null, MAIN_LOG4J2_XML, spec -> spec.path("src/main/resources/log4j2.xml")),
                text(null, TEST_LOG4J2_XML, spec -> spec.path("src/test/resources/log4j2-test.xml"))
        );
    }

    @Test
    @DisplayName("concatenated SLF4J calls get parameterised after conversion")
    void parameterisesConcatenatedSlf4jCallsAfterConversion() {
        rewriteRun(
                java(
                        """
                                package com.example;

                                import org.apache.logging.log4j.LogManager;
                                import org.apache.logging.log4j.Logger;

                                public class Concat {
                                    private static final Logger logger = LogManager.getLogger(Concat.class);

                                    public void greet(String userId) {
                                        logger.info("user " + userId + " logged in");
                                    }
                                }
                                """,
                        """
                                package com.example;

                                import lombok.extern.slf4j.Slf4j;

                                @Slf4j
                                public class Concat {

                                    public void greet(String userId) {
                                        log.info("user {} logged in", userId);
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
                                    annotationProcessor("org.projectlombok:lombok:1.18.44")

                                    compileOnly("org.projectlombok:lombok:1.18.44")

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
