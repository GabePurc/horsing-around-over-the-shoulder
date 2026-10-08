package dev.horsingaround.shoulder.gametest;

import dev.horsingaround.shoulder.ShoulderAim;
import dev.horsingaround.shoulder.ShoulderCamClient;
import dev.horsingaround.shoulder.config.ShoulderConfig;
import dev.horsingaround.shoulder.config.ShoulderSettingsScreen;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.TestInput;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Camera;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3fc;

/**
 * Drives the over-the-shoulder camera on foot (and riding, when Horsing Around is installed) and checks framing,
 * steadiness, crosshair aiming and settings. Run with {@code ./gradlew runClientGameTest}; writes
 * shoulder-camera-report.txt and screenshots to the test run folder.
 */
public final class ShoulderCamTest implements FabricClientGameTest {
	private final List<String> report = new ArrayList<>();
	private int failures;

	@Override
	public void runTest(final ClientGameTestContext ctx) {
		try (TestSingleplayerContext world = ctx.worldBuilder()
			.adjustSettings(settings -> settings.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE))
			.create()) {
			final TestServerContext server = world.getServer();
			final TestInput input = ctx.getInput();
			server.runCommand("time set noon");
			world.getConnection().waitForChunksRender();
			ctx.runOnClient(mc -> {
				ShoulderCamClient.setEnabled(true);
				mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
				mc.gui.hud.getChat().clearMessages(false);
			});
			input.lookAt(180.0F, 5.0F);
			ctx.waitTicks(30);

			onFoot(ctx, input);
			crosshair(ctx, input, server);
			settings(ctx);
			riding(ctx, input, server);

			ctx.runOnClient(mc -> ShoulderCamClient.setEnabled(false));
			ctx.waitTicks(5);
			check("turned off: vanilla centred camera again (blocks)", Math.abs(cameraSideOffset(ctx)), 0.0, 0.1);
		} finally {
			writeReport();
		}
		if (this.failures > 0) {
			throw new AssertionError(this.failures + " camera checks failed; see shoulder-camera-report.txt");
		}
	}

	private void onFoot(final ClientGameTestContext ctx, final TestInput input) {
		section("On foot");
		check("camera sits off the right shoulder (blocks)", cameraSideOffset(ctx), 0.5, 1.0);
		screenshot(ctx, "01_on_foot");

		// Walk while sweeping the view: the camera must look exactly where the player looks and keep a steady orbit.
		// Frames are capped at 30 a second so they fall at varied points between ticks (uncapped, the frame before each
		// sample lands near the end of a tick): the orbit must hold mid-tick too.
		final int framerateLimit = ctx.computeOnClient(mc -> mc.options.framerateLimit().get());
		ctx.runOnClient(mc -> mc.options.framerateLimit().set(30));
		input.holdKey(o -> o.keyUp);
		float worstAngle = 0.0F;
		double nearest = Double.MAX_VALUE;
		double farthest = 0.0;
		double sideLow = Double.MAX_VALUE;
		double sideHigh = -Double.MAX_VALUE;
		for (int i = 0; i < 24; i++) {
			input.lookAt(180.0F + i * 7.5F, 10.0F);
			ctx.waitTick();
			final double[] frame = ctx.computeOnClient(mc -> {
				final Camera camera = mc.gameRenderer.mainCamera();
				final float partialTicks = framePartialTicks(mc);
				return new double[] {
					Math.abs(Mth.wrapDegrees(camera.yRot() - mc.player.getViewYRot(partialTicks))) + Math.abs(camera.xRot() - mc.player.getViewXRot(partialTicks)),
					camera.position().distanceTo(mc.player.getEyePosition(partialTicks))
				};
			});
			worstAngle = Math.max(worstAngle, (float) frame[0]);
			nearest = Math.min(nearest, frame[1]);
			farthest = Math.max(farthest, frame[1]);
			final double side = cameraSideOffset(ctx);
			sideLow = Math.min(sideLow, side);
			sideHigh = Math.max(sideHigh, side);
			if (i == 12) {
				screenshot(ctx, "02_walking_turning");
			}
		}
		input.releaseKey(o -> o.keyUp);
		ctx.runOnClient(mc -> mc.options.framerateLimit().set(framerateLimit));
		check("camera looks exactly where the player looks (deg off)", worstAngle, 0.0, 0.01);
		check("orbit radius steady while walking and turning (blocks of variation)", farthest - nearest, 0.0, 0.05);
		check("shoulder offset steady while walking and turning (blocks of variation)", sideHigh - sideLow, 0.0, 0.05);

		ctx.runOnClient(mc -> ShoulderCamClient.swapShoulder());
		ctx.waitTicks(30);
		check("swap shoulder moves the camera left (blocks)", cameraSideOffset(ctx), -1.0, -0.5);
		ctx.runOnClient(mc -> ShoulderCamClient.swapShoulder());
		ctx.waitTicks(30);
	}

	private void crosshair(final ClientGameTestContext ctx, final TestInput input, final TestServerContext server) {
		section("Centre crosshair aiming");
		// A wall 3 blocks ahead: the block under the centre crosshair must be the one the player targets, and the
		// server must hear the rotation toward it (that is what aims arrows).
		server.runCommand("execute at @p run fill ~-4 ~ ~-3 ~4 ~3 ~-3 minecraft:oak_planks");
		input.lookAt(180.0F, 0.0F);
		ctx.waitTicks(10);
		final boolean sameBlock = ctx.computeOnClient(mc -> {
			final Camera camera = mc.gameRenderer.mainCamera();
			final Vec3 from = camera.position();
			final Vector3fc f = camera.forwardVector();
			final Vec3 to = from.add(f.x() * 32.0, f.y() * 32.0, f.z() * 32.0);
			final BlockHitResult underCrosshair = mc.level.clip(new ClipContext(from, to, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, mc.player));
			return mc.hitResult instanceof BlockHitResult targeted
				&& targeted.getType() != HitResult.Type.MISS
				&& targeted.getBlockPos().equals(underCrosshair.getBlockPos());
		});
		check("targets the block under the centre crosshair", sameBlock);
		screenshot(ctx, "03_crosshair_wall");
		final float[] yaws = ctx.computeOnClient(mc -> new float[] {mc.player.getYRot(), ShoulderAim.yaw()});
		final float serverYaw = server.computeOnServer(s -> s.getPlayerList().getPlayers().get(0).getYRot());
		check("view stays straight while aim corrects for the shoulder (deg between)", Math.abs(Mth.wrapDegrees(yaws[1] - yaws[0])), 3.0, 25.0);
		check("server aims where the crosshair points (deg off)", Math.abs(Mth.wrapDegrees(serverYaw - yaws[1])), 0.0, 1.0);
		server.runCommand("execute at @p run fill ~-4 ~ ~-3 ~4 ~3 ~-3 minecraft:air");
		input.lookAt(180.0F, 5.0F);
		ctx.waitTicks(10);
	}

	private void settings(final ClientGameTestContext ctx) {
		section("Settings (Mod Menu)");
		check("Mod Menu lists the camera settings", FabricLoader.getInstance().getEntrypointContainers("modmenu", Object.class).stream()
			.anyMatch(entry -> entry.getProvider().getMetadata().getId().equals("horsingaround_shoulder")));
		ctx.runOnClient(mc -> ShoulderConfig.get().footSide = 1.2F);
		ctx.waitTicks(30);
		check("a changed setting applies live (shoulder offset, blocks)", cameraSideOffset(ctx), 1.1, 1.3);
		ctx.setScreen(() -> new ShoulderSettingsScreen(null));
		ctx.waitTicks(5);
		screenshot(ctx, "04_settings");
		ctx.setScreen(() -> null);

		// A settings file from before version 2 still on the old riding height (it sat too high) moves to the new
		// default; one where the player chose their own keeps it.
		check("old riding height moves to the new default (blocks)", loadOldFile(ctx, 0.7F), 0.34, 0.36);
		check("a riding height the player chose is kept (blocks)", loadOldFile(ctx, 0.9F), 0.89, 0.91);
		ctx.runOnClient(mc -> {
			ShoulderConfig.reset();
			ShoulderConfig.save();
			ShoulderCamClient.setEnabled(true);
		});
		ctx.waitTicks(30);
	}

	/** Writes a version-1 settings file (no version) with this riding height, loads it, and returns the height it ends up at. */
	private static double loadOldFile(final ClientGameTestContext ctx, final float rideHeight) {
		return ctx.computeOnClient(mc -> {
			final Path file = FabricLoader.getInstance().getConfigDir().resolve("horsingaround_shoulder.json");
			try {
				Files.writeString(file, String.format(Locale.ROOT, "{\"enabled\": true, \"rideHeight\": %.2f}", rideHeight));
			} catch (final IOException e) {
				throw new RuntimeException(e);
			}
			ShoulderConfig.load();
			return (double) ShoulderConfig.get().rideHeight;
		});
	}

	private void riding(final ClientGameTestContext ctx, final TestInput input, final TestServerContext server) {
		if (!FabricLoader.getInstance().isModLoaded("horsingaround")) {
			log("== Riding: skipped (Horsing Around not installed; build it in ../Horsing Around to include it)");
			return;
		}
		section("Riding (with Horsing Around)");
		server.runCommand("execute at @p run summon minecraft:horse ~ ~ ~ {Tame:1b,Rotation:[180f,0f],"
			+ "equipment:{saddle:{id:\"minecraft:saddle\",count:1}},Tags:[\"shoulder_test\"]}");
		for (int attempt = 0; attempt < 10 && !ctx.computeOnClient(mc -> mc.player.getVehicle() instanceof AbstractHorse); attempt++) {
			ctx.waitTicks(5);
			server.runCommand("ride @p mount @e[type=minecraft:horse,tag=shoulder_test,limit=1]");
		}
		ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
		input.lookAt(180.0F, 10.0F);
		ctx.waitTicks(30);
		check("riding: camera sits off the right shoulder (blocks)", cameraSideOffset(ctx), 0.3, 1.0);
		// The pivot rides 0.35 above the eyes; the camera backs off along the view (10 deg down, ~4 blocks).
		check("riding: camera sits low behind the rider (above the eyes, blocks)", ctx.computeOnClient(mc ->
			mc.gameRenderer.mainCamera().position().y - mc.player.getEyePosition(framePartialTicks(mc)).y), 0.8, 1.25);
		screenshot(ctx, "05_riding");
		input.holdKeyFor(o -> o.keyShift, 3);
		ctx.waitTicks(10);
		ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
	}

	/** Camera position relative to the player's eyes along the player's right, blocks (negative = left). */
	private static double cameraSideOffset(final ClientGameTestContext ctx) {
		return ctx.computeOnClient(mc -> {
			final float partialTicks = framePartialTicks(mc);
			final Vec3 camera = mc.gameRenderer.mainCamera().position();
			final Vec3 eye = mc.player.getEyePosition(partialTicks);
			final float yaw = mc.player.getViewYRot(partialTicks) * Mth.DEG_TO_RAD;
			return (camera.x - eye.x) * -Mth.cos(yaw) + (camera.z - eye.z) * -Mth.sin(yaw);
		});
	}

	/**
	 * Partial tick the last rendered frame placed the camera at. Test code runs between frames, after the player has
	 * moved for the latest tick, while the camera still shows the previous frame: that frame drew the player at a
	 * partial tick set by real-time frame pacing, so comparing the camera with the player at partial tick 1.0 adds up
	 * to one tick of movement (0.2 blocks walking) that varies from run to run. Compare with the player as that frame
	 * drew them instead.
	 */
	private static float framePartialTicks(final Minecraft mc) {
		return mc.gameRenderer.gameRenderState().levelRenderState.cameraRenderState.cameraEntityPartialTicks;
	}

	private void screenshot(final ClientGameTestContext ctx, final String name) {
		log("  screenshot %s -> %s", name, ctx.takeScreenshot("shoulder_" + name));
	}

	private void section(final String name) {
		log("== %s", name);
	}

	private void check(final String name, final boolean ok) {
		if (!ok) {
			this.failures++;
		}
		log("  %s  %s", ok ? "PASS" : "FAIL", name);
	}

	private void check(final String name, final double value, final double min, final double max) {
		final boolean ok = value >= min && value <= max;
		if (!ok) {
			this.failures++;
		}
		log("  %s  %s = %.3f  (target %.3f..%.3f)", ok ? "PASS" : "FAIL", name, value, min, max);
	}

	private void log(final String format, final Object... args) {
		final String line = String.format(Locale.ROOT, format, args);
		this.report.add(line);
		System.out.println("[shoulder-test] " + line);
	}

	private void writeReport() {
		final Path file = FabricLoader.getInstance().getGameDir().resolve("shoulder-camera-report.txt");
		try {
			Files.write(file, this.report);
		} catch (final IOException e) {
			throw new RuntimeException("Could not write " + file, e);
		}
	}
}
