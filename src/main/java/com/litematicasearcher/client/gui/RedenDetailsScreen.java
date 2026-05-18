package com.litematicasearcher.client.gui;

import com.litematicasearcher.client.api.RedenApiClient;
import com.litematicasearcher.client.api.RedenApiLanguage;
import com.litematicasearcher.client.api.RedenMachine;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

public final class RedenDetailsScreen extends Screen {
	private static final int PADDING = 12;
	private static final int PANEL_COLOR = 0x80000000;
	private static final int TEXT_PRIMARY = 0xFFFFFFFF;
	private static final int TEXT_SECONDARY = 0xFFB8B8B8;
	private static final NumberFormat NUMBER_FORMAT = NumberFormat.getIntegerInstance(Locale.getDefault());

	private final Screen parent;
	private final RedenApiClient apiClient = new RedenApiClient();
	private final String machineKey;
	private RedenMachine machine;
	private Component statusMessage = Component.translatable("litematicasearcher.screen.details.status.loading");
	private boolean requestStarted;

	public RedenDetailsScreen(Screen parent, RedenMachine initialMachine) {
		super(Component.translatable("litematicasearcher.screen.details.title"));
		this.parent = parent;
		this.machine = initialMachine;
		this.machineKey = initialMachine.key();
	}

	@Override
	protected void init() {
		addRenderableWidget(Button.builder(
				Component.translatable("gui.back"),
				button -> onClose()
		).bounds(PADDING, PADDING, 60, 20).build());

		if (!requestStarted) {
			requestStarted = true;
			fetchDetails();
		}
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
		renderPanel(graphics);
		super.render(graphics, mouseX, mouseY, delta);
	}

	@Override
	public void onClose() {
		if (minecraft != null) {
			minecraft.setScreen(parent);
		}
	}

	private void fetchDetails() {
		if (minecraft == null) {
			return;
		}

		apiClient.fetchDetails(machineKey, RedenApiLanguage.currentMinecraftLanguage())
				.whenComplete((result, throwable) -> minecraft.execute(() -> {
					if (throwable != null) {
						statusMessage = Component.translatable("litematicasearcher.screen.details.status.error");
						return;
					}

					if (result.isPresent()) {
						machine = result.get();
						statusMessage = Component.empty();
					} else {
						statusMessage = Component.translatable("litematicasearcher.screen.details.status.not_found");
					}
				}));
	}

	private void renderPanel(GuiGraphics graphics) {
		int panelLeft = PADDING;
		int panelTop = 42;
		int panelRight = width - PADDING;
		int panelBottom = height - PADDING;
		RedenSearchScreen.drawRoundedRect(graphics, panelLeft, panelTop, panelRight, panelBottom, 6, PANEL_COLOR);

		if (!statusMessage.getString().isEmpty()) {
			graphics.drawCenteredString(font, statusMessage, width / 2, panelTop + 16, TEXT_SECONDARY);
		}

		if (machine == null) {
			return;
		}

		graphics.drawCenteredString(font, machine.name(), width / 2, 17, TEXT_PRIMARY);

		int imageSize = Math.min(180, Math.max(96, (panelRight - panelLeft) / 3));
		int imageX = panelLeft + 14;
		int imageY = panelTop + 14;
		RedenSearchScreen.drawRoundedRect(graphics, imageX, imageY, imageX + imageSize, imageY + imageSize, 5, 0x66000000);
		renderImage(graphics, imageX, imageY, imageSize);

		int infoX = imageX + imageSize + 18;
		int infoY = imageY;
		int infoWidth = Math.max(80, panelRight - infoX - 14);
		drawInfoLine(graphics, "litematicasearcher.screen.details.author", machine.author().username(), infoX, infoY, infoWidth);
		drawInfoLine(graphics, "litematicasearcher.screen.details.likes", NUMBER_FORMAT.format(machine.upVotes()), infoX, infoY + 16, infoWidth);
		drawInfoLine(graphics, "litematicasearcher.screen.details.downloads", NUMBER_FORMAT.format(machine.downloads()), infoX, infoY + 32, infoWidth);
		drawInfoLine(graphics, "litematicasearcher.screen.details.versions", String.join(", ", machine.versions()), infoX, infoY + 48, infoWidth);

		graphics.drawString(font, Component.translatable("litematicasearcher.screen.details.description"), imageX, imageY + imageSize + 14, TEXT_PRIMARY, true);
		Component description = Component.literal(machine.description().isBlank() ? "-" : machine.description());
		List<FormattedCharSequence> lines = font.split(description, panelRight - imageX - 14);
		int textY = imageY + imageSize + 30;

		for (FormattedCharSequence line : lines) {
			if (textY > panelBottom - 12) {
				break;
			}

			graphics.drawString(font, line, imageX, textY, TEXT_SECONDARY, true);
			textY += 10;
		}
	}

	private void renderImage(GuiGraphics graphics, int x, int y, int size) {
		String imageUrl = machine.thumbnailUrl().isBlank() ? machine.imageUrl() : machine.thumbnailUrl();
		Identifier texture = RedenImageCache.textureFor(imageUrl);

		if (texture == null) {
			graphics.drawCenteredString(font, "...", x + size / 2, y + size / 2 - 4, TEXT_SECONDARY);
			return;
		}

		graphics.blit(texture, x, y, x + size, y + size, 0.0F, 1.0F, 0.0F, 1.0F);
	}

	private void drawInfoLine(GuiGraphics graphics, String key, String value, int x, int y, int maxWidth) {
		String text = Component.translatable(key, value.isBlank() ? "-" : value).getString();
		graphics.drawString(font, font.plainSubstrByWidth(text, maxWidth), x, y, TEXT_SECONDARY, true);
	}
}
