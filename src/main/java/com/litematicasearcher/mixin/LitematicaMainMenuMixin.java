package com.litematicasearcher.mixin;

import com.litematicasearcher.client.LitematicaSearcherClient;
import fi.dy.masa.litematica.gui.GuiMainMenu;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.util.StringUtils;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = GuiMainMenu.class, remap = false)
public abstract class LitematicaMainMenuMixin extends GuiBase {
	@Inject(method = "initGui", at = @At("TAIL"), remap = false)
	private void addRedenSearchButton(CallbackInfo ci) {
		int buttonWidth = 160;
		int x = this.width - buttonWidth - 12;
		int y = this.height - 26;

		ButtonGeneric button = new ButtonGeneric(
				x,
				y,
				buttonWidth,
				20,
				StringUtils.translate("litematicasearcher.gui.button.reden_search")
		);

		this.addButton(button, (btn, mouseButton) ->
				LitematicaSearcherClient.openSearchScreen((Screen) (Object) this)
		);
	}
}
