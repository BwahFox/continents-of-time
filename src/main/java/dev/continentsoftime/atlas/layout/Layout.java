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
	 * The signed coast field at a column: positive inland, negative at sea, zero on the coastline. Its magnitude
	 * is what {@code Seabed} turns into depths and the coast band. Unshaped land (finite levels, or
	 * {@link #single()}) is {@code 1}.
	 */
	double fieldAt(int blockX, int blockZ);

	/**
	 * The era that generates the chunk, or {@link #OCEAN} for a chunk with no land column at all. Every
	 * generation step of a chunk must go to one generator, so ownership is decided once per chunk: an era if any
	 * of its columns is that era's land, otherwise the atlas's own ocean.
	 */
	default int chunkOwner(int chunkX, int chunkZ) {
		int baseX = chunkX << 4;
		int baseZ = chunkZ << 4;
		for (int dx = 0; dx < 16; dx++) {
			for (int dz = 0; dz < 16; dz++) {
				int era = eraAt(baseX + dx, baseZ + dz);
				if (era != OCEAN) {
					return era;
				}
			}
		}
		return OCEAN;
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

			@Override
			public double fieldAt(int blockX, int blockZ) {
				return 1;
			}
		};
	}
}
