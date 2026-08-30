package dev.continentsoftime.mixin;

import dev.continentsoftime.atlas.Translated;
import mod.bluestaggo.modernerbeta.level.biome.injection.BiomeInjectionContext;
import mod.bluestaggo.modernerbeta.level.biome.injection.predicates.OutOfBoundsInjectionPredicate;
import mod.bluestaggo.modernerbeta.settings.component.WorldBorderLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * The biome side of moving an anchored era: Moderner Beta paints {@code the_void} outside a finite level (and
 * outside a bordered world) by testing the column against the preset's world border, centred on the origin.
 * Test relative to the era's seat instead.
 */
@Mixin(value = OutOfBoundsInjectionPredicate.class, remap = false)
public abstract class OutOfBoundsInjectionPredicateMixin {

	@Redirect(
		method = "shouldApply",
		at = @At(value = "INVOKE", target = "Lmod/bluestaggo/modernerbeta/settings/component/WorldBorderLocation;containsPoint(III)Z")
	)
	private boolean continentsoftime$containsPointAtSeat(WorldBorderLocation border, int x, int z, int margin, BiomeInjectionContext context) {
		if (context.chunkGenerator != null && context.chunkGenerator.getChunkProvider() instanceof Translated translated) {
			return border.containsPoint(x - translated.continentsoftime$offsetX(), z - translated.continentsoftime$offsetZ(), margin);
		}
		return border.containsPoint(x, z, margin);
	}
}
