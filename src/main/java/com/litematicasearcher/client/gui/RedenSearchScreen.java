package com.litematicasearcher.client.gui;

import com.litematicasearcher.client.api.RedenApiClient;
import com.litematicasearcher.client.api.RedenMachine;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

public final class RedenSearchScreen extends Screen {
	private static final int PADDING = 12;
	private static final int NAV_HEIGHT = 28;
	private static final int ROW_HEIGHT = 72;
	private static final int ROW_GAP = 6;
	private static final int IMAGE_SIZE = 52;
	private static final int PANEL_COLOR = 0x80000000;
	private static final int PANEL_HOVER_COLOR = 0xA0000000;
	private static final int CHIP_COLOR = 0x66000000;
	private static final int TEXT_PRIMARY = 0xFFFFFFFF;
	private static final int TEXT_SECONDARY = 0xFFB8B8B8;
	private static final NumberFormat NUMBER_FORMAT = NumberFormat.getIntegerInstance(Locale.getDefault());

	private final Screen parent;
	private final RedenApiClient apiClient = new RedenApiClient();

	private EditBox searchBox;
	private Button searchButton;
	private List<RedenMachine> machines = List.of();
	private Component statusMessage = Component.translatable("litematicasearcher.screen.search.status.loading");
	private boolean initialSearchStarted;
	private boolean loading;
	private int activeRequestId;
	private double scrollOffset;

