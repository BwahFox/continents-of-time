package dev.continentsoftime.mixin;

import dev.continentsoftime.atlas.Translated;
import mod.bluestaggo.modernerbeta.level.biome.provider.BiomeProviderFractal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Moves a fractal biome map to its seat. Legacy Console's map is bounded to a finite square around the origin
 * (an in-range predicate inside the layer stack); shifting every lookup's coordinates by the seat moves the
 * whole map, bounds included. Biome-coordinate entries shift by a quarter of the block offset (it is
 * chunk-aligned, so that is whole).
 */
@Mixin(value = BiomeProviderFractal.class, remap = false)
public abstract class BiomeProviderFractalMixin {
	private int continentsoftime$dx() {
		return ((Translated) this).continentsoftime$offsetX();
	}

	private int continentsoftime$dz() {
		return ((Translated) this).continentsoftime$offsetZ();
	}

	@ModifyVariable(method = {"getBiome", "getBiomeForStep", "getBiomeName", "getBiomeNameForStep"}, at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private int continentsoftime$biomeX(int biomeX) {
		return biomeX - (continentsoftime$dx() >> 2);
	}

	@ModifyVariable(method = {"getBiome", "getBiomeForStep", "getBiomeName", "getBiomeNameForStep"}, at = @At("HEAD"), argsOnly = true, ordinal = 2)
	private int continentsoftime$biomeZ(int biomeZ) {
		return biomeZ - (continentsoftime$dz() >> 2);
	}

	@ModifyVariable(method = "getBiomeBlock", at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private int continentsoftime$blockX(int x) {
		return x - continentsoftime$dx();
	}

	@ModifyVariable(method = "getBiomeBlock", at = @At("HEAD"), argsOnly = true, ordinal = 2)
	private int continentsoftime$blockZ(int z) {
		return z - continentsoftime$dz();
	}
}
