package com.doubleclick.wadii.storage;

/** Result of a successful upload. {@code key} is a server-generated name; the original filename is never kept. */
public record StoredFile(String key, String contentType, long size) {
}
