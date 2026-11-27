package io.github.ericrc77.fileorganizer;

/** What to do when the destination already holds a file with the same name. */
public enum ConflictPolicy {
    /** Keep both files by adding a numeric suffix. This is the default. */
    RENAME,
    /** Leave the source file where it is. */
    SKIP,
    /** Replace the destination file. Destructive, so the CLI requires explicit confirmation. */
    OVERWRITE
}
