package dev.continentsoftime.atlas;

import net.minecraft.resources.Identifier;

import java.util.List;

/** The default era roster: every world-generation type Moderner Beta offers, plus the game's own modern generator. */
public final class Eras {
	private Eras() {}

	/** The modern (1.18+) generator, i.e. vanilla's {@code minecraft:overworld} noise settings. */
	public static final Identifier MODERN = Identifier.withDefaultNamespace("overworld");

	private static Identifier mb(String preset) {
		return Identifier.fromNamespaceAndPath("moderner_beta", preset);
	}

	/**
	 * Chronological through Java Edition, then the sidelines (Pocket/Bedrock, Legacy Console, Skylands). Variant
	 * presets Moderner Beta also ships (large biomes, amplified, "water world", ...) are knobs on these eras, not
	 * eras, so they are not seated by default; a config roster can add any of them. The one exception is Alpha's
	 * winter mode, seated after Alpha: to the people who play Alpha it is its own world (author's call, 2026-08-29).
	 */
	public static final List<Identifier> DEFAULT_ROSTER = List.of(
		mb("classic_0_0_14a_08"),
		mb("classic_0_30"),
		mb("indev"),
		mb("infdev_227"),
		mb("infdev_325"),
		mb("infdev_415"),
		mb("infdev_420"),
		mb("infdev_611"),
		mb("alpha"),
		mb("alpha_winter"),
		mb("beta_1_1_02"),
		mb("beta"),
		mb("beta_1_8_1"),
		mb("beta_1_9_pre_3"),
		mb("release_1_0_0"),
		mb("release_1_1"),
		mb("release_1_2_5"),
		mb("release_1_6_4"),
		mb("release_1_12_2"),
		mb("release_1_17_1"),
		MODERN,
		mb("pe"),
		mb("bedrock_1_2"),
		mb("bedrock_1_17"),
		mb("legacy_console_large"),
		mb("skylands")
	);

	/** True for ids that name a vanilla noise-settings preset rather than a Moderner Beta settings preset. */
	public static boolean isVanilla(Identifier era) {
		return era.getNamespace().equals(Identifier.DEFAULT_NAMESPACE);
	}
}
