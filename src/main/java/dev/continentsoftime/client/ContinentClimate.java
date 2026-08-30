package dev.continentsoftime.client;

import dev.continentsoftime.atlas.layout.Layout;
import mod.bluestaggo.modernerbeta.api.level.biome.climate.ClimateSampler;
import mod.bluestaggo.modernerbeta.api.level.biome.climate.ClimateSamplerSky;
import mod.bluestaggo.modernerbeta.api.level.biome.climate.Clime;
import mod.bluestaggo.modernerbeta.settings.component.ClimateDistribution;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * One climate sampler for the whole atlas, on the client: every sample at {@code (x, z)} goes to the climate
 * sampler of the era whose continent holds the column. Moderner Beta's client colouring (grass and foliage
 * tints, the sky colour, its fog weighting) samples a single {@link ClimateSampler} by block position, so
 * installing this one in its samplers makes those visuals per continent.
 *
 * <p>Columns with no era climate — the modern continent, the open sea, eras whose biome provider does not sample
 * a climate (Infdev, Alpha, the fractal biome maps from 1.2.5 on, finite levels) — keep vanilla's biome-driven
 * colours. Moderner Beta's flags for whether to tint at all ({@link #useBiomeColor()} and friends) are not
 * positional, so the composite answers "yes" whenever any era would, and the atlas's own client mixins make the
 * decision per column with {@link #tintsVegetation}, {@link #tintsSky} and {@link #oldFog}. The {@link Fallback}
 * covers any sample that still lands outside a climate era (it returns the vanilla biome's own climate).
 *
 * <p>Minecraft-free apart from Moderner Beta's climate interfaces, so a harness can check the routing headlessly.
 */
public final class ContinentClimate implements ClimateSampler, ClimateSamplerSky {

	/** The climate of a column outside every climate era: the vanilla biome's temperature and downfall. */
	@FunctionalInterface
	public interface Fallback {
		Clime clime(int x, int z);
	}

	private final Layout layout;
	private final @Nullable ClimateSampler[] samplers;
	private final boolean[] modernerBeta;
	private final Fallback fallback;
	private final boolean anyVegetation;
	private final ClimateDistribution distribution;
	private final int climateEras;

	/**
	 * @param layout       the atlas layout, rebuilt on the client from the seed and footprints the server sent
	 * @param samplers     one entry per roster era, in roster order: the era's climate sampler, or {@code null}
	 *                     when the era has none
	 * @param modernerBeta one flag per roster era: whether the era is one of Moderner Beta's (old fog applies)
	 * @param fallback     the climate of a column outside every climate era
	 */
	public ContinentClimate(Layout layout, List<@Nullable ClimateSampler> samplers, List<Boolean> modernerBeta, Fallback fallback) {
		if (samplers.size() != modernerBeta.size() || samplers.isEmpty()) {
			throw new IllegalArgumentException("One sampler and one flag per era: " + samplers.size() + " vs " + modernerBeta.size());
		}
		this.layout = layout;
		this.samplers = samplers.toArray(new ClimateSampler[0]);
		this.modernerBeta = new boolean[modernerBeta.size()];
		for (int i = 0; i < modernerBeta.size(); i++) {
			this.modernerBeta[i] = modernerBeta.get(i);
		}
		this.fallback = fallback;

		boolean vegetation = false;
		boolean fuzzyGrass = false;
		boolean smoothBorders = true;
		int count = 0;
		for (ClimateSampler sampler : this.samplers) {
			if (sampler == null) {
				continue;
			}
			count++;
			vegetation |= sampler.useBiomeColor();
			fuzzyGrass |= sampler.getDistribution().fuzzyGrass();
			smoothBorders &= sampler.getDistribution().smoothBorders();
		}
		this.climateEras = count;
		this.anyVegetation = vegetation;
		// Not positional in Moderner Beta's API: fuzzy grass if any era wants it, smooth borders only if every
		// climate era does (the smoothing samples neighbours and would bleed across a coast otherwise).
		this.distribution = count == 0 ? ClimateDistribution.DEFAULT : new ClimateDistribution(fuzzyGrass, smoothBorders);
	}

	/** How many roster eras have a climate sampler. */
	public int climateEras() {
		return climateEras;
	}

	private @Nullable ClimateSampler at(int x, int z) {
		int era = layout.eraAt(x, z);
		return era == Layout.OCEAN ? null : samplers[era];
	}

	// ---- the per-column decisions the atlas's client mixins make ----

	/** Whether grass and foliage at the column take Moderner Beta's climate tint rather than vanilla's biome colour. */
	public boolean tintsVegetation(int x, int z) {
		ClimateSampler sampler = at(x, z);
		return sampler != null && sampler.useBiomeColor();
	}

	/** Whether the sky over the column takes Moderner Beta's climate colour. */
	public boolean tintsSky(int x, int z) {
		return at(x, z) instanceof ClimateSamplerSky sky && sky.useSkyColor();
	}

	/** Whether the column is on one of Moderner Beta's continents, where its old fog weighting applies. */
	public boolean oldFog(int x, int z) {
		int era = layout.eraAt(x, z);
		return era != Layout.OCEAN && modernerBeta[era];
	}

	// ---- ClimateSampler / ClimateSamplerSky: routed by column ----

	@Override
	public Clime sample(int x, int z) {
		ClimateSampler sampler = at(x, z);
		return sampler != null ? sampler.sample(x, z) : fallback.clime(x, z);
	}

	@Override
	public double sampleSky(int x, int z) {
		// Only reached for columns where tintsSky() holds (the sky layer checks first); temperate otherwise.
		return at(x, z) instanceof ClimateSamplerSky sky ? sky.sampleSky(x, z) : 0.5;
	}

	@Override
	public boolean useBiomeColor() {
		return anyVegetation;
	}

	/** Always false: Moderner Beta's own sky layer stays inert, and the atlas's positional sky layer decides per column. */
	@Override
	public boolean useSkyColor() {
		return false;
	}

	/** Always false: water keeps vanilla's per-biome colour everywhere (see ARCHITECTURE, "Per-continent visuals"). */
	@Override
	public boolean useWaterColor() {
		return false;
	}

	@Override
	public ClimateDistribution getDistribution() {
		return distribution;
	}

	@Override
	public String getDebugText(int x, int z) {
		ClimateSampler sampler = at(x, z);
		return sampler != null ? sampler.getDebugText(x, z) : "";
	}
}
