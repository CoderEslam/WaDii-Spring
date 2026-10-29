package com.doubleclick.wadii.storage;

import java.util.Set;

/** Supported file types: canonical extension, accepted extensions and MIME types, and a magic-number check. */
public enum FileKind {
    JPEG("jpg", Set.of("jpg", "jpeg"), Set.of("image/jpeg", "image/jpg")),
    PNG("png", Set.of("png"), Set.of("image/png")),
    GIF("gif", Set.of("gif"), Set.of("image/gif")),
    WEBP("webp", Set.of("webp"), Set.of("image/webp")),
    PDF("pdf", Set.of("pdf"), Set.of("application/pdf")),
    MP4("mp4", Set.of("mp4"), Set.of("video/mp4")),
    WEBM("webm", Set.of("webm"), Set.of("video/webm", "audio/webm")),
    MP3("mp3", Set.of("mp3"), Set.of("audio/mpeg", "audio/mp3")),
    OGG("ogg", Set.of("ogg", "oga"), Set.of("audio/ogg")),
    M4A("m4a", Set.of("m4a"), Set.of("audio/mp4", "audio/x-m4a", "audio/m4a"));

    private final String extension;
    private final Set<String> extensions;
    private final Set<String> mimeTypes;

    FileKind(String extension, Set<String> extensions, Set<String> mimeTypes) {
        this.extension = extension;
        this.extensions = extensions;
        this.mimeTypes = mimeTypes;
    }

    public String extension() {
        return extension;
    }

    public String primaryMime() {
        return canonicalMime();
    }

    private String canonicalMime() {
        return switch (this) {
            case JPEG -> "image/jpeg";
            case PNG -> "image/png";
            case GIF -> "image/gif";
            case WEBP -> "image/webp";
            case PDF -> "application/pdf";
            case MP4 -> "video/mp4";
            case WEBM -> "video/webm";
            case MP3 -> "audio/mpeg";
            case OGG -> "audio/ogg";
            case M4A -> "audio/mp4";
        };
    }

    public boolean acceptsExtension(String ext) {
        return extensions.contains(ext);
    }

    public boolean acceptsMime(String mime) {
        return mimeTypes.contains(mime);
    }

    /** Checks the file signature (magic bytes). */
    public boolean matches(byte[] h) {
        return switch (this) {
            case JPEG -> h.length >= 3 && (h[0] & 0xFF) == 0xFF && (h[1] & 0xFF) == 0xD8 && (h[2] & 0xFF) == 0xFF;
            case PNG -> h.length >= 8 && (h[0] & 0xFF) == 0x89 && h[1] == 'P' && h[2] == 'N' && h[3] == 'G';
            case GIF -> h.length >= 4 && h[0] == 'G' && h[1] == 'I' && h[2] == 'F' && h[3] == '8';
            case WEBP -> h.length >= 12 && h[0] == 'R' && h[1] == 'I' && h[2] == 'F' && h[3] == 'F'
                    && h[8] == 'W' && h[9] == 'E' && h[10] == 'B' && h[11] == 'P';
            case PDF -> h.length >= 4 && h[0] == '%' && h[1] == 'P' && h[2] == 'D' && h[3] == 'F';
            case MP4, M4A -> h.length >= 8 && h[4] == 'f' && h[5] == 't' && h[6] == 'y' && h[7] == 'p';
            case WEBM -> h.length >= 4 && (h[0] & 0xFF) == 0x1A && (h[1] & 0xFF) == 0x45 && (h[2] & 0xFF) == 0xDF && (h[3] & 0xFF) == 0xA3;
            case MP3 -> h.length >= 3 && ((h[0] == 'I' && h[1] == 'D' && h[2] == '3')
                    || ((h[0] & 0xFF) == 0xFF && ((h[1] & 0xFF) & 0xE0) == 0xE0));
            case OGG -> h.length >= 4 && h[0] == 'O' && h[1] == 'g' && h[2] == 'g' && h[3] == 'S';
        };
    }
}
