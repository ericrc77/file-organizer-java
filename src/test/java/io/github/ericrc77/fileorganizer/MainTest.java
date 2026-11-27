package io.github.ericrc77.fileorganizer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MainTest {

    @TempDir
    Path folder;

    private final ByteArrayOutputStream out = new ByteArrayOutputStream();
    private final ByteArrayOutputStream err = new ByteArrayOutputStream();

    private int run(String... args) {
        return Main.run(args,
                new PrintStream(out, true, StandardCharsets.UTF_8),
                new PrintStream(err, true, StandardCharsets.UTF_8));
    }

    @Test
    void overwriteRequiresConfirmation() throws IOException {
        Files.writeString(folder.resolve("a.txt"), "a");

        int exit = run(folder.toString(), "--on-conflict", "overwrite");

        assertEquals(Main.EXIT_USAGE, exit);
        assertTrue(Files.exists(folder.resolve("a.txt")));
    }

    @Test
    void overwriteCanBePreviewedWithoutConfirmation() {
        Options options = Options.parse(folder.toString(), "--on-conflict", "overwrite", "--dry-run");

        assertEquals(ConflictPolicy.OVERWRITE, options.policy());
    }

    @Test
    void rejectsInvalidArguments() {
        assertThrows(IllegalArgumentException.class, () -> Options.parse());
        assertThrows(IllegalArgumentException.class, () -> Options.parse("a", "b"));
        assertThrows(IllegalArgumentException.class, () -> Options.parse("a", "--force"));
        assertThrows(IllegalArgumentException.class, () -> Options.parse("a", "--on-conflict"));
        assertThrows(IllegalArgumentException.class, () -> Options.parse("a", "--on-conflict", "merge"));
    }

    @Test
    void printsASummary() throws IOException {
        Files.writeString(folder.resolve("a.txt"), "a");

        assertEquals(Main.EXIT_OK, run(folder.toString()));
        assertTrue(out.toString(StandardCharsets.UTF_8).contains("Moved: 1, skipped: 0, failed: 0"));
    }

    @Test
    void returnsAFailureExitCode() throws IOException {
        Files.writeString(folder.resolve("Documents"), "blocks the folder");
        Files.writeString(folder.resolve("a.txt"), "a");

        assertEquals(Main.EXIT_FAILURES, run(folder.toString()));
    }
}
