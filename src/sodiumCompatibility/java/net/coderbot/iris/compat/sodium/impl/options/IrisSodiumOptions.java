package net.coderbot.iris.compat.sodium.impl.options;

import me.jellysquid.mods.sodium.client.gui.options.OptionFlag;
import me.jellysquid.mods.sodium.client.gui.options.OptionImpact;
import me.jellysquid.mods.sodium.client.gui.options.OptionImpl;
import me.jellysquid.mods.sodium.client.gui.options.control.ControlValueFormatter;
import me.jellysquid.mods.sodium.client.gui.options.control.CyclingControl;
import me.jellysquid.mods.sodium.client.gui.options.control.SliderControl;
import me.jellysquid.mods.sodium.client.gui.options.storage.MinecraftOptionsStorage;
import net.coderbot.iris.Iris;
import net.coderbot.iris.colorspace.ColorBlindness;
import net.coderbot.iris.colorspace.ColorSpace;
import net.coderbot.iris.gl.IrisRenderSystem;
import net.coderbot.iris.gui.option.IrisVideoSettings;
import net.minecraft.client.Options;
import net.minecraft.network.chat.Component;



import java.io.IOException;

public class IrisSodiumOptions {
    public static OptionImpl<Options, Integer> createMaxShadowDistanceSlider(MinecraftOptionsStorage vanillaOpts) {
        OptionImpl<Options, Integer> maxShadowDistanceSlider = OptionImpl.createBuilder(int.class, vanillaOpts)
                .setName(Component.translatable("options.iris.shadowDistance"))
                .setTooltip(Component.translatable("options.iris.shadowDistance.sodium_tooltip"))
                .setControl(option -> new SliderControl(option, 0, 32, 1, ControlValueFormatter.quantityOrDisabled("Chunks", "Disabled")))
				.setBinding((options, value) -> {
						IrisVideoSettings.shadowDistance = value;
						try {
							Iris.getIrisConfig().save();
						} catch (IOException e) {
							e.printStackTrace();
						}
					},
					options -> IrisVideoSettings.getOverriddenShadowDistance(IrisVideoSettings.shadowDistance))
                .setImpact(OptionImpact.HIGH)
                .setEnabled(true)
                .build();

        ((OptionImplExtended) maxShadowDistanceSlider).iris$dynamicallyEnable(IrisVideoSettings::isShadowDistanceSliderEnabled);

        return maxShadowDistanceSlider;
    }

    public static OptionImpl<Options, ColorSpace> createColorSpaceButton(MinecraftOptionsStorage vanillaOpts) {
        OptionImpl<Options, ColorSpace> colorSpace = OptionImpl.createBuilder(ColorSpace.class, vanillaOpts)
                .setName(Component.translatable("options.iris.colorSpace"))
                .setTooltip(Component.translatable("options.iris.colorSpace.sodium_tooltip"))
				.setControl(option -> new CyclingControl<>(option, ColorSpace.class,
				new Component[] { Component.literal("SRGB"), Component.literal("DCI_P3"), Component.literal("Display P3"), Component.literal("REC2020") }))
				.setBinding((options, value) -> {
						IrisVideoSettings.colorSpace = value;
						IrisVideoSettings.colorSpaceChanged();
						try {
							Iris.getIrisConfig().save();
						} catch (IOException e) {
							e.printStackTrace();
						}
					},
					options -> IrisVideoSettings.colorSpace)
                .setImpact(OptionImpact.LOW)
                .setEnabled(true)
                .build();

        ((OptionImplExtended) colorSpace).iris$dynamicallyEnable(IrisRenderSystem::supportsCompute);

        return colorSpace;
    }

    public static OptionImpl<Options, ColorBlindness> createColorBlindnessButton(MinecraftOptionsStorage vanillaOpts) {
        OptionImpl<Options, ColorBlindness> colorSpace = OptionImpl.createBuilder(ColorBlindness.class, vanillaOpts)
                .setName(Component.translatable("options.iris.colorBlindness"))
                .setTooltip(Component.translatable("options.iris.colorBlindness.sodium_tooltip"))
				.setControl(option -> new CyclingControl<>(option, ColorBlindness.class,
				new Component[] { Component.literal("None"), Component.literal("Protanopia"), Component.literal("Deuteranopia"), Component.literal("Tritanopia") }))
				.setBinding((options, value) -> {
						IrisVideoSettings.colorBlindness = value;
						IrisVideoSettings.colorBlindnessChanged();
						try {
							Iris.getIrisConfig().save();
						} catch (IOException e) {
							e.printStackTrace();
						}
					},
					options -> IrisVideoSettings.colorBlindness)
                .setImpact(OptionImpact.LOW)
                .setEnabled(true)
                .build();

        ((OptionImplExtended) colorSpace).iris$dynamicallyEnable(IrisRenderSystem::supportsCompute);

        return colorSpace;
    }
    public static OptionImpl<Options, Integer> createColorBlindnessIntensity(MinecraftOptionsStorage vanillaOpts) {
        OptionImpl<Options, Integer> colorSpace = OptionImpl.createBuilder(int.class, vanillaOpts)
                .setName(Component.translatable("options.iris.colorBlindnessIntensity"))
                .setTooltip(Component.translatable("options.iris.colorBlindnessIntensity.sodium_tooltip"))
				.setControl(option -> new SliderControl(option, 0, 65535, 5, (v) -> String.valueOf((float) Math.round((float) v / 65535.0f * 100) / 100)))
				.setBinding((options, value) -> {
						IrisVideoSettings.colorBlindnessIntensity = (float) value / 65535.0f;
						IrisVideoSettings.colorBlindnessChanged();
						try {
							Iris.getIrisConfig().save();
						} catch (IOException e) {
							e.printStackTrace();
						}
					},
					options -> (int) (IrisVideoSettings.colorBlindnessIntensity * 65535))
                .setImpact(OptionImpact.LOW)
                .setEnabled(true)
                .build();

        ((OptionImplExtended) colorSpace).iris$dynamicallyEnable(IrisRenderSystem::supportsCompute);

        return colorSpace;
    }

    public static OptionImpl<Options, SupportedGraphicsMode> createLimitedVideoSettingsButton(MinecraftOptionsStorage vanillaOpts) {
        return OptionImpl.createBuilder(SupportedGraphicsMode.class, vanillaOpts)
                .setName(Component.translatable("options.graphics"))
				// TODO: State that Fabulous Graphics is incompatible with Shader Packs in the tooltip
                .setTooltip(Component.translatable("sodium.options.graphics_quality.tooltip"))
                .setControl(option -> new CyclingControl<>(option, SupportedGraphicsMode.class,
						new Component[] { Component.literal("Fast"), Component.literal("Fancy") }))
                .setBinding(
                        (opts, value) -> opts.graphicsMode().set(value.toVanilla()),
                        opts -> SupportedGraphicsMode.fromVanilla(opts.graphicsMode().get()))
                .setImpact(OptionImpact.HIGH)
                .setFlags(OptionFlag.REQUIRES_RENDERER_RELOAD)
                .build();
    }
}
