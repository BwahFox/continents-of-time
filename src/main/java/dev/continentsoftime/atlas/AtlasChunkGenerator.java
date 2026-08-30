//~ map_codec
package dev.continentsoftime.atlas;

import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.continentsoftime.ContinentsOfTime;
import dev.continentsoftime.atlas.layout.Seabed;
import dev.continentsoftime.util.Compat;
import net.minecraft.util.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import dev.continentsoftime.atlas.timeline.EraStructures;
import dev.continentsoftime.atlas.timeline.EraVersion;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.level.levelgen.blending.Blender;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.CompletableFuture;
//? if <1.21
//import java.util.concurrent.Executor;
//? if <1.21.2
//import net.minecraft.world.level.levelgen.GenerationStep;

/**
 * The master generator. Every per-chunk generation step is routed to the {@link HostedEra} that owns the chunk;
 * chunks with no land column at all are <em>ocean chunks</em>, which the atlas generates itself: the modern
 * ocean (vanilla ocean biomes and surface rules) over a seabed from {@link Seabed}. World-wide answers (height
 * range, sea level, structure state) are the atlas's own.
 *
 * <p>At every coast the owning era's terrain is clamped into the {@link Seabed} height band after the era has
 * filled the chunk: sea columns become exactly the seabed (so an era chunk and an ocean chunk meet without a
 * step), and the coast band eases the era's terrain down to the shoreline so no era ends in a wall at the water.
 * Eras whose own sea level is far below the atlas's (Skylands) get the seabed under everything and are never
 * clipped.
 *
 * <p>It extends {@link NoiseBasedChunkGenerator} over vanilla's overworld settings so the server builds the
 * modern {@link RandomState} for the dimension (surface system, aquifer noises); hosted eras receive that state
 * and use what they need of it, and ocean chunks are surfaced by it directly.
 */
