package dev.continentsoftime.atlas.timeline;

import mod.bluestaggo.modernerbeta.settings.ModernBetaSettings;
import mod.bluestaggo.modernerbeta.settings.SettingsComponentTypes;
import mod.bluestaggo.modernerbeta.settings.component.CaveBiomeVoronoi;
import mod.bluestaggo.modernerbeta.level.biome.voronoi.VoronoiPointCaveBiome;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Which underground biomes existed in which version. Moderner Beta's presets inject vanilla's modern cave biomes
 * below the surface of every era from Beta on (a voronoi map of points, each naming a biome or "keep the surface
 * biome"); on an era-accurate atlas the points whose biome is newer than the era are dropped, so a Beta continent
 * has plain caves, a 1.17.1 one has none of 1.18's, and only the modern era gets sulfur caves. Unknown cave biomes
 * (a mod's) are kept.
 */
public final class EraCaveBiomes {
	private static final Map<Identifier, EraVersion> INTRODUCED = Map.of(
		Identifier.withDefaultNamespace("dripstone_caves"), EraVersion.release(18, 0),
		Identifier.withDefaultNamespace("lush_caves"), EraVersion.release(18, 0),
		Identifier.withDefaultNamespace("deep_dark"), EraVersion.release(19, 0),
		Identifier.withDefaultNamespace("sulfur_caves"), new EraVersion(EraVersion.RELEASE, 26, 2, 0)
	);

	private EraCaveBiomes() {}

	/** The version a vanilla cave biome first generated in, if known. */
	public static Optional<EraVersion> introduced(Identifier caveBiome) {
		return Optional.ofNullable(INTRODUCED.get(caveBiome));
	}

	/** Whether the cave biome may appear under a continent of the given version. */
	public static boolean allows(EraVersion era, Identifier caveBiome) {
		EraVersion introduced = INTRODUCED.get(caveBiome);
		return introduced == null || !era.isBefore(introduced);
	}

	/**
	 * The era's cave-biome settings with the biomes it was too early for removed. {@code reference} is the
	 * preset-referencing settings the biome source is built from (they keep resolving through the preset, so the
	 * filtered voronoi map overlays the preset's); {@code resolved} is the same after resolution, to read the map.
	 * Returns {@code reference} unchanged when nothing needs removing, and "no cave biomes" when nothing survives.
	 */
	public static ModernBetaSettings filter(ModernBetaSettings reference, ModernBetaSettings resolved, EraVersion era) {
		CaveBiomeVoronoi voronoi = resolved.get(SettingsComponentTypes.CAVE_BIOME_VORONOI);
		if (voronoi == null || voronoi.points().isEmpty()) {
			return reference;
		}
		List<VoronoiPointCaveBiome> kept = voronoi.points().stream()
			.filter(point -> point.biome().flatMap(holder -> holder.unwrapKey()).map(key -> allows(era, key.identifier())).orElse(true))
			.toList();
		if (kept.size() == voronoi.points().size()) {
			return reference;
		}
		if (kept.stream().noneMatch(point -> point.biome().isPresent())) {
			return ModernBetaSettings.noCaveBiomes();
		}
		return reference.extend()
			.add(SettingsComponentTypes.CAVE_BIOME_VORONOI,
				new CaveBiomeVoronoi(voronoi.horizontalScale(), voronoi.verticalScale(), voronoi.depthMinY(), voronoi.depthMaxY(), kept))
			.build();
	}

	/** The cave biomes a resolved cave-biome settings object can place (ids), for display. */
	public static List<String> biomesIn(ModernBetaSettings resolved) {
		CaveBiomeVoronoi voronoi = resolved.get(SettingsComponentTypes.CAVE_BIOME_VORONOI);
		if (voronoi == null || !resolved.getProvider().getPath().equals("voronoi")) {
			return List.of();
		}
		return voronoi.points().stream()
			.flatMap(point -> point.biome().flatMap(holder -> holder.unwrapKey()).map(key -> key.identifier().toString()).stream())
			.distinct().sorted().toList();
	}
}
