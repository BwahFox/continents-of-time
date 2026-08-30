package dev.continentsoftime.mixin;

import dev.continentsoftime.atlas.Translated;
import mod.bluestaggo.modernerbeta.api.level.chunk.ChunkProviderNoise;
import mod.bluestaggo.modernerbeta.settings.component.WorldBorderLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Moves a Moderner Beta noise era's <em>world border</em> (Legacy Console: a finite square with an ocean
 * falloff, centred on the origin) to its seat. The noise terrain itself is position-agnostic; only the border
 * tests take world coordinates. Three of them live here, two more in {@link ChunkProviderNoise3DMixin}.
 */
@Mixin(value = ChunkProviderNoise.class, remap = false)
public abstract class ChunkProviderNoiseMixin {
	private int continentsoftime$dx() {
		return ((Translated) this).continentsoftime$offsetX();
	}

	private int continentsoftime$dz() {
		return ((Translated) this).continentsoftime$offsetZ();
	}

	@Redirect(method = "skipChunk", at = @At(value = "INVOKE", target = "Lmod/bluestaggo/modernerbeta/settings/component/WorldBorderLocation;containsChunk(II)Z"))
	private boolean continentsoftime$containsChunk(WorldBorderLocation border, int chunkX, int chunkZ) {
		return border.containsChunk(chunkX - (continentsoftime$dx() >> 4), chunkZ - (continentsoftime$dz() >> 4));
	}

	@Redirect(method = "modifyEdgeDensity", at = @At(value = "INVOKE", target = "Lmod/bluestaggo/modernerbeta/settings/component/WorldBorderLocation;modifyDensity(DII)D"))
	private double continentsoftime$modifyDensity(WorldBorderLocation border, double density, int x, int z) {
		return border.modifyDensity(density, x - continentsoftime$dx(), z - continentsoftime$dz());
	}

	@Redirect(method = "lambda$getBaseBlockSource$0", at = @At(value = "INVOKE", target = "Lmod/bluestaggo/modernerbeta/settings/component/WorldBorderLocation;containsPoint(II)Z"))
	private boolean continentsoftime$blockSourceContains(WorldBorderLocation border, int x, int z) {
		return border.containsPoint(x - continentsoftime$dx(), z - continentsoftime$dz());
	}
}
