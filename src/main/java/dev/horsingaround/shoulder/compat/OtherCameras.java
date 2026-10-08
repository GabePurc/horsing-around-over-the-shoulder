package dev.horsingaround.shoulder.compat;

import com.github.exopandora.shouldersurfing.api.client.IShoulderSurfing;
import net.fabricmc.loader.api.FabricLoader;

/** Other camera mods: while one of them is placing the third-person camera, this one steps aside. */
public final class OtherCameras {
	private static final boolean SHOULDER_SURFING = FabricLoader.getInstance().isModLoaded("shouldersurfing");

	private OtherCameras() {
	}

	public static boolean active() {
		return SHOULDER_SURFING && ShoulderSurfing.active();
	}

	/** Only loaded when Shoulder Surfing Reloaded is. */
	private static final class ShoulderSurfing {
		static boolean active() {
			return IShoulderSurfing.getInstance().isShoulderSurfing();
		}
	}
}
