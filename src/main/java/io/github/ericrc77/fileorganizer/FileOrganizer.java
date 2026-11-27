package io.github.ericrc77.fileorganizer;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Moves the files found directly inside a folder into one subfolder per {@link Category}.
 * Subfolders are never traversed, and no file is replaced unless the policy is
 * {@link ConflictPolicy#OVERWRITE}.
 */
public final class FileOrganizer {

    private static final int MAX_RENAME_ATTEMPTS = 10_000;

    /** Extensions used by browsers and editors for files that are still being written. */
    private static final Set<String> TEMPORARY_EXTENSIONS =
            Set.of("part", "partial", "crdownload", "download", "tmp");

    private final Path root;
    private final ConflictPolicy policy;
    private final boolean dryRun;

    /** Destinations already promised during a dry run, so the preview matches a real run. */
    private final Set<Path> planned = new HashSet<>();

    public FileOrganizer(Path root, ConflictPolicy policy, boolean dryRun) {
        Path normalized = root.toAbsolutePath().normalize();
        if (!Files.isDirectory(normalized)) {
            throw new IllegalArgumentException("Not a folder: " + normalized);
        }
        if (normalized.getParent() == null) {
            throw new IllegalArgumentException("Refusing to organize a file system root: " + normalized);
        }
        this.root = normalized;
        this.policy = policy;
        this.dryRun = dryRun;
    }

    public Path root() {
        return root;
    }

    /** Files that are candidates to be moved, sorted by path for predictable output. */
    public List<Path> eligibleFiles() throws IOException {
        List<Path> files = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(root, this::isEligible)) {
            stream.forEach(files::add);
        }
        files.sort(null);
        return files;
    }

    public List<Result> organizeAll() throws IOException {
        planned.clear();
        return eligibleFiles().stream().map(this::organize).toList();
    }

    /**
     * Regular, visible and complete files. Symbolic links are left untouched, and so are files
     * named like a destination folder, because moving them would make results depend on order.
     */
    boolean isEligible(Path file) {
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
            return false;
        }
        String name = file.getFileName().toString();
        if (name.startsWith(".") || name.startsWith("~$")
                || Category.isFolderName(name)
                || TEMPORARY_EXTENSIONS.contains(Category.extensionOf(name))) {
            return false;
        }
        try {
            return !Files.isHidden(file);
        } catch (IOException e) {
            return false;
        }
    }

    public Result organize(Path file) {
        String name = file.getFileName().toString();
        Path folder = root.resolve(Category.of(name).folderName());
        if (Files.exists(folder) && !Files.isDirectory(folder)) {
            return failed(file, folder, "a file is using the folder name " + folder.getFileName());
        }
        try {
            if (!dryRun) {
                Files.createDirectories(folder);
            }
            return switch (policy) {
                case RENAME -> moveKeepingBoth(file, folder, name);
                case SKIP -> moveUnlessTaken(file, folder.resolve(name))
                        .orElseGet(() -> new Result(file, folder.resolve(name), Result.Status.SKIPPED,
                                "destination already exists"));
                case OVERWRITE -> moveReplacing(file, folder.resolve(name));
            };
        } catch (IOException e) {
            return failed(file, folder.resolve(name), describe(e));
        }
    }

    private Result moveKeepingBoth(Path file, Path folder, String name) throws IOException {
        for (int attempt = 0; attempt < MAX_RENAME_ATTEMPTS; attempt++) {
            Path target = folder.resolve(attempt == 0 ? name : withSuffix(name, attempt));
            Optional<Result> result = moveUnlessTaken(file, target);
            if (result.isPresent()) {
                return result.get();
            }
        }
        return failed(file, folder.resolve(name), "no free file name found");
    }

    /** Returns an empty result when the target is taken. */
    private Optional<Result> moveUnlessTaken(Path file, Path target) throws IOException {
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS) || planned.contains(target)) {
            return Optional.empty();
        }
        if (dryRun) {
            planned.add(target);
            return Optional.of(new Result(file, target, Result.Status.PLANNED, ""));
        }
        try {
            // Without REPLACE_EXISTING the move fails if the target appeared in the meantime.
            Files.move(file, target);
        } catch (FileAlreadyExistsException e) {
            return Optional.empty();
        }
        return Optional.of(new Result(file, target, Result.Status.MOVED, ""));
    }

    private Result moveReplacing(Path file, Path target) throws IOException {
        if (Files.isDirectory(target, LinkOption.NOFOLLOW_LINKS)) {
            return failed(file, target, "destination is a folder");
        }
        if (dryRun) {
            String detail = Files.exists(target) ? "would replace the existing file" : "";
            return new Result(file, target, Result.Status.PLANNED, detail);
        }
        Files.move(file, target, StandardCopyOption.REPLACE_EXISTING);
        return new Result(file, target, Result.Status.MOVED, "");
    }

    /** {@code report.pdf} becomes {@code report (1).pdf}. */
    static String withSuffix(String name, int number) {
        int dot = name.lastIndexOf('.');
        return dot > 0
                ? name.substring(0, dot) + " (" + number + ")" + name.substring(dot)
                : name + " (" + number + ")";
    }

    private static Result failed(Path file, Path target, String detail) {
        return new Result(file, target, Result.Status.FAILED, detail);
    }

    private static String describe(IOException e) {
        return e.getClass().getSimpleName() + ": " + e.getMessage();
    }
}
