package dev.continentsoftime.atlas.layout;

/**
 * One era's place in the world: an axis-aligned box that its continent never leaves. The land inside is either
 * the whole box (finite eras) or a noise-shaped continent well inside it.
 *
 * @param era     index into the atlas's era roster
 * @param cellX   column of the layout cell this seat occupies (hex-offset grid, see {@link ContinentLayout})
 * @param cellZ   row of the layout cell
 * @param centerX box centre, blocks
 * @param centerZ box centre, blocks
 * @param halfX   half the box width, blocks
 * @param halfZ   half the box length, blocks
 * @param shaped  whether the coastline is carved by noise (infinite eras) or is the box itself (finite eras)
 */
public record Seat(int era, int cellX, int cellZ, int centerX, int centerZ, int halfX, int halfZ, boolean shaped) {
	public int minX() { return centerX - halfX; }
	public int maxX() { return centerX + halfX; }
	public int minZ() { return centerZ - halfZ; }
	public int maxZ() { return centerZ + halfZ; }

	public boolean containsBox(int x, int z) {
		return x >= minX() && x <= maxX() && z >= minZ() && z <= maxZ();
	}

	/** Euclidean distance from the point to the box (zero inside). */
	public double distanceToBox(int x, int z) {
		double dx = Math.max(0, Math.max(minX() - x, x - maxX()));
		double dz = Math.max(0, Math.max(minZ() - z, z - maxZ()));
		return Math.sqrt(dx * dx + dz * dz);
	}
}
