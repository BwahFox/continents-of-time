package dev.continentsoftime.client;

import dev.continentsoftime.ContinentsOfTime;
import dev.continentsoftime.atlas.Translated;
import dev.continentsoftime.atlas.layout.ContinentLayout;
import dev.continentsoftime.atlas.layout.Footprint;
import dev.continentsoftime.atlas.layout.Layout;
import dev.continentsoftime.atlas.layout.Seat;
import dev.continentsoftime.network.AtlasInfoPayload;
import dev.continentsoftime.util.Compat;
import mod.bluestaggo.modernerbeta.api.level.biome.BiomeProvider;
import mod.bluestaggo.modernerbeta.api.level.biome.climate.ClimateSampler;
import mod.bluestaggo.modernerbeta.api.level.biome.climate.Clime;
import mod.bluestaggo.modernerbeta.client.color.SkyColorSampler;
import mod.bluestaggo.modernerbeta.client.color.block.BlockColorSampler;
import mod.bluestaggo.modernerbeta.registry.ModernBetaRegistries;
import mod.bluestaggo.modernerbeta.settings.ModernBetaSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.util.Mth;
import net.minecraft.world.level.biome.Biome;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The client's copy of the atlas, for visuals only: built from {@link AtlasInfoPayload} when the server sends
 * level info, dropped on disconnect or when the player enters a level that is not an atlas. Holds the
 * {@link ContinentClimate} the client mixins consult and installs it in Moderner Beta's colour samplers.
 */
public final class ClientAtlas {
	private static volatile @Nullable ContinentClimate current;

	private ClientAtlas() {}

	/** The climate of the level the player is in, or {@code null} when it is not an atlas (or the client is not in a level). */
	public static @Nullable ContinentClimate current() {
		return current;
	}

	public static void apply(Minecraft client, AtlasInfoPayload payload) {
		ClientLevel level = client.level;
		if (!payload.isAtlas() || level == null) {
			clear();
			return;
		}

		List<Footprint> footprints = payload.eras().stream().map(AtlasInfoPayload.Era::footprint).toList();
		// Mirrors AtlasBiomeSource.init: one era is everywhere; more are laid out from the seed.
		Layout layout = footprints.size() == 1
			? Layout.single()
			: new ContinentLayout(payload.seed(), footprints, payload.home(), payload.maxContinentSize(), payload.oceanWidth(), payload.oceans());

		List<@Nullable ClimateSampler> samplers = new ArrayList<>(payload.eras().size());
		List<Boolean> modernerBeta = new ArrayList<>(payload.eras().size());
		for (int index = 0; index < payload.eras().size(); index++) {
			int eraIndex = index;
			AtlasInfoPayload.Era era = payload.eras().get(eraIndex);
			samplers.add(era.climateSettings().map(tag -> buildSampler(level, payload.seed(), era, tag, layout, eraIndex)).orElse(null));
			modernerBeta.add(era.modernerBeta());
		}

		ContinentClimate climate = new ContinentClimate(layout, samplers, modernerBeta, (x, z) -> vanillaClime(client, x, z));
		current = climate;
		BlockColorSampler.INSTANCE.setClimateSampler(climate);
		SkyColorSampler.INSTANCE.setClimateSampler(climate);
		ContinentsOfTime.LOGGER.info("Continents of Time: client climate installed for {} of {} eras (seed {})",
			climate.climateEras(), payload.eras().size(), payload.seed());
	}

	/**
	 * Builds the era's biome provider on the client exactly as the server did (same settings, same seed), the
	 * way Moderner Beta rebuilds its one provider on join, and keeps it if it samples a climate. An anchored era
	 * is translated to its seat like the server's copy. Failures are logged and leave the era without a climate.
	 */
	private static @Nullable ClimateSampler buildSampler(ClientLevel level, long seed, AtlasInfoPayload.Era era,
	                                                     net.minecraft.nbt.CompoundTag tag, Layout layout, int index) {
		try {
			ModernBetaSettings settings = ModernBetaSettings.fromCompound(level.registryAccess(), tag);
			BiomeProvider provider = Compat.value(ModernBetaRegistries.BIOME, settings.getProvider())
				.apply(settings, Compat.holders(level.registryAccess(), Registries.BIOME), seed);
			provider.init();
			if (era.anchored() && layout instanceof ContinentLayout continents && provider instanceof Translated translated) {
				Seat seat = continents.seats().stream().filter(s -> s.era() == index).findFirst().orElseThrow();
				translated.continentsoftime$translateTo(seat.centerX(), seat.centerZ());
			}
			return provider instanceof ClimateSampler sampler ? sampler : null;
		} catch (RuntimeException e) {
			ContinentsOfTime.LOGGER.warn("Continents of Time: could not build the client climate for {}", era.id(), e);
			return null;
		}
	}

	public static void clear() {
		ContinentClimate was = current;
		current = null;
		if (was == null) {
			return;
		}
		// Only undo our own installation; Moderner Beta may already have replaced it for its own level.
		if (BlockColorSampler.INSTANCE.getClimateSampler() == was) {
			BlockColorSampler.INSTANCE.setClimateSampler(null);
		}
		if (SkyColorSampler.INSTANCE.getClimateSampler() == was) {
			SkyColorSampler.INSTANCE.setClimateSampler(null);
		}
	}

	/** The vanilla biome's own climate at the column, as the grass and foliage colormaps read it. */
	private static Clime vanillaClime(Minecraft client, int x, int z) {
		ClientLevel level = client.level;
		if (level == null) {
			return new Clime(0.5, 0.5);
		}
		Biome biome = level.getBiome(new BlockPos(x, level.getSeaLevel(), z)).value();
		Biome.ClimateSettings climate = biome.climateSettings;
		return new Clime(Mth.clamp(climate.temperature(), 0, 1), Mth.clamp(climate.downfall(), 0, 1));
	}
}
