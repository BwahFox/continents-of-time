package dev.continentsoftime.atlas.structure;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.levelgen.structure.StructureSet;

import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Which structures existed in which version: a structure set is allowed on an era's continent when the era's
 * {@link EraVersion} is not before the version that introduced the set. Vanilla's sets are listed here (the
 * version in which the structure first generated); any other set — a mod's, or one of Moderner Beta's own —
 * is allowed everywhere, since nothing is known about it.
 */
public final class EraStructures {
	private static final Map<Identifier, EraVersion> INTRODUCED = Map.ofEntries(
		vanilla("villages", EraVersion.beta(8, 0)),
		vanilla("strongholds", EraVersion.beta(8, 0)),
		vanilla("mineshafts", EraVersion.beta(8, 0)),
		vanilla("nether_complexes", EraVersion.release(0, 0)),
		vanilla("desert_pyramids", EraVersion.release(3, 0)),
		vanilla("jungle_temples", EraVersion.release(3, 0)),
		vanilla("swamp_huts", EraVersion.release(4, 0)),
		vanilla("ocean_monuments", EraVersion.release(8, 0)),
		vanilla("igloos", EraVersion.release(9, 0)),
		vanilla("end_cities", EraVersion.release(9, 0)),
		vanilla("woodland_mansions", EraVersion.release(11, 0)),
		vanilla("shipwrecks", EraVersion.release(13, 0)),
		vanilla("ocean_ruins", EraVersion.release(13, 0)),
		vanilla("buried_treasures", EraVersion.release(13, 0)),
		vanilla("pillager_outposts", EraVersion.release(14, 0)),
		vanilla("ruined_portals", EraVersion.release(16, 0)),
		vanilla("nether_fossils", EraVersion.release(16, 0)),
		vanilla("ancient_cities", EraVersion.release(19, 0)),
		vanilla("trail_ruins", EraVersion.release(20, 0)),
		vanilla("trial_chambers", EraVersion.release(21, 0))
	);

	private static Map.Entry<Identifier, EraVersion> vanilla(String set, EraVersion introduced) {
		return Map.entry(Identifier.withDefaultNamespace(set), introduced);
	}

	private EraStructures() {}

	/** The version a vanilla structure set first generated in, if known. */
	public static Optional<EraVersion> introduced(Identifier structureSet) {
		return Optional.ofNullable(INTRODUCED.get(structureSet));
	}

	/** Whether the structure set may generate on a continent of the given version. */
	public static boolean allows(EraVersion era, Identifier structureSet) {
		EraVersion introduced = INTRODUCED.get(structureSet);
		return introduced == null || !era.isBefore(introduced);
	}

	/** The structure-set registry as an era sees it: everything it was too early for is missing. */
	public static HolderLookup<StructureSet> filtered(HolderLookup<StructureSet> all, EraVersion era) {
		return new HolderLookup<>() {
			@Override
			public Optional<Holder.Reference<StructureSet>> get(ResourceKey<StructureSet> key) {
				return allows(era, key.identifier()) ? all.get(key) : Optional.empty();
			}

			@Override
			public Optional<HolderSet.Named<StructureSet>> get(TagKey<StructureSet> tag) {
				return all.get(tag);
			}

			@Override
			public Stream<Holder.Reference<StructureSet>> listElements() {
				return all.listElements().filter(reference -> allows(era, reference.key().identifier()));
			}

			@Override
			public Stream<HolderSet.Named<StructureSet>> listTags() {
				return all.listTags();
			}
		};
	}
}
