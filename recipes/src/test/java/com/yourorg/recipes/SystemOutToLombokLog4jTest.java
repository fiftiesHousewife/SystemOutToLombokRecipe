package com.yourorg.recipes;

import org.junit.jupiter.api.Test;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class SystemOutToLombokLog4jTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new SystemOutToLombokLog4j())
                .parser(JavaParser.fromJavaVersion()
                        .classpath("lombok", "log4j-api"));
    }

    @Test
    void convertsSimplePrintlnToLogInfo() {
        rewriteRun(
                java(
                        """
                        package com.example;

                        import lombok.extern.log4j.Log4j2;

                        @Log4j2
                        public class MyClass {
                            public void doSomething() {
                                System.out.println("Hello World");
                            }
                        }
                        """,
                        """
                        package com.example;

                        import lombok.extern.log4j.Log4j2;

                        @Log4j2
                        public class MyClass {
                            public void doSomething() {
                                log.info("Hello World");
                            }
                        }
                        """
                )
        );
    }

    @Test
    void convertsSystemErrToLogError() {
        rewriteRun(
                java(
                        """
                        package com.example;

                        import lombok.extern.log4j.Log4j2;

                        @Log4j2
                        public class MyClass {
                            public void doSomething() {
                                System.err.println("Error occurred");
                            }
                        }
                        """,
                        """
                        package com.example;

                        import lombok.extern.log4j.Log4j2;

                        @Log4j2
                        public class MyClass {
                            public void doSomething() {
                                log.error("Error occurred");
                            }
                        }
                        """
                )
        );
    }

    @Test
    void convertsPrintlnWithConcatenationToParameterized() {
        rewriteRun(
                java(
                        """
                        package com.example;

                        import lombok.extern.log4j.Log4j2;

                        @Log4j2
                        public class MyClass {
                            public void doSomething(int value) {
                                System.out.println("Value: " + value);
                            }
                        }
                        """,
                        """
                        package com.example;

                        import lombok.extern.log4j.Log4j2;

                        @Log4j2
                        public class MyClass {
                            public void doSomething(int value) {
                                log.info("Value: {}", value);
                            }
                        }
                        """
                )
        );
    }

    @Test
    void convertsComplexConcatenation() {
        rewriteRun(
                java(
                        """
                        package com.example;

                        import lombok.extern.log4j.Log4j2;

                        @Log4j2
                        public class MyClass {
                            public void doSomething(int x, int y) {
                                System.out.println("x = " + x + ", y = " + y);
                            }
                        }
                        """,
                        """
                        package com.example;

                        import lombok.extern.log4j.Log4j2;

                        @Log4j2
                        public class MyClass {
                            public void doSomething(int x, int y) {
                                log.info("x = {}, y = {}", x, y);
                            }
                        }
                        """
                )
        );
    }

    @Test
    void convertsPrintfToLogInfo() {
        rewriteRun(
                java(
                        """
                        package com.example;

                        import lombok.extern.log4j.Log4j2;

                        @Log4j2
                        public class MyClass {
                            public void doSomething(int value) {
                                System.out.printf("Value: %d%n", value);
                            }
                        }
                        """,
                        """
                        package com.example;

                        import lombok.extern.log4j.Log4j2;

                        @Log4j2
                        public class MyClass {
                            public void doSomething(int value) {
                                log.info("Value: %d%n", value);
                            }
                        }
                        """
                )
        );
    }

    @Test
    void convertsMultiplePrintStatements() {
        rewriteRun(
                java(
                        """
                        package com.example;

                        import lombok.extern.log4j.Log4j2;

                        @Log4j2
                        public class MyClass {
                            public void doSomething() {
                                System.out.println("First message");
                                System.out.println("Second message");
                                System.err.println("Error message");
                            }
                        }
                        """,
                        """
                        package com.example;

                        import lombok.extern.log4j.Log4j2;

                        @Log4j2
                        public class MyClass {
                            public void doSomething() {
                                log.info("First message");
                                log.info("Second message");
                                log.error("Error message");
                            }
                        }
                        """
                )
        );
    }

    @Test
    void convertsEmptyPrintln() {
        rewriteRun(
                java(
                        """
                        package com.example;

                        import lombok.extern.log4j.Log4j2;

                        @Log4j2
                        public class MyClass {
                            public void doSomething() {
                                System.out.println();
                            }
                        }
                        """,
                        """
                        package com.example;

                        import lombok.extern.log4j.Log4j2;

                        @Log4j2
                        public class MyClass {
                            public void doSomething() {
                                log.info("");
                            }
                        }
                        """
                )
        );
    }

    @Test
    void convertsPrintWithArgument() {
        rewriteRun(
                java(
                        """
                        package com.example;

                        import lombok.extern.log4j.Log4j2;

                        @Log4j2
                        public class MyClass {
                            public void doSomething() {
                                System.out.print("Message");
                            }
                        }
                        """,
                        """
                        package com.example;

                        import lombok.extern.log4j.Log4j2;

                        @Log4j2
                        public class MyClass {
                            public void doSomething() {
                                log.info("Message");
                            }
                        }
                        """
                )
        );
    }

    @Test
    void doesNotConvertNonSystemOutMethods() {
        rewriteRun(
                java(
                        """
                        package com.example;

                        import lombok.extern.log4j.Log4j2;

                        @Log4j2
                        public class MyClass {
                            public void println(String msg) {
                                // Custom println method
                            }

                            public void doSomething() {
                                println("test");
                            }
                        }
                        """
                )
        );
    }
}
