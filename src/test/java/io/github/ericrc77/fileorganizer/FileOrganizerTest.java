package io.github.ericrc77.fileorganizer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileOrganizerTest {

    @TempDir
    Path folder;

    private Path file(String relative, String content) throws IOException {
        Path path = folder.resolve(relative);
        Files.createDirectories(path.getParent());
        return Files.writeString(path, content);
    }

    private List<Result> run(ConflictPolicy policy, boolean dryRun) throws IOException {
        return new FileOrganizer(folder, policy, dryRun).organizeAll();
    }

    @Test
    void movesFilesIntoCategoryFolders() throws IOException {
        file("photo.JPG", "p");
        file("notes.txt", "n");
        file("unknown.xyz", "u");
        file("README", "r");

        List<Result> results = run(ConflictPolicy.RENAME, false);

        assertEquals(4, results.size());
        assertTrue(Files.exists(folder.resolve("Images/photo.JPG")));
        assertTrue(Files.exists(folder.resolve("Documents/notes.txt")));
        assertTrue(Files.exists(folder.resolve("Other/unknown.xyz")));
        assertTrue(Files.exists(folder.resolve("Other/README")));
    }

    @Test
    void dryRunChangesNothing() throws IOException {
        file("notes.txt", "n");

        List<Result> results = run(ConflictPolicy.RENAME, true);

        assertEquals(Result.Status.PLANNED, results.get(0).status());
        assertTrue(Files.exists(folder.resolve("notes.txt")));
        assertFalse(Files.exists(folder.resolve("Documents")));
    }

    @Test
    void renameKeepsBothFiles() throws IOException {
        file("Documents/notes.txt", "old");
        file("Documents/notes (1).txt", "older");
        file("notes.txt", "new");

        run(ConflictPolicy.RENAME, false);

        assertEquals("old", Files.readString(folder.resolve("Documents/notes.txt")));
        assertEquals("older", Files.readString(folder.resolve("Documents/notes (1).txt")));
        assertEquals("new", Files.readString(folder.resolve("Documents/notes (2).txt")));
    }

    @Test
    void dryRunPreviewsTheSameNamesAsARealRun() throws IOException {
        file("Documents/a.txt", "existing");
        file("a.txt", "first");
        file("a (1).txt", "second");

        List<Path> preview = run(ConflictPolicy.RENAME, true).stream().map(Result::target).toList();
        List<Path> real = run(ConflictPolicy.RENAME, false).stream().map(Result::target).toList();

        assertEquals(real, preview);
        assertEquals(2, preview.stream().distinct().count());
    }

    @Test
    void skipLeavesTheSourceInPlace() throws IOException {
        file("Documents/notes.txt", "old");
        file("notes.txt", "new");

        List<Result> results = run(ConflictPolicy.SKIP, false);

        assertEquals(Result.Status.SKIPPED, results.get(0).status());
        assertEquals("new", Files.readString(folder.resolve("notes.txt")));
        assertEquals("old", Files.readString(folder.resolve("Documents/notes.txt")));
    }

    @Test
    void overwriteReplacesOnlyWhenRequested() throws IOException {
        file("Documents/notes.txt", "old");
        file("notes.txt", "new");

        run(ConflictPolicy.OVERWRITE, false);

        assertEquals("new", Files.readString(folder.resolve("Documents/notes.txt")));
    }

    @Test
    void overwriteNeverReplacesAFolder() throws IOException {
        Files.createDirectories(folder.resolve("Documents/notes.txt"));
        file("notes.txt", "new");

        List<Result> results = run(ConflictPolicy.OVERWRITE, false);

        assertEquals(Result.Status.FAILED, results.get(0).status());
        assertTrue(Files.isDirectory(folder.resolve("Documents/notes.txt")));
        assertTrue(Files.exists(folder.resolve("notes.txt")));
    }

    @Test
    void ignoresHiddenTemporaryAndNestedFiles() throws IOException {
        file(".env", "secret");
        file("movie.mp4.part", "partial");
        file("setup.crdownload", "partial");
        file("~$report.docx", "lock");
        file("project/source.txt", "nested");

        assertTrue(run(ConflictPolicy.RENAME, false).isEmpty());
        try (Stream<Path> entries = Files.list(folder)) {
            assertEquals(5, entries.count());
        }
    }

    @Test
    void reportsFailureWhenAFileUsesTheCategoryName() throws IOException {
        file("Documents", "not a folder");
        file("notes.txt", "n");

        List<Result> preview = run(ConflictPolicy.RENAME, true);
        List<Result> real = run(ConflictPolicy.RENAME, false);

        assertEquals(1, real.size());
        assertEquals(Result.Status.FAILED, preview.get(0).status());
        assertEquals(Result.Status.FAILED, real.get(0).status());
        assertEquals("not a folder", Files.readString(folder.resolve("Documents")));
        assertTrue(Files.exists(folder.resolve("notes.txt")));
    }

    @Test
    void rejectsMissingFoldersAndFileSystemRoots() {
        assertThrows(IllegalArgumentException.class,
                () -> new FileOrganizer(folder.resolve("missing"), ConflictPolicy.RENAME, true));
        assertThrows(IllegalArgumentException.class,
                () -> new FileOrganizer(folder.getRoot(), ConflictPolicy.RENAME, true));
    }

    @Test
    void addsSuffixBeforeTheExtension() {
        assertEquals("report (1).pdf", FileOrganizer.withSuffix("report.pdf", 1));
        assertEquals("README (2)", FileOrganizer.withSuffix("README", 2));
    }
}
