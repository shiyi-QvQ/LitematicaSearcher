package com.litematicasearcher.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.litematicasearcher.LitematicaSearcher;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Persistent mod configuration.
 * - Stores user preferences (image source, footer info, download compat).
 * - Auto-loaded on init and saved on first change.
 */
public final class RedenConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final int CURRENT_VERSION = 1;

	public enum ImageSource {
		DIRECT,
		PROXY,
		AUTO
	}

	private int configVersion = CURRENT_VERSION;
	private ImageSource imageSource = ImageSource.AUTO;
	private boolean showFooterCredits = true;
	private boolean compatibleDownload = true;

	private static RedenConfig instance;

	public static RedenConfig get() {
		if (instance == null) {
			instance = load();
		}
		return instance;
	}

	public static void save() {
		if (instance == null) {
			return;
		}
		Path target = configPath();
		try {
			Files.createDirectories(target.getParent());
			Files.writeString(target, GSON.toJson(instance), StandardCharsets.UTF_8);
		} catch (IOException e) {
			LitematicaSearcher.LOGGER.warn("Failed to save Reden config: {}", e.getMessage());
		}
	}

	private static RedenConfig load() {
		Path target = configPath();
		if (Files.exists(target)) {
			try {
				String json = Files.readString(target, StandardCharsets.UTF_8);
				RedenConfig loaded = GSON.fromJson(json, RedenConfig.class);
				if (loaded != null) {
					loaded.configVersion = CURRENT_VERSION;
					return loaded;
				}
			} catch (Exception e) {
				LitematicaSearcher.LOGGER.warn("Failed to load Reden config, using defaults: {}", e.getMessage());
			}
		}
		return new RedenConfig();
	}

	private static Path configPath() {
		return Minecraft.getInstance()
				.gameDirectory
				.toPath()
				.resolve("litematicasearcher")
				.resolve("config.json");
	}

	public ImageSource imageSource() {
		return imageSource == null ? ImageSource.AUTO : imageSource;
	}

	public void setImageSource(ImageSource value) {
		this.imageSource = value == null ? ImageSource.AUTO : value;
		save();
	}

	public boolean showFooterCredits() {
		return showFooterCredits;
	}

	public void setShowFooterCredits(boolean value) {
		this.showFooterCredits = value;
		save();
	}

	public boolean compatibleDownload() {
		return compatibleDownload;
	}

	public void setCompatibleDownload(boolean value) {
		this.compatibleDownload = value;
		save();
	}
}
