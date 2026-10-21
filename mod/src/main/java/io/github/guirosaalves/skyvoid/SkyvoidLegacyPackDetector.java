package io.github.guirosaalves.skyvoid;

import java.util.Collection;
import java.util.Locale;

/**
 * An external standalone Additions pack belongs to the save's preexisting
 * datapack configuration. Don't overwrite its game rules at first load.
 * Full import and worldgen pack de-duplication are separate migration steps.
 */
public final class SkyvoidLegacyPackDetector {
    private SkyvoidLegacyPackDetector() {}

    public static boolean hasExternalAdditions(Collection<String> enabledPacks) {
        return enabledPacks.stream()
                .map(id -> id.toLowerCase(Locale.ROOT))
                .anyMatch(id -> id.startsWith("file/") && id.contains("skyvoid_additions"));
    }
}
