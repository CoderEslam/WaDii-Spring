package com.doubleclick.wadii.common.security;

/** Published whenever a request is refused for authorization reasons; the audit module records it. */
public record AccessDeniedRecorded(String endpoint, String reason) {
}
