package dev.continentsoftime;

import dev.continentsoftime.atlas.AtlasBiomeSource;
import dev.continentsoftime.atlas.AtlasChunkGenerator;
import dev.continentsoftime.command.CotCommand;
import dev.continentsoftime.config.ContinentsConfig;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.dimension.LevelStem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Mod entry point: registers the atlas generator and biome source, and starts hosted eras with the world seed. */
public final class ContinentsOfTime implements ModInitializer {
	public static final String MOD_ID = "continentsoftime";
	public static final Logger LOGGER = LoggerFactory.getLogger("ContinentsOfTime");

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	@Override
	public void onInitialize() {
		Registry.register(BuiltInRegistries.CHUNK_GENERATOR, id("atlas"), AtlasChunkGenerator.CODEC);
		Registry.register(BuiltInRegistries.BIOME_SOURCE, id("atlas"), AtlasBiomeSource.CODEC);
		ServerLifecycleEvents.SERVER_STARTING.register(ContinentsOfTime::initWorlds);
		CotCommand.register();

		ContinentsConfig config = ContinentsConfig.get();
		LOGGER.info("Continents of Time: {} eras in the roster, continents up to {} blocks, oceans at least {} blocks",
			config.eras().size(), config.maxContinentSize(), config.oceanWidth());
	}

	private static void initWorlds(MinecraftServer server) {
		long seed = server.getWorldGenSettings().options().seed();
		Registry<LevelStem> stems = server.registries().compositeAccess().lookupOrThrow(Registries.LEVEL_STEM);
		stems.entrySet().forEach(entry -> {
			if (entry.getValue().generator() instanceof AtlasChunkGenerator atlas) {
				atlas.init(seed);
				LOGGER.info("Continents of Time: {} hosts {} era(s): {}", entry.getKey().identifier(),
					atlas.atlas().eras().size(), atlas.atlas().settings().eras());
			}
		});
	}
}
