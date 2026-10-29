package com.doubleclick.wadii.storage;

import java.util.EnumSet;
import java.util.Set;

/** What may be uploaded where. Private categories are never served by the public endpoint. */
public enum FileCategory {
    PROFILE_IMAGE(false, EnumSet.of(FileKind.JPEG, FileKind.PNG, FileKind.WEBP, FileKind.GIF), 5),
    PROVIDER_DOCUMENT(true, EnumSet.of(FileKind.JPEG, FileKind.PNG, FileKind.WEBP, FileKind.PDF), 10),
    ORDER_ATTACHMENT(true, EnumSet.of(FileKind.JPEG, FileKind.PNG, FileKind.WEBP, FileKind.PDF), 10),
    PAYMENT_PROOF(true, EnumSet.of(FileKind.JPEG, FileKind.PNG, FileKind.WEBP, FileKind.PDF), 10),
    DISPUTE_EVIDENCE(true, EnumSet.of(FileKind.JPEG, FileKind.PNG, FileKind.WEBP, FileKind.PDF, FileKind.MP4,
            FileKind.WEBM, FileKind.MP3, FileKind.OGG, FileKind.M4A), 10),
    CHAT_MEDIA(true, EnumSet.of(FileKind.JPEG, FileKind.PNG, FileKind.WEBP, FileKind.MP3, FileKind.OGG, FileKind.M4A), 10);

    private final boolean privateFile;
    private final Set<FileKind> allowed;
    private final int maxMegabytes;

    FileCategory(boolean privateFile, Set<FileKind> allowed, int maxMegabytes) {
        this.privateFile = privateFile;
        this.allowed = allowed;
        this.maxMegabytes = maxMegabytes;
    }

    public boolean isPrivate() {
        return privateFile;
    }

    public Set<FileKind> allowed() {
        return allowed;
    }

    public long maxBytes() {
        return maxMegabytes * 1024L * 1024L;
    }
}
