package io.github.ericrc77.fileorganizer;

import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Destination folders and the file extensions that belong to each one. */
public enum Category {
    IMAGES("Images", "jpg", "jpeg", "png", "gif", "bmp", "svg", "webp", "heic"),
    PDFS("PDFs", "pdf"),
    DOCUMENTS("Documents", "doc", "docx", "odt", "txt", "rtf", "md"),
    SPREADSHEETS("Spreadsheets", "xls", "xlsx", "ods", "csv"),
    PRESENTATIONS("Presentations", "ppt", "pptx", "odp"),
    ARCHIVES("Archives", "zip", "rar", "7z", "tar", "gz"),
    INSTALLERS("Installers", "exe", "msi", "bat", "sh", "dmg", "deb"),
    AUDIO("Audio", "mp3", "wav", "ogg", "flac", "m4a"),
    VIDEOS("Videos", "mp4", "avi", "mov", "wmv", "mkv", "webm"),
    OTHER("Other");

    private final String folderName;
    private final Set<String> extensions;

    private static final Map<String, Category> BY_EXTENSION = Arrays.stream(values())
            .flatMap(category -> category.extensions.stream().map(ext -> Map.entry(ext, category)))
            .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));

    Category(String folderName, String... extensions) {
        this.folderName = folderName;
        this.extensions = Set.of(extensions);
    }

    public String folderName() {
        return folderName;
    }

    public static Category of(String fileName) {
        return BY_EXTENSION.getOrDefault(extensionOf(fileName), OTHER);
    }

    /** Whether a name collides with one of the destination folders on any file system. */
    static boolean isFolderName(String name) {
        return Arrays.stream(values()).anyMatch(category -> category.folderName.equalsIgnoreCase(name));
    }

    /** Lower-case extension without the dot, or an empty string. Dot files have no extension. */
    static String extensionOf(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
    }
}
