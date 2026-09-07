package dev.harpia.source;

import static java.nio.charset.CodingErrorAction.REPORT;

import dev.harpia.diag.DiagnosticCollector;
import dev.harpia.diag.ErrorCodes;
import dev.harpia.diag.SourceRef;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/** A source document decoded and normalized into Harpia's canonical in-memory representation. */
public record SourceFile(String relativePath, String content) {

    private static final char BYTE_ORDER_MARK = '\uFEFF';

    public SourceFile {
        Objects.requireNonNull(relativePath, "relativePath");
        Objects.requireNonNull(content, "content");
        if (relativePath.isBlank() || relativePath.indexOf('\\') >= 0) {
            throw new IllegalArgumentException("relativePath must be a non-blank '/' path");
        }
        if (content.indexOf('\r') >= 0) {
            throw new IllegalArgumentException("content must use canonical LF line endings");
        }
        if (!content.isEmpty() && content.charAt(0) == BYTE_ORDER_MARK) {
            throw new IllegalArgumentException("content must not contain a leading UTF-8 BOM");
        }
    }

    /**
     * Reads one regular source file without following a symlink. Expected input and I/O failures
     * become diagnostics so the compiler can continue inspecting independent files.
     */
    public static Optional<SourceFile> read(
            Path projectRoot, Path sourcePath, DiagnosticCollector diagnostics) {
        Objects.requireNonNull(projectRoot, "projectRoot");
        Objects.requireNonNull(sourcePath, "sourcePath");
        Objects.requireNonNull(diagnostics, "diagnostics");

        Path root = projectRoot.toAbsolutePath().normalize();
        Path source = sourcePath.toAbsolutePath().normalize();
        String displayPath = displayPath(root, source);

        if (!source.startsWith(root)) {
            diagnostics.error(
                    ErrorCodes.IO_PATH_ESCAPE,
                    "source path escapes the project root: " + sourcePath,
                    SourceRef.file(displayPath));
            return Optional.empty();
        }
        if (Files.isSymbolicLink(source)) {
            diagnostics.warning(
                    ErrorCodes.IO_SYMLINK_IGNORED,
                    "symbolic link in specs was ignored",
                    SourceRef.file(displayPath));
            return Optional.empty();
        }

        try {
            byte[] bytes = Files.readAllBytes(source);
            String decoded = decodeUtf8(bytes);
            if (!decoded.isEmpty() && decoded.charAt(0) == BYTE_ORDER_MARK) {
                decoded = decoded.substring(1);
            }
            String normalized = decoded.replace("\r\n", "\n").replace('\r', '\n');
            return Optional.of(new SourceFile(displayPath, normalized));
        } catch (CharacterCodingException exception) {
            diagnostics.error(
                    ErrorCodes.IO_NOT_UTF8,
                    "source file is not valid UTF-8",
                    SourceRef.file(displayPath));
        } catch (IOException exception) {
            diagnostics.error(
                    ErrorCodes.IO_FAILURE,
                    "could not read source file: " + exception.getMessage(),
                    SourceRef.file(displayPath));
        }
        return Optional.empty();
    }

    private static String decodeUtf8(byte[] bytes) throws CharacterCodingException {
        return StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(REPORT)
                .onUnmappableCharacter(REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString();
    }

    static String displayPath(Path projectRoot, Path path) {
        Path normalizedRoot = projectRoot.toAbsolutePath().normalize();
        Path normalizedPath = path.toAbsolutePath().normalize();
        String value = normalizedPath.startsWith(normalizedRoot)
                ? normalizedRoot.relativize(normalizedPath).toString()
                : normalizedPath.toString();
        return value.replace('\\', '/');
    }
}
