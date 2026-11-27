package io.github.ericrc77.fileorganizer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WatchTest {

    @TempDir
    Path folder;

    @Test
    void releasesAFileOnlyAfterItStopsChanging() throws IOException {
        StabilityTracker tracker = new StabilityTracker(Duration.ofSeconds(3));
        Path file = Files.writeString(folder.resolve("big.zip"), "a");
        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        tracker.track(file, start);

        Files.writeString(file, "more", StandardOpenOption.APPEND);
        assertEquals(List.of(), tracker.takeStable(start.plusSeconds(4)));
        assertEquals(List.of(), tracker.takeStable(start.plusSeconds(6)));
        assertEquals(List.of(file), tracker.takeStable(start.plusSeconds(7)));
        assertTrue(tracker.isEmpty());
    }

    @Test
    void forgetsFilesThatDisappear() throws IOException {
        StabilityTracker tracker = new StabilityTracker(Duration.ZERO);
        Path file = Files.writeString(folder.resolve("gone.txt"), "a");
        tracker.track(file, Instant.now());
        Files.delete(file);

        assertEquals(List.of(), tracker.takeStable(Instant.now()));
        assertTrue(tracker.isEmpty());
    }

    @Test
    void organizesExistingAndNewFiles() throws Exception {
        Files.writeString(folder.resolve("existing.txt"), "a");
        BlockingQueue<Result> results = new LinkedBlockingQueue<>();
        FileOrganizer organizer = new FileOrganizer(folder, ConflictPolicy.RENAME, false);
        FolderWatcher watcher = new FolderWatcher(organizer, Duration.ofMillis(200), results::add);
        Thread thread = new Thread(() -> {
            try {
                watcher.run();
            } catch (IOException e) {
                throw new AssertionError(e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        thread.start();
        try {
            // Generous timeouts: some platforms implement WatchService by polling.
            assertNotNull(results.poll(30, TimeUnit.SECONDS), "existing file");
            Files.writeString(folder.resolve("later.pdf"), "b");
            assertNotNull(results.poll(30, TimeUnit.SECONDS), "new file");
        } finally {
            thread.interrupt();
            thread.join(5_000);
        }
        assertTrue(Files.exists(folder.resolve("Documents/existing.txt")));
        assertTrue(Files.exists(folder.resolve("PDFs/later.pdf")));
    }
}
