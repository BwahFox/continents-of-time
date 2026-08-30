package dev.continentsoftime.structure;

import dev.continentsoftime.atlas.Eras;
import dev.continentsoftime.atlas.structure.EraStructures;
import dev.continentsoftime.atlas.structure.EraVersion;
import net.minecraft.resources.Identifier;

import java.util.List;

/**
 * Headless check of the era/structure timeline: {@code ./gradlew structuresTest}. Every default-roster era parses
 * to the expected version and the versions are ordered like the roster (the Java line of it); representative
 * structure sets are allowed exactly from the version that introduced them; unknown sets are allowed everywhere.
 */
public final class EraStructuresHarness {
	private static int failures;

	public static void main(String[] args) {
		System.out.println("==== era versions");
		expect("moderner_beta:classic_0_30", "Classic 0.30.0");
		expect("moderner_beta:indev", "Indev");
		expect("moderner_beta:infdev_420", "Infdev 420");
		expect("moderner_beta:alpha", "Alpha 1.1.2");
		expect("moderner_beta:alpha_winter", "Alpha 1.1.2");
		expect("moderner_beta:beta_1_1_02", "Beta 1.1.2");
		expect("moderner_beta:beta", "Beta 1.7.3");
		expect("moderner_beta:beta_large_biomes", "Beta 1.7.3");
		expect("moderner_beta:beta_1_8_1", "Beta 1.8.1");
		expect("moderner_beta:beta_1_9_pre_3", "Beta 1.9");
		expect("moderner_beta:release_1_0_0", "Release 1.0");
		expect("moderner_beta:release_1_2_5", "Release 1.2.5");
		expect("moderner_beta:release_1_12_2_amplified", "Release 1.12.2");
		expect("moderner_beta:release_1_17_1", "Release 1.17.1");
		expect("moderner_beta:pe", "Release 1.4.2");
		expect("moderner_beta:bedrock_1_17", "Release 1.17");
		expect("moderner_beta:legacy_console_large", "Release 1.13");
		expect("moderner_beta:skylands", "Beta 1.7.3");
		expect("minecraft:overworld", "modern");
		expect("somemod:weird_preset", "modern");

		System.out.println("==== roster order (Java line)");
		List<Identifier> java = Eras.DEFAULT_ROSTER.subList(0, Eras.DEFAULT_ROSTER.indexOf(Eras.MODERN) + 1);
		EraVersion previous = null;
		int disorder = 0;
		for (Identifier era : java) {
			EraVersion version = EraVersion.of(era);
			if (previous != null && version.isBefore(previous)) {
				disorder++;
				System.out.println("  out of order: " + era + " (" + version + ") after " + previous);
			}
			previous = version;
		}
		check(disorder == 0, "the Java line of the roster is non-decreasing in version (" + java.size() + " eras)");

		System.out.println("==== structure sets");
		allowed("moderner_beta:alpha", "minecraft:villages", false);
		allowed("moderner_beta:beta", "minecraft:villages", false);
		allowed("moderner_beta:beta_1_8_1", "minecraft:villages", true);
		allowed("moderner_beta:beta_1_8_1", "minecraft:strongholds", true);
		allowed("moderner_beta:beta_1_8_1", "minecraft:mineshafts", true);
		allowed("moderner_beta:beta_1_8_1", "minecraft:desert_pyramids", false);
		allowed("moderner_beta:release_1_2_5", "minecraft:desert_pyramids", false);
		allowed("moderner_beta:release_1_6_4", "minecraft:desert_pyramids", true);
		allowed("moderner_beta:release_1_6_4", "minecraft:swamp_huts", true);
		allowed("moderner_beta:release_1_6_4", "minecraft:ocean_monuments", false);
		allowed("moderner_beta:release_1_12_2", "minecraft:ocean_monuments", true);
		allowed("moderner_beta:release_1_12_2", "minecraft:woodland_mansions", true);
		allowed("moderner_beta:release_1_12_2", "minecraft:shipwrecks", false);
		allowed("moderner_beta:release_1_17_1", "minecraft:shipwrecks", true);
		allowed("moderner_beta:release_1_17_1", "minecraft:pillager_outposts", true);
		allowed("moderner_beta:release_1_17_1", "minecraft:ruined_portals", true);
		allowed("moderner_beta:release_1_17_1", "minecraft:ancient_cities", false);
		allowed("moderner_beta:release_1_17_1", "minecraft:trial_chambers", false);
		allowed("minecraft:overworld", "minecraft:trial_chambers", true);
		allowed("moderner_beta:pe", "minecraft:villages", true);
		allowed("moderner_beta:pe", "minecraft:swamp_huts", true);
		allowed("moderner_beta:pe", "minecraft:ocean_monuments", false);
		allowed("moderner_beta:legacy_console_large", "minecraft:shipwrecks", true);
		allowed("moderner_beta:legacy_console_large", "minecraft:pillager_outposts", false);
		allowed("moderner_beta:skylands", "minecraft:villages", false);
		allowed("moderner_beta:classic_0_30", "moderner_beta:ocean_shrine", true);
		allowed("moderner_beta:alpha", "somemod:castles", true);
		check(EraStructures.introduced(Identifier.parse("minecraft:villages")).isPresent(), "villages have a known introduction");
		check(EraStructures.introduced(Identifier.parse("somemod:castles")).isEmpty(), "unknown sets have none");

		if (failures > 0) {
			System.out.println("FAILED: " + failures + " check(s)");
			System.exit(1);
		}
		System.out.println("Structures harness: all checks passed");
	}

	private static void expect(String era, String version) {
		String got = EraVersion.of(Identifier.parse(era)).toString();
		check(got.equals(version), era + " -> " + got + (got.equals(version) ? "" : " (expected " + version + ")"));
	}

	private static void allowed(String era, String set, boolean expected) {
		boolean got = EraStructures.allows(EraVersion.of(Identifier.parse(era)), Identifier.parse(set));
		check(got == expected, set + " on " + era + ": " + (got ? "allowed" : "off") + (got == expected ? "" : " (expected " + (expected ? "allowed" : "off") + ")"));
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
