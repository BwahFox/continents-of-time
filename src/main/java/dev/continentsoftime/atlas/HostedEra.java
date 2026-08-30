package dev.continentsoftime.atlas;

import dev.continentsoftime.atlas.layout.Footprint;
import mod.bluestaggo.modernerbeta.api.level.chunk.ChunkProviderFinite;
import mod.bluestaggo.modernerbeta.api.level.chunk.surface.SurfaceConfig;
import mod.bluestaggo.modernerbeta.level.biome.ModernBetaBiomeSource;
import mod.bluestaggo.modernerbeta.level.chunk.ModernBetaChunkGenerator;
import mod.bluestaggo.modernerbeta.settings.ModernBetaSettingsPreset;
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
public record HostedEra(Identifier id, ChunkGenerator generator, BiomeSource biomeSource) {

	/** Registries the atlas needs to build hosted eras; supplied by the biome source's codec. */
	public record Registries26(
		HolderGetter<Biome> biomes,
		HolderGetter<ModernBetaSettingsPreset> presets,
		HolderGetter<SurfaceConfig> surfaceConfigs,
		HolderGetter<NoiseGeneratorSettings> noiseSettings,
		HolderGetter<MultiNoiseBiomeSourceParameterList> parameterLists,
		RegistryOps.RegistryInfoLookup lookup
	) {}

	public static HostedEra create(Identifier era, Registries26 registries) {
		if (Eras.isVanilla(era)) {
			BiomeSource biomeSource = MultiNoiseBiomeSource.createFromPreset(
				registries.parameterLists().getOrThrow(MultiNoiseBiomeSourceParameterLists.OVERWORLD));
			ChunkGenerator generator = new NoiseBasedChunkGenerator(biomeSource,
				registries.noiseSettings().getOrThrow(ResourceKey.create(Registries.NOISE_SETTINGS, era)));
			return new HostedEra(era, generator, biomeSource);
		}

		// A preset reference: Moderner Beta resolves the preset's three settings groups lazily, by id, through its
		// preset registry, the same way its own world screen does.
		ModernBetaSettingsPreset reference = ModernBetaSettingsPreset.referenced(era, registries.lookup());
		ModernBetaBiomeSource biomeSource = new ModernBetaBiomeSource(
			registries.biomes(), registries.presets(), reference.biomeSettings(), reference.caveBiomeSettings());
		ModernBetaChunkGenerator generator = new ModernBetaChunkGenerator(
			biomeSource, registries.presets(), registries.surfaceConfigs(), reference.chunkSettings());
		return new HostedEra(era, generator, biomeSource);
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
	 * What the layout needs to seat this era. Finite eras (Classic, Indev) are their level's own size and are
	 * seated as-is; every other era is shaped inside a box of the configured maximum. Valid after {@link #init}.
	 */
	public Footprint footprint(int maxContinentSize) {
		if (generator instanceof ModernBetaChunkGenerator mb && mb.getChunkProvider() instanceof ChunkProviderFinite finite) {
			return Footprint.finite(finite.getLevelWidth(), finite.getLevelLength());
		}
		return Footprint.shaped(maxContinentSize);
	}
}
