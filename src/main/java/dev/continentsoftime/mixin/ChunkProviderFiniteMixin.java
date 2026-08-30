package dev.continentsoftime.mixin;

import dev.continentsoftime.atlas.Translated;
import mod.bluestaggo.modernerbeta.api.level.chunk.ChunkProviderFinite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Moves a Moderner Beta finite level (Classic, Indev) from the world origin to its seat.
 *
 * <p>The finite provider keeps its whole level in memory and anchors it on (0, 0): {@code inWorldBounds} and
 * {@code getHeight} take world coordinates and subtract half the level size, and {@code generateTerrain} reads
 * the level array at an offset derived from the chunk position. Shifting those three coordinate entries by the
 * seat's centre (chunk-aligned, so the chunk offsets stay whole) is enough for the level to generate around the
 * seat: everything else works in level or chunk-local coordinates, and the surface step's own bounds test goes
 * through {@code inWorldBounds} too.
 */
@Mixin(value = ChunkProviderFinite.class, remap = false)
public abstract class ChunkProviderFiniteMixin {
	private int continentsoftime$dx() {
		return ((Translated) this).continentsoftime$offsetX();
	}

	private int continentsoftime$dz() {
		return ((Translated) this).continentsoftime$offsetZ();
	}

	@ModifyVariable(method = "inWorldBounds", at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private int continentsoftime$boundsX(int x) {
		return x - continentsoftime$dx();
	}

	@ModifyVariable(method = "inWorldBounds", at = @At("HEAD"), argsOnly = true, ordinal = 1)
	private int continentsoftime$boundsZ(int z) {
		return z - continentsoftime$dz();
	}

	@ModifyVariable(method = "getHeight", at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private int continentsoftime$heightX(int x) {
		return x - continentsoftime$dx();
	}

	@ModifyVariable(method = "getHeight", at = @At("HEAD"), argsOnly = true, ordinal = 1)
	private int continentsoftime$heightZ(int z) {
		return z - continentsoftime$dz();
	}

	// generateTerrain(ChunkAccess, StructureManager): the level-array offsets are the third and fourth int locals
	// stored (after chunkX and chunkZ); shift them by the seat so the chunk reads the right slice of the level.
	@ModifyVariable(method = "generateTerrain", at = @At("STORE"), ordinal = 2)
	private int continentsoftime$terrainOffsetX(int offsetX) {
		return offsetX - continentsoftime$dx();
	}

	@ModifyVariable(method = "generateTerrain", at = @At("STORE"), ordinal = 3)
	private int continentsoftime$terrainOffsetZ(int offsetZ) {
		return offsetZ - continentsoftime$dz();
	}
}
