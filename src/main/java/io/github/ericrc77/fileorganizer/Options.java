package io.github.ericrc77.fileorganizer;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Locale;

/** Parsed command line. */
record Options(Path folder, ConflictPolicy policy, boolean dryRun, boolean watch, boolean help, boolean version) {

    static Options parse(String... args) {
        Path folder = null;
        ConflictPolicy policy = ConflictPolicy.RENAME;
        boolean dryRun = false;
        boolean watch = false;
        boolean confirmed = false;
        boolean help = false;
        boolean version = false;

        Iterator<String> iterator = Arrays.asList(args).iterator();
        while (iterator.hasNext()) {
            String argument = iterator.next();
            switch (argument) {
                case "-h", "--help" -> help = true;
                case "--version" -> version = true;
                case "--dry-run" -> dryRun = true;
                case "--watch" -> watch = true;
                case "--yes" -> confirmed = true;
                case "--on-conflict" -> policy = parsePolicy(iterator);
                default -> {
                    if (argument.startsWith("-")) {
                        throw new IllegalArgumentException("Unknown option: " + argument);
                    }
                    if (folder != null) {
                        throw new IllegalArgumentException("Only one folder can be organized at a time");
                    }
                    folder = toPath(argument);
                }
            }
        }

        if (!help && !version) {
            if (folder == null) {
                throw new IllegalArgumentException("Missing folder");
            }
            if (policy == ConflictPolicy.OVERWRITE && !dryRun && !confirmed) {
                throw new IllegalArgumentException(
                        "--on-conflict overwrite deletes existing files; add --yes to confirm");
            }
        }
        return new Options(folder, policy, dryRun, watch, help, version);
    }

    private static ConflictPolicy parsePolicy(Iterator<String> iterator) {
        if (!iterator.hasNext()) {
            throw new IllegalArgumentException("--on-conflict needs a value: rename, skip or overwrite");
        }
        String value = iterator.next();
        try {
            return ConflictPolicy.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown conflict policy: " + value, e);
        }
    }

    private static Path toPath(String argument) {
        try {
            return Path.of(argument);
        } catch (InvalidPathException e) {
            throw new IllegalArgumentException("Invalid path: " + argument, e);
        }
    }
}
