package dev.continentsoftime.mixin;

import mod.bluestaggo.modernerbeta.api.level.chunk.ChunkProviderNoise;
import mod.bluestaggo.modernerbeta.settings.component.WorldBorderLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Read access to a noise provider's world border, to know whether the era is anchored on the origin. */
@Mixin(value = ChunkProviderNoise.class, remap = false)
public interface ChunkProviderNoiseAccessor {
	@Accessor("worldBorderLocation")
	WorldBorderLocation continentsoftime$worldBorder();
}
