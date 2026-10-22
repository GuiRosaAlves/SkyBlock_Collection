package io.github.guirosaalves.skyvoid.mixin;

import io.github.guirosaalves.skyvoid.SkyvoidVoidStructureRuntime;
import net.minecraft.core.registries.Registries;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Preserve a real original start/box for disabled structures, but no blocks. */
@Mixin(StructureStart.class)
public abstract class SkyvoidStructureStartMixin {
    @Inject(method = "placeInChunk", at = @At("HEAD"), cancellable = true)
    private void skyvoid$voidDisabledOriginal(
            WorldGenLevel level, StructureManager structureManager,
            ChunkGenerator generator, RandomSource random, BoundingBox bounds,
            ChunkPos sourcePos, CallbackInfo ci) {
        var settings = SkyvoidVoidStructureRuntime.current();
        if (!settings.enabled()
                || !"skyvoid_worldgen_structures".equals(settings.worldgen())) return;
        var registry = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
        var id = registry.getKey(((StructureStart)(Object)this).getStructure());
        if (id != null && settings.weights()
                .getOrDefault(id.toString(), 1) == 0) ci.cancel();
    }
}
