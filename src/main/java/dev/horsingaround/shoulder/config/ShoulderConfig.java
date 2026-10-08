package dev.horsingaround.shoulder.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Player-tweakable camera settings, saved to {@code config/horsingaround_shoulder.json}. Distances are in blocks.
 * Edited from the settings screen (Mod Menu) and applied live.
 */
public final class ShoulderConfig {
	private static final Logger LOGGER = LoggerFactory.getLogger("horsingaround_shoulder");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("horsingaround_shoulder.json");
	private static ShoulderConfig instance = new ShoulderConfig();
	/** Riding height before version 2 (it sat too high); a saved file still on it moves to the new default. */
	private static final float OLD_RIDE_HEIGHT = 0.7F;
	private static final int VERSION = 2;

	/** Settings file version. */
	public int version = VERSION;
	public boolean enabled = true;
	public boolean leftShoulder = false;

	// On foot.
	public float footDistance = 2.8F;
	public float footSide = 0.75F;
	public float footHeight = 0.15F;
	// Aiming a bow, crossbow, trident or spyglass.
	public float aimDistance = 1.7F;
	public float aimSide = 0.6F;
	// Riding (with Horsing Around): distance is a share of the horse camera's speed-based distance.
	public float rideDistanceScale = 1.0F;
	public float rideSide = 0.6F;
	public float rideHeight = 0.35F;
	/** Share of the saddle bounce the riding camera takes. */
	public float rideBounce = 0.12F;
	/** How quickly framing changes (on foot / aiming / riding, shoulder swap), per second. */
	public float transitionSpeed = 6.0F;
	/** How tightly the camera follows jumps and steps on foot, per second. */
	public float heightFollow = 25.0F;

	public static ShoulderConfig get() {
		return instance;
	}

	public static void load() {
		if (!Files.exists(FILE)) {
			return;
		}
		int fileVersion = VERSION;
		try (Reader reader = Files.newBufferedReader(FILE)) {
			final JsonElement json = JsonParser.parseReader(reader);
			final ShoulderConfig loaded = GSON.fromJson(json, ShoulderConfig.class);
			if (loaded == null) {
				return;
			}
			instance = loaded;
			// Files from before version 2 have no version.
			final JsonElement version = json.getAsJsonObject().get("version");
			fileVersion = version == null ? 1 : version.getAsInt();
		} catch (final IOException | RuntimeException e) {
			LOGGER.warn("Could not read {}, using defaults", FILE, e);
			return;
		}
		migrate(instance, fileVersion);
	}

	/** Older files keep the player's own choices; only values still at an old default move to the new one. */
	private static void migrate(final ShoulderConfig c, final int fileVersion) {
		if (fileVersion >= VERSION) {
			return;
		}
		if (Math.abs(c.rideHeight - OLD_RIDE_HEIGHT) < 1.0E-4F) {
			c.rideHeight = new ShoulderConfig().rideHeight;
		}
		c.version = VERSION;
		save();
	}

	public static void save() {
		try {
			Files.createDirectories(FILE.getParent());
			try (Writer writer = Files.newBufferedWriter(FILE)) {
				GSON.toJson(instance, writer);
			}
		} catch (final IOException e) {
			LOGGER.warn("Could not save {}", FILE, e);
		}
	}

	public static void reset() {
		instance = new ShoulderConfig();
	}
}
