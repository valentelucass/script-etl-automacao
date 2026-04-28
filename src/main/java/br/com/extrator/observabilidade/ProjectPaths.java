package br.com.extrator.observabilidade;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Resolve caminhos canonicos do workspace, mesmo quando a aplicacao e iniciada
 * a partir de subpastas operacionais como scripts/windows.
 */
public final class ProjectPaths {
    private static final String PROP_BASE_DIR = "etl.base.dir";
    private static final String PROP_BASE_DIR_LEGACY = "ETL_BASE_DIR";
    private static final Path PROJECT_ROOT = resolverProjectRoot();

    private ProjectPaths() {
        // utility class
    }

    public static Path projectRoot() {
        return PROJECT_ROOT;
    }

    public static Path resolveFromProjectRoot(final String first, final String... more) {
        return PROJECT_ROOT.resolve(Path.of(first, more)).toAbsolutePath().normalize();
    }

    public static Path resolveRuntimePath(final String first, final String... more) {
        return resolveFromProjectRoot("runtime").resolve(Path.of(first, more)).toAbsolutePath().normalize();
    }

    public static Path resolveFlexible(final String rawPath) {
        if (rawPath == null || rawPath.isBlank()) {
            return PROJECT_ROOT;
        }
        final Path configured = Path.of(rawPath.trim());
        if (configured.isAbsolute()) {
            return configured.normalize();
        }
        return PROJECT_ROOT.resolve(configured).toAbsolutePath().normalize();
    }

    private static Path resolverProjectRoot() {
        final Optional<Path> override = resolverOverrideConfigurado();
        if (override.isPresent()) {
            return override.get();
        }

        final Path currentDir = Path.of(System.getProperty("user.dir", ".")).toAbsolutePath().normalize();
        Path cursor = currentDir;
        while (cursor != null) {
            if (Files.isRegularFile(cursor.resolve("pom.xml"))) {
                return cursor;
            }
            cursor = cursor.getParent();
        }
        return currentDir;
    }

    private static Optional<Path> resolverOverrideConfigurado() {
        return resolverCaminho(System.getProperty(PROP_BASE_DIR))
            .or(() -> resolverCaminho(System.getProperty(PROP_BASE_DIR_LEGACY)))
            .or(() -> resolverCaminho(System.getenv(PROP_BASE_DIR_LEGACY)))
            .or(() -> resolverCaminho(System.getenv(PROP_BASE_DIR)));
    }

    private static Optional<Path> resolverCaminho(final String rawPath) {
        if (rawPath == null || rawPath.isBlank()) {
            return Optional.empty();
        }
        final Path path = Path.of(rawPath.trim());
        if (path.isAbsolute()) {
            return Optional.of(path.normalize());
        }
        return Optional.of(
            Path.of(System.getProperty("user.dir", "."))
                .resolve(path)
                .toAbsolutePath()
                .normalize()
        );
    }
}
