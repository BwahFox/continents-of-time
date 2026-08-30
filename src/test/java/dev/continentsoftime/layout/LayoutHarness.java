package dev.continentsoftime.layout;

import dev.continentsoftime.atlas.layout.ContinentLayout;
import dev.continentsoftime.atlas.layout.Footprint;
import dev.continentsoftime.atlas.layout.Layout;
import dev.continentsoftime.atlas.layout.Seabed;
import dev.continentsoftime.atlas.layout.Seat;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Headless check of the atlas layout: {@code ./gradlew layoutTest [-Pseeds=1,2,3]}. No Minecraft involved.
 *
 * <p>For each seed it builds the default roster's layout (25 eras: 21 shaped, Legacy Console bordered at 5120, 3 finite 256x256 levels; home is
 * roster index 19, like {@code minecraft:overworld} in the default roster) and asserts: determinism across two
 * instances; every era present; the origin and a disc around it on the home continent; every continent's land
 * inside its box and no wider than the maximum; at least {@code oceanWidth} between any two continents' land;
 * a per-column cost worth caching once. It writes {@code build/layout/<seed>.png} (one pixel per 32 blocks) and
 * prints a coarse ASCII map for eyeballing.
 */
public final class LayoutHarness {
	private static final int ERAS = 25;
	private static final int HOME = 19;
	private static final int[] FINITE = {0, 1, 2};
	/** Legacy Console: an infinite generator with an origin-centred 5120-block world border, seated bordered. */
	private static final int LEGACY_CONSOLE = 23;
	private static final int MAX_SIZE = 10_000;
	private static final int OCEAN = 2_000;
	private static final int PIXEL = 32;

	private static int failures;

	public static void main(String[] args) throws IOException {
		long[] seeds = {20260829L, 1L, -7L, 123456789L};
		String property = System.getProperty("seeds");
		if (property != null && !property.isBlank()) {
			seeds = Arrays.stream(property.split(",")).mapToLong(s -> Long.parseLong(s.trim())).toArray();
		}
		Path out = Path.of("build", "layout");
		Files.createDirectories(out);

		for (long seed : seeds) {
			long started = System.nanoTime();
			System.out.println("==== seed " + seed);
			ContinentLayout layout = new ContinentLayout(seed, footprints(), HOME, MAX_SIZE, OCEAN);
			System.out.println(layout.describe());
			checkDeterminism(seed, layout);
			checkOrigin(layout);
			checkChunkOwnerIsOnLandOrNearest(layout);
			int[][] extents = measure(layout);
			checkExtents(layout, extents);
			checkGaps(layout, extents);
			checkEveryEraPresent(layout, extents);
			timeColumns(layout);
			checkSeabed(seed, layout);
			printCrossings(layout);
			render(layout, out.resolve(seed + ".png"));
			renderZoom(layout, out.resolve(seed + "-home.png"));
			ascii(layout);
			System.out.printf("seed %d checked in %.1f s%n", seed, (System.nanoTime() - started) / 1e9);
		}

		if (failures > 0) {
			System.err.println(failures + " layout check(s) FAILED");
			System.exit(1);
		}
		System.out.println("All layout checks passed for " + seeds.length + " seed(s)");
	}

