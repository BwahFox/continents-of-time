package dev.continentsoftime.mixin.client;

import dev.continentsoftime.client.ClientAtlas;
import dev.continentsoftime.client.ContinentClimate;
import mod.bluestaggo.modernerbeta.client.color.block.GrassBlockTintSource;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Makes Moderner Beta's grass tint positional: outside a climate era's continent the block takes vanilla's
 * blended biome colour, exactly what Moderner Beta returns when its tinting is off. Also covers short grass and
 * ferns, whose tint source extends this one and calls up into it.
 */
@Mixin(value = GrassBlockTintSource.class, remap = false)
public abstract class GrassBlockTintSourceMixin {
	// Moderner Beta's tint source is a BlockTintSource from 26.1 (colorInWorld) and a BlockColor before (getColor,
	// where level and pos may be null for an inventory block; that path is left to Moderner Beta).
	//? if >=26.1 {
	@Inject(method = "colorInWorld", at = @At("HEAD"), cancellable = true)
	private void continentsoftime$vanillaOutsideClimateEras(BlockState state, BlockAndTintGetter level, BlockPos pos, CallbackInfoReturnable<Integer> cir) {
		ContinentClimate climate = ClientAtlas.current();
		if (climate != null && !climate.tintsVegetation(pos.getX(), pos.getZ())) {
			cir.setReturnValue(BiomeColors.getAverageGrassColor(level, pos));
		}
	}
	//?} else {
	/*@Inject(method = "getColor", at = @At("HEAD"), cancellable = true)
	private void continentsoftime$vanillaOutsideClimateEras(BlockState state, BlockAndTintGetter level, BlockPos pos, int tintIndex, CallbackInfoReturnable<Integer> cir) {
		ContinentClimate climate = ClientAtlas.current();
		if (climate != null && level != null && pos != null && !climate.tintsVegetation(pos.getX(), pos.getZ())) {
			cir.setReturnValue(BiomeColors.getAverageGrassColor(level, pos));
		}
	}
	*///?}
}
