package dev.continentsoftime.mixin;

import dev.continentsoftime.atlas.Translated;
import mod.bluestaggo.modernerbeta.level.chunk.provider.ChunkProviderNoise3D;
import mod.bluestaggo.modernerbeta.settings.component.WorldBorderLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** The surface steps' world-border tests, shifted to the seat (see {@link ChunkProviderNoiseMixin}). */
@Mixin(value = ChunkProviderNoise3D.class, remap = false)
public abstract class ChunkProviderNoise3DMixin {
	@Redirect(method = {"provideSurface", "provideSurfaceExtra"}, at = @At(value = "INVOKE", target = "Lmod/bluestaggo/modernerbeta/settings/component/WorldBorderLocation;containsPoint(II)Z"))
	private boolean continentsoftime$surfaceContains(WorldBorderLocation border, int x, int z) {
		Translated translated = (Translated) this;
		return border.containsPoint(x - translated.continentsoftime$offsetX(), z - translated.continentsoftime$offsetZ());
	}
}
