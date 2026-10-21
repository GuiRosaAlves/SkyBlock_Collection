package io.github.guirosaalves.skyvoid;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Pure diff between applied gameplay settings and requested settings.
 * Reload only when something actually changed to avoid resetting schedules
 * unnecessarily while using the NeoForge configuration screen.
 */
public final class SkyvoidAdditionsCommands {
    private SkyvoidAdditionsCommands() {}

    public static List<String> changes(Map<String, Boolean> applied, Map<String, Boolean> requested) {
        List<String> result = new ArrayList<>();
        for (var entry : new TreeMap<>(requested).entrySet()) {
            String key = entry.getKey();
            if (!key.matches("[a-z0-9_]+")) {
                throw new IllegalArgumentException("Invalid SkyVoid Additions key: " + key);
            }
            if (!Objects.equals(applied.get(key), entry.getValue())) {
                result.add("scoreboard players set " + key + " skyvoid_config "
                        + (entry.getValue() ? "1" : "0"));
            }
        }
        if (!result.isEmpty()) {
            result.add("function skyvoid_additions:technical/load");
        }
        return List.copyOf(result);
    }
}
