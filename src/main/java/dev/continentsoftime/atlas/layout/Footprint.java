package dev.continentsoftime.atlas.layout;

/**
 * What the layout knows about one era before seating it.
 *
 * @param width  upper bound on the continent's east-west extent, in blocks
 * @param length upper bound on the continent's north-south extent, in blocks
 * @param shaped {@code true} for an infinite era, whose continent the layout carves out of the box with noise;
 *               {@code false} for a finite era (Classic, Indev), whose level is the island already: the box is
 *               the level's own size and every column inside it belongs to the era
 */
public record Footprint(int width, int length, boolean shaped) {
	public Footprint {
		if (width < 16 || length < 16) {
			throw new IllegalArgumentException("Footprint must be at least 16 blocks on each side: " + width + "x" + length);
		}
	}

	/** An infinite era: the layout shapes it inside a box no larger than {@code maxContinentSize}. */
	public static Footprint shaped(int maxContinentSize) {
		return new Footprint(maxContinentSize, maxContinentSize, true);
	}

	/** A finite level of the given size, seated as-is. */
	public static Footprint finite(int width, int length) {
		return new Footprint(width, length, false);
	}
}
