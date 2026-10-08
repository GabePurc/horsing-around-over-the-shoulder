package dev.horsingaround.shoulder.config;

import dev.horsingaround.shoulder.ShoulderCamClient;
import java.util.Locale;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ImageWidget;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * Settings for the over-the-shoulder camera, in the style of the vanilla options screens, with the mod's icon in the
 * title, values in blocks, a tooltip on every option, and changes applied live.
 */
public final class ShoulderSettingsScreen extends OptionsSubScreen {
	private static final String KEY = "options.horsingaround_shoulder.";
	private static final Identifier ICON = Identifier.fromNamespaceAndPath(ShoulderCamClient.MOD_ID, "icon.png");
	/** Section headings: saddle-leather tan, as in Horsing Around's settings. */
	private static final int SECTION_COLOR = 0xE3B778;
	private static final ShoulderConfig DEFAULTS = new ShoulderConfig();

	public ShoulderSettingsScreen(final Screen parent) {
		super(parent, Minecraft.getInstance().options, Component.translatable(KEY + "title"));
	}

	@Override
	protected void addTitle() {
		final LinearLayout title = LinearLayout.horizontal().spacing(6);
		title.defaultCellSetting().alignVerticallyMiddle();
		title.addChild(ImageWidget.texture(16, 16, ICON, 16, 16));
		title.addChild(new StringWidget(this.title, this.font));
		this.layout.addToHeader(title);
	}

	@Override
	protected void addOptions() {
		final ShoulderConfig c = ShoulderConfig.get();
		this.list.addHeader(Component.translatable(KEY + "subtitle").withStyle(ChatFormatting.GRAY));
		this.list.addSmall(
			OptionInstance.createBoolean(KEY + "enabled", tooltip("enabled"), c.enabled, value -> ShoulderCamClient.setEnabled(value)),
			OptionInstance.createBoolean(
				KEY + "shoulder", tooltip("shoulder"),
				(caption, left) -> Component.translatable(KEY + (left ? "left" : "right")),
				c.leftShoulder, value -> c.leftShoulder = value
			)
		);

		this.list.addHeader(section("on_foot"));
		this.list.addSmall(
			blocks("foot_distance", 1.0F, 6.0F, c.footDistance, v -> c.footDistance = v),
			blocks("foot_side", 0.0F, 1.5F, c.footSide, v -> c.footSide = v),
			blocks("foot_height", -0.5F, 1.0F, c.footHeight, v -> c.footHeight = v),
			ofDefault("height_follow", c.heightFollow, DEFAULTS.heightFollow, v -> c.heightFollow = v)
		);

		this.list.addHeader(section("aiming"));
		this.list.addSmall(
			blocks("aim_distance", 0.8F, 4.0F, c.aimDistance, v -> c.aimDistance = v),
			blocks("aim_side", 0.0F, 1.2F, c.aimSide, v -> c.aimSide = v)
		);

		this.list.addHeader(section("riding"));
		this.list.addSmall(
			percent("ride_distance", 50, 150, c.rideDistanceScale, v -> c.rideDistanceScale = v),
			blocks("ride_side", 0.0F, 1.5F, c.rideSide, v -> c.rideSide = v),
			blocks("ride_height", 0.0F, 2.0F, c.rideHeight, v -> c.rideHeight = v),
			percent("ride_bounce", 0, 50, c.rideBounce, v -> c.rideBounce = v)
		);

		this.list.addHeader(section("motion"));
		this.list.addBig(ofDefault("transition_speed", c.transitionSpeed, DEFAULTS.transitionSpeed, v -> c.transitionSpeed = v));
	}

	@Override
	protected void addFooter() {
		final LinearLayout footer = this.layout.addToFooter(LinearLayout.horizontal().spacing(8));
		footer.addChild(Button.builder(Component.translatable(KEY + "reset"), button -> {
			ShoulderConfig.reset();
			ShoulderCamClient.setEnabled(ShoulderConfig.get().enabled);
			this.minecraft.gui.setScreen(new ShoulderSettingsScreen(this.lastScreen));
		}).width(150).build());
		footer.addChild(Button.builder(CommonComponents.GUI_DONE, button -> this.onClose()).width(150).build());
	}

	@Override
	public void removed() {
		super.removed();
		ShoulderConfig.save();
	}

	private static Component section(final String id) {
		return Component.translatable(KEY + id).withColor(SECTION_COLOR);
	}

	private static <T> OptionInstance.TooltipSupplier<T> tooltip(final String id) {
		return OptionInstance.cachedConstantTooltip(Component.translatable(KEY + id + ".tooltip"));
	}

	/** Slider in hundredths of a block, shown as "2.8 blocks". */
	private static OptionInstance<Integer> blocks(final String id, final float min, final float max, final float value, final Consumer<Float> apply) {
		return new OptionInstance<>(
			KEY + id, tooltip(id),
			(caption, hundredths) -> Options.genericValueLabel(caption, Component.translatable(KEY + "blocks", trim(hundredths / 100.0F))),
			new OptionInstance.IntRange(Math.round(min * 100.0F), Math.round(max * 100.0F)),
			Math.round(value * 100.0F),
			hundredths -> apply.accept(hundredths / 100.0F)
		);
	}

	/** Slider in whole percent of a share; 0 reads "Off". */
	private static OptionInstance<Integer> percent(final String id, final int min, final int max, final float value, final Consumer<Float> apply) {
		return new OptionInstance<>(
			KEY + id, tooltip(id),
			(caption, percent) -> percent == 0
				? Options.genericValueLabel(caption, CommonComponents.OPTION_OFF)
				: Options.genericValueLabel(caption, Component.translatable(KEY + "percent", percent)),
			new OptionInstance.IntRange(min, max),
			Math.round(value * 100.0F),
			percent -> apply.accept(percent / 100.0F)
		);
	}

	/** A rate shown as a percentage of its default, so 100% is how it was designed. */
	private static OptionInstance<Integer> ofDefault(final String id, final float value, final float designed, final Consumer<Float> apply) {
		return new OptionInstance<>(
			KEY + id, tooltip(id),
			(caption, percent) -> Options.genericValueLabel(caption, Component.translatable(KEY + "percent", percent)),
			new OptionInstance.IntRange(25, 250),
			Math.round(value / designed * 100.0F),
			percent -> apply.accept(designed * percent / 100.0F)
		);
	}

	/** 2.80 -> "2.8", 0.75 -> "0.75", 3.00 -> "3". */
	private static String trim(final float value) {
		String text = String.format(Locale.ROOT, "%.2f", value);
		while (text.endsWith("0")) {
			text = text.substring(0, text.length() - 1);
		}
		return text.endsWith(".") ? text.substring(0, text.length() - 1) : text;
	}
}
