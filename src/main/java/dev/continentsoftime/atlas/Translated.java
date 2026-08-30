package dev.continentsoftime.atlas;

/**
 * A Moderner Beta chunk or biome provider that the atlas has moved away from the world origin. Implemented by
 * mixins on the provider base classes; the atlas sets the offset once the layout has seated the era.
 *
 * <p>Most eras are infinite and need no such thing: any region of an infinite world is as good as any other.
 * Some are <em>anchored</em> on the origin — finite levels (Classic, Indev) keep their whole level in memory
 * around (0, 0), and Legacy Console has an origin-centred world border with an ocean falloff and an
 * origin-centred biome map — and those generate their real content only around the origin unless every
 * origin-relative test inside them is shifted by the seat.
 */
public interface Translated {
	/** Put the provider's origin at the given chunk-aligned block position. */
	void continentsoftime$translateTo(int centerX, int centerZ);

	int continentsoftime$offsetX();

	int continentsoftime$offsetZ();
}
