package dev.stonertp.plugin.manager;

import dev.stonertp.plugin.PluginTestBase;
import dev.stonertp.plugin.model.TimeWindow;
import org.bukkit.Sound;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ManagerInternalsTest extends PluginTestBase {

    private BlockedTimeManager blockedAt(String isoUtcTime) {
        BlockedTimeManager manager = plugin.getBlockedTimeManager();
        manager.setClock(Clock.fixed(Instant.parse("2026-09-26T" + isoUtcTime + ":00Z"), ZoneOffset.UTC));
        return manager;
    }

    @Test
    void backToBackWindowsReportTheRealReopeningTime() {
        setConfig("blocked-times.timezone", "UTC");
        BlockedTimeManager manager = blockedAt("15:00");
        manager.add(TimeWindow.parse("14:00-16:00"));
        manager.add(TimeWindow.parse("16:00-18:00"));

        assertEquals("18:00", manager.formatBlockedUntil());
    }

    @Test
    void configuredTimezoneIsRespected() {
        setConfig("blocked-times.timezone", "Europe/Berlin");
        BlockedTimeManager manager = blockedAt("12:30");
        manager.add(TimeWindow.parse("14:00-15:00"));

        assertEquals("14:30", manager.formatNow(), "12:30 UTC is 14:30 in Berlin (summer time)");
        assertEquals("15:00", manager.formatBlockedUntil());
    }

    @Test
    void soundsResolveByConstantNameOrKey() {
        assertEquals(Sound.BLOCK_NOTE_BLOCK_HARP, ConfigManager.lookupSound("BLOCK_NOTE_BLOCK_HARP"));
        assertEquals(Sound.BLOCK_NOTE_BLOCK_HARP, ConfigManager.lookupSound("block_note_block_harp"));
        assertEquals(Sound.BLOCK_NOTE_BLOCK_HARP, ConfigManager.lookupSound("block.note_block.harp"));
        assertEquals(Sound.BLOCK_NOTE_BLOCK_HARP, ConfigManager.lookupSound("minecraft:block.note_block.harp"));
        assertNull(ConfigManager.lookupSound("NOT_A_SOUND"));
    }
}
