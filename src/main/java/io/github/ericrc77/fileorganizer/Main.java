package io.github.ericrc77.fileorganizer;

import java.io.IOException;
import java.io.PrintStream;
import java.time.Duration;
import java.util.List;

/** Command line entry point. */
public final class Main {

    static final int EXIT_OK = 0;
    static final int EXIT_FAILURES = 1;
    static final int EXIT_USAGE = 2;

    private static final Duration QUIET_PERIOD = Duration.ofSeconds(3);

    private static final String USAGE = """
            Usage: java -jar file-organizer.jar <folder> [options]

            Sorts the files directly inside <folder> into subfolders by type.

            Options:
              --dry-run                 show what would happen without changing anything
              --on-conflict <policy>    rename (default), skip or overwrite
              --yes                     confirm --on-conflict overwrite
              --watch                   keep running and organize new files
              --version                 print the version
              -h, --help                print this help

            Exit codes: 0 success, 1 some files failed, 2 invalid usage.""";

    private Main() {
    }

    public static void main(String[] args) {
        System.exit(run(args, System.out, System.err));
    }

    static int run(String[] args, PrintStream out, PrintStream err) {
        try {
            Options options = Options.parse(args);
            if (options.help()) {
                out.println(USAGE);
                return EXIT_OK;
            }
            if (options.version()) {
                String version = Main.class.getPackage().getImplementationVersion();
                out.println(version == null ? "development" : version);
                return EXIT_OK;
            }
            FileOrganizer organizer = new FileOrganizer(options.folder(), options.policy(), options.dryRun());
            if (options.watch()) {
                out.println("Watching " + organizer.root() + " (press Ctrl+C to stop)");
                new FolderWatcher(organizer, QUIET_PERIOD, result -> print(organizer, result, out, err)).run();
                return EXIT_OK;
            }
            List<Result> results = organizer.organizeAll();
            results.forEach(result -> print(organizer, result, out, err));
            return summarize(results, options.dryRun(), out);
        } catch (IllegalArgumentException e) {
            err.println(e.getMessage());
            err.println(USAGE);
            return EXIT_USAGE;
        } catch (IOException e) {
            err.println("Error: " + e.getMessage());
            return EXIT_FAILURES;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return EXIT_OK;
        }
    }

    private static void print(FileOrganizer organizer, Result result, PrintStream out, PrintStream err) {
        String source = organizer.root().relativize(result.source()).toString();
        String target = result.target() == null ? "" : organizer.root().relativize(result.target()).toString();
        String note = result.detail().isEmpty() ? "" : " (" + result.detail() + ")";
        String line = String.format("%-8s %s -> %s%s", result.status(), source, target, note);
        (result.status() == Result.Status.FAILED ? err : out).println(line);
    }

    private static int summarize(List<Result> results, boolean dryRun, PrintStream out) {
        long done = count(results, dryRun ? Result.Status.PLANNED : Result.Status.MOVED);
        long skipped = count(results, Result.Status.SKIPPED);
        long failed = count(results, Result.Status.FAILED);
        out.printf("%s: %d, skipped: %d, failed: %d%n", dryRun ? "Planned" : "Moved", done, skipped, failed);
        if (dryRun) {
            out.println("Dry run: nothing was changed.");
        }
        return failed == 0 ? EXIT_OK : EXIT_FAILURES;
    }

    private static long count(List<Result> results, Result.Status status) {
        return results.stream().filter(result -> result.status() == status).count();
    }
}
