package dev.stonertp.plugin.model;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

public record TimeWindow(LocalTime start, LocalTime end) {
    private static final DateTimeFormatter INPUT = DateTimeFormatter.ofPattern("H:mm");
    private static final DateTimeFormatter OUTPUT = DateTimeFormatter.ofPattern("HH:mm");

    public TimeWindow {
        if (start.equals(end)) {
            throw new IllegalArgumentException("Start and end time must differ");
        }
    }

    /** Parses "HH:MM-HH:MM"; windows may wrap past midnight, e.g. "22:00-02:00". */
    public static TimeWindow parse(String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("Missing time window");
        }
        String[] parts = raw.split("-");
        if (parts.length != 2) {
            throw new IllegalArgumentException("Expected HH:MM-HH:MM but got '" + raw + "'");
        }
        return new TimeWindow(parseTime(parts[0]), parseTime(parts[1]));
    }

    /** Accepts "14:00", "9:30", "14" and "24:00" (treated as midnight). */
    public static LocalTime parseTime(String raw) {
        String value = raw.trim();
        if (value.matches("\\d{1,2}")) {
            value = value + ":00";
        }
        if (value.equals("24:00")) {
            return LocalTime.MIDNIGHT;
        }
        try {
            return LocalTime.parse(value, INPUT);
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException("Invalid time '" + raw + "'", ex);
        }
    }

    public boolean contains(LocalTime time) {
        if (start.isBefore(end)) {
            return !time.isBefore(start) && time.isBefore(end);
        }
        return !time.isBefore(start) || time.isBefore(end);
    }

    public String formatStart() {
        return OUTPUT.format(start);
    }

    public String formatEnd() {
        return OUTPUT.format(end);
    }

    public String serialize() {
        return formatStart() + "-" + formatEnd();
    }
}
