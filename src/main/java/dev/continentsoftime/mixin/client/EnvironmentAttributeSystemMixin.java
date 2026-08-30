package dev.continentsoftime.mixin.client;

import dev.continentsoftime.client.ClientAtlas;
import dev.continentsoftime.client.ContinentClimate;
import mod.bluestaggo.modernerbeta.client.color.SkyColorSampler;
import net.minecraft.util.Mth;
import net.minecraft.world.attribute.EnvironmentAttributeSystem;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The per-continent sky: a positional layer over the biome sky colour, added where Moderner Beta adds its own.
 * Moderner Beta's layer stays inert on an atlas (the composite climate reports no sky colouring); this one asks
 * the climate whether the camera's column is on a continent with a climate sky and, if so, takes Moderner Beta's
 * sky colour for it — computed by its sampler from the composite, so the era's own climate decides the hue.
 */
@Mixin(EnvironmentAttributeSystem.class)
public abstract class EnvironmentAttributeSystemMixin {
	@Inject(
		method = "addDefaultLayers",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/attribute/EnvironmentAttributeSystem;addBiomeLayer(Lnet/minecraft/world/attribute/EnvironmentAttributeSystem$Builder;Lnet/minecraft/core/HolderLookup;Lnet/minecraft/world/level/biome/BiomeManager;)V",
			shift = At.Shift.AFTER
		)
	)
	private static void continentsoftime$addContinentSky(EnvironmentAttributeSystem.Builder builder, Level level, CallbackInfo ci) {
		builder.addPositionalLayer(EnvironmentAttributes.SKY_COLOR, (color, pos, interpolator) -> {
			ContinentClimate climate = ClientAtlas.current();
			SkyColorSampler sampler = SkyColorSampler.INSTANCE;
			if (climate != null && sampler.getClimateSampler() == climate && climate.tintsSky(Mth.floor(pos.x()), Mth.floor(pos.z()))) {
				return sampler.getSkyColor(pos);
			}
			return color;
		});
	}
}
