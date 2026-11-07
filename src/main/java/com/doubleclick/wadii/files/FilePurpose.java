package com.doubleclick.wadii.files;

import java.util.EnumSet;
import java.util.Set;

/** Why a file is uploaded; decides allowed types and whether it is served publicly. */
public enum FilePurpose {
    PROVIDER_DOCUMENT(false, EnumSet.of(FileKind.JPEG, FileKind.PNG, FileKind.WEBP, FileKind.PDF)),
    EVIDENCE(false, EnumSet.of(FileKind.JPEG, FileKind.PNG, FileKind.WEBP, FileKind.PDF)),
    REQUEST_PHOTO(false, EnumSet.of(FileKind.JPEG, FileKind.PNG, FileKind.WEBP)),
    CHAT(false, EnumSet.of(FileKind.JPEG, FileKind.PNG, FileKind.WEBP, FileKind.PDF)),
    LISTING_PHOTO(true, EnumSet.of(FileKind.JPEG, FileKind.PNG, FileKind.WEBP));

    public static final long MAX_BYTES = 10L * 1024 * 1024;

    private final boolean publicFile;
    private final Set<FileKind> allowed;

    FilePurpose(boolean publicFile, Set<FileKind> allowed) {
        this.publicFile = publicFile;
        this.allowed = allowed;
    }

    public boolean isPublic() {
        return publicFile;
    }

    public Set<FileKind> allowed() {
        return allowed;
    }
}
