# Pre-release smoke test

Run this checklist before tagging and publishing a new version. It exercises each top-level recipe against a fresh Gradle project and confirms the transformed code still compiles — the thing the unit tests alone can't prove.

Takes ~10 minutes.

> **Coverage note.** Single-module dependency-management behaviour (catalog
> seeding, `AddDependency` configuration ordering) is now also covered by the
> `integrationTest` source set, which uses `withToolingApi()` and resolves a
> real `GradleProject` marker against Maven Central. That verifies what the
> RewriteTest matrix can't — but it only models a single `forProjectDirectory`
> call, so the multi-module and `includeBuild` shapes in §2a remain
> bootstrap-only.

## 1. Build the local jar

```bash
./gradlew clean build jar
```

Confirm `build/libs/system-out-to-lombok-log4j-<version>.jar` exists and `./gradlew test` shows all tests green.

## 2. Smoke-test each top-level recipe variant

For each variant listed below:

1. Wipe and bootstrap a fresh throwaway project:

   ```bash
   TEST=/tmp/smoke-$(date +%s) && mkdir -p "$TEST/gradle/wrapper" "$TEST/src/main/java/com/example"
   cp gradle/wrapper/gradle-wrapper.* "$TEST/gradle/wrapper/"
   cp gradlew gradlew.bat "$TEST/" && chmod +x "$TEST/gradlew"
   echo 'rootProject.name = "smoke"' > "$TEST/settings.gradle.kts"
   ```

2. Write a minimal `libs.versions.toml`, a `build.gradle.kts` pointing at the local jar with the chosen `activeRecipe(...)`, and a source file exercising the inputs the recipe targets (System.out calls, a manual `LogManager.getLogger(...)` field, etc. — see the variant-specific fixtures below).

3. `cd "$TEST" && ./gradlew rewriteDryRun` — inspect `build/reports/rewrite/rewrite.patch`. Every change should look intentional. If the diff is empty when it shouldn't be, or looks wrong, stop and diagnose before publishing.

4. `./gradlew rewriteRun` to apply, then `./gradlew compileJava` to confirm the result still compiles with the new dependencies.

### Variants to test

Catalog handling is auto-detected post-0.5: each non-`NoDeps` recipe checks whether `gradle/libs.versions.toml` is present and adds inline declarations or seeds the catalog accordingly. So instead of separate `Catalog` variants, run each non-`NoDeps` recipe **twice** — once with an empty `gradle/libs.versions.toml` present, once without.

| Recipe | Fixture | Catalog axis |
| --- | --- | --- |
| `io.github.fiftieshousewife.SystemOutToSlf4jRecipe` | Greeting.java | with + without |
| `io.github.fiftieshousewife.SystemOutToSlf4jRecipeNoDeps` | Greeting.java | n/a (no dep management) |
| `io.github.fiftieshousewife.ConvertManualLoggerToSlf4jRecipe` | OrderService.java | with + without |
| `io.github.fiftieshousewife.ConvertManualLoggerToSlf4jRecipeNoDeps` | OrderService.java | n/a |
| `io.github.fiftieshousewife.MigrateToSlf4jRecipe` | Greeting.java + OrderService.java | with + without |
| `io.github.fiftieshousewife.MigrateToSlf4jRecipeNoDeps` | Greeting.java + OrderService.java | n/a |

For `*NoDeps` variants the throwaway project must declare Lombok / SLF4J / log4j2 itself before `compileJava` will pass — otherwise the rewrite produces uncompilable Java.

### Fixtures

Minimal `System.out` source (`src/main/java/com/example/Greeting.java`):

```java
package com.example;

public class Greeting {
    public void say(String name) {
        System.out.println("Hello, " + name);
    }

    public void fail(Exception e) {
        e.printStackTrace();
    }
}
```

Minimal manual-Log4j2 source for the conversion recipes (`src/main/java/com/example/OrderService.java`):

```java
package com.example;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class OrderService {
    private static final Logger logger = LogManager.getLogger(OrderService.class);

    public void placeOrder(String id) { logger.info("Placing " + id); }
}
```

## 2a. Project-shape matrix (post-0.5)

