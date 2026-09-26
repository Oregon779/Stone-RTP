package dev.stonertp.plugin.model;

public record RTPWorldSettings(String worldName, boolean enabled, int centerX, int centerZ, int radiusMin, int radiusMax,
                               SearchMode searchMode) {

    public boolean isConfigured() {
        return radiusMax > radiusMin && radiusMax > 0;
    }
}
