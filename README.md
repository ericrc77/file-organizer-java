# File Organizer

[![CI](https://github.com/ericrc77/file-organizer-java/actions/workflows/ci.yml/badge.svg)](https://github.com/ericrc77/file-organizer-java/actions/workflows/ci.yml)

A command-line tool that sorts the files of a folder, such as `Downloads`, into subfolders by type.
It is built around one rule: **organizing a folder must never lose a file.**

## Features

- **Dry run**: preview every move, including the final file names, before changing anything.
- **Safe conflicts**: by default both files are kept (`notes.txt` and `notes (1).txt`).
- **Explicit overwrite**: replacing files requires `--on-conflict overwrite --yes`.
- **Watch mode**: organizes new files once they stop changing, so downloads in progress are left alone.
- **Conservative scope**: subfolders, hidden files, symbolic links and partial downloads are never moved.
- **No runtime dependencies**: a single JAR for Java 17 or newer.

## Installation

Download `file-organizer.jar` from the [latest release](https://github.com/ericrc77/file-organizer-java/releases/latest).

To build from source (JDK 17+):

```bash
git clone https://github.com/ericrc77/file-organizer-java.git
cd file-organizer-java
./mvnw verify          # Windows: mvnw.cmd verify
```

The JAR is written to `target/file-organizer.jar`.

## Usage

```text
java -jar file-organizer.jar <folder> [options]

  --dry-run                 show what would happen without changing anything
  --on-conflict <policy>    rename (default), skip or overwrite
  --yes                     confirm --on-conflict overwrite
  --watch                   keep running and organize new files
  --version                 print the version
  -h, --help                print this help
```

Preview first:

```console
$ java -jar file-organizer.jar . --dry-run
PLANNED  backup.zip -> Archives\backup.zip
PLANNED  holiday.jpg -> Images\holiday.jpg
PLANNED  invoice.pdf -> PDFs\invoice.pdf
PLANNED  notes.txt -> Documents\notes (1).txt
Planned: 4, skipped: 0, failed: 0
Dry run: nothing was changed.
```

Then run it without `--dry-run`. The example above comes from a Windows run on a folder that already had
`Documents/notes.txt`.

| Exit code | Meaning |
|-----------|---------|
| 0 | Success |
| 1 | At least one file could not be moved |
| 2 | Invalid usage |

## Categories

| Folder | Extensions |
|--------|------------|
| Images | jpg, jpeg, png, gif, bmp, svg, webp, heic |
| PDFs | pdf |
| Documents | doc, docx, odt, txt, rtf, md |
| Spreadsheets | xls, xlsx, ods, csv |
| Presentations | ppt, pptx, odp |
| Archives | zip, rar, 7z, tar, gz |
| Installers | exe, msi, bat, sh, dmg, deb |
| Audio | mp3, wav, ogg, flac, m4a |
| Videos | mp4, avi, mov, wmv, mkv, webm |
| Other | everything else |

## Design

| Class | Responsibility |
|-------|----------------|
| `FileOrganizer` | Chooses destinations and moves files |
| `Category`, `ConflictPolicy` | Domain rules |
| `FolderWatcher`, `StabilityTracker` | Watch mode and detection of files still being written |
| `Options`, `Main` | Command line parsing and output |

- Moves use `Files.move` without `REPLACE_EXISTING`, so a destination created after the existence check makes the
  move fail instead of being replaced.
- A dry run remembers the names it has already promised, so its preview matches the real run.
- Files named like a destination folder are ignored to keep results independent of processing order.
- Compiler warnings fail the build (`-Xlint:all -Werror`).

## Limitations

- Only the top level of the folder is organized.
- There is no undo command. Use `--dry-run` before the first run.
- A download that pauses for longer than three seconds and does not use a temporary extension may be moved
  while incomplete in watch mode.
- Categories are not configurable yet.

## License

[MIT](LICENSE)
