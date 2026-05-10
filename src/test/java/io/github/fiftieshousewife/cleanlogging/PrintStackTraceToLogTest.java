package io.github.fiftieshousewife.cleanlogging;

import org.junit.jupiter.api.Test;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.TypeValidation;

import static org.openrewrite.java.Assertions.java;

class PrintStackTraceToLogTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new PrintStackTraceToLog(false))
                .parser(JavaParser.fromJavaVersion())
                .typeValidationOptions(TypeValidation.none());
    }

    @Test
    void convertsPrintStackTraceToLogError() {
        rewriteRun(
                java(
                        """
                        package com.example;

                        import lombok.extern.slf4j.Slf4j;

                        @Slf4j
                        public class MyClass {
                            public void handleError() {
                                try {
                                    riskyOperation();
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }

                            private void riskyOperation() throws Exception {
                                throw new Exception("error");
                            }
                        }
                        """,
                        """
                        package com.example;

                        import lombok.extern.slf4j.Slf4j;

                        @Slf4j
                        public class MyClass {
                            public void handleError() {
                                try {
                                    riskyOperation();
                                } catch (Exception e) {
                                    log.error("Exception occurred", e);
                                }
                            }

                            private void riskyOperation() throws Exception {
                                throw new Exception("error");
                            }
                        }
                        """
                )
        );
    }

    @Test
    void convertsMultiplePrintStackTraces() {
        rewriteRun(
                java(
                        """
                        package com.example;

                        import lombok.extern.slf4j.Slf4j;

                        @Slf4j
                        public class MyClass {
                            public void method1() {
                                try {
                                    operation();
                                } catch (Exception ex) {
                                    ex.printStackTrace();
                                }
                            }

                            public void method2() {
                                try {
                                    operation();
                                } catch (RuntimeException error) {
                                    error.printStackTrace();
                                }
                            }

                            private void operation() {
                                throw new RuntimeException();
                            }
                        }
                        """,
                        """
                        package com.example;

                        import lombok.extern.slf4j.Slf4j;

                        @Slf4j
                        public class MyClass {
                            public void method1() {
                                try {
                                    operation();
                                } catch (Exception ex) {
                                    log.error("Exception occurred", ex);
                                }
                            }

                            public void method2() {
                                try {
                                    operation();
                                } catch (RuntimeException error) {
                                    log.error("Exception occurred", error);
                                }
                            }

                            private void operation() {
                                throw new RuntimeException();
                            }
                        }
                        """
                )
        );
    }

    @Test
    void handlesDifferentExceptionTypes() {
        rewriteRun(
                java(
                        """
                        package com.example;

                        import lombok.extern.slf4j.Slf4j;
                        import java.io.IOException;

                        @Slf4j
                        public class MyClass {
                            public void handleIO() {
                                try {
                                    readFile();
                                } catch (IOException ioe) {
                                    ioe.printStackTrace();
                                }
                            }

                            private void readFile() throws IOException {
                                throw new IOException("file not found");
                            }
                        }
                        """,
                        """
                        package com.example;

                        import lombok.extern.slf4j.Slf4j;
                        import java.io.IOException;

                        @Slf4j
                        public class MyClass {
                            public void handleIO() {
                                try {
                                    readFile();
                                } catch (IOException ioe) {
                                    log.error("Exception occurred", ioe);
                                }
                            }

                            private void readFile() throws IOException {
                                throw new IOException("file not found");
                            }
                        }
                        """
                )
        );
    }

    @Test
    void convertsPrintStackTraceWithSystemErr() {
        rewriteRun(
                java(
                        """
                        package com.example;

                        import lombok.extern.slf4j.Slf4j;

                        @Slf4j
                        public class MyClass {
                            public void handleError() {
                                try {
                                    riskyOperation();
                                } catch (Exception e) {
                                    e.printStackTrace(System.err);
                                }
                            }

                            private void riskyOperation() throws Exception {
                                throw new Exception("error");
                            }
                        }
                        """,
                        """
                        package com.example;

                        import lombok.extern.slf4j.Slf4j;

                        @Slf4j
                        public class MyClass {
                            public void handleError() {
                                try {
                                    riskyOperation();
                                } catch (Exception e) {
                                    log.error("Exception occurred", e);
                                }
                            }

                            private void riskyOperation() throws Exception {
                                throw new Exception("error");
                            }
                        }
                        """
                )
        );
    }

    @Test
    void convertsPrintStackTraceWithSystemOut() {
        rewriteRun(
                java(
                        """
                        package com.example;

                        import lombok.extern.slf4j.Slf4j;

                        @Slf4j
                        public class MyClass {
                            public void handleError() {
                                try {
                                    riskyOperation();
                                } catch (Exception e) {
                                    e.printStackTrace(System.out);
                                }
                            }

                            private void riskyOperation() throws Exception {
                                throw new Exception("error");
                            }
                        }
                        """,
                        """
                        package com.example;

                        import lombok.extern.slf4j.Slf4j;

                        @Slf4j
                        public class MyClass {
                            public void handleError() {
                                try {
                                    riskyOperation();
                                } catch (Exception e) {
                                    log.error("Exception occurred", e);
                                }
                            }

                            private void riskyOperation() throws Exception {
                                throw new Exception("error");
                            }
                        }
                        """
                )
        );
    }

    @Test
    void doesNotConvertNonPrintStackTraceMethods() {
        rewriteRun(
                java(
                        """
                        package com.example;

                        import lombok.extern.slf4j.Slf4j;

                        @Slf4j
                        public class MyClass {
                            public void test() {
                                Exception e = new Exception();
                                String message = e.getMessage();
                            }
                        }
                        """
                )
        );
    }
}
