package dev.continentsoftime.atlas.layout;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The atlas layout: every era seated once in a first pass — the home era around the origin, the rest in roster
 * order around it — and then, because the world is infinite, a continent in <em>every</em> further cell of the
 * grid: a seeded pick among the roster's shaped, unanchored eras, so eras repeat outward for ever (finite levels
 * and bordered eras exist once: their generators are translated to one seat). Each continent is a noise-shaped
 * landmass inside a box no larger than {@code maxContinentSize}, with at least {@code oceanWidth} of open water
 * between any two boxes. Built from the world seed; deterministic; the far cells are seated lazily and cached.
 *
 * <h2>Placement</h2>
 * Seats live on a hex-offset grid of cells with pitch {@code maxContinentSize + oceanWidth}: cell {@code (i, j)}
 * is centred at {@code x = i * pitch (+ pitch / 2 on odd rows), z = j * pitch}, and owns a square
 * <em>region</em> {@code maxContinentSize} wide around that centre. Regions are therefore {@code oceanWidth}
 * apart in every direction, and a seat's box is placed anywhere inside its region, so the gap between any two
 * continents is at least {@code oceanWidth} by construction, whatever the noise does. The home era takes cell
 * {@code (0, 0)} with its box centred on the origin; the other cells are chosen by seeded growth outward from
 * it (each step adds a random cell adjacent to those already chosen), so the continents form one connected
 * archipelago, every continent one ocean crossing from a neighbour. Cells are handed out in the roster's order.
 *
 * <h2>Shape</h2>
 * A shaped seat maps its box to normalised coordinates {@code (u, v)} where {@code |u|, |v| < REACH} inside the
 * box, warps them with two fractal noises (at most {@link #WARP} each), and evaluates
 * {@code field = (1 - r^PROFILE) + core + DETAIL * noise - BIAS}, {@code r} being the superellipse radius of the
 * warped point and {@code core} a bump around the <em>unwarped</em> centre. Land is {@code field > 0}. Because
 * the noises are bounded to {@code [-1, 1]}: the box centre is always land
 * ({@code 1 - (2^(1/METRIC) * WARP)^PROFILE + CORE - DETAIL - BIAS > 0}); outside the core, land needs
 * {@code r < RIM}, and the unwarped coordinate is then below {@code RIM + WARP = REACH}, the box edge. Finite
 * eras are seated unshaped: the whole box (their level's own size) belongs to them.
 */
public final class ContinentLayout implements Layout {
	/** Largest domain-warp displacement per axis, in normalised units. */
	static final double WARP = 0.2;
	/** Amplitude of the coastline noise added to the radial profile. */
	static final double DETAIL = 0.4;
	/** Subtracted from the field. Must exceed {@link #DETAIL} so the box rim is sea whatever the noise says. */
	static final double BIAS = 0.42;
	/**
	 * The coastline noise is stretched by this and clamped back to {@code [-1, 1]}: a fractal sum rarely nears its
	 * bound, so without the gain the coast would hug one radius; with it, lobes, bays and offshore islands appear.
	 */
	static final double DETAIL_GAIN = 1.8;
	/** Exponent of the radial profile {@code 1 - r^PROFILE}; gentle, so the noise decides where the coast is. */
	static final double PROFILE = 2;
	/** Superellipse exponent of the radial metric: 2 is a circle; a little more uses the box's corners. */
	static final double METRIC = 2.4;
	/** A bump on the unwarped centre that guarantees a solid core: {@code CORE * (1 - r / CORE_RADIUS)^2} inside it. */
	static final double CORE = 0.35;
	static final double CORE_RADIUS = 0.4;
	/** Outside the core, land needs {@code 1 - r^PROFILE > BIAS - DETAIL}, i.e. warped {@code r < RIM}. */
	static final double RIM = Math.pow(1 - (BIAS - DETAIL), 1 / PROFILE);
	/** Unwarped normalised coordinate of the box edge; land never reaches it ({@code CORE_RADIUS < RIM} keeps the core inside too). */
	static final double REACH = RIM + WARP;
	/** Cycles per normalised unit of the warp and coastline noises. */
	static final double WARP_FREQUENCY = 0.9;
	static final double DETAIL_FREQUENCY = 1.2;
	static final int WARP_OCTAVES = 4;
	static final double WARP_PERSISTENCE = 0.55;
	static final double DETAIL_PERSISTENCE = 0.62;
	/** Finest coastline octave is kept at or above this wavelength, in blocks. */
	static final double FINEST_DETAIL_BLOCKS = 24;

	/** Shaped continents other than home are between this fraction of the maximum size and the maximum. */
	static final double MIN_SIZE_FRACTION = 0.7;
	/** The shorter axis of a shaped box is at least this fraction of the longer. */
	static final double MIN_ASPECT = 0.75;

	private static final int CACHE_LIMIT = 16_384;

	private final long seed;
	private final int maxSize;
	private final int oceanWidth;
	private final int pitch;
	private final int regionHalf;
	private final int home;
	private final boolean oceans;
	private final List<Seat> seats;
	private final Seat[] byEra;
	private final Map<Long, Seat> byCell = new HashMap<>();
	private final Shape[] shapes;
	/** Roster eras that may repeat beyond the first pass: shaped and not anchored. */
	private final int[] repeatable;
	private final List<Footprint> footprints;
	private final ConcurrentHashMap<Long, Seat> farSeats = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<Long, Shape> farShapes = new ConcurrentHashMap<>();
	private final boolean hasShapedSeat;
	private final ConcurrentHashMap<Long, byte[]> chunkCache = new ConcurrentHashMap<>();

	private record Shape(Noise warpX, Noise warpZ, Noise detail, int octaves) {}

	/**
	 * @param seed             the world seed
	 * @param footprints       one per roster era, in roster order
	 * @param home             roster index of the era whose continent holds the origin
	 * @param maxContinentSize upper bound on a continent's extent along either axis, in blocks
	 * @param oceanWidth       minimum open water between two continents, in blocks
	 */
	public ContinentLayout(long seed, List<Footprint> footprints, int home, int maxContinentSize, int oceanWidth) {
		this(seed, footprints, home, maxContinentSize, oceanWidth, true);
	}

	/**
	 * @param oceans {@code false} for the "no oceans" option: the shaped continents are seated exactly as with
	 *               oceans, but no column is open water — every column outside a continent belongs to the nearest
	 *               era, whose own terrain then fills the gap up to a hard seam with its neighbour's; the coast
	 *               field reads as inland everywhere (no seabed, no coast band). Sizes keep their meaning as spacing.
	 */
	public ContinentLayout(long seed, List<Footprint> footprints, int home, int maxContinentSize, int oceanWidth, boolean oceans) {
		if (footprints.isEmpty()) {
			throw new IllegalArgumentException("No eras to lay out");
		}
		if (footprints.size() > Byte.MAX_VALUE) {
			throw new IllegalArgumentException("At most " + Byte.MAX_VALUE + " eras can be laid out, got " + footprints.size());
		}
		if (home < 0 || home >= footprints.size()) {
			throw new IllegalArgumentException("Home era " + home + " is not in the roster of " + footprints.size());
		}
		if (maxContinentSize < 16 || oceanWidth < 0) {
			throw new IllegalArgumentException("Bad sizes: max continent " + maxContinentSize + ", ocean " + oceanWidth);
		}
		this.seed = seed;
		this.maxSize = maxContinentSize;
		this.oceanWidth = oceanWidth;
		this.pitch = maxContinentSize + oceanWidth;
		this.regionHalf = maxContinentSize / 2;
		this.home = home;
		this.oceans = oceans;

		SplittableRandom random = new SplittableRandom(seed);
		List<long[]> cells = growCells(random, footprints.size());

		List<Integer> order = new ArrayList<>();
		order.add(home);
		for (int era = 0; era < footprints.size(); era++) {
			if (era != home) {
				order.add(era);
			}
		}

		this.hasShapedSeat = footprints.stream().anyMatch(Footprint::shaped);
		this.footprints = List.copyOf(footprints);
		this.repeatable = java.util.stream.IntStream.range(0, footprints.size())
			.filter(era -> footprints.get(era).shaped() && !footprints.get(era).anchored()).toArray();
		this.seats = new ArrayList<>(footprints.size());
		this.byEra = new Seat[footprints.size()];
		this.shapes = new Shape[footprints.size()];
		for (int k = 0; k < order.size(); k++) {
			int era = order.get(k);
			Footprint footprint = footprints.get(era);
			long[] cell = cells.get(k);
			Seat seat = seat(random, era, (int) cell[0], (int) cell[1], footprint, k == 0);
			seats.add(seat);
			byEra[era] = seat;
			byCell.put(cellKey(seat.cellX(), seat.cellZ()), seat);
			if (footprint.shaped()) {
				shapes[era] = shape(era, seat);
			}
		}
	}

	// ---- placement ----

	/** Seeded growth outward from cell (0, 0): each step adds a random cell adjacent to the chosen set. */
	private static List<long[]> growCells(SplittableRandom random, int count) {
		List<long[]> chosen = new ArrayList<>();
		Set<Long> chosenKeys = new HashSet<>();
		List<long[]> frontier = new ArrayList<>();
		Set<Long> frontierKeys = new HashSet<>();

		long[] origin = {0, 0};
		chosen.add(origin);
		chosenKeys.add(cellKey(0, 0));
		addNeighbours(origin, chosenKeys, frontier, frontierKeys);

		while (chosen.size() < count) {
			long[] next = frontier.remove(random.nextInt(frontier.size()));
			frontierKeys.remove(cellKey((int) next[0], (int) next[1]));
			chosen.add(next);
			chosenKeys.add(cellKey((int) next[0], (int) next[1]));
			addNeighbours(next, chosenKeys, frontier, frontierKeys);
		}
		return chosen;
	}

	/** Six neighbours on the hex-offset grid (odd rows shifted half a pitch to +x). */
	private static void addNeighbours(long[] cell, Set<Long> chosen, List<long[]> frontier, Set<Long> frontierKeys) {
		int i = (int) cell[0];
		int j = (int) cell[1];
		int shift = (j & 1) != 0 ? 1 : 0;
		int[][] neighbours = {
			{i - 1, j}, {i + 1, j},
			{i - 1 + shift, j - 1}, {i + shift, j - 1},
			{i - 1 + shift, j + 1}, {i + shift, j + 1}
		};
		for (int[] n : neighbours) {
			long key = cellKey(n[0], n[1]);
			if (!chosen.contains(key) && frontierKeys.add(key)) {
				frontier.add(new long[] {n[0], n[1]});
			}
		}
	}

	private Seat seat(SplittableRandom random, int era, int cellX, int cellZ, Footprint footprint, boolean isHome) {
		// Draw the same values for every seat so one seat's kind never shifts the others' randomness.
		double sizeFraction = MIN_SIZE_FRACTION + (1 - MIN_SIZE_FRACTION) * random.nextDouble();
		double aspect = MIN_ASPECT + (1 - MIN_ASPECT) * random.nextDouble();
		boolean swap = random.nextBoolean();
		double offsetX = random.nextDouble();
		double offsetZ = random.nextDouble();

		int halfX;
		int halfZ;
		if (!footprint.shaped()) {
			halfX = Math.min(footprint.width(), maxSize) / 2;
			halfZ = Math.min(footprint.length(), maxSize) / 2;
		} else if (isHome) {
			halfX = regionHalf;
			halfZ = regionHalf;
		} else {
			double size = Math.min(Math.min(footprint.width(), footprint.length()), maxSize) * sizeFraction;
			double longHalf = size / 2;
			double shortHalf = size * aspect / 2;
			halfX = (int) (swap ? shortHalf : longHalf);
			halfZ = (int) (swap ? longHalf : shortHalf);
		}
		halfX = Math.max(8, Math.min(halfX, regionHalf));
		halfZ = Math.max(8, Math.min(halfZ, regionHalf));

		int centerX = cellCenterX(cellX, cellZ);
		int centerZ = cellCenterZ(cellZ);
		if (!isHome) {
			int playX = regionHalf - halfX;
			int playZ = regionHalf - halfZ;
			centerX += (int) Math.round((offsetX * 2 - 1) * playX);
			centerZ += (int) Math.round((offsetZ * 2 - 1) * playZ);
		}
		if (footprint.anchored()) {
			// An anchored era is translated to its seat by whole chunks, so its centre must be chunk-aligned. Snap
			// toward the cell's centre so the box stays inside its region.
			centerX = snapToward(centerX, cellCenterX(cellX, cellZ));
			centerZ = snapToward(centerZ, cellCenterZ(cellZ));
		}
		return new Seat(era, cellX, cellZ, centerX, centerZ, halfX, halfZ, footprint.shaped());
	}

	private static int snapToward(int value, int toward) {
		int down = Math.floorDiv(value, 16) * 16;
		int up = down + 16;
		if (down == value) {
			return value;
		}
		return Math.abs(down - toward) <= Math.abs(up - toward) ? down : up;
	}

	private Shape shape(int era, Seat seat) {
		// One normalised unit is halfX / REACH blocks; the base coastline wavelength is 1 / DETAIL_FREQUENCY units.
		double baseWavelengthBlocks = (seat.halfX() / REACH) / DETAIL_FREQUENCY;
		int octaves = (int) Math.floor(Math.log(baseWavelengthBlocks / FINEST_DETAIL_BLOCKS) / Math.log(2)) + 1;
		octaves = Math.max(1, Math.min(8, octaves));
		return new Shape(
			new Noise(mix(seed, era, 1)),
			new Noise(mix(seed, era, 2)),
			new Noise(mix(seed, era, 3)),
			octaves);
	}

	private int cellCenterX(int cellX, int cellZ) {
		return cellX * pitch + ((cellZ & 1) != 0 ? pitch / 2 : 0);
	}

	private int cellCenterZ(int cellZ) {
		return cellZ * pitch;
	}

	private static long cellKey(int i, int j) {
		return ((long) i << 32) ^ (j & 0xFFFFFFFFL);
	}

	private static long mix(long seed, int era, int salt) {
		long h = seed ^ (era * 0x9E3779B97F4A7C15L) ^ (salt * 0xC2B2AE3D27D4EB4FL);
		h = (h ^ (h >>> 30)) * 0xBF58476D1CE4E5B9L;
		h = (h ^ (h >>> 27)) * 0x94D049BB133111EBL;
		return h ^ (h >>> 31);
	}

	// ---- queries ----

	/** The seat whose box contains the column, or {@code null}. Boxes never overlap, so there is at most one. */
	public Seat seatContaining(int x, int z) {
		int j = Math.floorDiv(z + pitch / 2, pitch);
		if (Math.abs(z - (long) j * pitch) > regionHalf) {
			return null;
		}
		int shift = (j & 1) != 0 ? pitch / 2 : 0;
		int i = Math.floorDiv(x - shift + pitch / 2, pitch);
		if (Math.abs(x - shift - (long) i * pitch) > regionHalf) {
			return null;
		}
		Seat seat = seatAt(i, j);
		return seat != null && seat.containsBox(x, z) ? seat : null;
	}

	/** The seat in a cell: the first pass's, or a far seat built on demand; {@code null} only if no era can repeat. */
	private Seat seatAt(int i, int j) {
		long key = cellKey(i, j);
		Seat seat = byCell.get(key);
		if (seat != null || repeatable.length == 0) {
			return seat;
		}
		return farSeats.computeIfAbsent(key, k -> {
			SplittableRandom random = new SplittableRandom(mix(seed, (int) (k ^ (k >>> 32)), 7));
			int era = repeatable[random.nextInt(repeatable.length)];
			return seat(random, era, i, j, footprints.get(era), false);
		});
	}

	/** The coastline noise of a shaped seat: per era in the first pass, per cell beyond it (repeats get their own coast). */
	private Shape shapeOf(Seat seat) {
		if (byEra[seat.era()] == seat) {
			return shapes[seat.era()];
		}
		long key = cellKey(seat.cellX(), seat.cellZ());
		return farShapes.computeIfAbsent(key, k -> shape((int) (k ^ (k >>> 32)) * 31 + seat.era(), seat));
	}

	/**
	 * The shape field at a column: positive on land, negative at sea. Outside every box it is {@code -1}; inside
	 * an unshaped (finite) box it is {@code 1}; inside a shaped box it is the noise field, which crosses zero at
	 * the coast and falls with the radial profile.
	 */
	@Override
	public double fieldAt(int x, int z) {
		if (!oceans) {
			return 1;
		}
		Seat seat = seatContaining(x, z);
		if (seat == null) {
			return -1;
		}
		if (!seat.shaped()) {
			return 1;
		}
		return field(seat, shapeOf(seat), x, z);
	}

	private static double field(Seat seat, Shape shape, int x, int z) {
		double u = (x - seat.centerX()) * REACH / seat.halfX();
		double v = (z - seat.centerZ()) * REACH / seat.halfZ();
		double wu = u * WARP_FREQUENCY;
		double wv = v * WARP_FREQUENCY;
		double uu = u + WARP * shape.warpX().fractal(wu, wv, WARP_OCTAVES, WARP_PERSISTENCE);
		double vv = v + WARP * shape.warpZ().fractal(wu + 11.7, wv - 5.3, WARP_OCTAVES, WARP_PERSISTENCE);
		double r = Math.pow(Math.pow(Math.abs(uu), METRIC) + Math.pow(Math.abs(vv), METRIC), 1 / METRIC);
		double profile = 1 - Math.pow(r, PROFILE);
		double detail = DETAIL_GAIN * shape.detail().fractal(uu * DETAIL_FREQUENCY, vv * DETAIL_FREQUENCY, shape.octaves(), DETAIL_PERSISTENCE);
		detail = detail < -1 ? -1 : (detail > 1 ? 1 : detail);
		double unwarped = Math.sqrt(u * u + v * v);
		double core = unwarped < CORE_RADIUS ? CORE * (1 - unwarped / CORE_RADIUS) * (1 - unwarped / CORE_RADIUS) : 0;
		return profile + core + DETAIL * detail - BIAS;
	}

	/**
	 * {@link #eraAt} without the per-chunk cache: one column's answer at one column's cost. For sparse sampling
	 * (maps, harnesses); generation goes through the cache. Always the with-oceans answer ({@link #OCEAN} in the
	 * gaps): the "no oceans" option is applied when the cache is filled.
	 */
	public int eraAtColumn(int x, int z) {
		Seat seat = seatContaining(x, z);
		if (seat == null) {
			return OCEAN;
		}
		if (!seat.shaped()) {
			return seat.era();
		}
		return field(seat, shapeOf(seat), x, z) > 0 ? seat.era() : OCEAN;
	}

	@Override
	public int eraAt(int blockX, int blockZ) {
		return chunkEras(blockX >> 4, blockZ >> 4)[((blockX & 15) << 4) | (blockZ & 15)];
	}

	/** The 16x16 era map of a chunk (index {@code (x & 15) << 4 | (z & 15)}), cached. */
	private byte[] chunkEras(int chunkX, int chunkZ) {
		long key = ((long) chunkX << 32) ^ (chunkZ & 0xFFFFFFFFL);
		byte[] chunk = chunkCache.get(key);
		if (chunk == null) {
			if (chunkCache.size() >= CACHE_LIMIT) {
				chunkCache.clear();
			}
			chunk = new byte[256];
			int baseX = chunkX << 4;
			int baseZ = chunkZ << 4;
			for (int dx = 0; dx < 16; dx++) {
				for (int dz = 0; dz < 16; dz++) {
					int era = eraAtColumn(baseX + dx, baseZ + dz);
					if (era == OCEAN && !oceans) {
						era = nearestEraAt(baseX + dx, baseZ + dz);
					}
					chunk[(dx << 4) | dz] = (byte) era;
				}
			}
			chunkCache.put(key, chunk);
		}
		return chunk;
	}

	/** Any land column makes the chunk that era's; boxes never share a chunk (they are {@code oceanWidth} apart). */
	@Override
	public int chunkOwner(int chunkX, int chunkZ) {
		byte[] chunk = chunkEras(chunkX, chunkZ);
		for (byte era : chunk) {
			if (era != OCEAN) {
				return era;
			}
		}
		return OCEAN;
	}

	/**
	 * {@inheritDoc}
	 *
	 * <p>Only shaped continents are candidates: a finite era's generator produces its "beyond the level" border
	 * outside its box, so it must never own open water. (If every era is finite, the nearest box wins.) The
	 * candidates are the seats in the column's cell and the cells around it (a box never leaves its region, so a
	 * nearer box cannot hide further away); if none of those qualifies, the whole first pass is searched.
	 */
	@Override
	public int nearestEraAt(int blockX, int blockZ) {
		int j = Math.floorDiv(blockZ + pitch / 2, pitch);
		int shift = (j & 1) != 0 ? pitch / 2 : 0;
		int i = Math.floorDiv(blockX - shift + pitch / 2, pitch);
		Seat best = null;
		double bestDistance = Double.MAX_VALUE;
		for (int dj = -2; dj <= 2; dj++) {
			for (int di = -2; di <= 2; di++) {
				Seat seat = seatAt(i + di, j + dj);
				if (seat == null || (!seat.shaped() && hasShapedSeat)) {
					continue;
				}
				double d = seat.distanceToBox(blockX, blockZ);
				if (d < bestDistance) {
					bestDistance = d;
					best = seat;
				}
			}
		}
		if (best == null) {
			for (Seat seat : seats) {
				if (!seat.shaped() && hasShapedSeat) {
					continue;
				}
				double d = seat.distanceToBox(blockX, blockZ);
				if (d < bestDistance) {
					bestDistance = d;
					best = seat;
				}
			}
		}
		return best.era();
	}

	// ---- introspection ----

	public long seed() { return seed; }
	public int maxContinentSize() { return maxSize; }
	public int oceanWidth() { return oceanWidth; }
	public int pitch() { return pitch; }
	public int home() { return home; }
	/** Whether the gaps between continents are open water ({@code true}) or the nearest era's own terrain. */
	public boolean oceans() { return oceans; }
	/** The first pass's seats in growth order; the first is home. Far seats (repeats) are not listed. */
	public List<Seat> seats() { return List.copyOf(seats); }
	/** The first pass's seat of an era (the only one for finite and bordered eras). */
	public Seat seatOf(int era) { return byEra[era]; }
	/** The seat in a grid cell, first pass or repeat; {@code null} only when no era can repeat. */
	public Seat seatInCell(int cellX, int cellZ) { return seatAt(cellX, cellZ); }
	/** Roster indices of the eras that repeat beyond the first pass (shaped, not anchored). */
	public int[] repeatable() { return repeatable.clone(); }

	/** One line for the log. */
	public String describe() {
		int minCellX = Integer.MAX_VALUE, maxCellX = Integer.MIN_VALUE, minCellZ = Integer.MAX_VALUE, maxCellZ = Integer.MIN_VALUE;
		for (Seat seat : seats) {
			minCellX = Math.min(minCellX, seat.cellX());
			maxCellX = Math.max(maxCellX, seat.cellX());
			minCellZ = Math.min(minCellZ, seat.cellZ());
			maxCellZ = Math.max(maxCellZ, seat.cellZ());
		}
		return seats.size() + " continent(s) on a hex grid with pitch " + pitch + " (max size " + maxSize
			+ ", ocean " + oceanWidth + (oceans ? "" : ", NO OCEANS: gaps go to the nearest era") + "), cells x " + minCellX + ".." + maxCellX + ", z " + minCellZ + ".." + maxCellZ
			+ ", home era " + home + " at the origin; " + repeatable.length + " era(s) repeat in every further cell";
	}
}
