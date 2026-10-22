package io.github.guirosaalves.skyvoid.mixin;

import io.github.guirosaalves.skyvoid.SkyvoidVoidStructureRuntime;
import io.github.guirosaalves.skyvoid.SkyvoidVoidStructureSelector;
import net.minecraft.core.QuartPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * Opt-in hook on the same native structure-start method vanilla uses.
 * The original method validates all candidate-specific biome/height rules
 * and constructs the replacement's real pieces and bounding box.
 */
@Mixin(ChunkGenerator.class)
public abstract class SkyvoidChunkGeneratorMixin {
    @Unique private static final ThreadLocal<Boolean> skyvoid$nativeAttempt =
            ThreadLocal.withInitial(() -> false);

    @Invoker("tryGenerateStructure")
    protected abstract boolean skyvoid$tryNative(
            StructureSet.StructureSelectionEntry selected,
            StructureManager structureManager, RegistryAccess registryAccess,
            RandomState randomState, StructureTemplateManager templateManager,
            long seed, ChunkAccess centerChunk, ChunkPos sourceChunkPos,
            SectionPos sectionPos, ResourceKey<Level> dimension);

    @Inject(method = "tryGenerateStructure", at = @At("HEAD"), cancellable = true)
    private void skyvoid$replace(
            StructureSet.StructureSelectionEntry selected,
            StructureManager structureManager, RegistryAccess registryAccess,
            RandomState randomState, StructureTemplateManager templateManager,
            long seed, ChunkAccess centerChunk, ChunkPos sourceChunkPos,
            SectionPos sectionPos, ResourceKey<Level> dimension,
            CallbackInfoReturnable<Boolean> cir) {
        var settings = SkyvoidVoidStructureRuntime.current();
        if (skyvoid$nativeAttempt.get()
                || !settings.enabled()
                || !"skyvoid_worldgen_structures".equals(settings.worldgen())) return;

        String originId = selected.structure().unwrapKey().orElseThrow().identifier().toString();
        // Do not move progression-critical stronghold starts.
        if (originId.endsWith(":stronghold") || originId.endsWith(":strongholds")) return;

        Map<String, Integer> weights = settings.weights();
        var possibleBiomes = ((ChunkGenerator) (Object)this).getBiomeSource().possibleBiomes();
        var registry = registryAccess.lookupOrThrow(Registries.STRUCTURE);
        Map<String, net.minecraft.core.Holder<Structure>> holders = new HashMap<>();
        var options = new ArrayList<SkyvoidVoidStructureSelector.Candidate>();
        for (var holder : registry.listElements().toList()) {
            String id = holder.key().identifier().toString();
            if (id.endsWith(":stronghold") || id.endsWith(":strongholds")
                    || holder.value().biomes().stream().noneMatch(possibleBiomes::contains)) continue;
            int weight = weights.getOrDefault(id, 1);
            if (weight == 0) continue;
            holders.put(id, holder);
            options.add(new SkyvoidVoidStructureSelector.Candidate(id, weight, ctx -> true));
        }

        // Deterministic original-location lottery; Minecraft's native
        // tryGenerateStructure rejects structures invalid in the local biome.
        var biome = ((ChunkGenerator) (Object)this).getBiomeSource().getNoiseBiome(
                QuartPos.fromBlock(sourceChunkPos.getMiddleBlockX()), QuartPos.fromBlock(64),
                QuartPos.fromBlock(sourceChunkPos.getMiddleBlockZ()), randomState.sampler());
        var context = new SkyvoidVoidStructureSelector.Context(
                seed, dimension.identifier().toString(), sourceChunkPos.x(), sourceChunkPos.z(),
                biome.unwrapKey().map(k -> k.identifier().toString()).orElse("minecraft:unknown"),
                originId);
        skyvoid$nativeAttempt.set(true);
        try {
            var replacement = SkyvoidVoidStructureSelector.firstValid(context, options,
                    candidate -> skyvoid$tryNative(
                            new StructureSet.StructureSelectionEntry(holders.get(candidate.id()), 1),
                            structureManager, registryAccess, randomState, templateManager,
                            seed, centerChunk, sourceChunkPos, sectionPos, dimension));
            boolean retainedPlaceholder = false;
            if (weights.getOrDefault(originId, 1) == 0) {
                // Retain a genuine start and bounding box for a disabled
                // original; the separate placeInChunk mixin suppresses blocks.
                retainedPlaceholder = skyvoid$tryNative(selected, structureManager, registryAccess,
                        randomState, templateManager, seed, centerChunk, sourceChunkPos, sectionPos,
                        dimension);
            }
            // Tell vanilla that a valid start (replacement or placeholder)
            // was already stored. Returning false after storing a placeholder
            // could cause an extra structure to be selected from this set.
            cir.setReturnValue(replacement.isPresent() || retainedPlaceholder);
        } finally {
            skyvoid$nativeAttempt.remove();
        }
    }
}
