package dev.continentsoftime.atlas;

import dev.continentsoftime.atlas.layout.Footprint;
import dev.continentsoftime.mixin.ChunkProviderNoiseAccessor;
import mod.bluestaggo.modernerbeta.api.level.chunk.ChunkProviderFinite;
import mod.bluestaggo.modernerbeta.api.level.chunk.surface.SurfaceConfig;
import mod.bluestaggo.modernerbeta.level.biome.ModernBetaBiomeSource;
import mod.bluestaggo.modernerbeta.level.chunk.ModernBetaChunkGenerator;
import mod.bluestaggo.modernerbeta.settings.ModernBetaSettings;
import mod.bluestaggo.modernerbeta.settings.ModernBetaSettingsPreset;
import dev.continentsoftime.atlas.timeline.EraCaveBiomes;
import dev.continentsoftime.atlas.timeline.EraVersion;
import java.util.List;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterList;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterLists;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;

/**
 * One era's complete generator, hosted inside the atlas: a whole {@link ChunkGenerator} with its own
 * {@link BiomeSource}, exactly as that era's world type would build it on its own. The atlas routes each chunk to
 * one of these; the era code never knows it is sharing a world.
 *
 * <p>Moderner Beta eras are a {@link ModernBetaChunkGenerator} over one of its settings presets; the modern era is
 * vanilla's own noise generator.
 */
public record HostedEra(Identifier id, ChunkGenerator generator, BiomeSource biomeSource, List<String> caveBiomes) {

	/** Registries the atlas needs to build hosted eras; supplied by the biome source's codec. */
	public record Registries26(
		HolderGetter<Biome> biomes,
		HolderGetter<ModernBetaSettingsPreset> presets,
		HolderGetter<SurfaceConfig> surfaceConfigs,
		HolderGetter<NoiseGeneratorSettings> noiseSettings,
		HolderGetter<MultiNoiseBiomeSourceParameterList> parameterLists,
		RegistryOps.RegistryInfoLookup lookup
	) {}

	/**
	 * @param eraAccurate whether to keep underground biomes newer than the era out of its continent (see
	 *                    {@link EraCaveBiomes}); structures are filtered per chunk by the generator instead
	 */
	public static HostedEra create(Identifier era, Registries26 registries, boolean eraAccurate) {
		if (Eras.isVanilla(era)) {
			BiomeSource biomeSource = MultiNoiseBiomeSource.createFromPreset(
				registries.parameterLists().getOrThrow(MultiNoiseBiomeSourceParameterLists.OVERWORLD));
			ChunkGenerator generator = new NoiseBasedChunkGenerator(biomeSource,
				registries.noiseSettings().getOrThrow(ResourceKey.create(Registries.NOISE_SETTINGS, era)));
			return new HostedEra(era, generator, biomeSource, List.of("(vanilla: all)"));
		}

		// A preset reference: Moderner Beta resolves the preset's three settings groups lazily, by id, through its
		// preset registry, the same way its own world screen does.
		ModernBetaSettingsPreset reference = ModernBetaSettingsPreset.referenced(era, registries.lookup());
		ModernBetaSettings caveSettings = reference.caveBiomeSettings();
		ModernBetaSettings resolvedCaves = caveSettings.mapPreset(registries.presets(), ModernBetaSettingsPreset::caveBiomeSettings);
		if (eraAccurate) {
			caveSettings = EraCaveBiomes.filter(caveSettings, resolvedCaves, EraVersion.of(era));
			resolvedCaves = caveSettings.mapPreset(registries.presets(), ModernBetaSettingsPreset::caveBiomeSettings);
		}
		ModernBetaBiomeSource biomeSource = new ModernBetaBiomeSource(
			registries.biomes(), registries.presets(), reference.biomeSettings(), caveSettings);
		ModernBetaChunkGenerator generator = new ModernBetaChunkGenerator(
			biomeSource, registries.presets(), registries.surfaceConfigs(), reference.chunkSettings());
		return new HostedEra(era, generator, biomeSource, EraCaveBiomes.biomesIn(resolvedCaves));
	}

	/** Moderner Beta creates its providers at server start (it hooks the server for its own generators); we do the same for ours. */
	public void init(long seed) {
		if (biomeSource instanceof ModernBetaBiomeSource mb) {
			mb.initProvider(seed);
		}
		if (generator instanceof ModernBetaChunkGenerator mb) {
			mb.initProvider(seed);
		}
	}

	/**
	 * Move an anchored era's origin to its seat: its chunk provider and its biome provider both learn the offset
	 * (see {@link Translated}). A no-op for eras that are not anchored. Call after {@link #init}.
	 */
	public void translateTo(int centerX, int centerZ) {
		if (!footprint(Integer.MAX_VALUE).anchored()) {
			return;
		}
		if (generator instanceof ModernBetaChunkGenerator mb && mb.getChunkProvider() instanceof Translated translated) {
			translated.continentsoftime$translateTo(centerX, centerZ);
		}
		if (biomeSource instanceof ModernBetaBiomeSource mb && mb.getBiomeProvider() instanceof Translated translated) {
			translated.continentsoftime$translateTo(centerX, centerZ);
		}
	}

	/**
	 * What the layout needs to seat this era. Finite eras (Classic, Indev) are their level's own size and are
	 * seated as-is; a noise era with an enabled world border (Legacy Console) is shaped inside that border; every
	 * other era is shaped inside a box of the configured maximum. Valid after {@link #init}.
	 */
	public Footprint footprint(int maxContinentSize) {
		if (generator instanceof ModernBetaChunkGenerator mb) {
			if (mb.getChunkProvider() instanceof ChunkProviderFinite finite) {
				return Footprint.finite(finite.getLevelWidth(), finite.getLevelLength());
			}
			if (mb.getChunkProvider() instanceof ChunkProviderNoiseAccessor noise
				&& noise.continentsoftime$worldBorder() != null && noise.continentsoftime$worldBorder().enabled()) {
				return Footprint.bordered(Math.min(noise.continentsoftime$worldBorder().width(), maxContinentSize));
			}
		}
		return Footprint.shaped(maxContinentSize);
	}
}
