package dev.continentsoftime.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.continentsoftime.ContinentsOfTime;
import dev.continentsoftime.atlas.Eras;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.Identifier;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * The user's defaults for new worlds: {@code config/continentsoftime.json}. Read once at startup, written with
 * defaults if missing. Values are baked into a world when it is created (see {@code AtlasSettings}); editing the
 * file afterwards affects only worlds created later.
 *
 * @param maxContinentSize upper bound on a continent's extent along either axis, in blocks
 * @param oceanWidth       minimum open water between continents, in blocks
 * @param eras             the era roster in layout order; {@code null} in the file means the built-in default
 * @param oceans           {@code false} for the "no oceans" option: no open water, continents butt against each
 *                         other at hard seams (the nearest era's terrain fills the gaps)
 */
public record ContinentsConfig(int maxContinentSize, int oceanWidth, List<Identifier> eras, boolean oceans) {
	public static final int DEFAULT_MAX_CONTINENT_SIZE = 10_000;
	public static final int DEFAULT_OCEAN_WIDTH = 2_000;

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static ContinentsConfig loaded;

	public static ContinentsConfig defaults() {
		return new ContinentsConfig(DEFAULT_MAX_CONTINENT_SIZE, DEFAULT_OCEAN_WIDTH, Eras.DEFAULT_ROSTER, true);
	}

	public static synchronized ContinentsConfig get() {
		if (loaded == null) {
			loaded = load(FabricLoader.getInstance().getConfigDir().resolve(ContinentsOfTime.MOD_ID + ".json"));
		}
		return loaded;
	}

	static ContinentsConfig load(Path file) {
		ContinentsConfig defaults = defaults();
		if (!Files.exists(file)) {
			write(file, defaults);
			return defaults;
		}
		try {
			JsonObject json = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
			int maxContinentSize = json.has("maxContinentSize") ? json.get("maxContinentSize").getAsInt() : defaults.maxContinentSize();
			int oceanWidth = json.has("oceanWidth") ? json.get("oceanWidth").getAsInt() : defaults.oceanWidth();
			boolean oceans = json.has("oceans") ? json.get("oceans").getAsBoolean() : defaults.oceans();
			List<Identifier> eras = defaults.eras();
			if (json.has("eras") && json.get("eras").isJsonArray()) {
				List<Identifier> parsed = new ArrayList<>();
				for (JsonElement e : json.getAsJsonArray("eras")) {
					parsed.add(Identifier.parse(e.getAsString()));
				}
				if (!parsed.isEmpty()) {
					eras = List.copyOf(parsed);
				}
			}
			if (maxContinentSize < 256) {
				ContinentsOfTime.LOGGER.warn("maxContinentSize {} is below 256; using {}", maxContinentSize, DEFAULT_MAX_CONTINENT_SIZE);
				maxContinentSize = DEFAULT_MAX_CONTINENT_SIZE;
			}
			if (oceanWidth < 0) {
				oceanWidth = DEFAULT_OCEAN_WIDTH;
			}
			return new ContinentsConfig(maxContinentSize, oceanWidth, eras, oceans);
		} catch (IOException | RuntimeException e) {
			ContinentsOfTime.LOGGER.error("Could not read {}; using defaults", file, e);
			return defaults;
		}
	}

	private static void write(Path file, ContinentsConfig config) {
		JsonObject json = new JsonObject();
		json.addProperty("_comment", "Defaults for NEW worlds. maxContinentSize / oceanWidth are in blocks; oceans=false is the no-oceans option (no open water, continents meet at hard seams); eras lists the era roster in layout order (Moderner Beta settings presets, or minecraft:overworld for the modern generator).");
		json.addProperty("maxContinentSize", config.maxContinentSize());
		json.addProperty("oceanWidth", config.oceanWidth());
		json.addProperty("oceans", config.oceans());
		JsonArray eras = new JsonArray();
		config.eras().forEach(id -> eras.add(id.toString()));
		json.add("eras", eras);
		try {
			Files.createDirectories(file.getParent());
			Files.writeString(file, GSON.toJson(json));
		} catch (IOException e) {
			ContinentsOfTime.LOGGER.error("Could not write {}", file, e);
		}
	}
}
