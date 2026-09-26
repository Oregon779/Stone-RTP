package dev.stonertp.plugin.model;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TimeWindowTest {

    @ParameterizedTest
    @CsvSource({
            "14:00-18:00, 13:59, false",
            "14:00-18:00, 14:00, true",
            "14:00-18:00, 17:59, true",
            "14:00-18:00, 18:00, false",
            "22:00-02:00, 21:59, false",
            "22:00-02:00, 22:00, true",
            "22:00-02:00, 00:00, true",
            "22:00-02:00, 01:59, true",
            "22:00-02:00, 02:00, false",
            "22:00-24:00, 23:59, true",
            "22:00-24:00, 00:00, false",
    })
    void containsHandlesBoundariesAndMidnight(String window, String time, boolean expected) {
        assertEquals(expected, TimeWindow.parse(window).contains(LocalTime.parse(time)));
    }

    @ParameterizedTest
    @CsvSource({"9, 09:00", "9:30, 09:30", "14, 14:00", "' 14:05 ', 14:05", "24:00, 00:00"})
    void parseTimeAcceptsShortForms(String raw, String expected) {
        assertEquals(LocalTime.parse(expected), TimeWindow.parseTime(raw));
    }

    @ParameterizedTest
    @ValueSource(strings = {"25:00", "14:60", "abc", "", "14:00-14:00", "14:00", "1-2-3"})
    void invalidInputIsRejected(String raw) {
        assertThrows(IllegalArgumentException.class, () -> {
            if (raw.contains("-") || raw.isEmpty() || raw.equals("14:00")) {
                TimeWindow.parse(raw);
            } else {
                TimeWindow.parseTime(raw);
            }
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"14:00-18:00", "22:00-02:00", "00:00-06:30"})
    void serializeRoundTrips(String raw) {
        assertEquals(raw, TimeWindow.parse(raw).serialize());
    }
}
