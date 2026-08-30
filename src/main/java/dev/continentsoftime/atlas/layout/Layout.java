package dev.continentsoftime.atlas.layout;

/**
 * Decides which era owns a given block column. Indices refer to the atlas's era roster; {@link #OCEAN} means
 * open water between continents, which no era owns.
 *
 * <p>The real layout is {@link ContinentLayout}, built from the world seed. {@link #single()} puts one era
 * everywhere and exists for single-era verification worlds.
 */
public interface Layout {
	/** Returned by {@link #eraAt} for open ocean between continents. */
	int OCEAN = -1;

	/** Era index for the column at {@code blockX, blockZ}, or {@link #OCEAN}. Deterministic for the layout's seed. */
	int eraAt(int blockX, int blockZ);

	/**
	 * The era whose continent is nearest to the column; never {@link #OCEAN}. Equal to {@link #eraAt} on land.
	 * Ocean chunks are routed to this era's generator until the atlas generates oceans itself.
	 */
	int nearestEraAt(int blockX, int blockZ);

	/**
	 * The era that generates the chunk. Every generation step of a chunk must go to one hosted generator, so
	 * ownership is decided once per chunk, by its centre column: the owning era on land, the nearest era at sea.
	 */
	default int chunkOwner(int chunkX, int chunkZ) {
		int x = (chunkX << 4) + 8;
		int z = (chunkZ << 4) + 8;
		int era = eraAt(x, z);
		return era == OCEAN ? nearestEraAt(x, z) : era;
	}

	static Layout single() {
		return new Layout() {
			@Override
			public int eraAt(int blockX, int blockZ) {
				return 0;
			}

			@Override
			public int nearestEraAt(int blockX, int blockZ) {
				return 0;
			}
		};
	}
}
