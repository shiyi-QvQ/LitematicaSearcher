package com.litematicasearcher.client.api;

import net.minecraft.client.Minecraft;

import java.util.Locale;

public enum RedenApiLanguage {
	CN("cn"),
	EN("en");

	private final String apiCode;

	RedenApiLanguage(String apiCode) {
		this.apiCode = apiCode;
	}

	public String apiCode() {
		return apiCode;
	}

	public static RedenApiLanguage fromMinecraftLanguage(String languageCode) {
		if (languageCode == null) {
			return EN;
		}

		if ("zh_cn".equals(languageCode.toLowerCase(Locale.ROOT))) {
			return CN;
		}

		return EN;
	}

	public static RedenApiLanguage currentMinecraftLanguage() {
		Minecraft client = Minecraft.getInstance();
		return fromMinecraftLanguage(client.getLanguageManager().getSelected());
	}
}
