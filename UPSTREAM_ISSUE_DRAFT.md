**File at:** https://github.com/openrewrite/rewrite/issues/new

**Title:**

```
AddDependency no-op against build.gradle.kts when libs.versions.toml is present, on Gradle 9.x daemon (works on Gradle 8.x)
```

**Body:**

---

## Summary

`org.openrewrite.gradle.AddDependency` makes no changes to `build.gradle.kts`
when a `gradle/libs.versions.toml` is present in the project, **only when the
embedded Gradle daemon is 9.x**. The same recipe invocation against the same
inputs produces the expected edit on Gradle 8.14.3.

The catalog branch of `AddDependency` (which inserts `libs.foo` into the
build script) appears to be the affected path — inline dependency
declarations work fine on Gradle 9.

## Reproducer

Minimal `RewriteTest`, no other recipes involved. The catalog is pre-seeded
with the entry, so the test is purely exercising `AddDependency`'s
build-script-editing path.

```java
class UpstreamReproTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.beforeRecipe(withToolingApi("9.0.0"))
                .recipe(new AddDependency(
                        "org.projectlombok", "lombok", "1.18.44",
                        null, "compileOnly", null,
                        null, null, null, Boolean.TRUE
                ));
    }

    @Test
    void catalog_present_addDependency_should_edit_build_kts() {
        rewriteRun(
                toml(
                        """
                        [versions]
                        lombok = "1.18.44"

                        [libraries]
                        lombok = { module = "org.projectlombok:lombok", version.ref = "lombok" }
                        """,
                        spec -> spec.path("gradle/libs.versions.toml")
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
                            compileOnly("org.projectlombok:lombok:1.18.44")
                        }
                        """
                )
        );
    }
}
```

Result on Gradle 9.0.0 / 9.4.1 daemon:

```
java.lang.AssertionError: Recipe was expected to make a change but made no changes.
```

Result on Gradle 8.14.3 daemon: passes.

## Bisect

`openrewrite-core` pinned to **8.81.3** throughout. JDK 21 launcher
throughout (rules out the JDK 25 / Kotlin parser issue from #6132).

| Gradle daemon | inline scenario | catalog scenario |
|---|---|---|
| 8.14.3         | pass | pass |
| 9.0.0          | pass | **no edit to build.gradle.kts** |
| 9.4.1          | pass | **no edit to build.gradle.kts** |

So the trigger is specifically: Gradle 9.x daemon **and** `libs.versions.toml`
present. Inline dependency declarations are unaffected on Gradle 9.

## Versions tested

- `openrewrite-core`: 8.81.3 (latest at time of filing). Also reproduces
  with 8.81.2. (8.81.1 and earlier reject the `-b` flag on Gradle 9; the
  `isGradle9OrLater` gate landed in 8.81.2.)
- Gradle daemon: 9.0.0, 9.4.1 (both fail), 8.14.3 (passes).
- JDK launcher: 21. (JDK 25 launcher also reproduces but adds the noise
  from #6132.)
- OS: macOS, arm64.

## Not a duplicate of #6132

#6132 was the JDK 25 launcher case — `build.gradle.kts` couldn't be parsed
because the bundled Kotlin compiler (1.9.x) didn't support JDK 25 as a
runtime. That was fixed by the K2 upgrade in #6766 (`kotlin-compiler-embeddable
2.2.0`). With that fix in place, the **inline** case now works on JDK 25 +
Gradle 9 — but the **catalog** case still fails, regardless of JDK launcher
version, whenever the daemon is Gradle 9.x. The repro above runs on JDK 21
launcher specifically to rule out any K2 / Kotlin-parsing involvement.

## Hypotheses

Either the Tooling API in Gradle 9 returns a `GradleProject` model whose
catalog metadata isn't shaped the way `AddDependency`'s catalog-detection
branch expects, or `rewrite-gradle`'s build-script editing assumes a
Gradle 8 AST shape that no longer holds. Happy to gather more diagnostic
info (e.g., dump the `GradleProject` marker under each daemon) if useful.
