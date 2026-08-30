package dev.continentsoftime.atlas;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.continentsoftime.config.ContinentsConfig;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Optional;

/**
 * Everything the atlas needs to lay out a world, as stored in the world's generator JSON.
 *
 * <p>Fields left out of a world preset's JSON fall back to the user's config file at world-creation time; once the
 * world exists its level.dat carries the values explicitly, so later config edits never change an existing world.
 *
 * @param eras             the era roster, in layout order. An id in the {@code minecraft} namespace names a vanilla
 *                         noise-settings preset (the modern generator); any other id names a Moderner Beta settings
 *                         preset.
 * @param maxContinentSize upper bound on a continent's extent along either axis, in blocks. Continents are shaped
 *                         by noise and are usually smaller; none is ever larger.
 * @param oceanWidth       minimum open water between two continents, in blocks.
 * @param oceans           whether the gaps between continents are open water (the default) or, with the "no
 *                         oceans" option, the nearest era's own terrain up to a hard seam.
 */
public record AtlasSettings(List<Identifier> eras, int maxContinentSize, int oceanWidth, boolean oceans) {
	public static final Codec<AtlasSettings> CODEC = RecordCodecBuilder.create(i -> i.group(
		Identifier.CODEC.listOf().optionalFieldOf("eras").forGetter(s -> Optional.of(s.eras)),
		Codec.intRange(256, 1_000_000).optionalFieldOf("max_continent_size").forGetter(s -> Optional.of(s.maxContinentSize)),
		Codec.intRange(0, 1_000_000).optionalFieldOf("ocean_width").forGetter(s -> Optional.of(s.oceanWidth)),
		Codec.BOOL.optionalFieldOf("oceans").forGetter(s -> Optional.of(s.oceans))
	).apply(i, AtlasSettings::fromJson));

	private static AtlasSettings fromJson(Optional<List<Identifier>> eras, Optional<Integer> maxContinentSize, Optional<Integer> oceanWidth, Optional<Boolean> oceans) {
		ContinentsConfig config = ContinentsConfig.get();
		return new AtlasSettings(
			eras.orElseGet(config::eras),
			maxContinentSize.orElse(config.maxContinentSize()),
			oceanWidth.orElse(config.oceanWidth()),
			oceans.orElse(config.oceans())
		);
	}

	public AtlasSettings {
		if (eras.isEmpty()) {
			throw new IllegalArgumentException("Continents of Time: the era roster is empty");
		}
		eras = List.copyOf(eras);
	}
}
