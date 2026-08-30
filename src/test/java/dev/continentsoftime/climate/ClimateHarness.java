package dev.continentsoftime.climate;

import dev.continentsoftime.atlas.layout.ContinentLayout;
import dev.continentsoftime.atlas.layout.Footprint;
import dev.continentsoftime.atlas.layout.Layout;
import dev.continentsoftime.atlas.layout.Seat;
import dev.continentsoftime.client.ContinentClimate;
import dev.continentsoftime.network.AtlasInfoPayload;
import io.netty.buffer.Unpooled;
import mod.bluestaggo.modernerbeta.api.level.biome.climate.ClimateSampler;
import mod.bluestaggo.modernerbeta.api.level.biome.climate.ClimateSamplerSky;
import mod.bluestaggo.modernerbeta.api.level.biome.climate.Clime;
import mod.bluestaggo.modernerbeta.settings.component.ClimateDistribution;
import net.minecraft.SharedConstants;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Headless check of the client's composite climate: {@code ./gradlew climateTest}. No Minecraft client involved;
 * the eras' climate samplers are stubs that report which era answered.
 *
 * <p>Builds the default roster's layout for a seed, gives the climate-sampling eras (Beta 1.1_02 through
 * Release 1.1, and Pocket Edition with its colours switched off, as Moderner Beta's default config has them)
 * stub samplers, and asserts: on each climate era's continent the composite routes to that era's sampler and
 * the positional decisions say "tint"; on the modern continent, on the open sea and on continents without a
 * climate (Infdev, the fractal maps) it falls back to the vanilla climate and says "vanilla"; old fog applies
 * on every Moderner Beta continent and nowhere else; the non-positional flags are the conservative union/
 * intersection; a single-era layout routes everywhere; and the payload survives a round trip through its codec.
 */
public final class ClimateHarness {
	private static final int ERAS = 26;
	private static final int HOME = 20;
	private static final int[] FINITE = {0, 1, 2};
	private static final int LEGACY_CONSOLE = 24;
	/** Roster indices whose Moderner Beta biome provider samples a climate (beta_1_1_02 .. release_1_1, pe). */
	private static final int[] CLIMATE = {10, 11, 12, 13, 14, 15, 21};
	private static final int PE = 21;
	private static final int RELEASE_1_1 = 15;
	private static final int MAX_SIZE = 10_000;
	private static final int OCEAN = 2_000;

	private static int failures;

	/** A stub era climate: temperature encodes the era index so a sample says who answered. */
	private record Stub(int era, boolean colours, boolean sky, ClimateDistribution distribution) implements ClimateSampler, ClimateSamplerSky {
		@Override public Clime sample(int x, int z) { return new Clime(era / 100.0, 0.25); }
		@Override public double sampleSky(int x, int z) { return era; }
		@Override public boolean useBiomeColor() { return colours; }
		@Override public boolean useSkyColor() { return sky; }
		@Override public ClimateDistribution getDistribution() { return distribution; }
		@Override public String getDebugText(int x, int z) { return "era " + era; }
	}

	private static final Clime FALLBACK = new Clime(-1, -1);

	public static void main(String[] args) {
		// Moderner Beta's settings components touch vanilla's registries when they load; bootstrap them (no client, no server).
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();

		long[] seeds = {20260829L, 1L};
		String property = System.getProperty("seeds");
		if (property != null && !property.isBlank()) {
			seeds = Arrays.stream(property.split(",")).mapToLong(s -> Long.parseLong(s.trim())).toArray();
		}
		for (long seed : seeds) {
			System.out.println("==== seed " + seed);
			checkRoster(seed);
		}
		checkSingle();
		checkPayloadRoundTrip();

		if (failures > 0) {
			System.out.println("FAILED: " + failures + " check(s)");
			System.exit(1);
		}
		System.out.println("Climate harness: all checks passed");
	}

