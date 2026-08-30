package dev.continentsoftime.util;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;

import java.util.function.Predicate;

/**
 * The Minecraft API differences between the versions this mod is built for (1.20.1 and 26.2) that are not plain
 * renames, in one place. Plain renames (the identifier class's name, ...) are string replacements in
 * {@code build.gradle}; everything else is a {@code //?} Stonecutter condition, and lives here or, where a whole
 * method's shape differs (chunk-generation steps, networking, the client mixins), at that site.
 *
 * <p>Keeping the version-touching code in few, obvious places is what keeps the backport small: a new Minecraft
 * version means revisiting this class, the replacements, and the sites that say {@code //?}.
 */
public final class Compat {
	private Compat() {}

	// ---- identifiers: the factories changed in 1.21 (constructors before) ----

	public static Identifier id(String namespace, String path) {
		//? if >=1.21 {
		return Identifier.fromNamespaceAndPath(namespace, path);
		//?} else {
		/*return new Identifier(namespace, path);
		*///?}
	}

	public static Identifier vanillaId(String path) {
		//? if >=1.21 {
		return Identifier.withDefaultNamespace(path);
		//?} else {
		/*return new Identifier(path);
		*///?}
	}

	public static Identifier parseId(String id) {
		//? if >=1.21 {
		return Identifier.parse(id);
		//?} else {
		/*return new Identifier(id);
		*///?}
	}

	// ---- chunks ----

	/** Set a block during generation, no updates (the flag argument changed from a boolean to update flags in 1.21.5). */
	public static void setBlock(ChunkAccess chunk, BlockPos pos, BlockState state) {
		//? if >=1.21.5 {
		chunk.setBlockState(pos, state, 0);
		//?} else {
		/*chunk.setBlockState(pos, state, false);
		*///?}
	}

	public static int chunkX(ChunkPos pos) {
		//? if >=26.1 {
		return pos.x();
		//?} else {
		/*return pos.x;
		*///?}
	}

	public static int chunkZ(ChunkPos pos) {
		//? if >=26.1 {
		return pos.z();
		//?} else {
		/*return pos.z;
		*///?}
	}

	// ---- server and registries ----

	public static long seed(MinecraftServer server) {
		//? if >=1.21.2 {
		return server.getWorldGenSettings().options().seed();
		//?} else {
		/*return server.getWorldData().worldGenOptions().seed();
		*///?}
	}

	public static RegistryAccess registries(MinecraftServer server) {
		//? if >=1.21.2 {
		return server.registries().compositeAccess();
		//?} else {
		/*return server.registryAccess();
		*///?}
	}

	public static <T> Registry<T> registry(RegistryAccess access, ResourceKey<Registry<T>> key) {
		//? if >=1.21.2 {
		return access.lookupOrThrow(key);
		//?} else {
		/*return access.registryOrThrow(key);
		*///?}
	}

	/** A registry as a {@link HolderGetter} (a registry is one itself from 1.21.2; before, through its lookup view). */
	public static <T> HolderGetter<T> holders(RegistryAccess access, ResourceKey<Registry<T>> key) {
		//? if >=1.21.2 {
		return access.lookupOrThrow(key);
		//?} else {
		/*return access.registryOrThrow(key).asLookup();
		*///?}
	}

	/** {@code registry.getValue(id)} (1.21.2+), {@code registry.get(id)} before. */
	public static <T> T value(Registry<T> registry, Identifier id) {
		//? if >=1.21.2 {
		return registry.getValue(id);
		//?} else {
		/*return registry.get(id);
		*///?}
	}

	// ---- commands ----

	/** Operator level 2 ("gamemasters"), like {@code /tp}. */
	public static Predicate<CommandSourceStack> gamemasters() {
		//? if >=26.1 {
		return Commands.hasPermission(Commands.LEVEL_GAMEMASTERS);
		//?} else {
		/*return source -> source.hasPermission(2);
		*///?}
	}
}
