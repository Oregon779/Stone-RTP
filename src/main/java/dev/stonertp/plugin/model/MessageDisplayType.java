package dev.stonertp.plugin.model;

public enum MessageDisplayType {
    CHAT,
    ACTIONBAR,
    TITLE,
    BOSSBAR;

    public static MessageDisplayType fromConfig(String raw, MessageDisplayType fallback) {
        if (raw == null) {
            return fallback;
        }
        try {
            return MessageDisplayType.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return fallback;
        }
    }
}
