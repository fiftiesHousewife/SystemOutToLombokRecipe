package io.github.fiftieshousewife.cleanlogging.matrix;

import org.openrewrite.Tree;
import org.openrewrite.gradle.marker.GradleProject;

import java.util.Collections;

final class MatrixTestSupport {

    private MatrixTestSupport() {
    }

    static final String CATALOG_STUB = """
            [versions]
            lombok = "1.18.44"

            [libraries]
            lombok = { module = "org.projectlombok:lombok", version.ref = "lombok" }
            """;

    static final String PROPERTIES_STUB = """
            log4jVersion=2.25.4
            lombokVersion=1.18.44
            """;

    static GradleProject gradleProjectMarker(final String path) {
        return GradleProject.builder()
                .id(Tree.randomId())
                .group("com.example")
                .name(path.equals(":") ? "root" : path.replace(":", ""))
                .version("0.1.0")
                .path(path)
                .plugins(Collections.emptyList())
                .mavenRepositories(Collections.emptyList())
                .mavenPluginRepositories(Collections.emptyList())
                .nameToConfiguration(Collections.emptyMap())
                .build();
    }
}