public class AtlasChunkGenerator extends NoiseBasedChunkGenerator {
	public static final com.mojang.serialization.MapCodec<AtlasChunkGenerator> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
		BiomeSource.CODEC.fieldOf("biome_source").forGetter(g -> g.biomeSource),
		RegistryOps.retrieveGetter(Registries.NOISE_SETTINGS)
	).apply(i, i.stable(AtlasChunkGenerator::new)));

	private static final EnumSet<Heightmap.Types> GENERATION_HEIGHTMAPS = EnumSet.of(Heightmap.Types.OCEAN_FLOOR_WG, Heightmap.Types.WORLD_SURFACE_WG);
	private static final BlockState STONE = Blocks.STONE.defaultBlockState();
	private static final BlockState WATER = Blocks.WATER.defaultBlockState();
	private static final BlockState AIR = Blocks.AIR.defaultBlockState();
	private static final BlockState BEDROCK = Blocks.BEDROCK.defaultBlockState();

	private final AtlasBiomeSource atlas;
	private Seabed seabed;
	/** What the level handed {@link #createState}; the per-era states below are built from it on demand. */
	private @Nullable HolderLookup<StructureSet> structureSets;
	private @Nullable RandomState structureRandomState;
	private long structureSeed;
	/** Structure placement per era (index into the roster; {@code -1} is the ocean era), see {@link #stateFor}. */
	private final ConcurrentHashMap<Integer, ChunkGeneratorStructureState> eraStructureStates = new ConcurrentHashMap<>();

	public AtlasChunkGenerator(BiomeSource biomeSource, HolderGetter<NoiseGeneratorSettings> noiseSettings) {
		super(biomeSource, noiseSettings.getOrThrow(NoiseGeneratorSettings.OVERWORLD));
		if (!(biomeSource instanceof AtlasBiomeSource atlasSource)) {
			throw new IllegalArgumentException("Continents of Time's generator needs its own biome source, got " + biomeSource);
		}
		this.atlas = atlasSource;
		// The overworld settings holder is unbound while registries load, so no getSeaLevel()/getMinY() here;
		// the real seabed replaces this placeholder at server start (init).
		this.seabed = new Seabed(0, 63, -64, 319);
	}

	public AtlasBiomeSource atlas() {
		return atlas;
	}

	public Seabed seabed() {
		return seabed;
	}

	/** Server start, once per world: hosted eras build their providers for the seed, the layout and seabed follow. */
	public void init(long seed) {
		atlas.init(seed);
		this.seabed = new Seabed(seed, getSeaLevel(), getMinY(), getMinY() + getGenDepth() - 1);
	}

	// ---- structures: placement state per era ----

	/**
	 * The level's own state — the union over every era's biomes, used by {@code /locate} and vanilla's
	 * structure-aware code — plus a note of the inputs, so each era can get its own state on demand.
	 */
	@Override
	public ChunkGeneratorStructureState createState(HolderLookup<StructureSet> structureSets, RandomState randomState, long seed) {
		this.structureSets = structureSets;
		this.structureRandomState = randomState;
		this.structureSeed = seed;
		eraStructureStates.clear();
		return super.createState(structureSets, randomState, seed);
	}

	/**
	 * An era's structure placement: built by the era's own generator (Moderner Beta applies its preset's structure
	 * overrides and removals there) over the structure sets its version allows, when the world says structures are
	 * era-accurate. The ocean era's state covers ocean chunks.
	 */
	private ChunkGeneratorStructureState stateFor(@Nullable HostedEra owner) {
		int key = owner == null ? -1 : atlas.eras().indexOf(owner);
		return eraStructureStates.computeIfAbsent(key, k -> {
			HostedEra era = owner == null ? atlas.oceanEra() : owner;
			HolderLookup<StructureSet> sets = java.util.Objects.requireNonNull(structureSets, "structure state requested before createState");
			if (atlas.settings().eraAccurate()) {
				sets = EraStructures.filtered(sets, EraVersion.of(era.id()));
			}
			ChunkGeneratorStructureState state = era.generator().createState(sets, structureRandomState, structureSeed);
			// Vanilla computes a state's ring positions (strongholds) once, on the server thread, before any chunk
			// exists (ServerLevel's constructor). These per-era states are born on worker threads, and their first
			// use would otherwise be a lazy, unsynchronised fill of a plain hash map — under threaded world
			// generation (C2ME) two workers reach it at once and corrupt it. Filling it here, inside the map's
			// own lock, makes each state complete before anyone can see it.
			state.ensureStructuresGenerated();
			return state;
		});
	}

	/**
	 * The structure sets that can place on an era's continent (the ocean era's for {@code null}), named for
	 * {@code /cot structures}: registered sets by id; a generator's own inline set (Moderner Beta's preset overrides,
	 * such as its ocean shrine or Legacy Console's stronghold rings) by the structures in it.
	 */
	public List<String> structureSetsFor(@Nullable HostedEra era) {
		return stateFor(era).possibleStructureSets().stream()
			.map(holder -> holder.unwrapKey().map(key -> key.identifier().toString()).orElseGet(() ->
				holder.value().structures().stream()
					.map(entry -> entry.structure().unwrapKey().map(key -> key.identifier().toString()).orElse("?"))
					.collect(java.util.stream.Collectors.joining("+")) + " (override)"))
			.toList();
	}

	/** Same as vanilla's — the atlas's biome source validates biomes — but with the owning era's structure state. */
	@Override
	public void createStructures(RegistryAccess registryAccess, ChunkGeneratorStructureState levelState, StructureManager structureManager,
	                             ChunkAccess chunk, StructureTemplateManager templateManager
	                             //? if >=1.21.2
	                             , ResourceKey<Level> dimension
	) {
		super.createStructures(registryAccess, stateFor(owner(chunk)), structureManager, chunk, templateManager
			//? if >=1.21.2
			, dimension
		);
	}

	/** Chunk futures swallow exceptions into a failed chunk result; log ours first so the cause is in the log. */
	private static <T> T logged(String step, ChunkAccess chunk, java.util.function.Supplier<T> body) {
		try {
			return body.get();
		} catch (RuntimeException | Error e) {
			ContinentsOfTime.LOGGER.error("Continents of Time: {} failed for chunk {}", step, chunk.getPos(), e);
			throw e;
		}
	}

	private @Nullable HostedEra owner(ChunkAccess chunk) {
		ChunkPos pos = chunk.getPos();
		return atlas.ownerOfChunk(Compat.chunkX(pos), Compat.chunkZ(pos));
	}

	@Override
	protected com.mojang.serialization.MapCodec<? extends ChunkGenerator> codec() {
		return CODEC;
	}

	// ---- per-chunk steps: the owning era does the work; the atlas does the ocean and the coasts ----

	// Before 1.21 the biome and noise steps take the executor to run on as their first argument.
	@Override
	public CompletableFuture<ChunkAccess> createBiomes(
		//? if <1.21
		//Executor executor,
		RandomState randomState, Blender blender, StructureManager structureManager, ChunkAccess chunk) {
		HostedEra owner = owner(chunk);
		if (owner == null) {
			return CompletableFuture.supplyAsync(Util.name(() -> logged("ocean biomes", chunk, () -> {
				chunk.fillBiomesFromNoise(atlas, randomState.sampler());
				return chunk;
			}), () -> "cot_ocean_biomes"), Util.backgroundExecutor());
		}
		// Sea columns get the modern ocean at the surface step, not here: a ProtoChunk refuses biome reads until
		// its status reaches BIOMES, which happens only after this future completes.
		return owner.generator().createBiomes(
			//? if <1.21
			//executor,
			randomState, blender, structureManager, chunk);
	}

	@Override
	public CompletableFuture<ChunkAccess> fillFromNoise(
		//? if <1.21
		//Executor executor,
		Blender blender, RandomState randomState, StructureManager structureManager, ChunkAccess chunk) {
		HostedEra owner = owner(chunk);
		if (owner == null) {
			return CompletableFuture.supplyAsync(Util.name(() -> logged("ocean terrain", chunk, () -> buildOcean(chunk)), () -> "cot_ocean"), Util.backgroundExecutor());
		}
		return owner.generator().fillFromNoise(
				//? if <1.21
				//executor,
				blender, randomState, structureManager, chunk)
			.thenApply(c -> logged("shape coast", c, () -> shapeCoast(c, owner)));
	}

	/**
	 * The surface step runs one chunk at a time. The dimension has a single {@link RandomState#surfaceSystem()},
	 * and Moderner Beta parks its per-chunk context on it (the era's chunk provider and surface properties as
	 * plain fields, the chunk's random in a thread-local seeded at the head of vanilla's {@code buildSurface} only
	 * when a provider is already set) before handing it to vanilla. One generator per world never notices; the
	 * atlas runs up to twenty-six of them through that one object, and under threaded world generation (C2ME)
	 * two workers surfacing two eras at once overwrite each other's provider mid-chunk — and a chunk that starts
	 * with no provider (so no random of its own) and sees one appear from another thread before its surface-depth
	 * hook runs dies in {@code getSurfaceDepth}. Serialising the step keeps the context constant for the whole
	 * chunk; it is a small share of a chunk's work, so the other steps still parallelise.
	 */
	private static final Object SURFACE_LOCK = new Object();

	@Override
	public void buildSurface(WorldGenRegion region, StructureManager structureManager, RandomState randomState, ChunkAccess chunk) {
		HostedEra owner = owner(chunk);
		synchronized (SURFACE_LOCK) {
			if (owner == null) {
				super.buildSurface(region, structureManager, randomState, chunk); // vanilla's rules: gravel and sand floors, deepslate, bedrock
				return;
			}
			// Before the era's surface rules run, so they see the modern ocean on sea columns; and again after, because
			// Moderner Beta injects its own biomes (beaches, oceans) during its surface step.
			logged("paint sea", chunk, () -> paintSea(chunk, randomState.sampler()));
			logged("era surface", chunk, () -> { owner.generator().buildSurface(region, structureManager, randomState, chunk); return chunk; });
			logged("paint sea after surface", chunk, () -> paintSea(chunk, randomState.sampler()));
		}
	}

	// Before 1.21.2 carving is two steps (air, then liquid) and the step is an argument.
	@Override
	public void applyCarvers(WorldGenRegion region, long seed, RandomState randomState, BiomeManager biomeManager, StructureManager structureManager, ChunkAccess chunk
	                         //? if <1.21.2
	                         //, GenerationStep.Carving step
	) {
		HostedEra owner = owner(chunk);
		if (owner != null) {
			owner.generator().applyCarvers(region, seed, randomState, biomeManager, structureManager, chunk
				//? if <1.21.2
				//, step
			);
		}
	}

	@Override
	public void applyBiomeDecoration(WorldGenLevel level, ChunkAccess chunk, StructureManager structureManager) {
		HostedEra owner = owner(chunk);
		HostedEra decorator = owner != null ? owner : atlas.oceanEra();
		decorator.generator().applyBiomeDecoration(level, chunk, structureManager);
	}

	/**
	 * Vanilla validates a generator when a client re-opens a world by forcing its feature sort over its biome
	 * source's biomes. The atlas's biomes are every era's, and eras order the same features differently, so that
	 * sort fails ("feature order cycle") — and it is never used: every chunk is decorated by one hosted generator.
	 * Validate those instead, each over its own consistent biome set.
	 */
	//? if >=1.21 {
	@Override
	public void validate() {
		for (HostedEra era : atlas.eras()) {
			era.generator().validate();
		}
		atlas.oceanEra().generator().validate();
	}
	//?} else {
	/*// 1.20.1 has no validate(): its feature sort runs lazily on decoration, which the atlas never does itself.
	*///?}

	@Override
	public void spawnOriginalMobs(WorldGenRegion region) {
		ChunkPos center = region.getCenter();
		HostedEra owner = atlas.ownerOfChunk(Compat.chunkX(center), Compat.chunkZ(center));
		if (owner != null) {
			owner.generator().spawnOriginalMobs(region);
		}
	}

	/**
	 * How far up a hosted era's terrain is moved: zero for every era whose sea roughly matches ours; for an era
	 * whose sea is far below (Skylands, sea level 0), enough to put its sea level one block above ours, so its
	 * floating islands hang over open water instead of sitting in it.
	 */
	private int lift(HostedEra era) {
		int eraSea = era.generator().getSeaLevel();
		return eraSea < getSeaLevel() - 8 ? getSeaLevel() + 1 - eraSea : 0;
	}

	@Override
	public int getBaseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor level, RandomState randomState) {
		if (atlas.isSea(x, z)) {
			int floor = seabed.floor(x, z, atlas.layout().fieldAt(x, z));
			return (type == Heightmap.Types.OCEAN_FLOOR || type == Heightmap.Types.OCEAN_FLOOR_WG ? floor : getSeaLevel()) + 1;
		}
		HostedEra era = atlas.eraAtBlock(x, z);
		return era.generator().getBaseHeight(x, z, type, level, randomState) + lift(era);
	}

	@Override
	public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor level, RandomState randomState) {
		if (atlas.isSea(x, z)) {
			int floor = seabed.floor(x, z, atlas.layout().fieldAt(x, z));
			int minY = level.getMinY();
			BlockState[] column = new BlockState[level.getHeight()];
			for (int i = 0; i < column.length; i++) {
				int y = minY + i;
				column[i] = y <= floor ? STONE : (y <= getSeaLevel() ? WATER : AIR);
			}
			return new NoiseColumn(minY, column);
		}
		HostedEra era = atlas.eraAtBlock(x, z);
		NoiseColumn column = era.generator().getBaseColumn(x, z, level, randomState);
		int lift = lift(era);
		if (lift == 0) {
			return column;
		}
		int minY = level.getMinY();
		BlockState[] lifted = new BlockState[level.getHeight()];
		for (int i = 0; i < lifted.length; i++) {
			int from = minY + i - lift;
			lifted[i] = from >= minY ? column.getBlock(from) : (minY + i <= getSeaLevel() ? WATER : AIR);
		}
		return new NoiseColumn(minY, lifted);
	}

	@Override
	public void addDebugScreenInfo(List<String> lines, RandomState randomState, BlockPos pos) {
		HostedEra owner = atlas.ownerOfChunk(pos.getX() >> 4, pos.getZ() >> 4);
		if (owner != null) {
			owner.generator().addDebugScreenInfo(lines, randomState, pos);
		}
	}

	// ---- the ocean and the coasts ----

	/** An ocean chunk: stone up to the seabed, water up to sea level. Surface rules dress it later. */
	private ChunkAccess buildOcean(ChunkAccess chunk) {
		ChunkPos pos = chunk.getPos();
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		int minY = chunk.getMinY();
		int seaLevel = getSeaLevel();
		for (int dx = 0; dx < 16; dx++) {
			for (int dz = 0; dz < 16; dz++) {
				int x = pos.getMinBlockX() + dx;
				int z = pos.getMinBlockZ() + dz;
				int floor = seabed.floor(x, z, atlas.layout().fieldAt(x, z));
				for (int y = minY; y <= seaLevel; y++) {
					Compat.setBlock(chunk, cursor.set(x, y, z), y <= floor ? STONE : WATER);
				}
			}
		}
		Heightmap.primeHeightmaps(chunk, GENERATION_HEIGHTMAPS);
		return chunk;
	}

	/**
	 * Clamp the era's terrain into the seabed's height band wherever the chunk touches the sea. Sea columns become
	 * exactly the seabed; the coast band eases the terrain toward the shoreline; inland columns are untouched.
	 */
	private ChunkAccess shapeCoast(ChunkAccess chunk, HostedEra owner) {
		ChunkPos pos = chunk.getPos();
		int seaLevel = getSeaLevel();
		int minY = chunk.getMinY();
		int maxY = minY + chunk.getHeight() - 1;
		// An era whose own sea is far below ours has nothing under its land (Skylands): lift it clear of the water,
		// give it the seabed everywhere, never clip.
		int lift = lift(owner);
		boolean skyOcean = lift > 0;
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		boolean touched = false;
		BlockState[] column = skyOcean ? new BlockState[chunk.getHeight()] : null;

		for (int dx = 0; dx < 16; dx++) {
			for (int dz = 0; dz < 16; dz++) {
				int x = pos.getMinBlockX() + dx;
				int z = pos.getMinBlockZ() + dz;
				double field = atlas.layout().fieldAt(x, z);
				if (!skyOcean && Seabed.inland(field)) {
					continue;
				}
				if (skyOcean) {
					// Move the whole column up by the lift; what was above the top falls off, the bottom becomes air.
					for (int y = minY; y <= maxY; y++) {
						column[y - minY] = chunk.getBlockState(cursor.set(x, y, z));
					}
					for (int y = maxY; y >= minY; y--) {
						int from = y - lift;
						Compat.setBlock(chunk, cursor.set(x, y, z), from >= minY ? column[from - minY] : AIR);
					}
					touched = true;
				}
				if (skyOcean) {
					// The seabed and the sea go under everything, islands included; nothing above them is touched.
					int floor = seabed.floor(x, z, Math.min(field, Seabed.DEEP_FIELD));
					for (int y = minY; y <= seaLevel; y++) {
						BlockState state = chunk.getBlockState(cursor.set(x, y, z));
						if (state.isAir() || !state.getFluidState().isEmpty()) {
							Compat.setBlock(chunk, cursor, y == minY ? BEDROCK : y <= floor ? STONE : WATER);
						}
					}
					continue;
				}
				int lower = seabed.lowerBound(x, z, field);
				int upper = seabed.upperBound(x, z, field);

				int top = minY - 1;
				for (int y = maxY; y >= minY; y--) {
					BlockState state = chunk.getBlockState(cursor.set(x, y, z));
					if (!state.isAir() && state.getFluidState().isEmpty()) {
						top = y;
						break;
					}
				}
				if (top > upper) {
					for (int y = upper + 1; y <= top; y++) {
						Compat.setBlock(chunk, cursor.set(x, y, z), y <= seaLevel ? WATER : AIR);
					}
					top = upper;
					touched = true;
				}
				if (top < lower) {
					for (int y = Math.max(top + 1, minY); y <= lower; y++) {
						Compat.setBlock(chunk, cursor.set(x, y, z), STONE);
					}
					top = lower;
					touched = true;
				}
				// No open air below the waterline in a column the sea reaches, and no era water above it (Moderner
				// Beta's presets put their sea at 64; the atlas's is the modern 63, and one coast-long step would show).
				for (int y = top + 1; y <= seaLevel; y++) {
					if (chunk.getBlockState(cursor.set(x, y, z)).isAir()) {
						Compat.setBlock(chunk, cursor, WATER);
						touched = true;
					}
				}
				for (int y = Math.max(top + 1, seaLevel + 1); y <= seaLevel + 2; y++) {
					if (!chunk.getBlockState(cursor.set(x, y, z)).getFluidState().isEmpty()) {
						Compat.setBlock(chunk, cursor, AIR);
						touched = true;
					}
				}
			}
		}
		if (touched) {
			Heightmap.primeHeightmaps(chunk, GENERATION_HEIGHTMAPS);
		}
		return chunk;
	}

	/** Re-assert the modern ocean biome on every sea column of an era chunk (keeps what the era chose elsewhere). */
	private ChunkAccess paintSea(ChunkAccess chunk, Climate.Sampler sampler) {
		ChunkPos pos = chunk.getPos();
		boolean anySea = false;
		for (int dx = 0; dx < 16 && !anySea; dx += 4) {
			for (int dz = 0; dz < 16; dz += 4) {
				if (atlas.isSea(pos.getMinBlockX() + dx, pos.getMinBlockZ() + dz)) {
					anySea = true;
					break;
				}
			}
		}
		if (!anySea) {
			return chunk;
		}
		chunk.fillBiomesFromNoise((biomeX, biomeY, biomeZ, s) -> {
			if (atlas.isSea(QuartPos.toBlock(biomeX), QuartPos.toBlock(biomeZ))) {
				return atlas.oceanBiome(biomeX, biomeY, biomeZ, s);
			}
			return chunk.getNoiseBiome(biomeX, biomeY, biomeZ);
		}, sampler);
		return chunk;
	}

	// ---- world-wide answers stay the atlas's: the dimension's height range and sea level are the modern ones,
	// which every hosted era's noise settings fit inside (Moderner Beta's old-era settings start at y=-64 too).
}