	public RedenSearchScreen(Screen parent) {
		super(Component.translatable("litematicasearcher.screen.search.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		String currentQuery = searchBox == null ? "" : searchBox.getValue();
		int searchButtonWidth = 76;
		int backButtonWidth = 60;
		int navY = PADDING + 4;
		int searchX = PADDING + backButtonWidth + 8;
		int searchWidth = Math.max(80, width - searchX - searchButtonWidth - PADDING - 8);

		addRenderableWidget(Button.builder(
				Component.translatable("gui.back"),
				button -> onClose()
		).bounds(PADDING, navY, backButtonWidth, 20).build());

		searchBox = new EditBox(
				font,
				searchX,
				navY,
				searchWidth,
				20,
				Component.translatable("litematicasearcher.screen.search.input")
		);
		searchBox.setMaxLength(128);
		searchBox.setHint(Component.translatable("litematicasearcher.screen.search.placeholder"));
		searchBox.setValue(currentQuery);
		addRenderableWidget(searchBox);

		searchButton = addRenderableWidget(Button.builder(
				Component.translatable("litematicasearcher.screen.search.confirm"),
				button -> runSearch()
		).bounds(searchX + searchWidth + 8, navY, searchButtonWidth, 20).build());

		updateSearchButtonState();

		if (!initialSearchStarted) {
			initialSearchStarted = true;
			runSearch();
		}
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
		renderChrome(graphics);
		renderResults(graphics, mouseX, mouseY);
		super.render(graphics, mouseX, mouseY, delta);
	}

	@Override
	public boolean keyPressed(KeyEvent keyEvent) {
		if (super.keyPressed(keyEvent)) {
			return true;
		}

		if (keyEvent.key() == GLFW.GLFW_KEY_ENTER || keyEvent.key() == GLFW.GLFW_KEY_KP_ENTER) {
			runSearch();
			return true;
		}

		return false;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		if (isInList(mouseX, mouseY)) {
			scrollOffset = Mth.clamp(scrollOffset - verticalAmount * 24.0D, 0.0D, maxScrollOffset());
			return true;
		}

		return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent mouseButtonEvent, boolean doubleClick) {
		if (super.mouseClicked(mouseButtonEvent, doubleClick)) {
			return true;
		}

		if (mouseButtonEvent.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT && isInList(mouseButtonEvent.x(), mouseButtonEvent.y())) {
			int index = resultIndexAt(mouseButtonEvent.y());

			if (index >= 0 && index < machines.size()) {
				minecraft.setScreen(new RedenDetailsScreen(this, machines.get(index)));
				return true;
			}
		}

		return false;
	}

	@Override
	public void onClose() {
		if (minecraft != null) {
			minecraft.setScreen(parent);
		}
	}

	private void runSearch() {
		if (minecraft == null || loading) {
			return;
		}

		int requestId = ++activeRequestId;
		loading = true;
		statusMessage = Component.translatable("litematicasearcher.screen.search.status.loading");
		scrollOffset = 0.0D;
		updateSearchButtonState();

		String query = searchBox == null ? "" : searchBox.getValue();
		apiClient.search(query).whenComplete((response, throwable) -> minecraft.execute(() -> {
			if (requestId != activeRequestId) {
				return;
			}

			loading = false;
			updateSearchButtonState();

			if (throwable != null) {
				machines = List.of();
				statusMessage = Component.translatable("litematicasearcher.screen.search.status.error");
				return;
			}

			machines = response.machines();
			statusMessage = machines.isEmpty()
					? Component.translatable("litematicasearcher.screen.search.status.no_results")
					: Component.empty();
		}));
	}

	private void updateSearchButtonState() {
		if (searchButton != null) {
			searchButton.active = !loading;
		}
	}

	private void renderChrome(GuiGraphics graphics) {
		drawPanel(graphics, PADDING - 4, PADDING, width - PADDING + 4, PADDING + NAV_HEIGHT);
		drawPanel(graphics, listX(), listY(), listRight(), listBottom());
	}

	private void renderResults(GuiGraphics graphics, int mouseX, int mouseY) {
		int listX = listX();
		int listY = listY();
		int listRight = listRight();
		int listBottom = listBottom();

		if (machines.isEmpty()) {
			graphics.drawCenteredString(font, statusMessage, width / 2, listY + Math.max(18, (listBottom - listY) / 2), TEXT_SECONDARY);
			return;
		}

		graphics.enableScissor(listX, listY, listRight, listBottom);

		for (int index = 0; index < machines.size(); index++) {
			int rowY = listY + 8 + index * (ROW_HEIGHT + ROW_GAP) - (int) scrollOffset;

			if (rowY + ROW_HEIGHT < listY) {
				continue;
			}

			if (rowY > listBottom) {
				break;
			}

			renderRow(graphics, machines.get(index), listX + 8, rowY, listRight - listX - 16, mouseX, mouseY);
		}

		graphics.disableScissor();
	}

	private void renderRow(GuiGraphics graphics, RedenMachine machine, int x, int y, int rowWidth, int mouseX, int mouseY) {
		boolean hovered = mouseX >= x && mouseX <= x + rowWidth && mouseY >= y && mouseY <= y + ROW_HEIGHT;
		drawPanel(graphics, x, y, x + rowWidth, y + ROW_HEIGHT, hovered ? PANEL_HOVER_FILL : PANEL_FILL);

		int imageX = x + 10;
		int imageY = y + 10;
		drawPanel(graphics, imageX, imageY, imageX + IMAGE_SIZE, imageY + IMAGE_SIZE);
		renderMachineImage(graphics, machine, imageX, imageY);

		int statsWidth = 86;
		int textX = imageX + IMAGE_SIZE + 12;
		int textWidth = Math.max(40, rowWidth - IMAGE_SIZE - statsWidth - 44);
		String name = font.plainSubstrByWidth(machine.name(), textWidth);
		String author = Component.translatable(
				"litematicasearcher.screen.search.author",
				machine.author().username().isBlank() ? "-" : machine.author().username()
		).getString();

		graphics.drawString(font, name, textX, y + 15, TEXT_PRIMARY, true);
		graphics.drawString(font, font.plainSubstrByWidth(author, textWidth), textX, y + 40, TEXT_SECONDARY, true);

		int statsX = x + rowWidth - statsWidth - 10;
		renderStatChip(graphics, statsX, y + 8, statsWidth, "▲ " + NUMBER_FORMAT.format(machine.upVotes()));
		renderStatChip(graphics, statsX, y + 29, statsWidth, "↓ " + NUMBER_FORMAT.format(machine.downloads()));
		renderStatChip(graphics, statsX, y + 50, statsWidth, Component.translatable("litematicasearcher.version.1_21_x").getString());
	}

	private void renderMachineImage(GuiGraphics graphics, RedenMachine machine, int x, int y) {
		String imageUrl = machine.thumbnailUrl().isBlank() ? machine.imageUrl() : machine.thumbnailUrl();
		Identifier texture = RedenImageCache.textureFor(imageUrl);

		if (texture == null) {
			graphics.drawCenteredString(font, "...", x + IMAGE_SIZE / 2, y + 22, TEXT_SECONDARY);
			return;
		}

		graphics.blit(texture, x, y, x + IMAGE_SIZE, y + IMAGE_SIZE, 0.0F, 1.0F, 0.0F, 1.0F);
	}

	private void renderStatChip(GuiGraphics graphics, int x, int y, int width, String text) {
		graphics.fill(x, y, x + width, y + 16, CHIP_COLOR);
		graphics.drawString(font, font.plainSubstrByWidth(text, width - 8), x + 4, y + 4, TEXT_SECONDARY, true);
	}

	private int resultIndexAt(double mouseY) {
		int relativeY = (int) (mouseY - listY() - 8 + scrollOffset);

		if (relativeY < 0) {
			return -1;
		}

		int stride = ROW_HEIGHT + ROW_GAP;
		int index = relativeY / stride;
		int rowPosition = relativeY % stride;
		return rowPosition <= ROW_HEIGHT ? index : -1;
	}

	private boolean isInList(double mouseX, double mouseY) {
		return mouseX >= listX() && mouseX <= listRight() && mouseY >= listY() && mouseY <= listBottom();
	}

	private double maxScrollOffset() {
		int contentHeight = machines.size() * (ROW_HEIGHT + ROW_GAP) - ROW_GAP + 16;
		return Math.max(0, contentHeight - (listBottom() - listY()));
	}

	private int listX() {
		return PADDING;
	}

	private int listY() {
		return PADDING + NAV_HEIGHT + 10;
	}

	private int listRight() {
		return width - PADDING;
	}

	private int listBottom() {
		return height - PADDING;
	}

	static final int PANEL_FILL = 0xC0000000;
	static final int PANEL_HOVER_FILL = 0xE0000000;
	static final int PANEL_BORDER = 0xFF606060;

	static void drawPanel(GuiGraphics graphics, int left, int top, int right, int bottom) {
		drawPanel(graphics, left, top, right, bottom, PANEL_FILL);
	}

	static void drawPanel(GuiGraphics graphics, int left, int top, int right, int bottom, int fillColor) {
		graphics.fill(left + 1, top + 1, right - 1, bottom - 1, fillColor);
		graphics.fill(left, top, right, top + 1, PANEL_BORDER);
		graphics.fill(left, bottom - 1, right, bottom, PANEL_BORDER);
		graphics.fill(left, top, left + 1, bottom, PANEL_BORDER);
		graphics.fill(right - 1, top, right, bottom, PANEL_BORDER);
	}

	@Deprecated
	static void drawRoundedRect(GuiGraphics graphics, int left, int top, int right, int bottom, int radius, int color) {
		drawPanel(graphics, left, top, right, bottom);
	}
}
