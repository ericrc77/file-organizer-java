package io.github.ericrc77.fileorganizer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Holds back new files until their size and modification time stop changing, so a file is not
 * moved while another program is still writing it.
 */
final class StabilityTracker {

    private record Snapshot(long size, FileTime modified, Instant since) {

        boolean sameContentAs(BasicFileAttributes attributes) {
            return size == attributes.size() && modified.equals(attributes.lastModifiedTime());
        }
    }

    private final Duration quietPeriod;
    private final Map<Path, Snapshot> pending = new HashMap<>();

    StabilityTracker(Duration quietPeriod) {
        this.quietPeriod = quietPeriod;
    }

    void track(Path file, Instant now) {
        snapshot(file, now).ifPresentOrElse(s -> pending.put(file, s), () -> pending.remove(file));
    }

    boolean isEmpty() {
        return pending.isEmpty();
    }

    /** Removes and returns the files that have been unchanged for the whole quiet period. */
    List<Path> takeStable(Instant now) {
        List<Path> stable = new ArrayList<>();
        Iterator<Map.Entry<Path, Snapshot>> entries = pending.entrySet().iterator();
        while (entries.hasNext()) {
            Map.Entry<Path, Snapshot> entry = entries.next();
            BasicFileAttributes attributes = read(entry.getKey());
            if (attributes == null) {
                entries.remove();
            } else if (!entry.getValue().sameContentAs(attributes)) {
                entry.setValue(new Snapshot(attributes.size(), attributes.lastModifiedTime(), now));
            } else if (Duration.between(entry.getValue().since(), now).compareTo(quietPeriod) >= 0) {
                stable.add(entry.getKey());
                entries.remove();
            }
        }
        stable.sort(null);
        return stable;
    }

    private static Optional<Snapshot> snapshot(Path file, Instant now) {
        return Optional.ofNullable(read(file))
                .map(a -> new Snapshot(a.size(), a.lastModifiedTime(), now));
    }

    /** Attributes of a regular file, or {@code null} if it is gone or is not a regular file. */
    private static BasicFileAttributes read(Path file) {
        try {
            BasicFileAttributes attributes =
                    Files.readAttributes(file, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            return attributes.isRegularFile() ? attributes : null;
        } catch (IOException e) {
            return null;
        }
    }
}
