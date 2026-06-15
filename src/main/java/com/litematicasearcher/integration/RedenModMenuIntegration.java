package com.litematicasearcher.integration;

import com.litematicasearcher.client.LitematicaSearcherClient;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

public final class RedenModMenuIntegration implements ModMenuApi {
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return LitematicaSearcherClient::createSearchScreen;
	}
}
