package dev.continentsoftime.mixin.client;

import dev.continentsoftime.client.ClientAtlas;
import dev.continentsoftime.client.ContinentClimate;
import mod.bluestaggo.modernerbeta.client.color.SkyColorSampler;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
//? if >=1.21.11 {
import net.minecraft.world.attribute.EnvironmentAttributeSystem;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//?} else {
/*import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
*///?}

/**
 * The per-continent sky. Moderner Beta's own sky hook stays inert on an atlas (the composite climate reports no
 * sky colouring); this one asks the climate whether the camera's column is on a continent with a climate sky and,
 * if so, takes Moderner Beta's sky colour for it — computed by its sampler from the composite, so the era's own
 * climate decides the hue.
 *
 * <p>From 1.21.11 the sky colour is an environment attribute: a positional layer over the biome sky colour, added
 * where Moderner Beta adds its own. Before, {@code ClientLevel.getSkyColor} samples the biome colour into a local;
 * that local is replaced, at the same point Moderner Beta's mixin replaces it for its own levels.
 */
//? if >=1.21.11 {
@Mixin(EnvironmentAttributeSystem.class)
public abstract class ContinentSkyMixin {
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
//?} else {
/*@Mixin(ClientLevel.class)
public abstract class ContinentSkyMixin {
	// The second Vec3 stored in getSkyColor is the biome sky colour sampled around the camera (the first is the
	// sampling position); Moderner Beta's mixin replaces the same local for its own levels.
	@ModifyVariable(method = "getSkyColor", at = @At("STORE"), ordinal = 1)
	private Vec3 continentsoftime$continentSky(Vec3 biomeColor, Vec3 pos, float partialTick) {
		ContinentClimate climate = ClientAtlas.current();
		SkyColorSampler sampler = SkyColorSampler.INSTANCE;
		if (climate != null && sampler.getClimateSampler() == climate && climate.tintsSky(Mth.floor(pos.x()), Mth.floor(pos.z()))) {
			return sampler.getSkyColor(pos);
		}
		return biomeColor;
	}
}
*///?}
