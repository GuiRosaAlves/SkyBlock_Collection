package io.github.guirosaalves.skyvoid;

import org.junit.jupiter.api.Test;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

final class SkyvoidAdditionsCommandsTest {
    @Test void onlyChangedSettingsAreWritten() {
        var commands = SkyvoidAdditionsCommands.changes(
                Map.of("cobwebs_from_spiders", false, "traders_with_camels", true),
                Map.of("cobwebs_from_spiders", true, "traders_with_camels", true));
        assertEquals(2, commands.size());
        assertEquals("scoreboard players set cobwebs_from_spiders skyvoid_config 1", commands.getFirst());
        assertEquals("function skyvoid_additions:technical/load", commands.getLast());
    }

    @Test void noChangeDoesNotRestartDatapackSchedules() {
        assertTrue(SkyvoidAdditionsCommands.changes(Map.of("test", true), Map.of("test", true)).isEmpty());
    }

    @Test void defaultsPopulateMissingKeysAndCanDisable() {
        var commands = SkyvoidAdditionsCommands.changes(Map.of(), Map.of("example_feature", false));
        assertEquals("scoreboard players set example_feature skyvoid_config 0", commands.getFirst());
        assertEquals("function skyvoid_additions:technical/load", commands.getLast());
    }

    @Test void rejectsUnexpectedCommandInput() {
        assertThrows(IllegalArgumentException.class,
                () -> SkyvoidAdditionsCommands.changes(Map.of(), Map.of("a;op", true)));
    }
}
