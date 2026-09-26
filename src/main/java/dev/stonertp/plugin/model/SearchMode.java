package dev.stonertp.plugin.model;

public enum SearchMode {
    AUTO,
    SURFACE,
    CAVE;

    public static SearchMode fromConfig(String raw) {
        if (raw == null) {
            return AUTO;
        }
        try {
            return SearchMode.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return AUTO;
        }
    }
}
