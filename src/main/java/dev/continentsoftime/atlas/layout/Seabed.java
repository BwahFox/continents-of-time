package dev.continentsoftime.atlas.layout;

/**
 * The ocean floor and the coast band, as a function of the layout's signed field (positive inland, negative at
 * sea, zero on the coastline). Minecraft-free so the harness can check it.
 *
 * <p>Every column of the world gets a height band {@code [lower, upper]}:
 * <ul>
 *   <li>At sea ({@code field <= 0}) the band is a single height, the {@linkplain #floor seabed}: ocean chunks
 *       are built to it, and an era's terrain in a sea column is clamped to it — cut down or filled up — so an
 *       ocean chunk and an era chunk meet without a step.</li>
 *   <li>In the coast band ({@code 0 < field < INLAND}) the band opens up: the upper bound climbs away from
 *       the shoreline and the lower bound drops away, so terrain is only nudged where it would otherwise end
 *       in a wall at the water.</li>
 *   <li>Inland ({@code field >= INLAND}) the band is the whole world: the era's terrain is untouched.</li>
 * </ul>
 * Both bounds are continuous at {@code field == 0}, where they equal the shoreline height {@code seaLevel - SHORE}.
 */
public final class Seabed {
	/** Field value at which the coast band ends and the era's terrain is left alone. */
	public static final double INLAND = 0.06;
	/** Blocks below sea level at the shoreline (field 0). */
	public static final int SHORE = 2;
	/** Extra depth reached at {@link #DEEP_FIELD} and beyond. */
	public static final int MAX_DEPTH = 42;
	/** Field value (negative) at which the floor stops descending. */
	public static final double DEEP_FIELD = -0.25;
	/** Field value below which a column counts as deep ocean. */
	public static final double DEEP_BIOME_FIELD = -0.12;
	/** Amplitude of the floor's own relief, blocks. */
	public static final double RELIEF = 5;
	/** Wavelength of the floor's relief, blocks. */
	public static final double RELIEF_WAVELENGTH = 96;
	/** How far above the shoreline the upper bound reaches at the inland end of the band, blocks. */
	public static final int CLIP_RISE = 400;
	/** How far below the shoreline the lower bound reaches at the inland end of the band, blocks. */
	public static final int FILL_DROP = 400;

	private final Noise relief;
	private final int seaLevel;
	private final int minY;
	private final int maxY;

	/**
	 * @param seed     the world seed
	 * @param seaLevel the atlas's sea level (the modern 63)
	 * @param minY     lowest block of the dimension
	 * @param maxY     highest block of the dimension (inclusive)
	 */
	public Seabed(long seed, int seaLevel, int minY, int maxY) {
		this.relief = new Noise(seed ^ 0x5EABEDL);
		this.seaLevel = seaLevel;
		this.minY = minY;
		this.maxY = maxY;
	}

	public int seaLevel() { return seaLevel; }
	public int minY() { return minY; }
	public int maxY() { return maxY; }

	/** Whether a column with this field is left entirely to its era. */
	public static boolean inland(double field) {
		return field >= INLAND;
	}

	public static boolean sea(double field) {
		return field <= 0;
	}

	public static boolean deep(double field) {
		return field < DEEP_BIOME_FIELD;
	}

	/** Height of the top solid block of the seabed for a sea column ({@code field <= 0}). */
	public int floor(int x, int z, double field) {
		double t = Math.min(1, Math.max(0, field / DEEP_FIELD)); // 0 at the shore, 1 at DEEP_FIELD and beyond
		double s = t * t * (3 - 2 * t);
		double depth = SHORE + MAX_DEPTH * s;
		// Relief fades in away from the shore so the shoreline itself stays where the band expects it.
		double bumps = RELIEF * s * relief.fractal(x / RELIEF_WAVELENGTH, z / RELIEF_WAVELENGTH, 3);
		int y = (int) Math.round(seaLevel - depth + bumps);
		return Math.max(minY + 1, Math.min(seaLevel - SHORE, y));
	}

	/** Highest allowed top-of-terrain height for the column; {@code maxY} inland. */
	public int upperBound(int x, int z, double field) {
		if (inland(field)) {
			return maxY;
		}
		if (sea(field)) {
			return floor(x, z, field);
		}
		double t = field / INLAND;
		return Math.min(maxY, seaLevel - SHORE + (int) Math.round(CLIP_RISE * t * t));
	}

	/** Lowest allowed top-of-terrain height for the column; {@code minY} inland. */
	public int lowerBound(int x, int z, double field) {
		if (inland(field)) {
			return minY;
		}
		if (sea(field)) {
			return floor(x, z, field);
		}
		double t = field / INLAND;
		return Math.max(minY, seaLevel - SHORE - (int) Math.round(FILL_DROP * t * t));
	}
}
