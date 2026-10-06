package com.litematicasearcher;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

public class LitematicaSearcher implements ModInitializer {

	public static final String MOD_ID = "litematicasearcher";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	/**
	 * 获取配置目录
	 */
	public static File getConfigDirectory() {
		File configDir = FabricLoader.getInstance().getConfigDir().resolve("litematicasearcher").toFile();
		if (!configDir.exists()) {
			configDir.mkdirs();
		}
		return configDir;
	}

	@Override
	public void onInitialize() {
		LOGGER.info("Initializing {}", MOD_ID);
	}
}