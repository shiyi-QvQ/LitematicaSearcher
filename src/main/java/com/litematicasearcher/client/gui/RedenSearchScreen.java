package com.litematicasearcher.client.gui;

import com.litematicasearcher.client.api.RedenApiClient;
import com.litematicasearcher.client.api.RedenMachine;
import com.litematicasearcher.client.config.RedenConfig;
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
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class RedenSearchScreen extends Screen {
	private static final int PADDING = 12;
	private static final int NAV_HEIGHT = 30;
	private static final int FOOTER_HEIGHT = 32; // compact: pager + credit row
	private static final int CREDIT_HEIGHT = 18;
	private static final int ROW_HEIGHT = 64;
	private static final int ROW_GAP = 5;
	private static final int IMAGE_SIZE = 56;
	private static final int STATS_WIDTH = 100;
	private static final int SCROLLBAR_WIDTH = 8;
	private static final int SCROLLBAR_GAP = 4;
	private static final int PANEL_FILL = 0xC0000000;
	private static final int PANEL_HOVER_FILL = 0xE0000000;
	private static final int PANEL_BORDER = 0xFF606060;
	private static final int CHIP_COLOR = 0x66000000;
	private static final int TEXT_PRIMARY = 0xFFFFFFFF;
	private static final int TEXT_SECONDARY = 0xFFB8B8B8;
	private static final int SCROLLBAR_TRACK = 0x33FFFFFF;
	private static final int SCROLLBAR_THUMB = 0x88FFFFFF;
	private static final int SCROLLBAR_THUMB_HOVER = 0xCCFFFFFF;
	private static final NumberFormat NUMBER_FORMAT = NumberFormat.getIntegerInstance(Locale.getDefault());

	private static final int PAGE_SIZE = 10;

	private final Screen parent;
	private final RedenApiClient apiClient = new RedenApiClient();
	private final RedenConfig config = RedenConfig.get();

	private EditBox searchBox;
	private Button searchButton;
	private Button firstPageButton;
	private Button prevPageButton;
	private Button nextPageButton;
	private Button lastPageButton;
	private Button sortButton;
	private Button settingsButton;
	private List<RedenMachine> allMachines = List.of();
	private List<RedenMachine> displayedMachines = List.of();
	private Component statusMessage = Component.translatable("litematicasearcher.screen.search.status.loading");
	private Component paginationInfo = Component.empty();
	private boolean initialSearchStarted;
	private boolean loading;
	private int activeRequestId;
	private double scrollOffset;
	private int currentPage = 1;
	private int totalCount = 0;
	private SortMode sortMode = SortMode.UPDATED_DESC;
	private boolean draggingScrollbar = false;
	private double dragGrabOffset = 0.0D;

	public RedenSearchScreen(Screen parent) {
		super(Component.translatable("litematicasearcher.screen.search.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		String currentQuery = searchBox == null ? "" : searchBox.getValue();
		int searchButtonWidth = 76;
		int backButtonWidth = 60;
		int sortButtonWidth = 110;
		int settingsButtonWidth = 70;
		int navY = PADDING + 4;
		int searchX = PADDING + backButtonWidth + 8;
		int searchRight = width - PADDING - settingsButtonWidth - 8;
		int searchWidth = Math.max(80, searchRight - searchX - searchButtonWidth - sortButtonWidth - 16);

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

		int sortX = searchX + searchWidth + 8;
		sortButton = addRenderableWidget(Button.builder(
				sortMode.label(),
				button -> cycleSortMode()
		).bounds(sortX, navY, sortButtonWidth, 20).build());

		searchButton = addRenderableWidget(Button.builder(
				Component.translatable("litematicasearcher.screen.search.confirm"),
				button -> runSearch(1)
		).bounds(sortX + sortButtonWidth + 8, navY, searchButtonWidth, 20).build());

		settingsButton = addRenderableWidget(Button.builder(
				Component.translatable("litematicasearcher.screen.config.title"),
				button -> minecraft.setScreen(new RedenConfigScreen(this))
		).bounds(width - PADDING - settingsButtonWidth, navY, settingsButtonWidth, 20).build());

		int pagerY = height - PADDING - FOOTER_HEIGHT + CREDIT_HEIGHT + 6;
		int pageBtnWidth = 24;
		int pageBtnHeight = 14;
		int pageBtnGap = 3;
		int pagerX = PADDING;

		firstPageButton = addRenderableWidget(Button.builder(
				Component.literal("<<"),
				button -> runSearch(1)
		).bounds(pagerX, pagerY, pageBtnWidth, pageBtnHeight).build());

		prevPageButton = addRenderableWidget(Button.builder(
				Component.literal("<"),
				button -> runSearch(Math.max(1, currentPage - 1))
		).bounds(pagerX + (pageBtnWidth + pageBtnGap), pagerY, pageBtnWidth, pageBtnHeight).build());

		nextPageButton = addRenderableWidget(Button.builder(
				Component.literal(">"),
				button -> runSearch(currentPage + 1)
		).bounds(width - PADDING - pageBtnWidth * 2 - pageBtnGap, pagerY, pageBtnWidth, pageBtnHeight).build());

		lastPageButton = addRenderableWidget(Button.builder(
				Component.literal(">>"),
				button -> runSearch(Math.max(1, totalPages()))
		).bounds(width - PADDING - pageBtnWidth, pagerY, pageBtnWidth, pageBtnHeight).build());

		updateSearchButtonState();
		updatePagerButtons();

		if (!initialSearchStarted) {
			initialSearchStarted = true;
			runSearch(1);
		}
	}

	private void cycleSortMode() {
		SortMode[] values = SortMode.values();
		sortMode = values[(sortMode.ordinal() + 1) % values.length];
		if (sortButton != null) {
			sortButton.setMessage(sortMode.label());
		}
		applySort();
		scrollOffset = 0.0D;
	}

	private int totalPages() {
		if (totalCount <= 0) {
			return 1;
		}
		return Math.max(1, (totalCount + PAGE_SIZE - 1) / PAGE_SIZE);
	}

	private void updatePagerButtons() {
		boolean hasResults = !allMachines.isEmpty() || totalCount > 0;
		boolean atStart = currentPage <= 1;
		boolean atEnd = currentPage >= totalPages();

		if (firstPageButton != null) firstPageButton.active = hasResults && !atStart;
		if (prevPageButton != null) prevPageButton.active = hasResults && !atStart;
		if (nextPageButton != null) nextPageButton.active = hasResults && !atEnd;
		if (lastPageButton != null) lastPageButton.active = hasResults && !atEnd;
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
		renderChrome(graphics);
		renderResults(graphics, mouseX, mouseY);
		renderScrollbar(graphics, mouseX, mouseY);
		renderPaginationInfo(graphics);
		renderCredit(graphics);
		super.render(graphics, mouseX, mouseY, delta);
	}

	@Override
	public boolean keyPressed(KeyEvent keyEvent) {
		if (super.keyPressed(keyEvent)) {
			return true;
		}

		if (keyEvent.key() == GLFW.GLFW_KEY_ENTER || keyEvent.key() == GLFW.GLFW_KEY_KP_ENTER) {
			runSearch(1);
			return true;
		}

		return false;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		if (isInList(mouseX, mouseY) || isInScrollbarTrack(mouseX, mouseY)) {
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

		if (mouseButtonEvent.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
			double mouseX = mouseButtonEvent.x();
			double mouseY = mouseButtonEvent.y();

			// 优先处理滚动条点击/拖动
			if (isScrollbarVisible() && isInScrollbarTrack(mouseX, mouseY)) {
				int[] thumbBounds = thumbBounds();
				if (thumbBounds != null) {
					int thumbTop = thumbBounds[0];
					int thumbBottom = thumbBounds[1];
					if (mouseY >= thumbTop && mouseY <= thumbBottom) {
						draggingScrollbar = true;
						dragGrabOffset = mouseY - thumbTop;
					} else {
						// 点击轨道空白处：直接跳转
						double maxScroll = maxScrollOffset();
						int trackHeight = listBottom() - listY();
						int thumbHeight = thumbBottom - thumbTop;
						double clickRatio = (mouseY - listY() - thumbHeight / 2.0D) / Math.max(1, trackHeight - thumbHeight);
						scrollOffset = Mth.clamp(clickRatio * maxScroll, 0.0D, maxScroll);
					}
				}
				return true;
			}

			if (isInList(mouseX, mouseY)) {
				int index = resultIndexAt(mouseY);

				if (index >= 0 && index < displayedMachines.size()) {
					minecraft.setScreen(new RedenDetailsScreen(this, displayedMachines.get(index)));
					return true;
				}
			}
		}

		return false;
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent mouseButtonEvent) {
		if (mouseButtonEvent.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
			draggingScrollbar = false;
		}
		return super.mouseReleased(mouseButtonEvent);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent mouseButtonEvent, double deltaX, double deltaY) {
		if (draggingScrollbar && isScrollbarVisible()) {
			double mouseY = mouseButtonEvent.y();
			int trackTop = listY();
			int trackBottom = listBottom();
			int trackHeight = trackBottom - trackTop;
			int[] thumbBounds = thumbBounds();
			if (thumbBounds == null) {
				return true;
			}
			int thumbHeight = thumbBounds[1] - thumbBounds[0];
			int maxThumbTop = trackHeight - thumbHeight;
			if (maxThumbTop <= 0) {
				scrollOffset = 0.0D;
				return true;
			}
			double newThumbTop = Mth.clamp(mouseY - trackTop - dragGrabOffset, 0.0D, maxThumbTop);
			double ratio = newThumbTop / maxThumbTop;
			scrollOffset = Mth.clamp(ratio * maxScrollOffset(), 0.0D, maxScrollOffset());
			return true;
		}
		return super.mouseDragged(mouseButtonEvent, deltaX, deltaY);
	}

	@Override
	public void onClose() {
		if (minecraft != null) {
			minecraft.setScreen(parent);
		}
	}

	private void runSearch(int page) {
		if (minecraft == null || loading) {
			return;
		}

		int requestId = ++activeRequestId;
		loading = true;
		statusMessage = Component.translatable("litematicasearcher.screen.search.status.loading");
		scrollOffset = 0.0D;
		updateSearchButtonState();

		String query = searchBox == null ? "" : searchBox.getValue();
		apiClient.search(query, page, PAGE_SIZE).whenComplete((response, throwable) -> minecraft.execute(() -> {
			if (requestId != activeRequestId) {
				return;
			}

			loading = false;
			updateSearchButtonState();

			if (throwable != null) {
				allMachines = List.of();
				displayedMachines = List.of();
				totalCount = 0;
				currentPage = 1;
				paginationInfo = Component.empty();
				statusMessage = Component.translatable("litematicasearcher.screen.search.status.error");
				updatePagerButtons();
				return;
			}

			currentPage = page;
			allMachines = response.machines();
			totalCount = response.estimatedTotalHits();
			applySort();
			updatePagerButtons();

			paginationInfo = totalCount > 0
					? Component.translatable("litematicasearcher.screen.search.pagination.info",
							currentPage, totalPages(), totalCount)
					: Component.empty();

			statusMessage = allMachines.isEmpty()
					? Component.translatable("litematicasearcher.screen.search.status.no_results")
					: Component.empty();
		}));
	}

	private void applySort() {
		Comparator<RedenMachine> comparator = sortMode.comparator();
		displayedMachines = allMachines.stream().sorted(comparator).toList();
	}

	private void updateSearchButtonState() {
		if (searchButton != null) {
			searchButton.active = !loading;
		}
	}

	private void renderChrome(GuiGraphics graphics) {
		drawPanel(graphics, PADDING - 4, PADDING, width - PADDING + 4, PADDING + NAV_HEIGHT);
		// 列表区右边让出 SCROLLBAR_GAP + SCROLLBAR_WIDTH 给滚动条
		drawPanel(graphics, listX(), listY(), listRight() - SCROLLBAR_GAP - SCROLLBAR_WIDTH, listBottom());
		drawPanel(graphics, PADDING - 4, height - PADDING - FOOTER_HEIGHT, width - PADDING + 4, height - PADDING);
	}

	private void renderResults(GuiGraphics graphics, int mouseX, int mouseY) {
		int listX = listX();
		int listY = listY();
		int listRight = listRight() - SCROLLBAR_GAP - SCROLLBAR_WIDTH;
		int listBottom = listBottom();

		if (displayedMachines.isEmpty()) {
			graphics.drawCenteredString(font, statusMessage, (listX + listRight) / 2, listY + Math.max(18, (listBottom - listY) / 2), TEXT_SECONDARY);
			return;
		}

		graphics.enableScissor(listX, listY, listRight, listBottom);

		// FIX: rows previously drew 8px below the visible top, looking like a downward shift.
		// Now use 4px top padding to keep rows flush with the panel while still leaving room.
		for (int index = 0; index < displayedMachines.size(); index++) {
			int rowY = listY + 4 + index * (ROW_HEIGHT + ROW_GAP) - (int) scrollOffset;

			if (rowY + ROW_HEIGHT < listY) {
				continue;
			}

			if (rowY > listBottom) {
				break;
			}

			renderRow(graphics, displayedMachines.get(index), listX + 6, rowY, listRight - listX - 12, mouseX, mouseY);
		}

		graphics.disableScissor();
	}

	private void renderScrollbar(GuiGraphics graphics, int mouseX, int mouseY) {
		if (!isScrollbarVisible()) {
			return;
		}
		int trackX = scrollbarX();
		int trackTop = listY();
		int trackBottom = listBottom();
		// 轨道背景
		graphics.fill(trackX, trackTop, trackX + SCROLLBAR_WIDTH, trackBottom, SCROLLBAR_TRACK);
		int[] thumb = thumbBounds();
		if (thumb == null) {
			return;
		}
		int thumbTop = thumb[0];
		int thumbBottom = thumb[1];
		boolean hover = mouseX >= trackX && mouseX <= trackX + SCROLLBAR_WIDTH
				&& mouseY >= thumbTop && mouseY <= thumbBottom;
		int thumbColor = (hover || draggingScrollbar) ? SCROLLBAR_THUMB_HOVER : SCROLLBAR_THUMB;
		graphics.fill(trackX + 1, thumbTop, trackX + SCROLLBAR_WIDTH - 1, thumbBottom, thumbColor);
	}

	private void renderPaginationInfo(GuiGraphics graphics) {
		if (!paginationInfo.getString().isEmpty()) {
			int pagerTextY = height - PADDING - FOOTER_HEIGHT + CREDIT_HEIGHT + 8;
			int pagerTextX = (width - font.width(paginationInfo)) / 2;
			graphics.drawString(font, paginationInfo, pagerTextX, pagerTextY, TEXT_SECONDARY, true);
		}
	}

	private void renderCredit(GuiGraphics graphics) {
		if (!config.showFooterCredits()) {
			return;
		}
		// 数据源 + 致谢 — bottom-most row
		String credit = Component.translatable("litematicasearcher.credit.footer").getString();
		int creditY = height - PADDING - 14;
		int creditX = (width - font.width(credit)) / 2;
		graphics.drawString(font, credit, creditX, creditY, TEXT_SECONDARY, true);
	}

	private void renderRow(GuiGraphics graphics, RedenMachine machine, int x, int y, int rowWidth, int mouseX, int mouseY) {
		boolean hovered = mouseX >= x && mouseX <= x + rowWidth && mouseY >= y && mouseY <= y + ROW_HEIGHT;
		drawPanel(graphics, x, y, x + rowWidth, y + ROW_HEIGHT, hovered ? PANEL_HOVER_FILL : PANEL_FILL);

		int imageX = x + 8;
		int imageY = y + 4;
		drawPanel(graphics, imageX, imageY, imageX + IMAGE_SIZE, imageY + IMAGE_SIZE);
		renderMachineImage(graphics, machine, imageX, imageY);

		int textX = imageX + IMAGE_SIZE + 10;
		int textWidth = Math.max(40, rowWidth - IMAGE_SIZE - STATS_WIDTH - 36);
		String name = font.plainSubstrByWidth(machine.name(), textWidth);
		String author = Component.translatable(
				"litematicasearcher.screen.search.author",
				machine.author().username().isBlank() ? "-" : machine.author().username()
		).getString();

		graphics.drawString(font, name, textX, y + 10, TEXT_PRIMARY, true);
		graphics.drawString(font, font.plainSubstrByWidth(author, textWidth), textX, y + 30, TEXT_SECONDARY, true);

		String versionText = machine.versions().isEmpty()
				? Component.translatable("litematicasearcher.version.1_21_x").getString()
				: machine.versions().get(0);
		graphics.drawString(font, font.plainSubstrByWidth(versionText, textWidth), textX, y + 48, TEXT_SECONDARY, true);

		int statsX = x + rowWidth - STATS_WIDTH - 8;
		renderStatChip(graphics, statsX, y + 8, STATS_WIDTH, "\u25B2 " + NUMBER_FORMAT.format(machine.upVotes()));
		renderStatChip(graphics, statsX, y + 26, STATS_WIDTH, "\u2193 " + NUMBER_FORMAT.format(machine.downloads()));
		renderStatChip(graphics, statsX, y + 44, STATS_WIDTH, machine.isGenerationType()
				? Component.translatable("litematicasearcher.type.generation").getString()
				: Component.translatable("litematicasearcher.type.share").getString());
	}

	private void renderMachineImage(GuiGraphics graphics, RedenMachine machine, int x, int y) {
		String imageUrl = machine.thumbnailUrl().isBlank() ? machine.imageUrl() : machine.thumbnailUrl();
		Identifier texture = RedenImageCache.textureFor(imageUrl);

		if (texture == null) {
			graphics.drawCenteredString(font, "...", x + IMAGE_SIZE / 2, y + IMAGE_SIZE / 2 - 4, TEXT_SECONDARY);
			return;
		}

		graphics.blit(texture, x, y, x + IMAGE_SIZE, y + IMAGE_SIZE, 0.0F, 1.0F, 0.0F, 1.0F);
	}

	private void renderStatChip(GuiGraphics graphics, int x, int y, int width, String text) {
		graphics.fill(x, y, x + width, y + 16, CHIP_COLOR);
		graphics.drawString(font, font.plainSubstrByWidth(text, width - 8), x + 4, y + 4, TEXT_SECONDARY, true);
	}

	private int resultIndexAt(double mouseY) {
		int relativeY = (int) (mouseY - listY() - 4 + scrollOffset);

		if (relativeY < 0) {
			return -1;
		}

		int stride = ROW_HEIGHT + ROW_GAP;
		int index = relativeY / stride;
		int rowPosition = relativeY % stride;
		return rowPosition <= ROW_HEIGHT ? index : -1;
	}

	private boolean isInList(double mouseX, double mouseY) {
		int listRight = listRight() - SCROLLBAR_GAP - SCROLLBAR_WIDTH;
		return mouseX >= listX() && mouseX <= listRight && mouseY >= listY() && mouseY <= listBottom();
	}

	private boolean isScrollbarVisible() {
		return maxScrollOffset() > 0.0D && !displayedMachines.isEmpty();
	}

	private int scrollbarX() {
		return listRight() - SCROLLBAR_WIDTH;
	}

	private boolean isInScrollbarTrack(double mouseX, double mouseY) {
		if (!isScrollbarVisible()) {
			return false;
		}
		int trackX = scrollbarX();
		return mouseX >= trackX && mouseX <= trackX + SCROLLBAR_WIDTH
				&& mouseY >= listY() && mouseY <= listBottom();
	}

	private int[] thumbBounds() {
		if (!isScrollbarVisible()) {
			return null;
		}
		int trackTop = listY();
		int trackBottom = listBottom();
		int trackHeight = trackBottom - trackTop;
		int viewportHeight = displayedMachines.size() * (ROW_HEIGHT + ROW_GAP) + 8;
		int minThumbHeight = 24;
		int thumbHeight = Mth.clamp(trackHeight * trackHeight / Math.max(1, viewportHeight),
				minThumbHeight, trackHeight);
		double maxScroll = maxScrollOffset();
		int maxThumbTop = trackHeight - thumbHeight;
		int thumbTop;
		if (maxScroll <= 0.0D || maxThumbTop <= 0) {
			thumbTop = trackTop;
		} else {
			double ratio = scrollOffset / maxScroll;
			thumbTop = trackTop + (int) Math.round(ratio * maxThumbTop);
		}
		return new int[]{thumbTop, thumbTop + thumbHeight};
	}

	private double maxScrollOffset() {
		int contentHeight = displayedMachines.size() * (ROW_HEIGHT + ROW_GAP) + 8;
		return Math.max(0, contentHeight - (listBottom() - listY()));
	}

	private int listX() {
		return PADDING;
	}

	private int listY() {
		return PADDING + NAV_HEIGHT + 6;
	}

	private int listRight() {
		return width - PADDING;
	}

	private int listBottom() {
		return height - PADDING - FOOTER_HEIGHT;
	}

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

	private enum SortMode {
		UPDATED_DESC("litematicasearcher.screen.search.sort.updated_desc", Comparator.comparingLong(RedenMachine::updatedAt).reversed()),
		UPDATED_ASC("litematicasearcher.screen.search.sort.updated_asc", Comparator.comparingLong(RedenMachine::updatedAt)),
		UPVOTES_DESC("litematicasearcher.screen.search.sort.upvotes_desc", Comparator.comparingInt(RedenMachine::upVotes).reversed()),
		DOWNLOADS_DESC("litematicasearcher.screen.search.sort.downloads_desc", Comparator.comparingInt(RedenMachine::downloads).reversed());

		private final String langKey;
		private final Comparator<RedenMachine> comparator;

		SortMode(String langKey, Comparator<RedenMachine> comparator) {
			this.langKey = langKey;
			this.comparator = comparator;
		}

		Component label() {
			return Component.translatable(langKey);
		}

		Comparator<RedenMachine> comparator() {
			return comparator;
		}
	}
}
