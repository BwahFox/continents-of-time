package dev.continentsoftime.atlas;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.continentsoftime.ContinentsOfTime;
import dev.continentsoftime.atlas.layout.ContinentLayout;
import dev.continentsoftime.atlas.layout.Footprint;
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

	/** Server start: create every era's providers for this seed, then lay the continents out. */
	public void init(long seed) {
		eras.forEach(era -> era.init(seed));
		if (eras.size() == 1) {
			this.layout = Layout.single();
			return;
		}
		List<Footprint> footprints = eras.stream().map(era -> era.footprint(settings.maxContinentSize())).toList();
		ContinentLayout continents = new ContinentLayout(seed, footprints, homeEra(), settings.maxContinentSize(), settings.oceanWidth());
		ContinentsOfTime.LOGGER.info("Continents of Time layout: {}", continents.describe());
		for (var seat : continents.seats()) {
			ContinentsOfTime.LOGGER.info("  {} in x {}..{}, z {}..{}{}", eras.get(seat.era()).id(),
				seat.minX(), seat.maxX(), seat.minZ(), seat.maxZ(), seat.shaped() ? "" : " (finite level)");
		}
		this.layout = continents;
	}

	/** The era whose continent holds the origin: the modern generator if it is on the roster, else the first era. */
	public int homeEra() {
		int index = settings.eras().indexOf(Eras.MODERN);
		return index < 0 ? 0 : index;
	}

	/**
	 * The era that generates the column. Ownership is per chunk (every generation step of a chunk goes to one
	 * hosted generator), so this is the owner of the chunk the column is in; open ocean belongs to the nearest
	 * continent's era until the atlas generates oceans itself.
	 */
	public HostedEra eraAtBlock(int blockX, int blockZ) {
		return eraAtChunk(blockX >> 4, blockZ >> 4);
	}

	public HostedEra eraAtChunk(int chunkX, int chunkZ) {
		return eras.get(layout.chunkOwner(chunkX, chunkZ));
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
		int column = layout.eraAt(pos.getX(), pos.getZ());
		lines.add("Continents of Time era: " + era.id() + (column == Layout.OCEAN ? " (ocean)" : ""));
		era.biomeSource().addDebugInfo(lines, pos, sampler);
	}
}
