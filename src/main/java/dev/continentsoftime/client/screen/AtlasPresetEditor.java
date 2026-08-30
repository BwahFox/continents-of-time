package dev.continentsoftime.client.screen;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import dev.continentsoftime.ContinentsOfTime;
import dev.continentsoftime.atlas.AtlasChunkGenerator;
import dev.continentsoftime.atlas.AtlasSettings;
import dev.continentsoftime.config.ContinentsConfig;
import net.minecraft.client.gui.screens.worldselection.PresetEditor;
import net.minecraft.client.gui.screens.worldselection.WorldCreationContext;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import org.jetbrains.annotations.Nullable;

/**
 * The Create World screen's <b>Customize</b> button for the Continents of Time world type: vanilla asks the
 * world-creation state for a {@link PresetEditor} matching the selected preset (its own map covers flat and
 * single-biome worlds); {@code WorldCreationUiStateMixin} answers with this one for
 * {@code continentsoftime:continents_of_time}, the way Moderner Beta does for its preset.
 *
 * <p>The screen edits an {@link AtlasSettings}; applying it rebuilds the overworld generator through the atlas's
 * own codec — a hand-built {@code {"type": "continentsoftime:atlas", "biome_source": {...}}} parsed with registry
 * ops — which is exactly how the world preset is decoded at load, so a customized world and a config-default one
 * go through the same path. Client-only and optional: servers and the config file keep working without it.
 */
public final class AtlasPresetEditor {
	public static final ResourceKey<WorldPreset> PRESET = ResourceKey.create(Registries.WORLD_PRESET, ContinentsOfTime.id("continents_of_time"));
	private static final String ATLAS = ContinentsOfTime.id("atlas").toString();

	private AtlasPresetEditor() {}

	/** Whether the selected world type is the one this editor customizes. */
	public static boolean matches(@Nullable Holder<WorldPreset> preset) {
		return preset != null && preset.unwrapKey().filter(PRESET::equals).isPresent();
	}

	public static PresetEditor editor() {
		return (parent, context) -> new ContinentsCustomizeScreen(parent, context,
			settings -> parent.getUiState().updateDimensions(updater(settings)));
	}

	/** The settings the selected overworld generator carries, or the config defaults if it is not (yet) an atlas. */
	public static AtlasSettings currentSettings(WorldCreationContext context) {
		if (context.selectedDimensions().overworld() instanceof AtlasChunkGenerator atlas) {
			return atlas.atlas().settings();
		}
		return fromConfig();
	}

	/** What a new world gets without customizing: the config file's values. */
	public static AtlasSettings fromConfig() {
		ContinentsConfig config = ContinentsConfig.get();
		return new AtlasSettings(config.eras(), config.maxContinentSize(), config.oceanWidth(), config.oceans(), config.eraAccurate());
	}

	public static WorldCreationContext.DimensionsUpdater updater(AtlasSettings settings) {
		return (registries, dimensions) -> dimensions.replaceOverworldGenerator(registries, generator(registries, settings));
	}

	/** A fresh atlas generator over the settings, built the way a world preset is decoded. */
	public static ChunkGenerator generator(RegistryAccess registries, AtlasSettings settings) {
		RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, registries);
		JsonElement encoded = orThrow(AtlasSettings.CODEC.encodeStart(ops, settings), "encode atlas settings");
		JsonObject biomeSource = new JsonObject();
		biomeSource.addProperty("type", ATLAS);
		biomeSource.add("settings", encoded);
		JsonObject generator = new JsonObject();
		generator.addProperty("type", ATLAS);
		generator.add("biome_source", biomeSource);
		return orThrow(ChunkGenerator.CODEC.parse(ops, generator), "build the atlas generator");
	}

	private static <T> T orThrow(DataResult<T> result, String what) {
		return result.result().orElseThrow(() -> new IllegalStateException("Continents of Time: could not " + what + ": "
			+ result.error().map(e -> e.message()).orElse("?")));
	}
}
