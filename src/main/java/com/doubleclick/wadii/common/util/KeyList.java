package com.doubleclick.wadii.common.util;

import java.util.Arrays;
import java.util.List;

/** Stores short string lists (file keys) in one TEXT column as comma-separated values. */
public final class KeyList {

    private KeyList() {
    }

    public static String join(List<String> keys) {
        return keys == null || keys.isEmpty() ? null : String.join(",", keys);
    }

    public static List<String> split(String value) {
        return value == null || value.isBlank() ? List.of() : Arrays.stream(value.split(",")).filter(s -> !s.isBlank()).toList();
    }
}
