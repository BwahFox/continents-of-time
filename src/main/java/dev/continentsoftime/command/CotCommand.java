package dev.continentsoftime.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.continentsoftime.atlas.AtlasChunkGenerator;
import dev.continentsoftime.atlas.HostedEra;
import dev.continentsoftime.atlas.layout.ContinentLayout;
import dev.continentsoftime.atlas.layout.Seabed;
import dev.continentsoftime.atlas.layout.Seat;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * {@code /cot} — looking around the atlas without a map:
 * <ul>
 *   <li>{@code /cot seats} lists every era's seat (box and centre) in this world;</li>
 *   <li>{@code /cot where} says which era owns the chunk you are in, the coast field there, and the nearest continent;</li>
 *   <li>{@code /cot seat <era>} puts you on top of that era's continent at its centre (generating the chunk if needed).</li>
 * </ul>
 * Operator level 2, like {@code /tp}.
 */
public final class CotCommand {
	private CotCommand() {}

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> register(dispatcher));
	}

	private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("cot")
			.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
			.then(Commands.literal("seats").executes(context -> seats(context.getSource())))
			.then(Commands.literal("where").executes(context -> where(context.getSource())))
			.then(Commands.literal("seat")
				.then(Commands.argument("era", IdentifierArgument.id())
					.suggests((context, builder) -> {
						AtlasChunkGenerator atlas = atlas(context.getSource());
						return atlas == null ? builder.buildFuture()
							: SharedSuggestionProvider.suggest(atlas.atlas().settings().eras().stream().map(Identifier::toString), builder);
					})
					.executes(context -> seat(context.getSource(), IdentifierArgument.getId(context, "era"))))));
	}

	private static @Nullable AtlasChunkGenerator atlas(CommandSourceStack source) {
		return source.getLevel().getChunkSource().getGenerator() instanceof AtlasChunkGenerator atlas ? atlas : null;
	}

	private static @Nullable ContinentLayout layout(CommandSourceStack source) {
		AtlasChunkGenerator atlas = atlas(source);
		if (atlas == null) {
			source.sendFailure(Component.literal("This dimension is not a Continents of Time atlas"));
			return null;
		}
		if (!(atlas.atlas().layout() instanceof ContinentLayout layout)) {
			source.sendFailure(Component.literal("This world hosts one era everywhere; there are no seats"));
			return null;
		}
		return layout;
	}

	private static int seats(CommandSourceStack source) {
		ContinentLayout layout = layout(source);
		if (layout == null) {
			return 0;
		}
		AtlasChunkGenerator atlas = atlas(source);
		source.sendSuccess(() -> Component.literal(layout.describe()), false);
		for (Seat seat : layout.seats()) {
			HostedEra era = atlas.atlas().eras().get(seat.era());
			String kind = seat.shaped() ? "" : " (finite level)";
			source.sendSuccess(() -> Component.literal(String.format("%s: centre %d, %d; box x %d..%d, z %d..%d%s",
				era.id(), seat.centerX(), seat.centerZ(), seat.minX(), seat.maxX(), seat.minZ(), seat.maxZ(), kind)), false);
		}
		return layout.seats().size();
	}

	private static int where(CommandSourceStack source) {
		AtlasChunkGenerator atlas = atlas(source);
		if (atlas == null) {
			source.sendFailure(Component.literal("This dimension is not a Continents of Time atlas"));
			return 0;
		}
		Vec3 position = source.getPosition();
		int x = (int) Math.floor(position.x);
		int z = (int) Math.floor(position.z);
		HostedEra owner = atlas.atlas().ownerOfChunk(x >> 4, z >> 4);
		HostedEra nearest = atlas.atlas().eraAtBlock(x, z);
		double field = atlas.atlas().layout().fieldAt(x, z);
		String zone = Seabed.sea(field) ? "sea" : Seabed.inland(field) ? "inland" : "coast band";
		source.sendSuccess(() -> Component.literal(String.format("%d, %d: chunk owned by %s; coast field %.3f (%s); nearest continent %s",
			x, z, owner == null ? "the ocean" : owner.id(), field, zone, nearest.id())), false);
		return 1;
	}

	private static int seat(CommandSourceStack source, Identifier eraId) throws CommandSyntaxException {
		ContinentLayout layout = layout(source);
		if (layout == null) {
			return 0;
		}
		AtlasChunkGenerator atlas = atlas(source);
		int index = atlas.atlas().settings().eras().indexOf(eraId);
		if (index < 0) {
			source.sendFailure(Component.literal("No era " + eraId + " in this world's roster; see /cot seats"));
			return 0;
		}
		ServerPlayer player = source.getPlayerOrException();
		ServerLevel level = source.getLevel();
		Seat seat = layout.seatOf(index);
		int x = seat.centerX();
		int z = seat.centerZ();
		level.getChunk(x >> 4, z >> 4); // generate it if needed, so the height below is real
		int y = Math.max(level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z), atlas.getSeaLevel()) + 1;
		player.teleportTo(x + 0.5, y, z + 0.5);
		source.sendSuccess(() -> Component.literal(String.format("Teleported to %s at %d, %d, %d", eraId, x, y, z)), true);
		return 1;
	}
}
