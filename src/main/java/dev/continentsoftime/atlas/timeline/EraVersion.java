package dev.continentsoftime.atlas.timeline;

import net.minecraft.resources.Identifier;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Where an era sits on Java Edition's timeline, for deciding which structures existed then: a phase (Classic,
 * Indev, Infdev, Alpha, Beta, Release) and a version inside it. Non-Java eras are mapped to the Java version whose
 * structure set they had (Pocket Edition to 1.4, Bedrock by its number, Legacy Console to 1.13); the vanilla
 * generator and anything unrecognised count as modern, which allows every structure.
 *
 * @param phase 0 Classic, 1 Indev, 2 Infdev, 3 Alpha, 4 Beta, 5 Release
 */
public record EraVersion(int phase, int major, int minor, int patch) implements Comparable<EraVersion> {
	public static final int CLASSIC = 0, INDEV = 1, INFDEV = 2, ALPHA = 3, BETA = 4, RELEASE = 5;

	/** The current game: allows everything. */
	public static final EraVersion MODERN = new EraVersion(RELEASE, 99, 0, 0);

	public static EraVersion beta(int minor, int patch) {
		return new EraVersion(BETA, 1, minor, patch);
	}

	public static EraVersion release(int minor, int patch) {
		return new EraVersion(RELEASE, 1, minor, patch);
	}

	private static final Pattern BETA_VERSION = Pattern.compile("^beta_1_(\\d+)(?:_(\\d+)|_pre_?(\\d+))?");
	private static final Pattern RELEASE_VERSION = Pattern.compile("^release_1_(\\d+)(?:_(\\d+))?");
	private static final Pattern BEDROCK_VERSION = Pattern.compile("^bedrock_1_(\\d+)");
	private static final Pattern INFDEV_VERSION = Pattern.compile("^infdev_(\\d+)");

	/**
	 * The version of an era by its preset id. Moderner Beta's presets name their version ({@code beta_1_8_1},
	 * {@code release_1_12_2}, {@code infdev_420}); their variants ({@code beta_large_biomes}, {@code alpha_winter},
	 * {@code release_1_1_amplified}) share the base era's version. Anything else is {@link #MODERN}.
	 */
	public static EraVersion of(Identifier era) {
		if (era.getNamespace().equals(Identifier.DEFAULT_NAMESPACE)) {
			return MODERN;
		}
		String path = era.getPath();
		if (path.startsWith("classic")) {
			return new EraVersion(CLASSIC, 0, path.contains("0_30") ? 30 : 0, 0);
		}
		if (path.startsWith("indev")) {
			return new EraVersion(INDEV, 0, 0, 0);
		}
		Matcher m = INFDEV_VERSION.matcher(path);
		if (m.find()) {
			return new EraVersion(INFDEV, Integer.parseInt(m.group(1)), 0, 0);
		}
		if (path.startsWith("alpha")) {
			return new EraVersion(ALPHA, 1, 1, 2);
		}
		m = BETA_VERSION.matcher(path);
		if (m.find()) {
			int minor = Integer.parseInt(m.group(1));
			int patch = m.group(2) != null ? Integer.parseInt(m.group(2)) : 0;
			return beta(minor, patch);
		}
		if (path.startsWith("beta") || path.startsWith("skylands")) {
			return beta(7, 3); // Beta 1.7.3: the plain "beta" preset and its variants; Skylands is a Beta-era world
		}
		m = RELEASE_VERSION.matcher(path);
		if (m.find()) {
			return release(Integer.parseInt(m.group(1)), m.group(2) != null ? Integer.parseInt(m.group(2)) : 0);
		}
		if (path.startsWith("pe")) {
			return release(4, 2); // Pocket Edition's structure set (villages, strongholds, mineshafts, temples, huts)
		}
		m = BEDROCK_VERSION.matcher(path);
		if (m.find()) {
			return release(Integer.parseInt(m.group(1)), 0);
		}
		if (path.startsWith("legacy_console")) {
			return release(13, 0);
		}
		return MODERN;
	}

	@Override
	public int compareTo(EraVersion other) {
		if (phase != other.phase) return Integer.compare(phase, other.phase);
		if (major != other.major) return Integer.compare(major, other.major);
		if (minor != other.minor) return Integer.compare(minor, other.minor);
		return Integer.compare(patch, other.patch);
	}

	public boolean isBefore(EraVersion other) {
		return compareTo(other) < 0;
	}

	@Override
	public String toString() {
		return switch (phase) {
			case CLASSIC -> "Classic 0." + minor + "." + patch;
			case INDEV -> "Indev";
			case INFDEV -> "Infdev " + major;
			case ALPHA -> "Alpha " + major + "." + minor + "." + patch;
			case BETA -> "Beta " + major + "." + minor + (patch > 0 ? "." + patch : "");
			default -> this == MODERN || major >= 99 ? "modern" : "Release " + major + "." + minor + (patch > 0 ? "." + patch : "");
		};
	}
}