	private static void checkRoster(long seed) {
		ContinentLayout layout = new ContinentLayout(seed, footprints(), HOME, MAX_SIZE, OCEAN);
		List<ClimateSampler> samplers = new ArrayList<>();
		List<Boolean> modernerBeta = new ArrayList<>();
		for (int era = 0; era < ERAS; era++) {
			samplers.add(null);
			modernerBeta.add(era != HOME);
		}
		for (int era : CLIMATE) {
			boolean on = era != PE;
			samplers.set(era, new Stub(era, on, on, era == RELEASE_1_1 ? ClimateDistribution.RELEASE_1_1 : ClimateDistribution.BETA));
		}
		ContinentClimate climate = new ContinentClimate(layout, samplers, modernerBeta, (x, z) -> FALLBACK);

		check(climate.climateEras() == CLIMATE.length, "climate era count " + climate.climateEras());
		check(climate.useBiomeColor(), "useBiomeColor is the union: true");
		check(!climate.useSkyColor(), "useSkyColor is always false (the atlas's own sky layer decides)");
		check(!climate.useWaterColor(), "useWaterColor is always false");
		check(climate.getDistribution().fuzzyGrass(), "fuzzy grass if any era wants it");
		check(!climate.getDistribution().smoothBorders(), "smooth borders only if every climate era wants it");

		for (Seat seat : layout.seats()) {
			int era = seat.era();
			int x = seat.centerX();
			int z = seat.centerZ();
			check(layout.eraAt(x, z) == era, "seat centre of era " + era + " is its land");
			boolean hasClimate = samplers.get(era) != null;
			boolean on = hasClimate && era != PE;
			Clime sample = climate.sample(x, z);
			if (hasClimate) {
				check(sample.temp() == era / 100.0, "era " + era + " sample routed to its own sampler, got " + sample.temp());
				check(climate.sampleSky(x, z) == era, "era " + era + " sky sample routed");
				check(climate.getDebugText(x, z).equals("era " + era), "era " + era + " debug text routed");
			} else {
				check(sample == FALLBACK, "era " + era + " without a climate falls back to vanilla, got " + sample);
				check(climate.getDebugText(x, z).isEmpty(), "era " + era + " has no debug text");
			}
			check(climate.tintsVegetation(x, z) == on, "era " + era + " tintsVegetation == " + on);
			check(climate.tintsSky(x, z) == on, "era " + era + " tintsSky == " + on);
			check(climate.oldFog(x, z) == (era != HOME), "era " + era + " oldFog == " + (era != HOME));
		}

		// Open sea: walk east from the home seat's box edge until the layout says ocean.
		Seat home = layout.seats().stream().filter(s -> s.era() == HOME).findFirst().orElseThrow();
		int seaX = home.maxX() + 16;
		while (layout.eraAt(seaX, home.centerZ()) != Layout.OCEAN && seaX < home.maxX() + OCEAN) {
			seaX += 16;
		}
		check(layout.eraAt(seaX, home.centerZ()) == Layout.OCEAN, "found open sea east of home at x " + seaX);
		check(climate.sample(seaX, home.centerZ()) == FALLBACK, "open sea falls back to vanilla");
		check(!climate.tintsVegetation(seaX, home.centerZ()), "open sea: vanilla vegetation");
		check(!climate.tintsSky(seaX, home.centerZ()), "open sea: vanilla sky");
		check(!climate.oldFog(seaX, home.centerZ()), "open sea: vanilla fog");
	}

	private static void checkSingle() {
		System.out.println("==== single-era layout");
		ContinentClimate climate = new ContinentClimate(Layout.single(), List.of(new Stub(0, true, true, ClimateDistribution.BETA)), List.of(true), (x, z) -> FALLBACK);
		check(climate.sample(123_456, -987_654).temp() == 0, "single era answers everywhere");
		check(climate.tintsVegetation(-5, 5) && climate.tintsSky(-5, 5) && climate.oldFog(-5, 5), "single era: all positional decisions on");
		ContinentClimate none = new ContinentClimate(Layout.single(), Arrays.asList((ClimateSampler) null), List.of(false), (x, z) -> FALLBACK);
		check(none.sample(1, 1) == FALLBACK && !none.useBiomeColor() && none.getDistribution() == ClimateDistribution.DEFAULT, "single vanilla era: all off");
	}

	private static void checkPayloadRoundTrip() {
		System.out.println("==== payload codec");
		CompoundTag tag = new CompoundTag();
		tag.putString("moderner_beta:provider", "moderner_beta:beta");
		tag.putInt("answer", 42);
		AtlasInfoPayload sent = new AtlasInfoPayload(20260829L, 10_000, 2_000, 20, List.of(
			new AtlasInfoPayload.Era(Identifier.fromNamespaceAndPath("moderner_beta", "classic_0_30"), 256, 256, false, true, Optional.empty()),
			new AtlasInfoPayload.Era(Identifier.fromNamespaceAndPath("moderner_beta", "beta"), 10_000, 10_000, true, false, Optional.of(tag)),
			new AtlasInfoPayload.Era(Identifier.fromNamespaceAndPath("minecraft", "overworld"), 10_000, 10_000, true, false, Optional.empty())));
		RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
		AtlasInfoPayload.CODEC.encode(buf, sent);
		int bytes = buf.readableBytes();
		AtlasInfoPayload got = AtlasInfoPayload.CODEC.decode(buf);
		check(got.equals(sent), "payload round trip (" + bytes + " bytes): " + got);
		check(buf.readableBytes() == 0, "payload fully consumed");
		check(got.eras().get(0).modernerBeta() && !got.eras().get(2).modernerBeta(), "modernerBeta flag per era");
		check(got.eras().get(0).footprint().equals(Footprint.finite(256, 256)), "footprint reconstructed");
		check(!AtlasInfoPayload.NONE.isAtlas() && got.isAtlas(), "NONE is not an atlas");
	}

	private static List<Footprint> footprints() {
		List<Footprint> footprints = new ArrayList<>();
		for (int era = 0; era < ERAS; era++) {
			int index = era;
			boolean finite = Arrays.stream(FINITE).anyMatch(f -> f == index);
			footprints.add(finite ? Footprint.finite(256, 256) : era == LEGACY_CONSOLE ? Footprint.bordered(5120) : Footprint.shaped(MAX_SIZE));
		}
		return footprints;
	}

	private static void check(boolean condition, String what) {
		if (condition) {
			System.out.println("  ok   " + what);
		} else {
			failures++;
			System.out.println("  FAIL " + what);
		}
	}
}
