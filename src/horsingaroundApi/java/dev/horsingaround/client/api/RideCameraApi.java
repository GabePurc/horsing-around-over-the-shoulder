package dev.horsingaround.client.api;

/**
 * Compile-only mirror of Horsing Around's camera API. Keep the signatures in step with the real class in the
 * Horsing Around repo (src/client/java/dev/horsingaround/client/api/RideCameraApi.java). Never packaged.
 */
public final class RideCameraApi {
	private RideCameraApi() {
	}

	public static void setThirdPersonClaimed(final boolean claimed) {
		throw new UnsupportedOperationException("compile-only stub");
	}

	public static boolean isRiding() {
		throw new UnsupportedOperationException("compile-only stub");
	}

	public static double eyeY(final float partialTicks) {
		throw new UnsupportedOperationException("compile-only stub");
	}

	public static float speedFraction() {
		throw new UnsupportedOperationException("compile-only stub");
	}

	public static float distance(final float partialTicks) {
		throw new UnsupportedOperationException("compile-only stub");
	}

	public static float bounce() {
		throw new UnsupportedOperationException("compile-only stub");
	}
}
