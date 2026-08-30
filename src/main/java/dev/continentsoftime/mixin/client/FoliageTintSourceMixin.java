package dev.continentsoftime.mixin.client;

import dev.continentsoftime.client.ClientAtlas;
import dev.continentsoftime.client.ContinentClimate;
import mod.bluestaggo.modernerbeta.client.color.block.FoliageTintSource;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Makes Moderner Beta's foliage tint positional; see {@link GrassBlockTintSourceMixin}. */
@Mixin(value = FoliageTintSource.class, remap = false)
public abstract class FoliageTintSourceMixin {
	@Inject(method = "colorInWorld", at = @At("HEAD"), cancellable = true)
	private void continentsoftime$vanillaOutsideClimateEras(BlockState state, BlockAndTintGetter level, BlockPos pos, CallbackInfoReturnable<Integer> cir) {
		ContinentClimate climate = ClientAtlas.current();
		if (climate != null && !climate.tintsVegetation(pos.getX(), pos.getZ())) {
			cir.setReturnValue(BiomeColors.getAverageFoliageColor(level, pos));
		}
	}
}