**Automated by `./gradlew smokeTest`** — Templates A–F below run as cells in
`src/smokeTest/java/io/github/fiftieshousewife/smoketest/ProjectShapeSmokeTest.java`,
covered by the same publish gate as §2. The procedure below is kept as reference
for adding new templates and for manual inspection when a cell fails.

The single-module bootstrap in §2 covers only one corner of the shape matrix.
The §2a templates extend it to multi-module, build-logic-as-subproject, and
composite-build topologies in both Kotlin and Groovy DSL. Each one is
`rewriteDryRun` + `rewriteRun` + `compileJava` — same cycle as §2.

### Template A — multi-module Kotlin DSL

```bash
TEST=/tmp/smoke-multi-kotlin-$(date +%s)
mkdir -p "$TEST/gradle/wrapper" "$TEST/app/src/main/java/com/example" "$TEST/lib/src/main/java/com/example"
cp gradle/wrapper/gradle-wrapper.* "$TEST/gradle/wrapper/"
cp gradlew gradlew.bat "$TEST/" && chmod +x "$TEST/gradlew"
cat > "$TEST/settings.gradle.kts" <<'EOF'
rootProject.name = "smoke-multi"
include("app", "lib")
EOF
# Root build.gradle.kts points at the local recipe jar with the chosen activeRecipe(...)
# app/build.gradle.kts + lib/build.gradle.kts both declare the java plugin.
# Drop a System.out-using class in each module's src/main/java/com/example.
```

Apply the recipe at the root — every subproject's Java + `build.gradle.kts`
should transform, deps resolve, and `./gradlew :app:compileJava :lib:compileJava`
should pass.

### Template B — multi-module Groovy DSL

Same shape as Template A but with `settings.gradle`, `app/build.gradle`,
`lib/build.gradle` in Groovy DSL. Dependency lines use the paren-less command
form: `compileOnly 'org.projectlombok:lombok:1.18.44'`. After the recipe runs,
dependency declarations should rewrite to `compileOnly libs.lombok` — the
paren-less form preserved (regression check for the Phase B1 fix).

### Template C — Kotlin with `build-logic` subproject (`include`)

```bash
TEST=/tmp/smoke-buildlogic-kotlin-$(date +%s)
mkdir -p "$TEST/gradle/wrapper" \
         "$TEST/app/src/main/java/com/example" \
         "$TEST/build-logic"
cp gradle/wrapper/gradle-wrapper.* "$TEST/gradle/wrapper/"
cp gradlew gradlew.bat "$TEST/" && chmod +x "$TEST/gradlew"
# settings.gradle.kts: include("app", "build-logic")
# gradle/libs.versions.toml: empty [versions]/[libraries] tables — recipe will populate
# build.gradle.kts (root): rewrite plugin + activeRecipe + subprojects { apply(plugin = "java") }
# app/build.gradle.kts, build-logic/build.gradle.kts: inline Lombok deps
```

With `include`, build-logic is a regular subproject. Gradle's catalog
resolution uses the single root `gradle/libs.versions.toml`. One
`./gradlew rewriteRun` at the root rewrites every subproject's build file,
seeds the root catalog, and transforms any Java sources.

### Template D — Groovy with `build-logic` subproject (`include`)

Same layout as Template C but with `settings.gradle`, `build.gradle`,
`app/build.gradle`, `build-logic/build.gradle` in Groovy DSL and paren-less
dependency declarations. Rewritten output should produce `compileOnly
libs.lombok` — paren-less form preserved.

### Template E — Kotlin composite build (`includeBuild`)

```bash
TEST=/tmp/smoke-composite-kotlin-$(date +%s)
mkdir -p "$TEST/gradle/wrapper" \
         "$TEST/src/main/java/com/example" \
         "$TEST/build-logic/gradle/wrapper" \
         "$TEST/build-logic/gradle"
cp gradle/wrapper/gradle-wrapper.* "$TEST/gradle/wrapper/" "$TEST/build-logic/gradle/wrapper/"
cp gradlew gradlew.bat "$TEST/" "$TEST/build-logic/"
chmod +x "$TEST/gradlew" "$TEST/build-logic/gradlew"
# settings.gradle.kts: includeBuild("build-logic")
# build-logic/settings.gradle.kts: rootProject.name = "build-logic"
# EACH build has its own gradle/libs.versions.toml and its own build.gradle.kts
# EACH build applies the rewrite plugin and activates the recipe
```

