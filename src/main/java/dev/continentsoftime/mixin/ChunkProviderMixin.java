package dev.continentsoftime.mixin;

import dev.continentsoftime.atlas.Translated;
import mod.bluestaggo.modernerbeta.api.level.chunk.ChunkProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** Holds the atlas's translation offset on every chunk provider (zero unless the atlas sets it). */
@Mixin(value = ChunkProvider.class, remap = false)
public abstract class ChunkProviderMixin implements Translated {
	@Unique
	private int continentsoftime$offsetX;
	@Unique
	private int continentsoftime$offsetZ;

	@Override
	public void continentsoftime$translateTo(int centerX, int centerZ) {
		if ((centerX & 15) != 0 || (centerZ & 15) != 0) {
			throw new IllegalArgumentException("Translation offset must be chunk-aligned: " + centerX + "," + centerZ);
		}
		this.continentsoftime$offsetX = centerX;
		this.continentsoftime$offsetZ = centerZ;
	}

	@Override
	public int continentsoftime$offsetX() {
		return continentsoftime$offsetX;
	}

	@Override
	public int continentsoftime$offsetZ() {
		return continentsoftime$offsetZ;
	}
}
