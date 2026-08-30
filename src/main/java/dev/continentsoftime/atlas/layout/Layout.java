package dev.continentsoftime.atlas.layout;

/**
 * Decides which era owns a given block column. Indices refer to the atlas's era roster.
 *
 * <p>The real layout (continents from the seed, oceans between) is the atlas's heart and comes later; the first
 * playable uses {@link #single()}, one era everywhere.
 */
public interface Layout {
	/** Era index for the column at {@code blockX, blockZ}. Must be deterministic for the layout's seed. */
	int eraAt(int blockX, int blockZ);

	static Layout single() {
		return (x, z) -> 0;
	}
}
