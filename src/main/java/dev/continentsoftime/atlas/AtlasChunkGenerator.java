package dev.continentsoftime.atlas;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * The master generator. Every per-chunk generation step is routed to the {@link HostedEra} that owns the chunk;
 * world-wide answers (height range, sea level, structure state) are the atlas's own.
 *
 * <p>It extends {@link NoiseBasedChunkGenerator} over vanilla's overworld settings so the server builds the
 * modern {@link RandomState} for the dimension (surface system, aquifer noises); hosted eras receive that state and
 * use what they need of it. Oceans between continents and the seams under them come with the atlas milestone.
 */
public class AtlasChunkGenerator extends NoiseBasedChunkGenerator {
	public static final MapCodec<AtlasChunkGenerator> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
		BiomeSource.CODEC.fieldOf("biome_source").forGetter(g -> g.biomeSource),
		RegistryOps.retrieveGetter(Registries.NOISE_SETTINGS)
	).apply(i, i.stable(AtlasChunkGenerator::new)));

	private final AtlasBiomeSource atlas;

	public AtlasChunkGenerator(BiomeSource biomeSource, HolderGetter<NoiseGeneratorSettings> noiseSettings) {
		super(biomeSource, noiseSettings.getOrThrow(NoiseGeneratorSettings.OVERWORLD));
		if (!(biomeSource instanceof AtlasBiomeSource atlasSource)) {
			throw new IllegalArgumentException("Continents of Time's generator needs its own biome source, got " + biomeSource);
		}
		this.atlas = atlasSource;
	}

	public AtlasBiomeSource atlas() {
		return atlas;
	}

	/** Server start, once per world: hosted eras build their providers for the seed. */
	public void init(long seed) {
		atlas.init(seed);
	}

	private ChunkGenerator owner(ChunkAccess chunk) {
		ChunkPos pos = chunk.getPos();
		return atlas.eraAtChunk(pos.x(), pos.z()).generator();
	}

	private ChunkGenerator owner(int blockX, int blockZ) {
		return atlas.eraAtBlock(blockX, blockZ).generator();
	}

	@Override
	protected MapCodec<? extends ChunkGenerator> codec() {
		return CODEC;
	}

	// ---- per-chunk steps: the owning era does the work ----

	@Override
	public CompletableFuture<ChunkAccess> createBiomes(RandomState randomState, Blender blender, StructureManager structureManager, ChunkAccess chunk) {
		return owner(chunk).createBiomes(randomState, blender, structureManager, chunk);
	}

	@Override
	public CompletableFuture<ChunkAccess> fillFromNoise(Blender blender, RandomState randomState, StructureManager structureManager, ChunkAccess chunk) {
		return owner(chunk).fillFromNoise(blender, randomState, structureManager, chunk);
	}

	@Override
	public void buildSurface(WorldGenRegion region, StructureManager structureManager, RandomState randomState, ChunkAccess chunk) {
		owner(chunk).buildSurface(region, structureManager, randomState, chunk);
	}

	@Override
	public void applyCarvers(WorldGenRegion region, long seed, RandomState randomState, BiomeManager biomeManager, StructureManager structureManager, ChunkAccess chunk) {
		owner(chunk).applyCarvers(region, seed, randomState, biomeManager, structureManager, chunk);
	}

	@Override
	public void applyBiomeDecoration(WorldGenLevel level, ChunkAccess chunk, StructureManager structureManager) {
		owner(chunk).applyBiomeDecoration(level, chunk, structureManager);
	}

	@Override
	public void spawnOriginalMobs(WorldGenRegion region) {
		ChunkPos center = region.getCenter();
		atlas.eraAtChunk(center.x(), center.z()).generator().spawnOriginalMobs(region);
	}

	@Override
	public int getBaseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor level, RandomState randomState) {
		return owner(x, z).getBaseHeight(x, z, type, level, randomState);
	}

	@Override
	public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor level, RandomState randomState) {
		return owner(x, z).getBaseColumn(x, z, level, randomState);
	}

	@Override
	public void addDebugScreenInfo(List<String> lines, RandomState randomState, BlockPos pos) {
		owner(pos.getX(), pos.getZ()).addDebugScreenInfo(lines, randomState, pos);
	}

	// ---- world-wide answers stay the atlas's: the dimension's height range and sea level are the modern ones,
	// which every hosted era's noise settings fit inside (Moderner Beta's old-era settings start at y=-64 too).
}
