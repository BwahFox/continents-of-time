package dev.continentsoftime.mixin;

import mod.bluestaggo.modernerbeta.api.level.chunk.AquiferSamplerProvider;
import mod.bluestaggo.modernerbeta.util.random.BedrockRandomSource;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Aquifers on the Pocket/Bedrock eras get a vanilla random. Moderner Beta forks the aquifer's positional random
 * from the era's own source, which for presets with the Bedrock RNG is its {@code BedrockRandomSource} — a type of
 * its own. Vanilla does not care, but C2ME's aquifer optimisation ({@code optimizeAquifer}, on by default)
 * recognises only vanilla's two positional factories and throws on anything else, so every chunk of those eras
 * failed to generate under C2ME ("Failed to load chunk … IllegalArgumentException at RandomUtils.getRandom").
 * The swap happens only when the factory is the Bedrock one and only for the aquifer — the terrain, caves and
 * surface of those eras keep their own random; aquifers are a modern feature the Bedrock RNG was never
 * emulating anyway. Seeded from the Bedrock factory itself, so it stays a pure function of the world seed.
 */
@Mixin(value = AquiferSamplerProvider.class, remap = false)
public abstract class AquiferSamplerProviderMixin {
	@Shadow
	@Final
	@Mutable
	private PositionalRandomFactory randomFactory;

	// HEAD of the one aquifer-producing method, not the constructor: the class has two constructors, and a bare
	// "<init>" target is ambiguous (Mixin drops it), while picking one by descriptor would write vanilla type
	// names into this remap=false mixin, which the 1.20.1 remap would then leave wrong. The swap is idempotent
	// and the instanceof costs nothing per chunk.
	@Inject(method = "provideAquiferSampler", at = @At("HEAD"))
	private void continentsoftime$vanillaRandomForAquifers(CallbackInfoReturnable<net.minecraft.world.level.levelgen.Aquifer> cir) {
		if (this.randomFactory instanceof BedrockRandomSource.BedrockPositionalRandomFactory bedrock) {
			this.randomFactory = new LegacyRandomSource(bedrock.at(0, 0, 0).nextLong()).forkPositional();
		}
	}
}
