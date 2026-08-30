package dev.continentsoftime.atlas;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.continentsoftime.ContinentsOfTime;
import dev.continentsoftime.atlas.layout.ContinentLayout;
import dev.continentsoftime.atlas.layout.Footprint;
import dev.continentsoftime.atlas.layout.Layout;
import dev.continentsoftime.atlas.layout.Seabed;
import mod.bluestaggo.modernerbeta.registry.ModernBetaResourceKeys;
import mod.bluestaggo.modernerbeta.util.CodecUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.Climate;
import org.jspecify.annotations.Nullable;

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
	private final OceanBiomes oceanBiomes;
	private Layout layout;

	/** The modern ocean, picked by temperature like the overworld does, and deep or shallow by the coast field. */
	private record OceanBiomes(Holder<Biome> frozen, Holder<Biome> cold, Holder<Biome> normal, Holder<Biome> lukewarm, Holder<Biome> warm,
	                           Holder<Biome> deepFrozen, Holder<Biome> deepCold, Holder<Biome> deepNormal, Holder<Biome> deepLukewarm) {
		static OceanBiomes from(HostedEra.Registries26 registries) {
			var biomes = registries.biomes();
			return new OceanBiomes(
				biomes.getOrThrow(Biomes.FROZEN_OCEAN), biomes.getOrThrow(Biomes.COLD_OCEAN), biomes.getOrThrow(Biomes.OCEAN),
				biomes.getOrThrow(Biomes.LUKEWARM_OCEAN), biomes.getOrThrow(Biomes.WARM_OCEAN),
				biomes.getOrThrow(Biomes.DEEP_FROZEN_OCEAN), biomes.getOrThrow(Biomes.DEEP_COLD_OCEAN),
				biomes.getOrThrow(Biomes.DEEP_OCEAN), biomes.getOrThrow(Biomes.DEEP_LUKEWARM_OCEAN));
		}

		/** Vanilla's overworld temperature bands for oceans; warm ocean has no deep variant, like vanilla. */
		Holder<Biome> pick(double temperature, boolean deep) {
			if (temperature < -0.45) return deep ? deepFrozen : frozen;
			if (temperature < -0.15) return deep ? deepCold : cold;
			if (temperature < 0.2) return deep ? deepNormal : normal;
			if (temperature < 0.55) return deep ? deepLukewarm : lukewarm;
			return warm;
		}

		Stream<Holder<Biome>> all() {
			return Stream.of(frozen, cold, normal, lukewarm, warm, deepFrozen, deepCold, deepNormal, deepLukewarm);
		}
	}

	public AtlasBiomeSource(AtlasSettings settings, HostedEra.Registries26 registries) {
		this.settings = settings;
		this.eras = settings.eras().stream().map(id -> HostedEra.create(id, registries)).toList();
		this.oceanBiomes = OceanBiomes.from(registries);
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
			if (footprints.get(seat.era()).anchored()) {
				eras.get(seat.era()).translateTo(seat.centerX(), seat.centerZ());
			}
		}
		this.layout = continents;
	}

	/** The modern era, if it is on the roster (its generator decorates ocean chunks). */
	public @Nullable HostedEra modernEra() {
		int index = settings.eras().indexOf(Eras.MODERN);
		return index < 0 ? null : eras.get(index);
	}

	/** The era whose continent holds the origin: the modern generator if it is on the roster, else the first era. */
	public int homeEra() {
		int index = settings.eras().indexOf(Eras.MODERN);
		return index < 0 ? 0 : index;
	}

	/**
	 * The era that generates the chunk, or {@code null} for an ocean chunk (no land column at all), which the
	 * atlas generates itself. Ownership is per chunk: every generation step of a chunk goes to one generator.
	 */
	public @Nullable HostedEra ownerOfChunk(int chunkX, int chunkZ) {
		int owner = layout.chunkOwner(chunkX, chunkZ);
		return owner == Layout.OCEAN ? null : eras.get(owner);
	}

	/** The era whose generator answers for the column: the chunk's owner on land, the nearest continent's at sea. */
	public HostedEra eraAtBlock(int blockX, int blockZ) {
		HostedEra owner = ownerOfChunk(blockX >> 4, blockZ >> 4);
		return owner != null ? owner : eras.get(layout.nearestEraAt(blockX, blockZ));
	}

	/** Whether the column is open sea in the layout (its terrain is the seabed, its biome the modern ocean). */
	public boolean isSea(int blockX, int blockZ) {
		return layout.eraAt(blockX, blockZ) == Layout.OCEAN;
	}

	/** The modern ocean biome for a sea column: temperature from the sampler, deep or shallow from the coast field. */
	public Holder<Biome> oceanBiome(int biomeX, int biomeY, int biomeZ, Climate.Sampler sampler) {
		int x = QuartPos.toBlock(biomeX);
		int z = QuartPos.toBlock(biomeZ);
		double temperature = Climate.unquantizeCoord(sampler.sample(biomeX, biomeY, biomeZ).temperature());
		return oceanBiomes.pick(temperature, Seabed.deep(layout.fieldAt(x, z)));
	}

	@Override
	protected MapCodec<? extends BiomeSource> codec() {
		return CODEC;
	}

	@Override
	protected Stream<Holder<Biome>> collectPossibleBiomes() {
		return Stream.concat(eras.stream().flatMap(era -> era.biomeSource().possibleBiomes().stream()), oceanBiomes.all()).distinct();
	}

	/** Sea columns are the modern ocean (it simply stops at the coast); land columns ask the owning era. */
	@Override
	public Holder<Biome> getNoiseBiome(int biomeX, int biomeY, int biomeZ, Climate.Sampler sampler) {
		int x = QuartPos.toBlock(biomeX);
		int z = QuartPos.toBlock(biomeZ);
		if (isSea(x, z)) {
			return oceanBiome(biomeX, biomeY, biomeZ, sampler);
		}
		return eraAtBlock(x, z).biomeSource().getNoiseBiome(biomeX, biomeY, biomeZ, sampler);
	}

	@Override
	public void addDebugInfo(List<String> lines, BlockPos pos, Climate.Sampler sampler) {
		HostedEra owner = ownerOfChunk(pos.getX() >> 4, pos.getZ() >> 4);
		double field = layout.fieldAt(pos.getX(), pos.getZ());
		lines.add(String.format("Continents of Time: %s, coast field %.3f%s", owner == null ? "ocean" : owner.id(), field,
			Seabed.sea(field) ? " (sea)" : Seabed.inland(field) ? "" : " (coast band)"));
		if (owner != null) {
			owner.biomeSource().addDebugInfo(lines, pos, sampler);
		}
	}
}
