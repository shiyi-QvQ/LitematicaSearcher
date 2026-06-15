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
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;

import java.text.NumberFormat;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class RedenSearchScreen extends Screen {

	// ==================== 布局常量 ====================
	private static final int PADDING = 14;
	private static final int NAV_HEIGHT = 32;
	private static final int FOOTER_HEIGHT = 30;
	private static final int ROW_HEIGHT = 52;
	private static final int ROW_GAP = 2;
	private static final int IMAGE_SIZE = 40;
	private static final int STATS_WIDTH = 70;
	private static final int SCROLLBAR_WIDTH = 5;
	private static final int SCROLLBAR_GAP = 3;
	private static final int CORNER_RADIUS = 4;

	// ==================== 颜色常量 ====================
	// 深色主题 - 不透明实色
	private static final int BG_MAIN = 0xFF1A1A1A;
	private static final int BG_PANEL = 0xFF2D2D2D;
	private static final int BG_ROW = 0xFF252525;
	private static final int BG_ROW_HOVER = 0xFF3A3A3A;
	private static final int BG_ROW_ALT = 0xFF2A2A2A;
	private static final int BG_HEADER = 0xFF333333;
	private static final int BG_CHIP = 0xFF3A3A3A;

	private static final int TEXT_TITLE = 0xFFFFFFFF;
	private static final int TEXT_SECONDARY = 0xFFCCCCCC;
	private static final int TEXT_HINT = 0xFF888888;
	private static final int TEXT_ACCENT = 0xFF66CCFF;
	private static final int TEXT_GOLD = 0xFFFFAA44;
	private static final int TEXT_GREEN = 0xFF44DD88;
	private static final int TEXT_RED = 0xFFFF5555;

	private static final int BORDER_DIVIDER = 0xFF444444;

	private static final int SCROLLBAR_TRACK = 0xFF3A3A3A;
	private static final int SCROLLBAR_THUMB = 0xFF666666;
	private static final int SCROLLBAR_THUMB_HOVER = 0xFF888888;

	private static final NumberFormat NUMBER_FORMAT = NumberFormat.getIntegerInstance(Locale.getDefault());
	private static final int PAGE_SIZE = 12;

	// ==================== 成员变量 ====================
	private final Screen parent;
	private final RedenApiClient apiClient = new RedenApiClient();

	private EditBox searchBox;
	private Button searchButton;
	private Button firstPageButton;
	private Button prevPageButton;
	private Button nextPageButton;
	private Button lastPageButton;
	private Button sortButton;
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

		int backBtnW = 56;
		int searchBtnW = 64;
		int sortBtnW = 90;
		int settingsBtnW = 56;
		int navY = PADDING + 4;
		int searchX = PADDING + backBtnW + 8;
		int searchRight = width - PADDING - settingsBtnW - 8;
		int searchW = Math.max(100, searchRight - searchX - searchBtnW - sortBtnW - 16);

		// ---- 返回按钮 ----
		addRenderableWidget(Button.builder(
				Component.translatable("gui.back"),
				button -> onClose()
		).bounds(PADDING, navY, backBtnW, 20).build());

		// ---- 搜索框 ----
		searchBox = new EditBox(font, searchX, navY, searchW, 20,
				Component.translatable("litematicasearcher.screen.search.input"));
		searchBox.setMaxLength(128);
		searchBox.setHint(Component.translatable("litematicasearcher.screen.search.placeholder"));
		searchBox.setValue(currentQuery);
		searchBox.setFocused(true);
		addRenderableWidget(searchBox);

		// ---- 排序按钮 ----
		int sortX = searchX + searchW + 8;
		sortButton = addRenderableWidget(Button.builder(
				sortMode.label(),
				button -> cycleSortMode()
		).bounds(sortX, navY, sortBtnW, 20).build());

		// ---- 搜索按钮 ----
		searchButton = addRenderableWidget(Button.builder(
				Component.literal("搜索"),
				button -> runSearch(1)
		).bounds(sortX + sortBtnW + 8, navY, searchBtnW, 20).build());

		// ---- 设置按钮 ----
		addRenderableWidget(Button.builder(
				Component.literal("⚙"),
				button -> minecraft.setScreen(new RedenConfigScreen(this))
		).bounds(width - PADDING - settingsBtnW, navY, settingsBtnW, 20).build());

		// ---- 分页按钮 ----
		int pagerY = height - PADDING - FOOTER_HEIGHT + 6;
		int pBtnW = 22;
		int pBtnH = 14;
		int pGap = 3;

		firstPageButton = addRenderableWidget(Button.builder(
				Component.literal("⏮"),
				button -> runSearch(1)
		).bounds(PADDING, pagerY, pBtnW, pBtnH).build());

		prevPageButton = addRenderableWidget(Button.builder(
				Component.literal("◀"),
				button -> runSearch(Math.max(1, currentPage - 1))
		).bounds(PADDING + pBtnW + pGap, pagerY, pBtnW, pBtnH).build());

		nextPageButton = addRenderableWidget(Button.builder(
				Component.literal("▶"),
				button -> runSearch(currentPage + 1)
		).bounds(width - PADDING - pBtnW * 2 - pGap, pagerY, pBtnW, pBtnH).build());

		lastPageButton = addRenderableWidget(Button.builder(
				Component.literal("⏭"),
				button -> runSearch(Math.max(1, totalPages()))
		).bounds(width - PADDING - pBtnW, pagerY, pBtnW, pBtnH).build());

		updateSearchButtonState();
		updatePagerButtons();

		if (!initialSearchStarted) {
			initialSearchStarted = true;
			runSearch(1);
		}
	}

	// ==================== 辅助方法 ====================

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
		if (totalCount <= 0) return 1;
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

	// ==================== 搜索逻辑 ====================

	private void runSearch(int page) {
		if (loading) return;

		int requestId = ++activeRequestId;
		loading = true;
		statusMessage = Component.translatable("litematicasearcher.screen.search.status.loading");
		scrollOffset = 0.0D;
		updateSearchButtonState();

		String query = searchBox == null ? "" : searchBox.getValue();
		apiClient.search(query, page, PAGE_SIZE).whenComplete((response, throwable) -> minecraft.execute(() -> {
			if (requestId != activeRequestId) return;

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
		displayedMachines = allMachines.stream()
				.sorted(sortMode.comparator())
				.toList();
	}

	private void updateSearchButtonState() {
		if (searchButton != null) searchButton.active = !loading;
	}

	// ==================== 渲染 ====================

	@Override
	public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float delta) {
		renderBackground(graphics);
		renderChrome(graphics);
		renderResults(graphics, mouseX, mouseY);
		renderScrollbar(graphics, mouseX, mouseY);
		renderPaginationInfo(graphics);
		super.render(graphics, mouseX, mouseY, delta);
	}

	private void renderBackground(GuiGraphics graphics) {
		graphics.fill(0, 0, width, height, BG_MAIN);
	}

	private void renderChrome(GuiGraphics graphics) {
		// 顶部导航面板 - 实色
		graphics.fill(PADDING - 2, PADDING - 2,
				width - PADDING + 4, PADDING + NAV_HEIGHT + 4, BG_PANEL);

		// 列表区域背景 - 实色
		graphics.fill(listX() - 2, listY() - 2,
				listRight() - SCROLLBAR_GAP - SCROLLBAR_WIDTH + 4, listBottom() + 2, BG_PANEL);

		// 底部分隔线 - 实色
		int dividerY = height - PADDING - FOOTER_HEIGHT;
		graphics.fill(PADDING + 10, dividerY, width - PADDING - 10, dividerY + 1, BORDER_DIVIDER);
	}

	private void renderResults(GuiGraphics graphics, int mouseX, int mouseY) {
		int lx = listX();
		int ly = listY();
		int lr = listRight() - SCROLLBAR_GAP - SCROLLBAR_WIDTH;
		int lb = listBottom();

		if (displayedMachines.isEmpty()) {
			graphics.drawCenteredString(font, statusMessage,
					(lx + lr) / 2, ly + Math.max(18, (lb - ly) / 2), TEXT_SECONDARY);
			return;
		}

		graphics.enableScissor(lx, ly, lr, lb);

		int index = 0;
		for (RedenMachine machine : displayedMachines) {
			int rowY = ly + 2 + index * (ROW_HEIGHT + ROW_GAP) - (int) scrollOffset;

			if (rowY + ROW_HEIGHT < ly) {
				index++;
				continue;
			}
			if (rowY > lb) break;

			renderRow(graphics, machine, lx + 4, rowY, lr - lx - 8, mouseX, mouseY, index);
			index++;
		}

		graphics.disableScissor();
	}

	private void renderRow(GuiGraphics graphics, RedenMachine machine, int x, int y,
	                       int rowWidth, int mouseX, int mouseY, int index) {
		boolean hovered = mouseX >= x && mouseX <= x + rowWidth &&
				mouseY >= y && mouseY <= y + ROW_HEIGHT;

		// 行背景 - 实色交替
		int bgColor;
		if (hovered) {
			bgColor = BG_ROW_HOVER;
		} else {
			bgColor = (index % 2 == 0) ? BG_ROW : BG_ROW_ALT;
		}
		graphics.fill(x, y, x + rowWidth, y + ROW_HEIGHT, bgColor);

		// ---- 缩略图 ----
		int imgX = x + 4;
		int imgY = y + 4;
		graphics.fill(imgX, imgY, imgX + IMAGE_SIZE, imgY + IMAGE_SIZE, 0xFF3A3A3A);
		renderMachineImage(graphics, machine, imgX, imgY);

		// ---- 文字区域 ----
		int textX = imgX + IMAGE_SIZE + 8;
		int textW = Math.max(40, rowWidth - IMAGE_SIZE - STATS_WIDTH - 24);

		// 标题 - 白色
		String name = font.plainSubstrByWidth(machine.name(), textW);
		graphics.drawString(font, name, textX, y + 3, TEXT_TITLE, false);

		// 作者 + 版本 - 灰色
		String author = machine.author().username().isBlank() ? "未知" : machine.author().username();
		String version = machine.versions().isEmpty() ? "1.21.x" : machine.versions().get(0);
		String meta = "作者: " + font.plainSubstrByWidth(author, 80) + "  ·  " + version;
		graphics.drawString(font, font.plainSubstrByWidth(meta, textW - 10),
				textX, y + 18, TEXT_SECONDARY, false);

		// 类型 - 暗灰色
		String desc = machine.isGenerationType() ? "⚡ 生成器" : "📁 普通";
		graphics.drawString(font, font.plainSubstrByWidth(desc, textW - 10),
				textX, y + 33, TEXT_HINT, false);

		// ---- 统计标签（右侧） ----
		int statsX = x + rowWidth - STATS_WIDTH - 4;
		int chipY = y + 4;
		int chipH = 13;
		int chipGap = 2;

		// 点赞 - 红色爱心
		String upText = "❤ " + abbreviateNumber(machine.upVotes());
		renderStatChip(graphics, statsX, chipY, STATS_WIDTH, chipH, upText, TEXT_RED, 0xFF3A2A2A);

		// 下载 - 绿色
		String downText = "⬇ " + abbreviateNumber(machine.downloads());
		renderStatChip(graphics, statsX, chipY + chipH + chipGap, STATS_WIDTH, chipH,
				downText, TEXT_GREEN, 0xFF2A3A2A);

		// 类型标签 - 蓝色
		String typeText = machine.isGenerationType() ? "生成器" : "普通";
		renderStatChip(graphics, statsX, chipY + (chipH + chipGap) * 2, STATS_WIDTH, chipH,
				typeText, TEXT_ACCENT, 0xFF2A2A3A);
	}

	private void renderStatChip(GuiGraphics graphics, int x, int y, int w, int h,
	                            String text, int textColor, int bgColor) {
		graphics.fill(x, y, x + w, y + h, bgColor);
		graphics.drawString(font, font.plainSubstrByWidth(text, w - 8),
				x + (w - font.width(text)) / 2, y + (h - font.lineHeight) / 2 + 1,
				textColor, false);
	}

	private void renderMachineImage(GuiGraphics graphics, RedenMachine machine, int x, int y) {
		String imageUrl = machine.thumbnailUrl().isBlank() ? machine.imageUrl() : machine.thumbnailUrl();
		Identifier texture = RedenImageCache.textureFor(imageUrl);

		if (texture == null) {
			String icon = machine.isGenerationType() ? "⚡" : "📁";
			graphics.drawCenteredString(font, icon, x + IMAGE_SIZE / 2, y + IMAGE_SIZE / 2 - 4, TEXT_HINT);
			return;
		}

		graphics.blit(texture, x, y, x + IMAGE_SIZE, y + IMAGE_SIZE, 0.0F, 1.0F, 0.0F, 1.0F);
	}

	private void renderScrollbar(GuiGraphics graphics, int mouseX, int mouseY) {
		if (!isScrollbarVisible()) return;

		int trackX = scrollbarX();
		int trackTop = listY();
		int trackBottom = listBottom();

		graphics.fill(trackX, trackTop, trackX + SCROLLBAR_WIDTH, trackBottom, SCROLLBAR_TRACK);

		int[] thumb = thumbBounds();
		if (thumb == null) return;

		int thumbTop = thumb[0];
		int thumbBottom = thumb[1];
		boolean hover = mouseX >= trackX && mouseX <= trackX + SCROLLBAR_WIDTH &&
				mouseY >= thumbTop && mouseY <= thumbBottom;

		int color = (hover || draggingScrollbar) ? SCROLLBAR_THUMB_HOVER : SCROLLBAR_THUMB;
		graphics.fill(trackX + 1, thumbTop, trackX + SCROLLBAR_WIDTH - 1, thumbBottom, color);
	}

	private void renderPaginationInfo(GuiGraphics graphics) {
		if (!paginationInfo.getString().isEmpty()) {
			int pagerY = height - PADDING - FOOTER_HEIGHT + 7;
			int textX = (width - font.width(paginationInfo)) / 2;
			graphics.drawString(font, paginationInfo, textX, pagerY, TEXT_SECONDARY, false);
		}
	}

	// ==================== 工具方法 ====================

	private String abbreviateNumber(int num) {
		if (num >= 1000000) return (num / 1000000) + "." + ((num % 1000000) / 100000) + "M";
		if (num >= 1000) return (num / 1000) + "k";
		return String.valueOf(num);
	}

	// ==================== 事件处理 ====================

	@Override
	public boolean keyPressed(@NotNull KeyEvent keyEvent) {
		if (super.keyPressed(keyEvent)) return true;
		if (keyEvent.key() == GLFW.GLFW_KEY_ENTER || keyEvent.key() == GLFW.GLFW_KEY_KP_ENTER) {
			runSearch(1);
			return true;
		}
		return false;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		if (isInList(mouseX, mouseY) || isInScrollbarTrack(mouseX, mouseY)) {
			scrollOffset = Mth.clamp(scrollOffset - verticalAmount * 18.0D, 0.0D, maxScrollOffset());
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
	}

	@Override
	public boolean mouseClicked(@NotNull MouseButtonEvent mouseButtonEvent, boolean doubleClick) {
		if (super.mouseClicked(mouseButtonEvent, doubleClick)) return true;

		if (mouseButtonEvent.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
			double mx = mouseButtonEvent.x();
			double my = mouseButtonEvent.y();

			if (isScrollbarVisible() && isInScrollbarTrack(mx, my)) {
				int[] tb = thumbBounds();
				if (tb != null) {
					int tt = tb[0], tb2 = tb[1];
					if (my >= tt && my <= tb2) {
						draggingScrollbar = true;
						dragGrabOffset = my - tt;
					} else {
						double maxScroll = maxScrollOffset();
						int trackHeight = listBottom() - listY();
						int thumbHeight = tb2 - tt;
						double ratio = (my - listY() - thumbHeight / 2.0D) / Math.max(1, trackHeight - thumbHeight);
						scrollOffset = Mth.clamp(ratio * maxScroll, 0.0D, maxScroll);
					}
				}
				return true;
			}

			if (isInList(mx, my)) {
				int idx = resultIndexAt(my);
				if (idx >= 0 && idx < displayedMachines.size()) {
					minecraft.setScreen(new RedenDetailsScreen(this, displayedMachines.get(idx)));
					return true;
				}
			}
		}
		return false;
	}

	@Override
	public boolean mouseReleased(@NotNull MouseButtonEvent mouseButtonEvent) {
		if (mouseButtonEvent.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
			draggingScrollbar = false;
		}
		return super.mouseReleased(mouseButtonEvent);
	}

	@Override
	public boolean mouseDragged(@NotNull MouseButtonEvent mouseButtonEvent, double deltaX, double deltaY) {
		if (draggingScrollbar && isScrollbarVisible()) {
			double my = mouseButtonEvent.y();
			int trackTop = listY();
			int trackBottom = listBottom();
			int trackHeight = trackBottom - trackTop;
			int[] tb = thumbBounds();
			if (tb == null) return true;
			int thumbHeight = tb[1] - tb[0];
			int maxThumbTop = trackHeight - thumbHeight;
			if (maxThumbTop <= 0) {
				scrollOffset = 0.0D;
				return true;
			}
			double newTop = Mth.clamp(my - trackTop - dragGrabOffset, 0.0D, maxThumbTop);
			scrollOffset = Mth.clamp((newTop / maxThumbTop) * maxScrollOffset(), 0.0D, maxScrollOffset());
			return true;
		}
		return super.mouseDragged(mouseButtonEvent, deltaX, deltaY);
	}

	@Override
	public void onClose() {
		if (minecraft != null) minecraft.setScreen(parent);
	}

	// ==================== 辅助计算 ====================

	private int resultIndexAt(double mouseY) {
		int relY = (int) (mouseY - listY() - 2 + scrollOffset);
		if (relY < 0) return -1;
		int stride = ROW_HEIGHT + ROW_GAP;
		int idx = relY / stride;
		int pos = relY % stride;
		return pos <= ROW_HEIGHT ? idx : -1;
	}

	private boolean isInList(double mx, double my) {
		int lr = listRight() - SCROLLBAR_GAP - SCROLLBAR_WIDTH;
		return mx >= listX() && mx <= lr && my >= listY() && my <= listBottom();
	}

	private boolean isScrollbarVisible() {
		return maxScrollOffset() > 0.0D && !displayedMachines.isEmpty();
	}

	private int scrollbarX() {
		return listRight() - SCROLLBAR_WIDTH;
	}

	private boolean isInScrollbarTrack(double mx, double my) {
		if (!isScrollbarVisible()) return false;
		int tx = scrollbarX();
		return mx >= tx && mx <= tx + SCROLLBAR_WIDTH && my >= listY() && my <= listBottom();
	}

	private int[] thumbBounds() {
		if (!isScrollbarVisible()) return null;
		int trackTop = listY();
		int trackBottom = listBottom();
		int trackHeight = trackBottom - trackTop;
		int contentHeight = displayedMachines.size() * (ROW_HEIGHT + ROW_GAP) + 4;
		int minThumb = 18;
		int thumbHeight = Mth.clamp(trackHeight * trackHeight / Math.max(1, contentHeight), minThumb, trackHeight);
		double maxScroll = maxScrollOffset();
		int maxThumbTop = trackHeight - thumbHeight;
		int thumbTop;
		if (maxScroll <= 0.0D || maxThumbTop <= 0) {
			thumbTop = trackTop;
		} else {
			thumbTop = trackTop + (int) Math.round((scrollOffset / maxScroll) * maxThumbTop);
		}
		return new int[]{thumbTop, thumbTop + thumbHeight};
	}

	private double maxScrollOffset() {
		int contentHeight = displayedMachines.size() * (ROW_HEIGHT + ROW_GAP) + 4;
		return Math.max(0, contentHeight - (listBottom() - listY()));
	}

	private int listX() { return PADDING + 2; }
	private int listY() { return PADDING + NAV_HEIGHT + 8; }
	private int listRight() { return width - PADDING - 2; }
	private int listBottom() { return height - PADDING - FOOTER_HEIGHT - 4; }

	// ==================== 排序模式 ====================

	private enum SortMode {
		UPDATED_DESC("最新", Comparator.comparingLong(RedenMachine::updatedAt).reversed()),
		UPDATED_ASC("最旧", Comparator.comparingLong(RedenMachine::updatedAt)),
		UPVOTES_DESC("最多点赞", Comparator.comparingInt(RedenMachine::upVotes).reversed()),
		DOWNLOADS_DESC("最多下载", Comparator.comparingInt(RedenMachine::downloads).reversed());

		private final String label;
		private final Comparator<RedenMachine> comparator;

		SortMode(String label, Comparator<RedenMachine> comparator) {
			this.label = label;
			this.comparator = comparator;
		}

		Component label() {
			return Component.literal(label);
		}

		Comparator<RedenMachine> comparator() {
			return comparator;
		}
	}
}