package com.litematicasearcher.client;

import com.litematicasearcher.LitematicaSearcher;
import com.litematicasearcher.client.gui.RedenSearchScreen;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

public final class LitematicaSearcherClient implements ClientModInitializer {
	private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(
			LitematicaSearcher.MOD_ID,
			"main"
	));

	private static KeyMapping openSearchKey;

	@Override
	public void onInitializeClient() {
		openSearchKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
				"key.litematicasearcher.open_search",
				InputConstants.Type.KEYSYM,
				GLFW.GLFW_KEY_O,
				CATEGORY
		));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (openSearchKey.consumeClick()) {
				openSearchScreen(client.screen);
			}
		});
	}

	public static Screen createSearchScreen(Screen parent) {
		return new RedenSearchScreen(parent);
	}

	public static void openSearchScreen(Screen parent) {
		Minecraft.getInstance().setScreen(createSearchScreen(parent));
	}
}
