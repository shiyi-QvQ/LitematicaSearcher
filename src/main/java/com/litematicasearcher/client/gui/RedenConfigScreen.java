package com.litematicasearcher.client.gui;

import com.litematicasearcher.client.config.RedenConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.Objects;

/**
 * Settings screen. Three option cards stacked vertically, each with:
 *  - descriptive title on the left
 *  - control button on the right
 *  - inline hint text below the title (single line, no overlap with button)
 */
public final class RedenConfigScreen extends Screen {

	private static final int PADDING = 16;
	private static final int CARD_GAP = 8;
	private static final int CARD_PADDING = 10;
	private static final int ROW_HEIGHT = 20;
	private static final int CARD_HEIGHT = 36;

	private static final int LABEL_COLOR = 0xFFFFFFFF;
	private static final int HINT_COLOR = 0xFF808080;
	private static final int VALUE_COLOR = 0xFF64B5F6;

	private static final int PANEL_BG = 0xE0101010;
	private static final int CARD_BORDER = 0xFF404040;
	private static final int CARD_BORDER_HOVER = 0xFF64B5F6;

	private final Screen parent;
	private final RedenConfig config = RedenConfig.get();

	private Button imageSourceButton;
	private Button footerToggleButton;
	private Button downloadCompatButton;

	private int imageSourceCardY;
	private int footerCardY;
	private int downloadCardY;
	private int buttonsRowY;

	public RedenConfigScreen(Screen parent) {
		super(Component.translatable("litematicasearcher.screen.config.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		int cardW = width - PADDING * 2;
		int btnX = PADDING + cardW - 110;
		int btnW = 100;
		int y = PADDING + 28;

		// --- Card 1: 图片加载方式 ---
		imageSourceCardY = y;
		y += CARD_HEIGHT + CARD_GAP;

		// --- Card 2: 页脚致谢 ---
		footerCardY = y;
		y += CARD_HEIGHT + CARD_GAP;

		// --- Card 3: 兼容旧版下载 ---
		downloadCardY = y;
		y += CARD_HEIGHT + CARD_GAP + 6;

		// Bottom row: reset / back
		buttonsRowY = y;

		imageSourceButton = addRenderableWidget(Button.builder(
				Component.literal(imageSourceLabel(config.imageSource())),
				b -> cycleImageSource()
		).bounds(btnX, imageSourceCardY + (CARD_HEIGHT - 18) / 2, btnW, 18).build());

		footerToggleButton = addRenderableWidget(Button.builder(
				Component.literal(config.showFooterCredits() ? "开" : "关"),
				b -> {
					config.setShowFooterCredits(!config.showFooterCredits());
					footerToggleButton.setMessage(Component.literal(config.showFooterCredits() ? "开" : "关"));
				}
		).bounds(btnX, footerCardY + (CARD_HEIGHT - 18) / 2, btnW, 18).build());

		downloadCompatButton = addRenderableWidget(Button.builder(
				Component.literal(config.compatibleDownload() ? "开" : "关"),
				b -> {
					config.setCompatibleDownload(!config.compatibleDownload());
					downloadCompatButton.setMessage(Component.literal(config.compatibleDownload() ? "开" : "关"));
				}
		).bounds(btnX, downloadCardY + (CARD_HEIGHT - 18) / 2, btnW, 18).build());

		addRenderableWidget(Button.builder(
				Component.translatable("litematicasearcher.screen.config.reset"),
				b -> resetDefaults()
		).bounds(PADDING, buttonsRowY, 80, 20).build());

		addRenderableWidget(Button.builder(
				Component.translatable("gui.back"),
				b -> onClose()
		).bounds(width - PADDING - 80, buttonsRowY, 80, 20).build());
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
		super.render(graphics, mouseX, mouseY, delta);

		// Title
		graphics.drawCenteredString(font, title, width / 2, PADDING + 4, LABEL_COLOR);

		int cardW = width - PADDING * 2;
		int cardH = CARD_HEIGHT;

		// --- Card 1: image source
		drawCard(graphics, PADDING, imageSourceCardY, cardW, cardH, mouseX, mouseY,
				"litematicasearcher.screen.config.image_source",
				"redenmc 的图片有时是 JPEG 格式");

		// --- Card 2: footer
		drawCard(graphics, PADDING, footerCardY, cardW, cardH, mouseX, mouseY,
				"litematicasearcher.screen.config.footer",
				"在搜索页底部显示数据来源");

		// --- Card 3: download compat
		drawCard(graphics, PADDING, downloadCardY, cardW, cardH, mouseX, mouseY,
				"litematicasearcher.screen.config.download_compat",
				"启用后回退到旧版下载接口");
	}

	/**
	 * Draw one option card:
	 *  - title on the left top
	 *  - hint text on the left bottom (single short line, never reaches the button)
	 *  - button on the right
	 */
	private void drawCard(GuiGraphics g, int x, int y, int w, int h,
	                      int mouseX, int mouseY,
	                      String titleKey, String hint) {
		boolean hovered = mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
		int border = hovered ? CARD_BORDER_HOVER : CARD_BORDER;

		// Fill
		g.fill(x, y, x + w, y + h, PANEL_BG);
		// Border (top, bottom, left, right)
		g.fill(x, y, x + w, y + 1, border);
		g.fill(x, y + h - 1, x + w, y + h, border);
		g.fill(x, y, x + 1, y + h, border);
		g.fill(x + w - 1, y, x + w, y + h, border);

		// Title (top-left)
		String title = Component.translatable(titleKey).getString();
		g.drawString(font, title, x + CARD_PADDING, y + 5, LABEL_COLOR, false);

		// Hint (just below the title, kept to the left ~ 60% of the card so it never collides with the button)
		int hintMaxWidth = (int) (w * 0.55F);
		String hintTrimmed = font.plainSubstrByWidth(hint, hintMaxWidth);
		g.drawString(font, hintTrimmed, x + CARD_PADDING, y + 18, HINT_COLOR, false);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent mouseButtonEvent, boolean doubleClick) {
		return super.mouseClicked(mouseButtonEvent, doubleClick);
	}

	@Override
	public void onClose() {
		if (minecraft != null) {
			minecraft.setScreen(parent);
		}
	}

	private void cycleImageSource() {
		RedenConfig.ImageSource[] values = RedenConfig.ImageSource.values();
		RedenConfig.ImageSource next = values[(config.imageSource().ordinal() + 1) % values.length];
		config.setImageSource(next);
		if (imageSourceButton != null) {
			imageSourceButton.setMessage(Component.literal(imageSourceLabel(next)));
		}
	}

	private void resetDefaults() {
		config.setImageSource(RedenConfig.ImageSource.AUTO);
		config.setShowFooterCredits(true);
		config.setCompatibleDownload(true);
		if (imageSourceButton != null) {
			imageSourceButton.setMessage(Component.literal(imageSourceLabel(config.imageSource())));
		}
		if (footerToggleButton != null) {
			footerToggleButton.setMessage(Component.literal("开"));
		}
		if (downloadCompatButton != null) {
			downloadCompatButton.setMessage(Component.literal("开"));
		}
	}

	private static String imageSourceLabel(RedenConfig.ImageSource source) {
		return switch (source) {
			case DIRECT -> "直连";
			case PROXY -> "代理";
			case AUTO -> "自动";
		};
	}
}
