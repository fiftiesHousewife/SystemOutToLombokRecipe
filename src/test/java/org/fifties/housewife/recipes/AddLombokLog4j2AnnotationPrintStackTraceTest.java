package org.fifties.housewife.recipes;

import org.junit.jupiter.api.Test;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.TypeValidation;

import static org.openrewrite.java.Assertions.java;

class AddLombokLog4j2AnnotationPrintStackTraceTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new AddLombokLog4j2Annotation())
            .afterTypeValidationOptions(TypeValidation.none());
    }

    @Test
    void addsLog4j2AnnotationToClassWithPrintStackTrace() {
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

                        import lombok.extern.log4j.Log4j2;

                        @Log4j2
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
    void addsLog4j2AnnotationToClassWithMultiplePrintStackTraces() {
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

                        import lombok.extern.log4j.Log4j2;

                        @Log4j2
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
