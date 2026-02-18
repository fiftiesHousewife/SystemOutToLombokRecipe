# Dependency Versions

This project uses the latest stable versions of all dependencies as of February 2026.

## Version Management with TOML

All versions are managed centrally using **Gradle Version Catalog** in `gradle/libs.versions.toml`:

**Benefits:**
- ✅ Single source of truth for all versions
- ✅ Type-safe dependency accessors (e.g., `libs.lombok`, `libs.bundles.junit`)
- ✅ IDE autocomplete support
- ✅ Easy version updates in one place
- ✅ Prevents version conflicts

**Usage in build files:**
```kotlin
dependencies {
    implementation(libs.openrewrite.java)
    compileOnly(libs.lombok)
    testImplementation(libs.bundles.junit)
}
```

## Core Dependencies

| Dependency | Version | Purpose |
|------------|---------|---------|
| **OpenRewrite Gradle Plugin** | 7.26.0 | Latest OpenRewrite plugin (Feb 11, 2026) |
| **OpenRewrite BOM** | latest.release | Manages OpenRewrite library versions |
| **Lombok** | 1.18.42 | Latest Lombok (Sep 18, 2025) - Includes JDK24/25 support |
| **Log4j2 API** | 2.25.3 | Latest Log4j2 (Dec 2025/Jan 2026) - Security fix for SSL/TLS hostname verification |
| **Log4j2 Core** | 2.25.3 | Latest Log4j2 Core |
| **JUnit** | 6.0.3 | Latest JUnit 6 (Feb 15, 2026) - Requires Java 17+ |
| **AssertJ** | 3.27.6 | Latest stable AssertJ 3.x |
| **JSpecify** | 1.0.0 | Nullability annotations for IDE warnings |

## Version Notes

### OpenRewrite 7.26.0
- Released: February 11, 2026
- Compatible with Gradle 4.0+
- Includes latest recipe improvements

### Lombok 1.18.42
- Released: September 18, 2025
- JDK 24 and JDK 25 support
- JSpecify support for nullity annotations
- Adds `@lombok.Generated` by default to generated methods

### Log4j2 2.25.3
- Released: December 2025 / Early 2026
- **Security Fix**: Proper hostname verification for SSL/TLS in Socket Appender
- Previous versions (2.0-beta9 through 2.25.2) failed to perform proper hostname verification
- Requires Java 8+ (Java 17+ recommended)

### JUnit 6.0.3
- Released: February 15, 2026
- Major evolution from JUnit 5
- **Breaking Change**: Requires Java 17 and Kotlin 2.1+
- Fixed deadlock in NamespacedHierarchicalStore
- Package: org.junit.jupiter

### AssertJ 3.27.6
- Latest stable release in 3.x series
- Note: AssertJ 4.0.0-M1 milestone available but not used (pre-release)

## Build Tool Versions

| Tool | Version | Notes |
|------|---------|-------|
| **Gradle Wrapper** | 8.12 | Included in project |
| **Java Toolchain** | 17 | Configured for all modules |
| **Java Compiler (main)** | 8 | Recipe bytecode targets Java 8 for max compatibility |
| **Java Compiler (test)** | 17 | Test code uses Java 17 features (text blocks) |

## Updating Dependencies

### OpenRewrite Plugin
```kotlin
plugins {
    id("org.openrewrite.rewrite") version "7.26.0"
}
```

### Recipes Module Dependencies
```kotlin
dependencies {
    // OpenRewrite (uses BOM for version management)
    implementation(platform("org.openrewrite.recipe:rewrite-recipe-bom:latest.release"))
    implementation("org.openrewrite:rewrite-java")

    // Lombok
    compileOnly("org.projectlombok:lombok:1.18.42")
    testCompileOnly("org.projectlombok:lombok:1.18.42")

    // Log4j2
    testImplementation("org.apache.logging.log4j:log4j-api:2.25.3")
    testImplementation("org.apache.logging.log4j:log4j-core:2.25.3")

    // Testing
    testImplementation(platform("org.junit:junit-bom:6.0.3"))
    testImplementation("org.junit.jupiter:junit-jupiter-api")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
    testImplementation("org.assertj:assertj-core:3.27.6")
}
```

### YAML Recipe Dependencies
The YAML recipe uses version patterns for automatic updates:
```yaml
# Lombok - pattern will match latest 1.18.x
version: 1.18.x

# Log4j2 - pattern will match latest 2.x
version: 2.x
```

## Compatibility Matrix

| Recipe Component | Minimum Java | Recommended Java | Target Java |
|------------------|--------------|------------------|-------------|
| Recipe Code (main) | Java 8 | Java 17 | Java 8 bytecode |
| Recipe Tests | Java 17 | Java 17 | Java 17 bytecode |
| Target Projects | Java 8+ | Java 11+ | Any |

## Known Version Issues

### JUnit 6 Migration Note
JUnit 6 requires Java 17+, which is why we've configured:
- Recipes compile to Java 8 bytecode (maximum compatibility)
- Tests use Java 17 (for JUnit 6 and text blocks)

### Lombok Annotation Processing
When testing recipes that use Lombok annotations, ensure Lombok is on the test classpath:
```kotlin
testCompileOnly("org.projectlombok:lombok:1.18.42")
```

### Log4j2 Security Note
Version 2.25.3 is recommended as it fixes a critical SSL/TLS hostname verification issue (CVE-2025-68161). Earlier versions should be upgraded.

## Checking for Updates

To check for newer versions:

```bash
# Check OpenRewrite plugin
https://plugins.gradle.org/plugin/org.openrewrite.rewrite

# Check Lombok
https://projectlombok.org/changelog

# Check Log4j2
https://logging.apache.org/log4j/2.x/release-notes.html

# Check JUnit
https://junit.org/junit5/docs/current/release-notes/

# Check AssertJ
https://mvnrepository.com/artifact/org.assertj/assertj-core
```

## Version Update History

| Date | Component | Old Version | New Version | Reason |
|------|-----------|-------------|-------------|--------|
| 2026-02-18 | All | Various | Latest | Initial release with latest versions |
| 2026-02-18 | OpenRewrite | 6.25.0 | 7.26.0 | Update to latest |
| 2026-02-18 | Lombok | 1.18.34 | 1.18.42 | Update to latest with JDK 24/25 support |
| 2026-02-18 | Log4j2 | 2.23.1 | 2.25.3 | Security update for CVE-2025-68161 |
| 2026-02-18 | JUnit | 5.10.3 | 6.0.3 | Major version upgrade to JUnit 6 |
| 2026-02-18 | AssertJ | 3.24.2 | 3.27.6 | Update to latest stable 3.x |
| 2026-02-18 | Build System | Inline versions | TOML catalog | Migrate to Gradle version catalog |
| 2026-02-18 | JSpecify | N/A | 1.0.0 | Add nullability annotations (@NullMarked) |

---

**Last Updated**: February 18, 2026
**Next Review**: Quarterly or when security updates are released
