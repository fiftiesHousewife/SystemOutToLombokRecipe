# Pre-release smoke test

Run this checklist before tagging and publishing a new version. It exercises each top-level recipe against a fresh Gradle project and confirms the transformed code still compiles — the thing the unit tests alone can't prove.

Takes ~10 minutes.

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

- `io.github.fiftieshousewife.SystemOutToLombokLog4jRecipe` — default (inline deps)
- `io.github.fiftieshousewife.SystemOutToLombokLog4jRecipeNoDeps` — manual dep management
- `io.github.fiftieshousewife.SystemOutToLombokLog4jRecipeCatalog` — version catalog
- `io.github.fiftieshousewife.ConvertManualLog4j2ToLombokRecipe` — migrate hand-rolled Log4j2 loggers
- `io.github.fiftieshousewife.ConvertManualLog4j2ToLombokRecipeCatalog` — same, with catalog

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

Minimal manual-Log4j2 source for the conversion recipes:

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

The single-module bootstrap in §2 covers only one corner of the shape matrix.
For anything touching version catalogs, Groovy DSL support, or
composite/`buildSrc` conventions, run the relevant template below. Each one is
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

### Template C — composite `build-logic` (Kotlin)

```bash
TEST=/tmp/smoke-buildlogic-kotlin-$(date +%s)
mkdir -p "$TEST/gradle/wrapper" \
         "$TEST/build-logic/src/main/kotlin" \
         "$TEST/build-logic/gradle" \
         "$TEST/src/main/java/com/example"
cp gradle/wrapper/gradle-wrapper.* "$TEST/gradle/wrapper/"
cp gradlew gradlew.bat "$TEST/" && chmod +x "$TEST/gradlew"
# settings.gradle.kts: includeBuild("build-logic")
# build-logic/settings.gradle.kts: declares the `build-logic` included build
# build-logic/build.gradle.kts: plugins { `kotlin-dsl` }; dependencies { implementation("org.projectlombok:lombok:1.18.44") }
# build-logic/gradle/libs.versions.toml: minimal catalog with [versions]/[libraries] for lombok
# Root build.gradle.kts applies the convention plugin + the recipe's active list
```

Recipe must rewrite the `implementation("org.projectlombok:lombok:…")` line in
`build-logic/build.gradle.kts` to `implementation(libs.lombok)` using the
`build-logic/gradle/libs.versions.toml` catalog.

### Template D — composite `build-logic` (Groovy)

Same as Template C but with `build-logic/build.gradle` (Groovy DSL) and
`id 'groovy-gradle-plugin'`. The rewrite should produce
`implementation libs.lombok` — paren-less. Also confirms the nested catalog is
discovered by path suffix.

### Expected outcomes

| Template | Java rewritten | Build file rewritten | `compileJava` green |
| --- | --- | --- | --- |
| A | ✓ both modules | ✓ both modules | ✓ |
| B | ✓ both modules | ✓ both modules (paren-less preserved) | ✓ |
| C | ✓ | ✓ convention plugin | ✓ |
| D | ✓ | ✓ convention plugin (paren-less) | ✓ |

If any cell doesn't match this table, stop and diagnose — that's a regression
on the project-shape matrix BACKLOG item.

## 3. Publish to Maven Local and resolve via coordinates

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
