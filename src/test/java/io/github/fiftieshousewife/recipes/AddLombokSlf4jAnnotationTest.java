package io.github.fiftieshousewife.recipes;

import org.junit.jupiter.api.Test;
import org.openrewrite.Tree;
import org.openrewrite.java.marker.JavaSourceSet;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.TypeValidation;

import java.util.Collections;

import static org.openrewrite.java.Assertions.java;

class AddLombokSlf4jAnnotationTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new AddLombokSlf4jAnnotation())
            .afterTypeValidationOptions(TypeValidation.none());
    }

    private static JavaSourceSet sourceSetWith(String... fqTypes) {
        return new JavaSourceSet(Tree.randomId(), "main",
                java.util.Arrays.stream(fqTypes)
                        .<JavaType.FullyQualified>map(JavaType.ShallowClass::build)
                        .toList(),
                Collections.emptyMap());
    }

    @Test
    void addsSlf4jAnnotationToClassWithSystemOut() {
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

                        import lombok.extern.slf4j.Slf4j;

                        @Slf4j
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
    void addsSlf4jAnnotationToClassWithMultipleSystemOut() {
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

                        import lombok.extern.slf4j.Slf4j;

                        @Slf4j
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

                        import lombok.extern.slf4j.Slf4j;

                        @Slf4j
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
    void addsSlf4jAnnotationForSystemErr() {
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

                        import lombok.extern.slf4j.Slf4j;

                        @Slf4j
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
    void addsSlf4jAnnotationForJulUsage() {
        rewriteRun(
                java(
                        """
                        package com.example;

                        import java.util.logging.Logger;

                        public class MyClass {
                            private static final Logger logger = Logger.getLogger(MyClass.class.getName());

                            public void doSomething() {
                                logger.info("hello");
                            }
                        }
                        """,
                        """
                        package com.example;

                        import lombok.extern.slf4j.Slf4j;

                        import java.util.logging.Logger;

                        @Slf4j
                        public class MyClass {
                            private static final Logger logger = Logger.getLogger(MyClass.class.getName());

                            public void doSomething() {
                                logger.info("hello");
                            }
                        }
                        """
                )
        );
    }

    @Test
    void doesNotAddAnnotationIfAlreadyHasSlf4j() {
        rewriteRun(
                java(
                        """
                        package com.example;

                        import lombok.extern.slf4j.Slf4j;

                        @Slf4j
                        public class MyClass {
                            public void doSomething() {
                                System.out.println("already annotated");
                            }
                        }
                        """
                )
        );
    }

    @Test
    void doesNotAddAnnotationIfClassHasExistingLogFieldButNoJul() {
        rewriteRun(
                java(
                        """
                        package com.example;

                        public class MyClass {
                            private static final String log = "custom";

                            public void doSomething() {
                                System.out.println("skip me");
                            }
                        }
                        """
                )
        );
    }

    @Test
    void skipsAnnotation_whenRequireLombokIsTrueAndClasspathLacksLombok() {
        rewriteRun(
                spec -> spec.recipe(new AddLombokSlf4jAnnotation(true))
                        .afterTypeValidationOptions(TypeValidation.none()),
                java(
                        """
                        package com.example;

                        public class MyClass {
                            public void doSomething() {
                                System.out.println("Hello World");
                            }
                        }
                        """,
                        spec -> spec.markers(sourceSetWith("java.util.List"))
                )
        );
    }

    @Test
    void addsAnnotation_whenRequireLombokIsTrueAndClasspathContainsLombok() {
        rewriteRun(
                spec -> spec.recipe(new AddLombokSlf4jAnnotation(true))
                        .afterTypeValidationOptions(TypeValidation.none()),
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

                        import lombok.extern.slf4j.Slf4j;

                        @Slf4j
                        public class MyClass {
                            public void doSomething() {
                                System.out.println("Hello World");
                            }
                        }
                        """,
                        spec -> spec.markers(sourceSetWith(LombokLoggingAnnotation.SLF4J.fqn()))
                )
        );
    }

    @Test
    void skipsAnnotation_whenRequireLombokIsTrueAndNoSourceSetMarker() {
        rewriteRun(
                spec -> spec.recipe(new AddLombokSlf4jAnnotation(true))
                        .afterTypeValidationOptions(TypeValidation.none()),
                java(
                        """
                        package com.example;

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
    void addsSlf4jAnnotationForSystemOutPrintf() {
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

                        import lombok.extern.slf4j.Slf4j;

                        @Slf4j
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
