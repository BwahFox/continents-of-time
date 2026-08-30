package dev.continentsoftime.network;

import dev.continentsoftime.ContinentsOfTime;
import dev.continentsoftime.atlas.AtlasBiomeSource;
import dev.continentsoftime.atlas.AtlasChunkGenerator;
import dev.continentsoftime.atlas.AtlasSettings;
import dev.continentsoftime.atlas.Eras;
import dev.continentsoftime.atlas.HostedEra;
import dev.continentsoftime.atlas.layout.Footprint;
import mod.bluestaggo.modernerbeta.api.level.biome.climate.ClimateSampler;
import mod.bluestaggo.modernerbeta.level.biome.ModernBetaBiomeSource;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * What a client with this mod installed needs to rebuild the atlas for visuals: the seed and sizes the layout is
 * built from, every era's footprint, and — for eras whose Moderner Beta biome provider samples a climate — the
 * provider's settings, so the client can build the same provider and sample the same climate. Sent whenever the
 * server sends a player their level info (join, respawn, dimension change); an empty roster means "this level is
 * not an atlas" and the client drops any climate it holds. Vanilla clients never receive it (the server checks
 * that the client registered the channel), so the server never depends on it.
 *
 * <p>Moderner Beta does the same for its own worlds with one provider; this is the same idea for a roster.
 */
public record AtlasInfoPayload(long seed, int maxContinentSize, int oceanWidth, int home, List<Era> eras)
	implements CustomPacketPayload {

	/** One era as the layout and the client's climate need it. {@code climateSettings} is present only for climate-sampling eras. */
	public record Era(Identifier id, int width, int length, boolean shaped, boolean anchored, Optional<CompoundTag> climateSettings) {
		static final StreamCodec<RegistryFriendlyByteBuf, Era> CODEC = StreamCodec.composite(
			Identifier.STREAM_CODEC, Era::id,
			ByteBufCodecs.VAR_INT, Era::width,
			ByteBufCodecs.VAR_INT, Era::length,
			ByteBufCodecs.BOOL, Era::shaped,
			ByteBufCodecs.BOOL, Era::anchored,
			ByteBufCodecs.COMPOUND_TAG.apply(ByteBufCodecs::optional), Era::climateSettings,
			Era::new);

		public Footprint footprint() {
			return new Footprint(width, length, shaped, anchored);
		}

		/** Whether the era is one of Moderner Beta's (as opposed to the vanilla generator). */
		public boolean modernerBeta() {
			return !Eras.isVanilla(id);
		}
	}

	public static final Type<AtlasInfoPayload> TYPE = new Type<>(ContinentsOfTime.id("atlas_info"));
	public static final StreamCodec<RegistryFriendlyByteBuf, AtlasInfoPayload> CODEC = StreamCodec.composite(
		ByteBufCodecs.LONG, AtlasInfoPayload::seed,
		ByteBufCodecs.VAR_INT, AtlasInfoPayload::maxContinentSize,
		ByteBufCodecs.VAR_INT, AtlasInfoPayload::oceanWidth,
		ByteBufCodecs.VAR_INT, AtlasInfoPayload::home,
		Era.CODEC.apply(ByteBufCodecs.list()), AtlasInfoPayload::eras,
		AtlasInfoPayload::new);

	/** "This level is not an atlas." */
	public static final AtlasInfoPayload NONE = new AtlasInfoPayload(0, 0, 0, 0, List.of());

	public boolean isAtlas() {
		return !eras.isEmpty();
	}

	/** Describes the level's atlas, or {@link #NONE} when the level's generator is not the atlas (the Nether, say). */
	public static AtlasInfoPayload of(ServerLevel level) {
		if (!(level.getChunkSource().getGenerator() instanceof AtlasChunkGenerator generator)) {
			return NONE;
		}
		AtlasBiomeSource atlas = generator.atlas();
		AtlasSettings settings = atlas.settings();
		List<Era> eras = new ArrayList<>(atlas.eras().size());
		for (HostedEra era : atlas.eras()) {
			Footprint footprint = era.footprint(settings.maxContinentSize());
			Optional<CompoundTag> climate = Optional.empty();
			if (era.biomeSource() instanceof ModernBetaBiomeSource mb
				&& mb.getBiomeProvider() instanceof ClimateSampler) {
				climate = Optional.of(mb.getBiomeProvider().getSettings().toCompound());
			}
			eras.add(new Era(era.id(), footprint.width(), footprint.length(), footprint.shaped(), footprint.anchored(), climate));
		}
		return new AtlasInfoPayload(level.getSeed(), settings.maxContinentSize(), settings.oceanWidth(), atlas.homeEra(), eras);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
