package com.litematicasearcher.client.gui;

import com.litematicasearcher.client.api.RedenApiClient;
import com.litematicasearcher.client.api.RedenApiLanguage;
import com.litematicasearcher.client.api.RedenMachine;
import com.litematicasearcher.client.api.RedenTag;
import com.litematicasearcher.client.download.RedenConditionParser;
import com.litematicasearcher.client.download.RedenDownloadManager;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

import java.nio.file.Path;
import java.text.NumberFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletionException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class RedenDetailsScreen extends Screen {
	private static final int PADDING = 12;
	private static final int GAP = 10;
	private static final int TEXT_PRIMARY = 0xFFFFFFFF;
	private static final int TEXT_SECONDARY = 0xFFB8B8B8;
	private static final int TEXT_ERROR = 0xFFFF5555;
	private static final int TEXT_LIKES = 0xFFFFAA55;
	private static final int TEXT_DOWNLOADS = 0xFF55AAFF;
	private static final int PANEL_BORDER = 0xFF606060;
	private static final int PANEL_FILL = 0xC0000000;
	private static final int PANEL_HEADER_FILL = 0xE61E2B38;
	private static final int PANEL_INFO_FILL = 0xE62C3E50;
	private static final int PANEL_DOWNLOADS_FILL = 0xE6274960;
	private static final int CHIP_LIKE = 0xE6553030;
	private static final int CHIP_DOWNLOAD = 0xE6255070;
	private static final int CHIP_TAG = 0xE6304050;
	private static final int CHIP_VERSION = 0xE62980B9;
	private static final int GALLERY_HEIGHT = 80;
	private static final NumberFormat NUMBER_FORMAT = NumberFormat.getIntegerInstance(Locale.getDefault());
	private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM);

	private static final Pattern TAG_PATTERN = Pattern.compile("</?([a-zA-Z][a-zA-Z0-9]*)");

	private final Screen parent;
	private final RedenApiClient apiClient = new RedenApiClient();
	private final String machineKey;
	private final Map<String, EditBox> sizeInputs = new LinkedHashMap<>();
	private final Map<String, String> sizeValues = new HashMap<>();
	private final Set<String> invalidAxes = new HashSet<>();
	private final List<Button> downloadButtons = new ArrayList<>();
	private RedenMachine machine;
	private Button generateButton;
	private Component statusMessage = Component.translatable("litematicasearcher.screen.details.status.loading");
	private Component downloadStatusMessage = Component.empty();
	private boolean requestStarted;
	private boolean downloading;
	private double scrollOffset;
	private List<DescriptionSegment> descriptionSegments = List.of();

	public RedenDetailsScreen(Screen parent, RedenMachine initialMachine) {
		super(Component.translatable("litematicasearcher.screen.details.title"));
		this.parent = parent;
		this.machine = initialMachine;
		this.machineKey = initialMachine.key();
	}

	@Override
	protected void init() {
		sizeInputs.clear();
		downloadButtons.clear();
		generateButton = null;

		addRenderableWidget(Button.builder(
				Component.translatable("gui.back"),
				button -> onClose()
		).bounds(PADDING, PADDING, 60, 20).build());

		cacheDescriptionSegments();
		addDownloadWidgets();

		if (!requestStarted) {
			requestStarted = true;
			fetchDetails();
		}

		scrollOffset = Mth.clamp(scrollOffset, 0.0, maxScrollOffset());
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
		renderChrome(graphics);
		renderFixedStatus(graphics);
		renderBody(graphics, mouseX, mouseY);
		super.render(graphics, mouseX, mouseY, delta);
		renderInputErrors(graphics);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		if (mouseY >= bodyTop() && mouseY <= bodyBottom()) {
			scrollOffset = Mth.clamp(scrollOffset - verticalAmount * 20.0, 0.0, maxScrollOffset());
			refreshDownloadWidgets();
			return true;
		}

		return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
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

	private int titleY() {
		return 34;
	}

	private int statusY() {
		return titleY() + 14;
	}

	private int bodyTop() {
		int top = statusY();
		if (!downloadStatusMessage.getString().isEmpty()) {
			top += 12;
		}
		return top;
	}

	private int bodyBottom() {
		return height - PADDING;
	}

	private int scrollY() {
		return bodyTop() - (int) scrollOffset;
	}

	private int leftPanelWidth() {
		int availableWidth = width - PADDING * 2 - GAP;
		return Math.max(170, availableWidth * 60 / 100);
	}

	private int rightPanelWidth() {
		int availableWidth = width - PADDING * 2 - GAP;
		return availableWidth - leftPanelWidth();
	}

	private int imageSize() {
		int leftWidth = leftPanelWidth();
		int availableWidth = leftWidth - 28;
		int availableHeight = (bodyBottom() - bodyTop()) / 2;
		return Math.max(96, Math.min(availableWidth, availableHeight));
	}

	private int galleryImageSize() {
		return GALLERY_HEIGHT - 16;
	}

	private int leftContentHeight() {
		int imgSize = imageSize();
		int descHeight = computeDescriptionPixelHeight();
		int galleryH = machine != null && !machine.images().isEmpty() ? GALLERY_HEIGHT : 0;
		return 14 + imgSize + galleryH + 12 + descHeight + 12;
	}

	private int computeDescriptionPixelHeight() {
		if (descriptionSegments.isEmpty()) {
			return 16;
		}

		int height = 16;
		int lineWidth = leftPanelWidth() - 28;

		for (DescriptionSegment segment : descriptionSegments) {
			if (segment.newline) {
				height += 10;
				continue;
			}

			int lineHeight = segment.large ? 14 : 10;
			List<FormattedCharSequence> lines = font.split(Component.literal(segment.text), lineWidth);
			height += Math.max(1, lines.size()) * lineHeight;
		}

		return height;
	}

	private int infoContentHeight() {
		int rightWidth = rightPanelWidth() - 28;
		int height = 14;
		height += 24; // header
		height += wrappedLineCount("litematicasearcher.screen.details.author", machine.author().username(), rightWidth) * 14;
		height += wrappedLineCount("litematicasearcher.screen.details.updated", formatDate(machine.updatedAt()), rightWidth) * 14;
		height += wrappedLineCount("litematicasearcher.screen.details.versions", versionsText(), rightWidth) * 14;
		height += 18; // type + tag chip row
		if (!machine.featureTags().isEmpty()) {
			height += 24; // tag chips
		}
		height += 16; // separator
		height += 18; // stats row
		return height + 12;
	}

	private int downloadsNeededHeight() {
		if (!usesGeneratedDownload() && machine.attachments().isEmpty() && machine != null) {
			return 80;
		}

		if (!usesGeneratedDownload() && !machine.attachments().isEmpty()) {
			int attachmentRows = Math.max(1, machine.attachments().size());
			return 12 + 16 + attachmentRows * 24 + 30;
		}

		if (usesGeneratedDownload()) {
			int axisCount = 0;

			for (String axis : List.of("x", "y", "z")) {
				if (!machine.conditionsFor(axis).isEmpty()) {
					axisCount++;
				}
			}

			return 12 + 16 + axisCount * 24 + 28 + 30;
		}

		return 80;
	}

	private int contentHeight() {
		int left = leftContentHeight();
		int right = infoContentHeight() + GAP + downloadsNeededHeight();
		return Math.max(left, right);
	}

	private double maxScrollOffset() {
		return Math.max(0.0, contentHeight() - (bodyBottom() - bodyTop()));
	}

	private void cacheDescriptionSegments() {
		if (machine == null) {
			descriptionSegments = List.of();
			return;
		}

		String raw = machine.description().isBlank() ? "-" : machine.description();
		descriptionSegments = parseDescriptionHtml(raw);
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
						cacheDescriptionSegments();
						scrollOffset = 0.0;
						refreshDownloadWidgets();
					} else {
						statusMessage = Component.translatable("litematicasearcher.screen.details.status.not_found");
					}
				}));
	}

	private void refreshDownloadWidgets() {
		clearWidgets();
		addRenderableWidget(Button.builder(
				Component.translatable("gui.back"),
				button -> onClose()
		).bounds(PADDING, PADDING, 60, 20).build());
		addDownloadWidgets();
	}

	private void addDownloadWidgets() {
		if (machine == null) {
			return;
		}

		downloadButtons.clear();
		Bounds downloads = downloadsPanel();

		if (usesGeneratedDownload()) {
			int inputX = downloads.left + 72;
			int inputY = downloads.top + 30;
			int inputWidth = Math.min(100, Math.max(56, downloads.width() - 100));

			for (String axis : List.of("x", "y", "z")) {
				if (machine.conditionsFor(axis).isEmpty()) {
					continue;
				}

				EditBox input = new EditBox(
						font,
						inputX,
						inputY,
						inputWidth,
						18,
						Component.translatable("litematicasearcher.screen.details.size." + axis)
				);
				input.setMaxLength(6);
				input.setValue(sizeValues.getOrDefault(axis, ""));
				input.setResponder(value -> {
					sizeValues.put(axis, value);
					invalidAxes.remove(axis);
					input.setTextColor(EditBox.DEFAULT_TEXT_COLOR);
				});
				sizeInputs.put(axis, input);
				addRenderableWidget(input);
				inputY += 24;
			}

			int buttonY = inputY + 6;
			int buttonWidth = Math.min(140, downloads.width() - 28);
			generateButton = addRenderableWidget(Button.builder(
					Component.translatable("litematicasearcher.screen.details.download.generate"),
					button -> startGeneratedDownload()
			).bounds(downloads.left + 14, buttonY, buttonWidth, 20).build());
		} else {
			int y = downloads.top + 30;
			int buttonWidth = 74;

			for (int index = 0; index < machine.attachments().size(); index++) {
				int buttonY = y + index * 24;

				if (buttonY + 20 > downloads.bottom - 30) {
					break;
				}

				int attachmentIndex = index;
				Button button = addRenderableWidget(Button.builder(
						Component.translatable("litematicasearcher.screen.details.download"),
						pressed -> startAttachmentDownload(attachmentIndex)
				).bounds(downloads.right - buttonWidth - 14, buttonY, buttonWidth, 20).build());
				downloadButtons.add(button);
			}
		}

		updateDownloadButtonState();
	}

	private void renderChrome(GuiGraphics graphics) {
		if (machine != null) {
			graphics.drawCenteredString(font, machine.name(), width / 2, titleY(), TEXT_PRIMARY);
		}

		if (!statusMessage.getString().isEmpty()) {
			graphics.drawCenteredString(font, statusMessage, width / 2, bodyTop() + 16, TEXT_SECONDARY);
		}
	}

	private void renderFixedStatus(GuiGraphics graphics) {
		if (!downloadStatusMessage.getString().isEmpty()) {
			int color = invalidAxes.isEmpty() ? TEXT_SECONDARY : TEXT_ERROR;
			graphics.drawCenteredString(font, downloadStatusMessage, width / 2, statusY(), color);
		}
	}

	private void renderBody(GuiGraphics graphics, int mouseX, int mouseY) {
		if (machine == null) {
			return;
		}

		int bodyTop = bodyTop();
		int bodyBottom = bodyBottom();
		graphics.enableScissor(0, bodyTop, width, bodyBottom);

		Bounds left = leftPanel();
		Bounds info = infoPanel();
		Bounds downloads = downloadsPanel();

		fillPanel(graphics, left.left, left.top, left.right, left.bottom, PANEL_FILL);
		fillPanel(graphics, info.left, info.top, info.right, info.bottom, PANEL_INFO_FILL);
		fillPanel(graphics, downloads.left, downloads.top, downloads.right, downloads.bottom, PANEL_DOWNLOADS_FILL);

		renderLeftPanelContent(graphics, left);
		renderInfoPanelContent(graphics, info);
		renderDownloadPanelContent(graphics, downloads);

		graphics.disableScissor();
	}

	private void renderLeftPanelContent(GuiGraphics graphics, Bounds bounds) {
		int imgSize = imageSize();
		int imageX = bounds.left + 14;
		int imageY = bounds.top + 14;
		fillPanel(graphics, imageX, imageY, imageX + imgSize, imageY + imgSize, 0x66000000);
		renderImage(graphics, imageX, imageY, imgSize);

		int galleryY = imageY + imgSize + 8;
		if (!machine.images().isEmpty()) {
			renderGallery(graphics, bounds.left + 14, galleryY, bounds.width() - 28);
		}

		int textX = bounds.left + 14;
		int textY = galleryY + (machine.images().isEmpty() ? 4 : GALLERY_HEIGHT + 4);
		graphics.drawString(font, Component.translatable("litematicasearcher.screen.details.description"), textX, textY, TEXT_PRIMARY, true);
		textY += 16;

		int lineWidth = bounds.width() - 28;

		for (DescriptionSegment segment : descriptionSegments) {
			if (segment.newline) {
				textY += 10;
				continue;
			}

			if (textY + 10 > bounds.bottom - 12) {
				break;
			}

			int color = segment.large ? 0xFFFFD700
					: segment.medium ? 0xFFFFAA55
					: TEXT_SECONDARY;

			String text = segment.bullet ? "  \u2022 " + segment.text : segment.text;
			List<FormattedCharSequence> lines = font.split(Component.literal(text), lineWidth);

			for (FormattedCharSequence line : lines) {
				if (textY + 10 > bounds.bottom - 12) {
					break;
				}

				graphics.drawString(font, line, textX, textY, color, true);
				textY += segment.large ? 14 : 10;
			}
		}
	}

	private void renderGallery(GuiGraphics graphics, int x, int y, int totalWidth) {
		int thumbSize = galleryImageSize();
		int maxColumns = Math.max(1, totalWidth / (thumbSize + 8));
		int columns = Math.min(maxColumns, machine.images().size());

		int spacing = columns > 1 ? Math.max(2, (totalWidth - columns * thumbSize) / (columns - 1)) : 0;

		for (int i = 0; i < columns; i++) {
			int imgX = x + i * (thumbSize + spacing);
			fillPanel(graphics, imgX, y, imgX + thumbSize, y + thumbSize, 0x55000000);
			renderGalleryImage(graphics, machine.images().get(i), imgX, y, thumbSize);
		}
	}

	private void renderInfoPanelContent(GuiGraphics graphics, Bounds bounds) {
		int x = bounds.left + 14;
		int y = bounds.top + 14;
		int maxWidth = bounds.width() - 28;

		// Section header
		graphics.drawString(font, Component.translatable("litematicasearcher.screen.details.info.title"), x, y, TEXT_PRIMARY, true);
		y += 18;

		String author = Component.translatable("litematicasearcher.screen.details.author", machine.author().username().isBlank() ? "-" : machine.author().username()).getString();
		y = drawWrappedString(graphics, author, x, y, maxWidth, TEXT_SECONDARY);

		String date = Component.translatable("litematicasearcher.screen.details.updated", formatDate(machine.updatedAt())).getString();
		y = drawWrappedString(graphics, date, x, y, maxWidth, TEXT_SECONDARY);

		String versions = Component.translatable("litematicasearcher.screen.details.versions", versionsText()).getString();
		y = drawWrappedString(graphics, versions, x, y, maxWidth, TEXT_SECONDARY);

		// Type chip
		y += 4;
		String typeText = machine.isGenerationType()
				? Component.translatable("litematicasearcher.type.generation").getString()
				: Component.translatable("litematicasearcher.type.share").getString();
		int typeChipW = font.width(typeText) + 12;
		graphics.fill(x, y, x + typeChipW, y + 16, PANEL_FILL);
		graphics.drawString(font, typeText, x + 6, y + 4, TEXT_PRIMARY, true);
		y += 22;

		// Feature tags
		if (!machine.featureTags().isEmpty()) {
			int tagX = x;
			int tagY = y;
			for (RedenTag tag : machine.featureTags()) {
				String tagText = tag.name();
				int tagW = font.width(tagText) + 12;
				if (tagX + tagW > bounds.right - 14) {
					tagX = x;
					tagY += 20;
				}
				graphics.fill(tagX, tagY, tagX + tagW, tagY + 16, CHIP_TAG);
				graphics.drawString(font, tagText, tagX + 6, tagY + 4, TEXT_PRIMARY, true);
				tagX += tagW + 4;
			}
			y = tagY + 22;
		}

		// Separator
		graphics.fill(x, y, bounds.right - 14, y + 1, PANEL_BORDER);
		y += 6;

		// Stats with chips
		String likes = "\u25B2 " + NUMBER_FORMAT.format(machine.upVotes());
		String downs = "\u2193 " + NUMBER_FORMAT.format(machine.downloads());
		int likesChipW = font.width(likes) + 12;
		int downsChipW = font.width(downs) + 12;
		int statsY = y;
		graphics.fill(x, statsY, x + likesChipW, statsY + 18, CHIP_LIKE);
		graphics.drawString(font, likes, x + 6, statsY + 5, TEXT_PRIMARY, true);
		int downsX = x + likesChipW + 6;
		graphics.fill(downsX, statsY, downsX + downsChipW, statsY + 18, CHIP_DOWNLOAD);
		graphics.drawString(font, downs, downsX + 6, statsY + 5, TEXT_PRIMARY, true);
	}

	private void renderDownloadPanelContent(GuiGraphics graphics, Bounds bounds) {
		int x = bounds.left + 14;
		int y = bounds.top + 12;
		graphics.drawString(font, Component.translatable("litematicasearcher.screen.details.download.title"), x, y, TEXT_PRIMARY, true);

		if (usesGeneratedDownload()) {
			for (Map.Entry<String, EditBox> entry : sizeInputs.entrySet()) {
				EditBox input = entry.getValue();
				int color = invalidAxes.contains(entry.getKey()) ? TEXT_ERROR : TEXT_SECONDARY;
				graphics.drawString(font, Component.translatable("litematicasearcher.screen.details.size." + entry.getKey()), x, input.getY() + 5, color, true);
			}
		} else if (machine.attachments().isEmpty()) {
			graphics.drawString(font, Component.translatable("litematicasearcher.screen.details.download.none"), x, y + 20, TEXT_SECONDARY, true);
		} else {
			int textWidth = Math.max(40, bounds.width() - 112);

			for (int index = 0; index < Math.min(downloadButtons.size(), machine.attachments().size()); index++) {
				Button button = downloadButtons.get(index);
				String name = machine.attachments().get(index).name();
				graphics.drawString(font, font.plainSubstrByWidth(name, textWidth), x, button.getY() + 6, TEXT_SECONDARY, true);
			}
		}
	}

	private void renderInputErrors(GuiGraphics graphics) {
		for (String axis : invalidAxes) {
			EditBox input = sizeInputs.get(axis);

			if (input != null) {
				int inputBottom = input.getY() + input.getHeight();

				if (inputBottom > bodyTop() && input.getY() < bodyBottom()) {
					graphics.renderOutline(input.getX() - 1, input.getY() - 1, input.getWidth() + 2, input.getHeight() + 2, TEXT_ERROR);
				}
			}
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

	private void renderGalleryImage(GuiGraphics graphics, String imageUrl, int x, int y, int size) {
		Identifier texture = RedenImageCache.textureFor(imageUrl);

		if (texture == null) {
			graphics.drawCenteredString(font, "...", x + size / 2, y + size / 2 - 4, TEXT_SECONDARY);
			return;
		}

		graphics.blit(texture, x, y, x + size, y + size, 0.0F, 1.0F, 0.0F, 1.0F);
	}

	private void fillPanel(GuiGraphics graphics, int left, int top, int right, int bottom, int fillColor) {
		graphics.fill(left + 1, top + 1, right - 1, bottom - 1, fillColor);
		graphics.fill(left, top, right, top + 1, PANEL_BORDER);
		graphics.fill(left, bottom - 1, right, bottom, PANEL_BORDER);
		graphics.fill(left, top, left + 1, bottom, PANEL_BORDER);
		graphics.fill(right - 1, top, right, bottom, PANEL_BORDER);
	}

	private int drawWrappedString(GuiGraphics graphics, String text, int x, int y, int maxWidth, int color) {
		List<FormattedCharSequence> lines = font.split(Component.literal(text), maxWidth);

		for (FormattedCharSequence line : lines) {
			graphics.drawString(font, line, x, y, color, true);
			y += 14;
		}

		return y;
	}

	private int wrappedLineCount(String key, String value, int maxWidth) {
		String text = Component.translatable(key, value.isBlank() ? "-" : value).getString();
		return Math.max(1, font.split(Component.literal(text), maxWidth).size());
	}

	private void startAttachmentDownload(int attachmentIndex) {
		startDownload(RedenDownloadManager.downloadAttachment(machine, attachmentIndex));
	}

	private void startGeneratedDownload() {
		Map<String, Integer> sizes = validateGenerationInputs();

		if (sizes == null) {
			updateInputColors();
			return;
		}

		startDownload(RedenDownloadManager.downloadGenerated(machine, sizes));
	}

	private void startDownload(java.util.concurrent.CompletableFuture<Path> downloadFuture) {
		if (minecraft == null || downloading) {
			return;
		}

		downloading = true;
		downloadStatusMessage = Component.translatable("litematicasearcher.screen.details.download.status.downloading");
		updateDownloadButtonState();

		downloadFuture.whenComplete((path, throwable) -> minecraft.execute(() -> {
			downloading = false;
			updateDownloadButtonState();

			if (throwable != null) {
				downloadStatusMessage = Component.translatable("litematicasearcher.screen.details.download.status.error", rootMessage(throwable));
				return;
			}

			downloadStatusMessage = Component.translatable("litematicasearcher.screen.details.download.status.saved", path.getFileName().toString());
		}));
	}

	private Map<String, Integer> validateGenerationInputs() {
		invalidAxes.clear();
		Map<String, Integer> sizes = new LinkedHashMap<>();

		for (Map.Entry<String, EditBox> entry : sizeInputs.entrySet()) {
			String axis = entry.getKey();
			String value = entry.getValue().getValue().trim();
			int parsed;

			try {
				parsed = Integer.parseInt(value);
			} catch (NumberFormatException exception) {
				invalidAxes.add(axis);
				downloadStatusMessage = Component.translatable("litematicasearcher.screen.details.download.invalid_number", axis.toUpperCase(Locale.ROOT));
				return null;
			}

			RedenConditionParser.ValidationResult result = RedenConditionParser.parse(machine.conditionsFor(axis)).validate(parsed);

			if (!result.valid()) {
				invalidAxes.add(axis);
				downloadStatusMessage = validationMessage(axis, result);
				return null;
			}

			sizes.put(axis, parsed);
		}

		downloadStatusMessage = Component.empty();
		updateInputColors();
		return sizes;
	}

	private Component validationMessage(String axis, RedenConditionParser.ValidationResult result) {
		String axisName = axis.toUpperCase(Locale.ROOT);

		return switch (result.errorType()) {
			case TOO_SMALL -> Component.translatable("litematicasearcher.screen.details.download.invalid_min", axisName, result.firstValue());
			case TOO_LARGE -> Component.translatable("litematicasearcher.screen.details.download.invalid_max", axisName, result.firstValue());
			case BAD_STEP -> Component.translatable("litematicasearcher.screen.details.download.invalid_mod", axisName, result.firstValue(), result.secondValue());
			case NONE -> Component.empty();
		};
	}

	private void updateInputColors() {
		for (Map.Entry<String, EditBox> entry : sizeInputs.entrySet()) {
			entry.getValue().setTextColor(invalidAxes.contains(entry.getKey()) ? TEXT_ERROR : EditBox.DEFAULT_TEXT_COLOR);
		}
	}

	private void updateDownloadButtonState() {
		for (Button button : downloadButtons) {
			button.active = !downloading;
		}

		if (generateButton != null) {
			generateButton.active = !downloading;
		}
	}

	private boolean usesGeneratedDownload() {
		return machine != null
				&& machine.isGenerationType()
				&& (!machine.conditionsFor("x").isEmpty() || !machine.conditionsFor("y").isEmpty() || !machine.conditionsFor("z").isEmpty());
	}

	private String versionsText() {
		return machine.versions().isEmpty()
				? Component.translatable("litematicasearcher.version.1_21_x").getString()
				: String.join(", ", machine.versions());
	}

	private String formatDate(long epochMillis) {
		if (epochMillis <= 0L) {
			return "-";
		}

		return DATE_FORMATTER.format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()));
	}

	private String rootMessage(Throwable throwable) {
		Throwable current = throwable instanceof CompletionException && throwable.getCause() != null ? throwable.getCause() : throwable;
		return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
	}

	static List<DescriptionSegment> parseDescriptionHtml(String raw) {
		List<DescriptionSegment> segments = new ArrayList<>();
		Matcher matcher = TAG_PATTERN.matcher(raw);
		int lastEnd = 0;
		String currentMode = "";
		boolean bullet = false;

		while (matcher.find()) {
			if (matcher.start() > lastEnd) {
				String text = raw.substring(lastEnd, matcher.start()).trim();
				if (!text.isEmpty()) {
					segments.add(new DescriptionSegment(text, currentMode.equals("h1"), currentMode.equals("h2"), bullet, false));
				}
			}

			String tag = matcher.group(1).toLowerCase(Locale.ROOT);
			boolean closing = raw.startsWith("</", matcher.start());

			if (closing) {
				currentMode = "";
				bullet = false;
			} else {
				switch (tag) {
					case "h1":
						currentMode = "h1";
						break;
					case "h2":
						currentMode = "h2";
						break;
					case "li":
						bullet = true;
						break;
					case "br":
						segments.add(new DescriptionSegment("", false, false, false, true));
						break;
					case "p":
						currentMode = "";
						break;
				}
			}

			lastEnd = matcher.end();
		}

		if (lastEnd < raw.length()) {
			String text = raw.substring(lastEnd).trim();
			if (!text.isEmpty()) {
				segments.add(new DescriptionSegment(text, currentMode.equals("h1"), currentMode.equals("h2"), bullet, false));
			}
		}

		return List.copyOf(segments);
	}

	private Bounds leftPanel() {
		int leftWidth = leftPanelWidth();
		int sy = scrollY();
		int contentH = contentHeight();
		return new Bounds(PADDING, sy, PADDING + leftWidth, sy + contentH);
	}

	private Bounds infoPanel() {
		int leftRight = PADDING + leftPanelWidth();
		int rightRight = width - PADDING;
		int sy = scrollY();
		int infoH = infoContentHeight();
		return new Bounds(leftRight + GAP, sy, rightRight, sy + infoH);
	}

	private Bounds downloadsPanel() {
		Bounds info = infoPanel();
		int sy = scrollY();
		int contentH = contentHeight();
		int downloadsTop = info.bottom + GAP;
		return new Bounds(info.left, downloadsTop, info.right, sy + contentH);
	}

	record DescriptionSegment(String text, boolean large, boolean medium, boolean bullet, boolean newline) {}

	private record Bounds(int left, int top, int right, int bottom) {
		int width() {
			return right - left;
		}

		int height() {
			return bottom - top;
		}
	}
}
