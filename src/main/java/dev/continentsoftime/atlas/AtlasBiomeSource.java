package dev.continentsoftime.atlas;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.continentsoftime.atlas.layout.Layout;
import mod.bluestaggo.modernerbeta.registry.ModernBetaResourceKeys;
import mod.bluestaggo.modernerbeta.util.CodecUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;

import java.util.List;
import java.util.stream.Stream;

/**
 * The atlas's biome source: owns the era roster as {@link HostedEra}s and the {@link Layout}, and answers every
 * biome query by asking the era that owns the column. The {@link AtlasChunkGenerator} is built around it.
 */
public class AtlasBiomeSource extends BiomeSource {
	public static final MapCodec<AtlasBiomeSource> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
		AtlasSettings.CODEC.fieldOf("settings").forGetter(s -> s.settings),
		RegistryOps.retrieveGetter(Registries.BIOME),
		RegistryOps.retrieveGetter(ModernBetaResourceKeys.SETTINGS_PRESET),
		CodecUtil.retrieveLookup(ModernBetaResourceKeys.SURFACE_CONFIG),
		RegistryOps.retrieveGetter(Registries.NOISE_SETTINGS),
		RegistryOps.retrieveGetter(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST),
		CodecUtil.registryLookupCodec()
	).apply(i, i.stable((settings, biomes, presets, surfaceConfigs, noiseSettings, parameterLists, lookup) ->
		new AtlasBiomeSource(settings, new HostedEra.Registries26(biomes, presets, surfaceConfigs, noiseSettings, parameterLists, lookup)))));

	private final AtlasSettings settings;
	private final List<HostedEra> eras;
	private Layout layout;

	public AtlasBiomeSource(AtlasSettings settings, HostedEra.Registries26 registries) {
		this.settings = settings;
		this.eras = settings.eras().stream().map(id -> HostedEra.create(id, registries)).toList();
		this.layout = Layout.single();
	}

	public AtlasSettings settings() {
		return settings;
	}

	public List<HostedEra> eras() {
		return eras;
	}

	public Layout layout() {
		return layout;
	}

	/** Server start: create every era's providers for this seed and fix the layout. */
	public void init(long seed) {
		eras.forEach(era -> era.init(seed));
		this.layout = eras.size() == 1 ? Layout.single() : Layout.single(); // the real layout lands with the atlas milestone
	}

	public HostedEra eraAtBlock(int blockX, int blockZ) {
		return eras.get(layout.eraAt(blockX, blockZ));
	}

	public HostedEra eraAtChunk(int chunkX, int chunkZ) {
		return eraAtBlock((chunkX << 4) + 8, (chunkZ << 4) + 8);
	}

	@Override
	protected MapCodec<? extends BiomeSource> codec() {
		return CODEC;
	}

	@Override
	protected Stream<Holder<Biome>> collectPossibleBiomes() {
		return eras.stream().flatMap(era -> era.biomeSource().possibleBiomes().stream()).distinct();
	}

	@Override
	public Holder<Biome> getNoiseBiome(int biomeX, int biomeY, int biomeZ, Climate.Sampler sampler) {
		return eraAtBlock(QuartPos.toBlock(biomeX), QuartPos.toBlock(biomeZ)).biomeSource().getNoiseBiome(biomeX, biomeY, biomeZ, sampler);
	}

	@Override
	public void addDebugInfo(List<String> lines, BlockPos pos, Climate.Sampler sampler) {
		HostedEra era = eraAtBlock(pos.getX(), pos.getZ());
		lines.add("Continents of Time era: " + era.id());
		era.biomeSource().addDebugInfo(lines, pos, sampler);
	}
}
