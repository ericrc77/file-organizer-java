package io.github.ericrc77.fileorganizer;

import java.nio.file.Path;

/**
 * Outcome for a single file.
 *
 * @param source file that was considered
 * @param target destination, or {@code null} when none was chosen
 * @param status what happened
 * @param detail reason for a skip or failure, otherwise empty
 */
public record Result(Path source, Path target, Status status, String detail) {

    public enum Status { MOVED, PLANNED, SKIPPED, FAILED }
}
