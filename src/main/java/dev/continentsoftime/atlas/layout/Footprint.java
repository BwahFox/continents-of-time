package dev.continentsoftime.atlas.layout;

/**
 * What the layout knows about one era before seating it.
 *
 * @param width    upper bound on the continent's east-west extent, in blocks
 * @param length   upper bound on the continent's north-south extent, in blocks
 * @param shaped   {@code true} when the layout carves the continent out of the box with noise; {@code false} for
 *                 a finite level (Classic, Indev), whose level is the island already: the box is the level's own
 *                 size and every column inside it belongs to the era
 * @param anchored {@code true} when the era's generator is anchored on the world origin and must be translated to
 *                 its seat (finite levels; Legacy Console's bordered world); the seat's centre is then chunk-aligned
 */
public record Footprint(int width, int length, boolean shaped, boolean anchored) {
	public Footprint {
		if (width < 16 || length < 16) {
			throw new IllegalArgumentException("Footprint must be at least 16 blocks on each side: " + width + "x" + length);
		}
	}

	/** An infinite era: the layout shapes it inside a box no larger than {@code maxContinentSize}. */
	public static Footprint shaped(int maxContinentSize) {
		return new Footprint(maxContinentSize, maxContinentSize, true, false);
	}

	/** A finite level of the given size, seated as-is and translated to its seat. */
	public static Footprint finite(int width, int length) {
		return new Footprint(width, length, false, true);
	}

	/** An infinite generator with its own origin-centred world border: shaped inside the border, translated to its seat. */
	public static Footprint bordered(int width) {
		return new Footprint(width, width, true, true);
	}
}
