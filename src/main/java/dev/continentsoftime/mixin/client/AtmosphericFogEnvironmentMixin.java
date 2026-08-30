package dev.continentsoftime.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.continentsoftime.client.ClientAtlas;
import dev.continentsoftime.client.ContinentClimate;
import mod.bluestaggo.modernerbeta.ModernerBeta;
import mod.bluestaggo.modernerbeta.client.FogUtils;
import mod.bluestaggo.modernerbeta.settings.SettingsComponentTypes;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.environment.AtmosphericFogEnvironment;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Moderner Beta's old fog colour weighting, per continent: Moderner Beta applies it level-wide when the level is
 * one of its own (never the case for an atlas). This wraps the same power call in the fog colour computation and
 * applies Moderner Beta's weight when the camera stands on one of its continents, honouring its config switch;
 * the modern continent and the open sea keep vanilla's weighting.
 */
@Mixin(AtmosphericFogEnvironment.class)
public abstract class AtmosphericFogEnvironmentMixin {
	@WrapOperation(
		method = "getBaseColor",
		at = @At(value = "INVOKE", target = "Ljava/lang/Math;pow(DD)D", remap = false)
	)
	private double continentsoftime$oldFogOnOldContinents(double base, double exponent, Operation<Double> original,
	                                                       ClientLevel level, Camera camera, int renderDistance, float partialTick) {
		ContinentClimate climate = ClientAtlas.current();
		if (climate != null && ModernerBeta.config.getOrDefault(SettingsComponentTypes.CONFIG_MISCELLANEOUS).oldFogColorWeighting()) {
			Vec3 pos = camera.position();
			if (climate.oldFog(Mth.floor(pos.x()), Mth.floor(pos.z()))) {
				return original.call((double) FogUtils.calculateFogWeight(renderDistance, camera, partialTick), exponent);
			}
		}
		return original.call(base, exponent);
	}
}