	private static List<Footprint> footprints() {
		List<Footprint> list = new ArrayList<>();
		for (int era = 0; era < ERAS; era++) {
			int e = era;
			boolean finite = Arrays.stream(FINITE).anyMatch(f -> f == e);
			list.add(finite ? Footprint.finite(256, 256) : era == LEGACY_CONSOLE ? Footprint.bordered(5120) : Footprint.shaped(MAX_SIZE));
		}
		return list;
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			failures++;
			System.err.println("FAIL: " + message);
		}
	}

	private static void checkDeterminism(long seed, ContinentLayout layout) {
		ContinentLayout again = new ContinentLayout(seed, footprints(), HOME, MAX_SIZE, OCEAN);
		check(layout.seats().equals(again.seats()), "seats differ between two builds of seed " + seed);
		int mismatches = 0;
		for (Seat seat : layout.seats()) {
			for (int x = seat.minX(); x <= seat.maxX(); x += 97) {
				for (int z = seat.minZ(); z <= seat.maxZ(); z += 101) {
					if (layout.eraAtColumn(x, z) != again.eraAtColumn(x, z)) {
						mismatches++;
					}
				}
			}
		}
		check(mismatches == 0, mismatches + " column(s) differ between two builds of seed " + seed);
		System.out.println("determinism: ok");
	}

	private static void checkOrigin(ContinentLayout layout) {
		check(layout.eraAt(0, 0) == HOME, "origin is era " + layout.eraAt(0, 0) + ", not home " + HOME);
		Seat home = layout.seatOf(HOME);
		check(home.centerX() == 0 && home.centerZ() == 0, "home box is not centred on the origin: " + home);
		int radius = 512;
		int sea = 0;
		for (int x = -radius; x <= radius; x += 8) {
			for (int z = -radius; z <= radius; z += 8) {
				if (x * x + z * z <= radius * radius && layout.eraAtColumn(x, z) != HOME) {
					sea++;
				}
			}
		}
		check(sea == 0, sea + " sampled column(s) within " + radius + " of the origin are not home land");
		System.out.println("origin: home land, and so is the " + radius + "-block disc around it");
	}

	private static void checkChunkOwnerIsOnLandOrNearest(ContinentLayout layout) {
		int bad = 0;
		for (int cx = -40; cx <= 40; cx += 3) {
			for (int cz = -40; cz <= 40; cz += 3) {
				int owner = layout.chunkOwner(cx, cz);
				int era = layout.eraAt((cx << 4) + 8, (cz << 4) + 8);
				if (era != Layout.OCEAN && owner != era) {
					bad++;
				}
				if (owner < 0 || owner >= ERAS) {
					bad++;
				}
			}
		}
		check(bad == 0, bad + " chunk owner(s) disagree with the column under them");

		// Open water is never handed to a finite era (its generator only makes border outside its level).
		int finiteOwned = 0;
		for (Seat seat : layout.seats()) {
			if (seat.shaped()) {
				continue;
			}
			for (int d = 32; d <= 2048; d *= 2) {
				int[][] around = {{seat.minX() - d, seat.centerZ()}, {seat.maxX() + d, seat.centerZ()},
					{seat.centerX(), seat.minZ() - d}, {seat.centerX(), seat.maxZ() + d}};
				for (int[] p : around) {
					if (layout.nearestEraAt(p[0], p[1]) == seat.era()) {
						finiteOwned++;
					}
				}
			}
		}
		check(finiteOwned == 0, finiteOwned + " open-water column(s) around finite levels are routed to the finite era");
		System.out.println("ocean routing: never to a finite era");
	}

	/** Land bounding box per era: {minX, maxX, minZ, maxZ, landColumns}, measured at 8-block steps plus the box rim at 1-block steps. */
	private static int[][] measure(ContinentLayout layout) {
		int[][] extents = new int[ERAS][];
		for (Seat seat : layout.seats()) {
			int[] e = {Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, 0};
			int step = seat.shaped() ? 16 : 4;
			for (int x = seat.minX() - 16; x <= seat.maxX() + 16; x += step) {
				for (int z = seat.minZ() - 16; z <= seat.maxZ() + 16; z += step) {
					note(layout, seat, e, x, z);
				}
			}
			// The rim and a margin outside it, every block along the edge: land here would break the extent guarantee.
			for (int d : new int[] {0, 1, 2, 4, 8, 16}) {
				for (int x = seat.minX() - 16; x <= seat.maxX() + 16; x++) {
					note(layout, seat, e, x, seat.minZ() - d);
					note(layout, seat, e, x, seat.maxZ() + d);
				}
				for (int z = seat.minZ() - 16; z <= seat.maxZ() + 16; z++) {
					note(layout, seat, e, seat.minX() - d, z);
					note(layout, seat, e, seat.maxX() + d, z);
				}
			}
			extents[seat.era()] = e;
		}
		return extents;
	}

	private static void note(ContinentLayout layout, Seat seat, int[] e, int x, int z) {
		int era = layout.eraAtColumn(x, z);
		if (era == seat.era()) {
			e[0] = Math.min(e[0], x);
			e[1] = Math.max(e[1], x);
			e[2] = Math.min(e[2], z);
			e[3] = Math.max(e[3], z);
			e[4]++;
		} else if (era != Layout.OCEAN) {
			check(false, "era " + era + " found inside/around era " + seat.era() + "'s box at " + x + "," + z);
		}
	}

	private static void checkExtents(ContinentLayout layout, int[][] extents) {
		for (Seat seat : layout.seats()) {
			int[] e = extents[seat.era()];
			if (e[4] == 0) {
				continue; // reported by checkEveryEraPresent
			}
			int width = e[1] - e[0] + 1;
			int length = e[3] - e[2] + 1;
			check(width <= MAX_SIZE && length <= MAX_SIZE,
				"era " + seat.era() + " land is " + width + "x" + length + ", over the maximum " + MAX_SIZE);
			check(e[0] >= seat.minX() && e[1] <= seat.maxX() && e[2] >= seat.minZ() && e[3] <= seat.maxZ(),
				"era " + seat.era() + " land leaves its box: land " + Arrays.toString(e) + " box " + seat);
			double fill = seat.shaped() ? (double) e[4] * 256 / ((double) (2 * seat.halfX()) * (2 * seat.halfZ())) : 1;
			System.out.printf("era %2d cell (%3d,%3d) box %6d..%6d x %6d..%6d  land %5dx%-5d  fill %3.0f%%%s%n",
				seat.era(), seat.cellX(), seat.cellZ(), seat.minX(), seat.maxX(), seat.minZ(), seat.maxZ(),
				width, length, fill * 100, seat.shaped() ? "" : "  (finite)");
		}
	}

	private static void checkGaps(ContinentLayout layout, int[][] extents) {
		int minGap = Integer.MAX_VALUE;
		for (int a = 0; a < ERAS; a++) {
			for (int b = a + 1; b < ERAS; b++) {
				int[] ea = extents[a];
				int[] eb = extents[b];
				if (ea[4] == 0 || eb[4] == 0) {
					continue;
				}
				int gapX = Math.max(eb[0] - ea[1], ea[0] - eb[1]) - 1;
				int gapZ = Math.max(eb[2] - ea[3], ea[2] - eb[3]) - 1;
				int gap = Math.max(gapX, gapZ); // boxes are separated along at least one axis
				check(gap >= OCEAN, "eras " + a + " and " + b + " are only " + gap + " apart (need " + OCEAN + ")");
				minGap = Math.min(minGap, gap);
			}
		}
		System.out.println("smallest gap between two continents' land: " + minGap + " (minimum " + OCEAN + ")");
	}

	private static void checkEveryEraPresent(ContinentLayout layout, int[][] extents) {
		for (int era = 0; era < ERAS; era++) {
			check(extents[era][4] > 0, "era " + era + " has no land");
			check(layout.eraAtColumn(layout.seatOf(era).centerX(), layout.seatOf(era).centerZ()) == era,
				"era " + era + "'s box centre is not its land");
		}
		System.out.println("every era present: ok");
	}

	private static void timeColumns(ContinentLayout layout) {
		Seat home = layout.seatOf(HOME);
		int n = 0;
		long start = System.nanoTime();
		for (int x = home.minX(); x < home.maxX(); x += 16) {
			for (int z = home.minZ(); z < home.maxZ(); z += 16) {
				layout.eraAt(x, z); // each hit fills a fresh 16x16 chunk: 256 uncached columns
				n += 256;
			}
		}
		long elapsed = System.nanoTime() - start;
		System.out.printf("cost: %.2f us per uncached column (%d columns)%n", elapsed / 1000.0 / n, n);
	}

	/**
	 * The seabed and coast band: sea columns have a single height in {@code [minY+1, seaLevel-SHORE]} that deepens
	 * away from the coast; the band's bounds bracket each other, meet the seabed at the shoreline, and open to the
	 * whole world inland; ocean chunks and era chunks therefore agree wherever they meet.
	 */
	private static void checkSeabed(long seed, ContinentLayout layout) {
		Seabed seabed = new Seabed(seed, 63, -64, 319);
		Seat home = layout.seatOf(HOME);
		int bad = 0;
		int seaColumns = 0;
		int bandColumns = 0;
		int shallowest = Integer.MIN_VALUE;
		int deepest = Integer.MAX_VALUE;
		for (int x = home.minX() - OCEAN; x <= home.maxX() + OCEAN; x += 13) {
			for (int z = home.minZ() - OCEAN; z <= home.maxZ() + OCEAN; z += 17) {
				double field = layout.fieldAt(x, z);
				int lower = seabed.lowerBound(x, z, field);
				int upper = seabed.upperBound(x, z, field);
				if (lower > upper) {
					bad++;
				}
				if (Seabed.sea(field)) {
					seaColumns++;
					int floor = seabed.floor(x, z, field);
					if (floor != lower || floor != upper || floor < -63 || floor > 63 - Seabed.SHORE) {
						bad++;
					}
					shallowest = Math.max(shallowest, floor);
					deepest = Math.min(deepest, floor);
				} else if (!Seabed.inland(field)) {
					bandColumns++;
					if (upper < 63 - Seabed.SHORE || lower > 63 - Seabed.SHORE) {
						bad++; // the band always admits the shoreline height
					}
				} else if (lower != -64 || upper != 319) {
					bad++;
				}
			}
		}
		// Continuity at the shoreline: just inside and just outside field 0 the bounds agree to within the relief.
		int shoreline = 63 - Seabed.SHORE;
		int seaSide = seabed.floor(0, 0, -1e-9);
		int landLower = seabed.lowerBound(0, 0, 1e-9);
		int landUpper = seabed.upperBound(0, 0, 1e-9);
		if (seaSide != shoreline || landLower != shoreline || landUpper != shoreline) {
			bad++;
		}
		// Far out, the floor is deep and does not jump at the box edge (field -1 outside boxes).
		int deepInside = seabed.floor(1000, 1000, Seabed.DEEP_FIELD);
		int deepOutside = seabed.floor(1000, 1000, -1);
		if (Math.abs(deepInside - deepOutside) > 1) {
			bad++;
		}
		check(bad == 0, bad + " seabed/coast-band violation(s)");
		check(seaColumns > 0 && bandColumns > 0, "no sea or no coast band columns sampled around home");
		System.out.printf("seabed: %d sea columns (floor y %d..%d), %d coast-band columns, shoreline y %d%n",
			seaColumns, deepest, shallowest, bandColumns, shoreline);
	}

	/** Where the era changes walking along the axes from the origin: coordinates to probe on a real server. */
	private static void printCrossings(ContinentLayout layout) {
		int[][] directions = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
		String[] names = {"+x", "-x", "+z", "-z"};
		for (int d = 0; d < 4; d++) {
			StringBuilder sb = new StringBuilder("crossings " + names[d] + ":");
			int previous = layout.eraAtColumn(0, 0);
			for (int i = 1; i <= layout.pitch() * 2; i++) {
				int x = directions[d][0] * i;
				int z = directions[d][1] * i;
				int era = layout.eraAtColumn(x, z);
				if (era != previous) {
					sb.append(' ').append(x).append(',').append(z).append(' ').append(previous == Layout.OCEAN ? "sea" : "era " + previous)
						.append("->").append(era == Layout.OCEAN ? "sea" : "era " + era);
					previous = era;
				}
			}
			System.out.println(sb);
		}
	}

	private static void render(ContinentLayout layout, Path file) throws IOException {
		int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
		for (Seat seat : layout.seats()) {
			minX = Math.min(minX, seat.minX() - OCEAN);
			maxX = Math.max(maxX, seat.maxX() + OCEAN);
			minZ = Math.min(minZ, seat.minZ() - OCEAN);
			maxZ = Math.max(maxZ, seat.maxZ() + OCEAN);
		}
		int w = (maxX - minX) / PIXEL + 1;
		int h = (maxZ - minZ) / PIXEL + 1;
		BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
		for (int px = 0; px < w; px++) {
			for (int pz = 0; pz < h; pz++) {
				int x = minX + px * PIXEL;
				int z = minZ + pz * PIXEL;
				int era = layout.eraAtColumn(x, z);
				int rgb;
				if (era == Layout.OCEAN) {
					Seat seat = layout.seatContaining(x, z);
					rgb = seat == null ? 0x0B2A4A : 0x164A78; // deep ocean outside boxes, shelf inside
				} else {
					rgb = eraColor(era);
				}
				image.setRGB(px, pz, rgb);
			}
		}
		for (Seat seat : layout.seats()) { // box outlines
			for (int x = seat.minX(); x <= seat.maxX(); x += PIXEL) {
				plot(image, (x - minX) / PIXEL, (seat.minZ() - minZ) / PIXEL, 0x404040);
				plot(image, (x - minX) / PIXEL, (seat.maxZ() - minZ) / PIXEL, 0x404040);
			}
			for (int z = seat.minZ(); z <= seat.maxZ(); z += PIXEL) {
				plot(image, (seat.minX() - minX) / PIXEL, (z - minZ) / PIXEL, 0x404040);
				plot(image, (seat.maxX() - minX) / PIXEL, (z - minZ) / PIXEL, 0x404040);
			}
		}
		plot(image, (0 - minX) / PIXEL, (0 - minZ) / PIXEL, 0xFFFFFF);
		ImageIO.write(image, "png", file.toFile());
		System.out.println("map: " + file + " (" + w + "x" + h + ", " + PIXEL + " blocks per pixel)");
	}

	/** The home continent alone, 8 blocks per pixel, coloured by the field so the coastline's detail is visible. */
	private static void renderZoom(ContinentLayout layout, Path file) throws IOException {
		Seat home = layout.seatOf(HOME);
		int pixel = 8;
		int w = (home.maxX() - home.minX()) / pixel + 1;
		int h = (home.maxZ() - home.minZ()) / pixel + 1;
		BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
		for (int px = 0; px < w; px++) {
			for (int pz = 0; pz < h; pz++) {
				int x = home.minX() + px * pixel;
				int z = home.minZ() + pz * pixel;
				double f = layout.fieldAt(x, z);
				int rgb;
				if (f > 0) {
					int g = (int) Math.min(255, 110 + f * 300);
					rgb = (60 << 16) | (g << 8) | 50;
				} else {
					int b = (int) Math.max(40, 140 + f * 200);
					rgb = (20 << 16) | ((b / 2) << 8) | b;
				}
				image.setRGB(px, pz, rgb);
			}
		}
		plot(image, (0 - home.minX()) / pixel, (0 - home.minZ()) / pixel, 0xFFFFFF);
		ImageIO.write(image, "png", file.toFile());
		System.out.println("home map: " + file + " (" + w + "x" + h + ", " + pixel + " blocks per pixel)");
	}

	private static void plot(BufferedImage image, int x, int z, int rgb) {
		if (x >= 0 && z >= 0 && x < image.getWidth() && z < image.getHeight()) {
			image.setRGB(x, z, rgb);
		}
	}

	private static int eraColor(int era) {
		if (era == HOME) {
			return 0xE8E8E8;
		}
		float hue = (era * 0.618034f) % 1f;
		return java.awt.Color.HSBtoRGB(hue, 0.55f, 0.85f) & 0xFFFFFF;
	}

	private static void ascii(ContinentLayout layout) {
		int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
		for (Seat seat : layout.seats()) {
			minX = Math.min(minX, seat.minX());
			maxX = Math.max(maxX, seat.maxX());
			minZ = Math.min(minZ, seat.minZ());
			maxZ = Math.max(maxZ, seat.maxZ());
		}
		int columns = 150;
		int stepX = Math.max(1, (maxX - minX) / columns);
		int stepZ = stepX * 2; // terminal cells are about twice as tall as wide
		StringBuilder sb = new StringBuilder();
		for (int z = minZ; z <= maxZ; z += stepZ) {
			for (int x = minX; x <= maxX; x += stepX) {
				int era = layout.eraAtColumn(x, z);
				char c;
				if (x == 0 && z == 0 || Math.abs(x) < stepX && Math.abs(z) < stepZ) {
					c = '@';
				} else if (era == Layout.OCEAN) {
					c = layout.seatContaining(x, z) == null ? ' ' : '.';
				} else if (era == HOME) {
					c = '#';
				} else {
					c = (char) (era < 10 ? '0' + era : 'a' + era - 10);
				}
				sb.append(c);
			}
			sb.append('\n');
		}
		System.out.print(sb);
	}
}
