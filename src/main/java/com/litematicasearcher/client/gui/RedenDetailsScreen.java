package com.litematicasearcher.client.gui;

import com.litematicasearcher.client.api.RedenApiClient;
import com.litematicasearcher.client.api.RedenApiLanguage;
import com.litematicasearcher.client.api.RedenMachine;
import com.litematicasearcher.client.download.RedenConditionParser;
import com.litematicasearcher.client.download.RedenDownloadManager;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;

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

public final class RedenDetailsScreen extends Screen {
	private static final int PADDING = 12;
	private static final int GAP = 10;
	private static final int PANEL_COLOR = 0x80000000;
	private static final int TEXT_PRIMARY = 0xFFFFFFFF;
	private static final int TEXT_SECONDARY = 0xFFB8B8B8;
	private static final int TEXT_ERROR = 0xFFFF5555;
	private static final NumberFormat NUMBER_FORMAT = NumberFormat.getIntegerInstance(Locale.getDefault());
	private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM);

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

		addDownloadWidgets();

		if (!requestStarted) {
			requestStarted = true;
			fetchDetails();
		}
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
		renderPanels(graphics);
		super.render(graphics, mouseX, mouseY, delta);
		renderInputErrors(graphics);
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
						rebuildWidgets();
					} else {
						statusMessage = Component.translatable("litematicasearcher.screen.details.status.not_found");
					}
				}));
	}

	private void addDownloadWidgets() {
		if (machine == null) {
			return;
		}

		Bounds downloads = downloadsPanel();

		if (usesGeneratedDownload()) {
			int inputX = downloads.left + 80;
			int inputY = downloads.top + 32;
			int inputWidth = Math.min(90, Math.max(56, downloads.width() - 108));

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

			generateButton = addRenderableWidget(Button.builder(
					Component.translatable("litematicasearcher.screen.details.download.generate"),
					button -> startGeneratedDownload()
			).bounds(downloads.left + 14, Math.min(inputY + 4, downloads.bottom - 28), Math.min(140, downloads.width() - 28), 20).build());
		} else {
			int y = downloads.top + 30;
			int buttonWidth = 74;

			for (int index = 0; index < machine.attachments().size(); index++) {
				int buttonY = y + index * 24;

				if (buttonY + 20 > downloads.bottom - 24) {
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

	private void renderPanels(GuiGraphics graphics) {
		Bounds left = leftPanel();
		Bounds info = infoPanel();
		Bounds downloads = downloadsPanel();
		RedenSearchScreen.drawRoundedRect(graphics, left.left, left.top, left.right, left.bottom, 6, PANEL_COLOR);
		RedenSearchScreen.drawRoundedRect(graphics, info.left, info.top, info.right, info.bottom, 6, PANEL_COLOR);
		RedenSearchScreen.drawRoundedRect(graphics, downloads.left, downloads.top, downloads.right, downloads.bottom, 6, PANEL_COLOR);

		if (machine != null) {
			graphics.drawCenteredString(font, machine.name(), width / 2, 17, TEXT_PRIMARY);
		}

		if (!statusMessage.getString().isEmpty()) {
			graphics.drawCenteredString(font, statusMessage, width / 2, left.top + 16, TEXT_SECONDARY);
		}

		if (machine == null) {
			return;
		}

		renderLeftPanel(graphics, left);
		renderInfoPanel(graphics, info);
		renderDownloadPanel(graphics, downloads);
	}

	private void renderLeftPanel(GuiGraphics graphics, Bounds bounds) {
		int imageSize = Math.min(bounds.width() - 28, Math.max(88, bounds.height() / 2));
		int imageX = bounds.left + 14;
		int imageY = bounds.top + 14;
		RedenSearchScreen.drawRoundedRect(graphics, imageX, imageY, imageX + imageSize, imageY + imageSize, 5, 0x66000000);
		renderImage(graphics, imageX, imageY, imageSize);

		int textX = bounds.left + 14;
		int textY = imageY + imageSize + 16;
		graphics.drawString(font, Component.translatable("litematicasearcher.screen.details.description"), textX, textY, TEXT_PRIMARY, true);
		Component description = Component.literal(machine.description().isBlank() ? "-" : machine.description());
		List<FormattedCharSequence> lines = font.split(description, bounds.width() - 28);
		textY += 16;

		for (FormattedCharSequence line : lines) {
			if (textY > bounds.bottom - 12) {
				break;
			}

			graphics.drawString(font, line, textX, textY, TEXT_SECONDARY, true);
			textY += 10;
		}
	}

	private void renderInfoPanel(GuiGraphics graphics, Bounds bounds) {
		int x = bounds.left + 14;
		int y = bounds.top + 14;
		int width = bounds.width() - 28;
		drawInfoLine(graphics, "litematicasearcher.screen.details.author", machine.author().username(), x, y, width);
		drawInfoLine(graphics, "litematicasearcher.screen.details.updated", formatDate(machine.updatedAt()), x, y + 16, width);
		drawInfoLine(graphics, "litematicasearcher.screen.details.versions", versionsText(), x, y + 32, width);
		drawInfoLine(graphics, "litematicasearcher.screen.details.likes", NUMBER_FORMAT.format(machine.upVotes()), x, y + 48, width);
		drawInfoLine(graphics, "litematicasearcher.screen.details.downloads", NUMBER_FORMAT.format(machine.downloads()), x, y + 64, width);
	}

	private void renderDownloadPanel(GuiGraphics graphics, Bounds bounds) {
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

		if (!downloadStatusMessage.getString().isEmpty()) {
			int color = invalidAxes.isEmpty() ? TEXT_SECONDARY : TEXT_ERROR;
			graphics.drawString(font, font.plainSubstrByWidth(downloadStatusMessage.getString(), bounds.width() - 28), x, bounds.bottom - 14, color, true);
		}
	}

	private void renderInputErrors(GuiGraphics graphics) {
		for (String axis : invalidAxes) {
			EditBox input = sizeInputs.get(axis);

			if (input != null) {
				graphics.renderOutline(input.getX() - 1, input.getY() - 1, input.getWidth() + 2, input.getHeight() + 2, TEXT_ERROR);
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

	private void drawInfoLine(GuiGraphics graphics, String key, String value, int x, int y, int maxWidth) {
		String text = Component.translatable(key, value.isBlank() ? "-" : value).getString();
		graphics.drawString(font, font.plainSubstrByWidth(text, maxWidth), x, y, TEXT_SECONDARY, true);
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

	private Bounds leftPanel() {
		int bodyTop = 42;
		int bodyBottom = height - PADDING;
		int availableWidth = width - PADDING * 2 - GAP;
		int leftWidth = Math.max(170, availableWidth * 52 / 100);
		leftWidth = Math.min(leftWidth, availableWidth - 150);
		return new Bounds(PADDING, bodyTop, PADDING + leftWidth, bodyBottom);
	}

	private Bounds infoPanel() {
		Bounds left = leftPanel();
		int rightLeft = left.right + GAP;
		int rightRight = width - PADDING;
		int bodyTop = 42;
		int bodyBottom = height - PADDING;
		int infoBottom = bodyTop + Math.max(100, (bodyBottom - bodyTop - GAP) / 2);
		return new Bounds(rightLeft, bodyTop, rightRight, infoBottom);
	}

	private Bounds downloadsPanel() {
		Bounds info = infoPanel();
		return new Bounds(info.left, info.bottom + GAP, info.right, height - PADDING);
	}

	private record Bounds(int left, int top, int right, int bottom) {
		int width() {
			return right - left;
		}

		int height() {
			return bottom - top;
		}
	}
}