With `includeBuild`, build-logic is a separate Gradle build. The outer
rewrite pass does NOT reach into it. Run the recipe in **each build**:

```bash
cd "$TEST" && ./gradlew rewriteRun               # outer build
cd "$TEST/build-logic" && ./gradlew rewriteRun   # included build
```

Each build's own `gradle/libs.versions.toml` gets seeded; each build's own
build file is rewritten to use the local catalog.

### Template F — Groovy composite build (`includeBuild`)

Same structure as Template E but with `settings.gradle`, `build.gradle`,
and `build-logic/build.gradle` in Groovy DSL. Same two-pass rewrite.
Idiomatic paren-less form preserved in both builds.

### Expected outcomes

| Template | Rewrite invocations | Java rewritten | Build file rewritten | `compileJava` green |
| --- | --- | --- | --- | --- |
| A | 1× at root | ✓ both modules | ✓ both modules | ✓ |
| B | 1× at root | ✓ both modules | ✓ both modules (paren-less preserved) | ✓ |
| C | 1× at root | ✓ app | ✓ app + build-logic | ✓ |
| D | 1× at root | ✓ app | ✓ app + build-logic (paren-less) | ✓ |
| E | 2× (outer + `build-logic`) | ✓ outer | ✓ both builds, each via its own catalog | ✓ |
| F | 2× (outer + `build-logic`) | ✓ outer | ✓ both builds (paren-less) | ✓ |

If any cell doesn't match this table, stop and diagnose — that's a regression
on the project-shape matrix BACKLOG item.

## 3. Publish to Maven Local and resolve via coordinates

**Structurally automated by §2 + §2a + `RecipeResolutionSmokeTest`** — every
smoke cell (`SmokeTest`, `ProjectShapeSmokeTest`, `RecipeResolutionSmokeTest`)
already (a) `dependsOn(publishToMavenLocal)`, (b) scaffolds projects that
resolve the recipe via Maven coordinates from `mavenLocal()` rather than via
`files(...)`, and (c) walks the dry-run / apply / compile cycle (or, in the
resolution-only case, `dependencies --configuration rewrite`). The procedure
below is kept as the manual fallback if you want to inspect the resolved
artifact by hand.

```bash
./gradlew publishToMavenLocal
```

Point a fresh smoke project at the published artifact (via coordinates, not a file path) to confirm the POM and Gradle module metadata are correct:

```kotlin
repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    rewrite("io.github.fiftieshousewife:system-out-to-lombok-log4j:<version>")
}
```

Repeat the dry-run / apply / compile cycle for at least the default and catalog variants.

## 4. Check coverage and static analysis

```bash
./gradlew jacocoTestReport spotbugsMain spotbugsTest
```

- JaCoCo totals shouldn't drop meaningfully from the last release (baseline: ~92% instruction, 100% method).
- SpotBugs should have zero findings.

## 5. Verify the dependency graph is current

```bash
./gradlew dependencyUpdates
```

If a new OpenRewrite patch is out, consider bumping before the release.

### Refresh the pinned Lombok and log4j2 versions

The recipe pins `Lombok` and `log4j2-api` / `log4j2-core` in `src/main/resources/META-INF/rewrite/system-out-to-lombok.yml`. These aren't build dependencies of this project, so `dependencyUpdates` won't flag them — check Maven Central by hand:

```bash
# Latest stable Lombok
curl -s https://repo1.maven.org/maven2/org/projectlombok/lombok/maven-metadata.xml \
  | grep -oE '<release>[^<]+</release>'

# Latest stable log4j2 (filter out beta/rc)
curl -s https://repo1.maven.org/maven2/org/apache/logging/log4j/log4j-api/maven-metadata.xml \
  | grep -oE '<version>[0-9]+\.[0-9]+\.[0-9]+</version>' | tail -1
```

Update every `version:` line in the YAML and every `versionValue:` in the `AddVersionCatalogEntry` blocks to match. Re-run the smoke tests afterwards.

## 6. Only then

- Bump `version` in `build.gradle.kts` from `x.y-SNAPSHOT` to `x.y`.
- Commit, push, `./gradlew publishAndReleaseToMavenCentral`, tag `v<version>`, push the tag.
