package io.github.ericrc77.fileorganizer;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/** Organizes files as they arrive in the folder, until the thread is interrupted. */
final class FolderWatcher {

    private static final long POLL_MILLIS = 500;

    private final FileOrganizer organizer;
    private final Duration quietPeriod;
    private final Consumer<Result> listener;

    FolderWatcher(FileOrganizer organizer, Duration quietPeriod, Consumer<Result> listener) {
        this.organizer = organizer;
        this.quietPeriod = quietPeriod;
        this.listener = listener;
    }

    void run() throws IOException, InterruptedException {
        StabilityTracker tracker = new StabilityTracker(quietPeriod);
        Path root = organizer.root();
        try (WatchService service = FileSystems.getDefault().newWatchService()) {
            // Register before the first scan so files created in between are not missed.
            root.register(service, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_MODIFY);
            trackExisting(tracker);
            while (!Thread.currentThread().isInterrupted()) {
                WatchKey key = service.poll(POLL_MILLIS, TimeUnit.MILLISECONDS);
                if (key != null) {
                    for (WatchEvent<?> event : key.pollEvents()) {
                        if (event.kind() == StandardWatchEventKinds.OVERFLOW) {
                            // Events were dropped: rescan the folder.
                            trackExisting(tracker);
                        } else if (event.context() instanceof Path name) {
                            Path file = root.resolve(name);
                            if (organizer.isEligible(file)) {
                                tracker.track(file, Instant.now());
                            }
                        }
                    }
                    if (!key.reset()) {
                        throw new IOException("The folder is no longer accessible: " + root);
                    }
                }
                tracker.takeStable(Instant.now()).stream()
                        .filter(organizer::isEligible)
                        .map(organizer::organize)
                        .forEach(listener);
            }
        }
    }

    private void trackExisting(StabilityTracker tracker) throws IOException {
        Instant now = Instant.now();
        organizer.eligibleFiles().forEach(file -> tracker.track(file, now));
    }
}
