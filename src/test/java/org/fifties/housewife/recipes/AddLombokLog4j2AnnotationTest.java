package org.fifties.housewife.recipes;

import org.junit.jupiter.api.Test;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.TypeValidation;

import static org.openrewrite.java.Assertions.java;

class AddLombokLog4j2AnnotationTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new AddLombokLog4j2Annotation())
            .afterTypeValidationOptions(TypeValidation.none());
    }

    @Test
    void addsLog4j2AnnotationToClassWithSystemOut() {
        rewriteRun(
                java(
                        """
                        package com.example;

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
                                System.out.println("Hello World");
                            }
                        }
                        """
                )
        );
    }

    @Test
    void addsLog4j2AnnotationToClassWithMultipleSystemOut() {
        rewriteRun(
                java(
                        """
                        package com.example;

                        public class MyClass {
                            public void method1() {
                                System.out.println("First");
                            }

                            public void method2() {
                                System.out.println("Second");
                            }
                        }
                        """,
                        """
                        package com.example;

                        import lombok.extern.log4j.Log4j2;

                        @Log4j2
                        public class MyClass {
                            public void method1() {
                                System.out.println("First");
                            }

                            public void method2() {
                                System.out.println("Second");
                            }
                        }
                        """
                )
        );
    }

    @Test
    void doesNotAddAnnotationToClassWithoutSystemOut() {
        rewriteRun(
                java(
                        """
                        package com.example;

                        public class MyClass {
                            public void doSomething() {
                                String message = "Hello World";
                            }
                        }
                        """
                )
        );
    }

    @Test
    void doesNotAddAnnotationIfAlreadyHasLog4j2() {
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
                        """
                )
        );
    }

    @Test
    void addsLog4j2AnnotationForSystemErr() {
        rewriteRun(
                java(
                        """
                        package com.example;

                        public class MyClass {
                            public void doSomething() {
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
                                System.err.println("Error message");
                            }
                        }
                        """
                )
        );
    }

    @Test
    void addsLog4j2AnnotationForSystemOutPrintf() {
        rewriteRun(
                java(
                        """
                        package com.example;

                        public class MyClass {
                            public void doSomething() {
                                System.out.printf("Value: %d%n", 42);
                            }
                        }
                        """,
                        """
                        package com.example;

                        import lombok.extern.log4j.Log4j2;

                        @Log4j2
                        public class MyClass {
                            public void doSomething() {
                                System.out.printf("Value: %d%n", 42);
                            }
                        }
                        """
                )
        );
    }
}
