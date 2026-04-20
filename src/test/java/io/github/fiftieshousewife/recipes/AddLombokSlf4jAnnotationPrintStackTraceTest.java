package io.github.fiftieshousewife.recipes;

import org.junit.jupiter.api.Test;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.TypeValidation;

import static org.openrewrite.java.Assertions.java;

class AddLombokSlf4jAnnotationPrintStackTraceTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new AddLombokSlf4jAnnotation())
            .afterTypeValidationOptions(TypeValidation.none());
    }

    @Test
    void addsSlf4jAnnotationToClassWithPrintStackTrace() {
        rewriteRun(
                java(
                        """
                        package com.example;

                        public class MyClass {
                            public void handleError() {
                                try {
                                    riskyOperation();
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }

                            private void riskyOperation() throws Exception {
                                throw new Exception();
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
                                    e.printStackTrace();
                                }
                            }

                            private void riskyOperation() throws Exception {
                                throw new Exception();
                            }
                        }
                        """
                )
        );
    }

    @Test
    void addsSlf4jAnnotationToClassWithMultiplePrintStackTraces() {
        rewriteRun(
                java(
                        """
                        package com.example;

                        public class MyClass {
                            public void method1() {
                                try {
                                    operation();
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }

                            public void method2() {
                                try {
                                    operation();
                                } catch (RuntimeException ex) {
                                    ex.printStackTrace();
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
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }

                            public void method2() {
                                try {
                                    operation();
                                } catch (RuntimeException ex) {
                                    ex.printStackTrace();
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
}
