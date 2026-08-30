package dev.continentsoftime.atlas.layout;

import java.util.SplittableRandom;

/**
 * Seeded two-dimensional gradient noise with a fractal sum. Self-contained (no Minecraft) so the layout harness
 * can run it headless, and deterministic for a seed across JVMs.
 *
 * <p>Every value is bounded to {@code [-1, 1]}: a single lattice sample of unit-gradient 2-D noise cannot exceed
 * {@code sqrt(2)/2} in magnitude, so scaling by {@code sqrt(2)} makes the bound exactly 1, and the fractal sum
 * normalises by its total amplitude. The layout's extent guarantees lean on that bound.
 */
final class Noise {
	private static final int SIZE = 256;
	private static final int MASK = SIZE - 1;
	private static final int GRADIENTS = 16;
	private static final double[] GX = new double[GRADIENTS];
	private static final double[] GZ = new double[GRADIENTS];
	private static final double SCALE = Math.sqrt(2.0);

	static {
		for (int i = 0; i < GRADIENTS; i++) {
			double angle = (Math.PI * 2.0 * i) / GRADIENTS + 0.1;
			GX[i] = Math.cos(angle);
			GZ[i] = Math.sin(angle);
		}
	}

	private final short[] perm = new short[SIZE * 2];

	Noise(long seed) {
		SplittableRandom random = new SplittableRandom(seed);
		for (int i = 0; i < SIZE; i++) {
			perm[i] = (short) i;
		}
		for (int i = SIZE - 1; i > 0; i--) {
			int j = random.nextInt(i + 1);
			short t = perm[i];
			perm[i] = perm[j];
			perm[j] = t;
		}
		System.arraycopy(perm, 0, perm, SIZE, SIZE);
	}

	/** One lattice sample, in {@code [-1, 1]}. */
	double sample(double x, double z) {
		int xi = (int) Math.floor(x);
		int zi = (int) Math.floor(z);
		double xf = x - xi;
		double zf = z - zi;
		double u = fade(xf);
		double v = fade(zf);

		int a = perm[xi & MASK];
		int b = perm[(xi + 1) & MASK];
		int g00 = perm[a + (zi & MASK)] & (GRADIENTS - 1);
		int g01 = perm[a + ((zi + 1) & MASK)] & (GRADIENTS - 1);
		int g10 = perm[b + (zi & MASK)] & (GRADIENTS - 1);
		int g11 = perm[b + ((zi + 1) & MASK)] & (GRADIENTS - 1);

		double n00 = GX[g00] * xf + GZ[g00] * zf;
		double n10 = GX[g10] * (xf - 1) + GZ[g10] * zf;
		double n01 = GX[g01] * xf + GZ[g01] * (zf - 1);
		double n11 = GX[g11] * (xf - 1) + GZ[g11] * (zf - 1);

		double n = lerp(lerp(n00, n10, u), lerp(n01, n11, u), v) * SCALE;
		return n < -1 ? -1 : (n > 1 ? 1 : n);
	}

	/**
	 * Fractal sum of {@code octaves} samples, each at twice the frequency and half the amplitude of the last,
	 * normalised so the result stays in {@code [-1, 1]}. Octaves are offset from each other so their lattices do
	 * not line up.
	 */
	double fractal(double x, double z, int octaves) {
		return fractal(x, z, octaves, 0.5);
	}

	/** As {@link #fractal(double, double, int)} with each octave's amplitude {@code persistence} times the last's. */
	double fractal(double x, double z, int octaves, double persistence) {
		double sum = 0;
		double amplitude = 1;
		double total = 0;
		double frequency = 1;
		for (int i = 0; i < octaves; i++) {
			double offset = i * 37.31;
			sum += amplitude * sample(x * frequency + offset, z * frequency - offset);
			total += amplitude;
			amplitude *= persistence;
			frequency *= 2;
		}
		return sum / total;
	}

	private static double fade(double t) {
		return t * t * t * (t * (t * 6 - 15) + 10);
	}

	private static double lerp(double a, double b, double t) {
		return a + (b - a) * t;
	}
}
