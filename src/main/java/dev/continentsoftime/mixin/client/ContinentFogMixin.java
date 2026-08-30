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
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
//? if <1.21.11 {
/*import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
*///?}

/**
 * Moderner Beta's old fog colour weighting, per continent: Moderner Beta applies it level-wide when the level is
 * one of its own (never the case for an atlas). This wraps the same power call in the fog colour computation and
 * applies Moderner Beta's weight when the camera stands on one of its continents, honouring its config switch;
 * the modern continent and the open sea keep vanilla's weighting.
 *
 * <p>The fog colour is computed by {@code AtmosphericFogEnvironment.getBaseColor} from 1.21.11 and by the static
 * {@code FogRenderer.setupColor} before; the older one has no camera argument at the wrapped call, so it is
 * captured at the method's head, the way Moderner Beta's own mixin does.
 */
//? if >=1.21.11 {
@Mixin(net.minecraft.client.renderer.fog.environment.AtmosphericFogEnvironment.class)
public abstract class ContinentFogMixin {
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
//?} else {
/*@Mixin(net.minecraft.client.renderer.FogRenderer.class)
public abstract class ContinentFogMixin {
	@Unique
	private static Vec3 continentsoftime$cameraPos = Vec3.ZERO;
	@Unique
	private static int continentsoftime$renderDistance = 16;

	@Inject(method = "setupColor", at = @At("HEAD"))
	private static void continentsoftime$captureCamera(Camera camera, float partialTick, ClientLevel level, int renderDistance, float darkenWorldAmount, CallbackInfo ci) {
		continentsoftime$cameraPos = camera.getPosition();
		continentsoftime$renderDistance = renderDistance;
	}

	@WrapOperation(
		method = "setupColor",
		at = @At(value = "INVOKE", target = "Ljava/lang/Math;pow(DD)D", remap = false)
	)
	private static double continentsoftime$oldFogOnOldContinents(double base, double exponent, Operation<Double> original) {
		ContinentClimate climate = ClientAtlas.current();
		if (climate != null && ModernerBeta.config.getOrDefault(SettingsComponentTypes.CONFIG_MISCELLANEOUS).oldFogColorWeighting()) {
			Vec3 pos = continentsoftime$cameraPos;
			if (climate.oldFog(Mth.floor(pos.x()), Mth.floor(pos.z()))) {
				return original.call((double) FogUtils.calculateFogWeight(continentsoftime$renderDistance), exponent);
			}
		}
		return original.call(base, exponent);
	}
}
*///?}
