package com.litematicasearcher.client.gui;

import com.litematicasearcher.client.auth.AuthStateStorage;
import com.litematicasearcher.client.auth.AuthStateStorage.AuthState;
import com.litematicasearcher.client.config.RedenConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

import java.net.URI;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class RedenConfigScreen extends Screen {

	// ==================== 布局常量 ====================
	private static final int PADDING = 16;
	private static final int ROW_HEIGHT = 24;
	private static final int ROW_GAP = 6;
	private static final int BUTTON_WIDTH = 100;
	private static final int BUTTON_HEIGHT = 20;
	private static final int TAB_WIDTH = 80;
	private static final int TAB_HEIGHT = 24;
	private static final int EDIT_BOX_WIDTH = 200;
	private static final int EDIT_BOX_HEIGHT = 18;
	private static final int URL_BOX_WIDTH = 420;
	private static final int LABEL_INDENT = 30;

	// ==================== 颜色常量 ====================
	private static final int LABEL_COLOR = 0xFFFFFFFF;
	private static final int HINT_COLOR = 0xFF808080;
	private static final int SUCCESS_COLOR = 0xFF4CAF50;
	private static final int ERROR_COLOR = 0xFFF44336;
	private static final int INFO_COLOR = 0xFF64B5F6;
	private static final int TAB_SELECTED_COLOR = 0xFFFFFFFF;
	private static final int TAB_BG_SELECTED = 0x44FFFFFF;

	private static final String STATUS_ON = "开";
	private static final String STATUS_OFF = "关";

	private enum Tab {
		LOGIN("登录认证"),
		UPLOAD("上传作品"),
		SETTINGS("设置");
		private final String label;
		Tab(String label) { this.label = label; }
		public String getLabel() { return label; }
	}

	// ==================== 成员变量 ====================
	private final Screen parent;
	private final RedenConfig config = RedenConfig.get();

	private Tab currentTab = Tab.LOGIN;

	// 登录认证 Tab
	private Button startAuthButton;
	private Button pollStatusButton;
	private Button logoutButton;
	private EditBox urlDisplayBox;
	private String authStatus = "";
	private int authStatusColor = HINT_COLOR;
	private volatile boolean isPolling = false;
	private volatile boolean isAuthCompleted = false;

	// 上传作品 Tab
	private EditBox titleField;
	private EditBox descriptionField;
	private Button uploadButton;
	private String uploadStatus = "";
	private int uploadStatusColor = HINT_COLOR;

	// 设置 Tab
	private Button downloadCompatButton;
	private Button resetButton;

	// 布局坐标
	private int tabStartY;
	private int contentStartY;
	private int backButtonY;
	private int labelX;

	private long lastSaveTime = 0;
	private static final long FEEDBACK_DURATION = 2000;
	private String saveFeedback = "";

	// ==================== 构造函数 ====================
	public RedenConfigScreen(Screen parent) {
		super(Component.translatable("litematicasearcher.screen.config.title"));
		this.parent = parent;
	}

	// ==================== 生命周期方法 ====================

	@Override
	protected void init() {
		// 计算基础布局坐标
		tabStartY = PADDING + 30;
		contentStartY = tabStartY + TAB_HEIGHT + 20;
		backButtonY = height - PADDING - 20;
		labelX = PADDING + LABEL_INDENT;

		// 创建 URL 输入框（所有 Tab 共享）
		int urlBoxWidth = Math.min(URL_BOX_WIDTH, width - labelX - PADDING - 20);
		urlDisplayBox = new EditBox(
				font,
				labelX,
				contentStartY + BUTTON_HEIGHT + ROW_GAP + 10,
				urlBoxWidth,
				EDIT_BOX_HEIGHT,
				Component.literal("授权链接")
		);
		urlDisplayBox.setMaxLength(512);
		urlDisplayBox.setHint(Component.literal("点击启动设备认证后显示授权链接"));
		urlDisplayBox.setEditable(false);
		urlDisplayBox.setVisible(false);
		addRenderableWidget(urlDisplayBox);

		// 创建 Tab 按钮和内容
		createTabButtons();
		switchTab(currentTab);

		// 创建返回按钮
		addRenderableWidget(
				Button.builder(
								Component.translatable("gui.back"),
								button -> onClose()
						)
						.bounds(width - PADDING - 80, backButtonY, 80, 20)
						.build()
		);
	}

	private void createTabButtons() {
		int totalTabWidth = TAB_WIDTH * 3 + 8;
		int tabX = (width - totalTabWidth) / 2;

		addRenderableWidget(
				Button.builder(
								Component.literal(Tab.LOGIN.getLabel()),
								button -> switchTab(Tab.LOGIN)
						)
						.bounds(tabX, tabStartY, TAB_WIDTH, TAB_HEIGHT)
						.build()
		);

		addRenderableWidget(
				Button.builder(
								Component.literal(Tab.UPLOAD.getLabel()),
								button -> switchTab(Tab.UPLOAD)
						)
						.bounds(tabX + TAB_WIDTH + 4, tabStartY, TAB_WIDTH, TAB_HEIGHT)
						.build()
		);

		addRenderableWidget(
				Button.builder(
								Component.literal(Tab.SETTINGS.getLabel()),
								button -> switchTab(Tab.SETTINGS)
						)
						.bounds(tabX + (TAB_WIDTH + 4) * 2, tabStartY, TAB_WIDTH, TAB_HEIGHT)
						.build()
		);
	}

	private void switchTab(Tab tab) {
		this.currentTab = tab;

		// 清除旧 Tab 的控件（保留 Tab 按钮、返回按钮和 URL 输入框）
		removeTabWidgets();

		// 清除旧 Tab 的控件引用
		startAuthButton = null;
		pollStatusButton = null;
		logoutButton = null;
		titleField = null;
		descriptionField = null;
		uploadButton = null;
		downloadCompatButton = null;
		resetButton = null;

		// 初始化当前 Tab
		switch (tab) {
			case LOGIN -> initLoginTab();
			case UPLOAD -> initUploadTab();
			case SETTINGS -> initSettingsTab();
		}

		// 更新控件位置
		updateWidgetPositions();
	}

	/**
	 * 移除当前 Tab
	 */
	private void removeTabWidgets() {
		clearWidgets();
		createTabButtons();
		addRenderableWidget(urlDisplayBox);
		addRenderableWidget(
				Button.builder(
								Component.translatable("gui.back"),
								button -> onClose()
						)
						.bounds(width - PADDING - 80, backButtonY, 80, 20)
						.build()
		);
	}

	// ==================== 初始化各 Tab ====================

	private void initLoginTab() {
		AuthState authState = AuthStateStorage.getState();
		boolean isLoggedIn = authState.isAccessTokenValid();

		if (isLoggedIn) {
			logoutButton = addRenderableWidget(
					Button.builder(
									Component.literal("登出"),
									button -> handleLogout()
							)
							.bounds(0, 0, 80, BUTTON_HEIGHT)
							.build()
			);
		} else {
			startAuthButton = addRenderableWidget(
					Button.builder(
									Component.literal("启动设备认证"),
									button -> startDeviceAuth()
							)
							.bounds(0, 0, 120, BUTTON_HEIGHT)
							.build()
			);

			pollStatusButton = addRenderableWidget(
					Button.builder(
									Component.literal("检查授权状态"),
									button -> pollAuthStatus()
							)
							.bounds(0, 0, 120, BUTTON_HEIGHT)
							.build()
			);
			pollStatusButton.active = false;

			if (authState.isDeviceValid() && !authState.isLoggedIn()) {
				pollStatusButton.active = true;
				authStatus = "设备码有效，点击检查授权状态继续认证";
				authStatusColor = INFO_COLOR;
			}
		}
	}

	private void initUploadTab() {
		AuthState authState = AuthStateStorage.getState();
		boolean isLoggedIn = authState.isAccessTokenValid();

		if (!isLoggedIn) {
			Button goToLogin = addRenderableWidget(
					Button.builder(
									Component.literal("前往登录"),
									button -> switchTab(Tab.LOGIN)
							)
							.bounds(0, 0, 100, BUTTON_HEIGHT)
							.build()
			);
			goToLogin.setX((width - 100) / 2);
			goToLogin.setY((height - 20) / 2);
			return;
		}

		int fieldWidth = Math.min(EDIT_BOX_WIDTH, width - (PADDING + 120) - PADDING - 20);

		titleField = new EditBox(
				font,
				0, 0,
				fieldWidth,
				EDIT_BOX_HEIGHT,
				Component.literal("作品标题")
		);
		titleField.setMaxLength(64);
		titleField.setHint(Component.literal("请输入作品标题"));
		addRenderableWidget(titleField);

		descriptionField = new EditBox(
				font,
				0, 0,
				fieldWidth,
				EDIT_BOX_HEIGHT,
				Component.literal("作品描述")
		);
		descriptionField.setMaxLength(256);
		descriptionField.setHint(Component.literal("请输入作品描述（可选）"));
		addRenderableWidget(descriptionField);

		uploadButton = addRenderableWidget(
				Button.builder(
								Component.literal("上传作品"),
								button -> handleUpload()
						)
						.bounds(0, 0, 120, BUTTON_HEIGHT)
						.build()
		);
	}

	private void initSettingsTab() {
		downloadCompatButton = addRenderableWidget(
				Button.builder(
								Component.literal(config.compatibleDownload() ? STATUS_ON : STATUS_OFF),
								button -> toggleBooleanSetting(
										downloadCompatButton,
										config::compatibleDownload,
										config::setCompatibleDownload,
										"下载兼容已保存"
								)
						)
						.bounds(0, 0, BUTTON_WIDTH, BUTTON_HEIGHT)
						.build()
		);

		resetButton = addRenderableWidget(
				Button.builder(
								Component.translatable("litematicasearcher.screen.config.reset"),
								button -> resetDefaults()
						)
						.bounds(0, 0, 80, 20)
						.build()
		);
	}

	// ==================== 核心布局方法 ====================

	/**
	 * 集中更新所有控件的位置
	 */
	private void updateWidgetPositions() {
		// 更新 URL 输入框位置
		int urlBoxY = contentStartY + BUTTON_HEIGHT + ROW_GAP + 10;
		urlDisplayBox.setX(labelX);
		urlDisplayBox.setY(urlBoxY);
		urlDisplayBox.setWidth(Math.min(URL_BOX_WIDTH, width - labelX - PADDING - 20));

		// ---- 登录认证 Tab ----
		if (currentTab == Tab.LOGIN) {
			int loginRowY = contentStartY;

			if (logoutButton != null) {
				logoutButton.setX(labelX + 80);
				logoutButton.setY(loginRowY);
			}

			if (startAuthButton != null) {
				startAuthButton.setX(labelX + 80);
				startAuthButton.setY(loginRowY);
			}

			if (pollStatusButton != null) {
				pollStatusButton.setX(labelX + 210);
				pollStatusButton.setY(loginRowY);
			}
		}

		// ---- 上传作品 Tab ----
		if (currentTab == Tab.UPLOAD) {
			int uploadRowY = contentStartY + ROW_HEIGHT + ROW_GAP + 8;
			int fieldX = PADDING + 120;
			int fieldWidth = Math.min(EDIT_BOX_WIDTH, width - fieldX - PADDING - 20);

			if (titleField != null) {
				titleField.setX(fieldX);
				titleField.setY(uploadRowY + (ROW_HEIGHT - EDIT_BOX_HEIGHT) / 2);
				titleField.setWidth(fieldWidth);
			}

			if (descriptionField != null) {
				int descRowY = uploadRowY + ROW_HEIGHT + ROW_GAP;
				descriptionField.setX(fieldX);
				descriptionField.setY(descRowY + (ROW_HEIGHT - EDIT_BOX_HEIGHT) / 2);
				descriptionField.setWidth(fieldWidth);
			}

			if (uploadButton != null) {
				int btnRowY = uploadRowY + ROW_HEIGHT * 2 + ROW_GAP * 2 + 8;
				uploadButton.setX(labelX);
				uploadButton.setY(btnRowY + (ROW_HEIGHT - BUTTON_HEIGHT) / 2);
			}
		}

		// ---- 设置 Tab ----
		if (currentTab == Tab.SETTINGS) {
			int settingsRowY = contentStartY;
			int btnX = width - PADDING - BUTTON_WIDTH;

			if (downloadCompatButton != null) {
				downloadCompatButton.setX(btnX);
				downloadCompatButton.setY(settingsRowY + (ROW_HEIGHT - BUTTON_HEIGHT) / 2);
				settingsRowY += ROW_HEIGHT + ROW_GAP + 12;
			}

			if (resetButton != null) {
				resetButton.setX(PADDING);
				resetButton.setY(settingsRowY + (ROW_HEIGHT - 20) / 2);
			}
		}

		// 更新返回按钮位置
		backButtonY = height - PADDING - 20;
		for (var widget : children()) {
			if (widget instanceof Button btn &&
					btn.getMessage().getString().equals(Component.translatable("gui.back").getString())) {
				btn.setX(width - PADDING - 80);
				btn.setY(backButtonY);
			}
		}
	}

	// ==================== 业务逻辑 ====================

	private void startDeviceAuth() {
		if (startAuthButton == null) return;

		startAuthButton.active = false;
		authStatus = "正在启动认证...";
		authStatusColor = HINT_COLOR;
		isAuthCompleted = false;

		urlDisplayBox.setValue("");
		urlDisplayBox.setVisible(false);

		new Thread(() -> {
			try {
				AuthStateStorage.StartResponse response = AuthStateStorage.startDeviceFlow();

				String authUrl = response.getRedirectUrl();
				String deviceCode = authUrl.substring(authUrl.lastIndexOf("=") + 1);

				AuthStateStorage.updateDeviceState(
						deviceCode,
						response.getQueryToken(),
						response.getExpiresIn()
				);

				net.minecraft.client.Minecraft.getInstance().execute(() -> {
					urlDisplayBox.setValue(authUrl);
					urlDisplayBox.setVisible(true);
					urlDisplayBox.setHint(Component.literal(""));

					authStatus = "请复制上方链接到浏览器中打开授权";
					authStatusColor = INFO_COLOR;
					startAuthButton.active = true;
					pollStatusButton.active = true;
				});
			} catch (Exception e) {
				net.minecraft.client.Minecraft.getInstance().execute(() -> {
					authStatus = "启动认证失败: " + e.getMessage();
					authStatusColor = ERROR_COLOR;
					startAuthButton.active = true;
					urlDisplayBox.setVisible(false);
				});
			}
		}).start();
	}

	private void pollAuthStatus() {
		if (pollStatusButton == null) return;

		AuthState authState = AuthStateStorage.getState();
		if (authState.getQueryToken().isEmpty()) {
			authStatus = "请先启动认证";
			authStatusColor = ERROR_COLOR;
			return;
		}

		pollStatusButton.active = false;
		authStatus = "正在检查认证状态...";
		authStatusColor = HINT_COLOR;
		isPolling = true;
		isAuthCompleted = false;

		new Thread(() -> {
			try {
				int attempts = 0;
				int maxAttempts = 60;
				String queryToken = authState.getQueryToken();

				while (isPolling && attempts < maxAttempts && !Thread.currentThread().isInterrupted()) {
					attempts++;
					try {
						Thread.sleep(5000);
					} catch (InterruptedException e) {
						Thread.currentThread().interrupt();
						break;
					}

					try {
						AuthStateStorage.QueryResponse response = AuthStateStorage.queryAuthStatus(queryToken);

						if (response.isApproved()) {
							net.minecraft.client.Minecraft.getInstance().execute(() -> {
								AuthStateStorage.updateLoginState(
										response.getAccessToken(),
										"",
										response.getExpiresIn(),
										response.getUserId(),
										response.getUsername()
								);

								authStatus = "认证成功！欢迎 " + response.getUsername();
								authStatusColor = SUCCESS_COLOR;
								pollStatusButton.active = false;
								isPolling = false;
								isAuthCompleted = true;

								urlDisplayBox.setVisible(false);
								switchTab(currentTab);
							});
							return;
						} else if (response.isDenied()) {
							net.minecraft.client.Minecraft.getInstance().execute(() -> {
								authStatus = "用户拒绝了授权请求";
								authStatusColor = ERROR_COLOR;
								pollStatusButton.active = true;
								isPolling = false;
							});
							return;
						} else if (response.isExpired()) {
							net.minecraft.client.Minecraft.getInstance().execute(() -> {
								authStatus = "认证请求已过期，请重新启动认证";
								authStatusColor = ERROR_COLOR;
								pollStatusButton.active = true;
								isPolling = false;
								urlDisplayBox.setVisible(false);
								AuthStateStorage.getState().clearDeviceInfo();
								AuthStateStorage.save();
							});
							return;
						}

						int finalAttempts = attempts;
						net.minecraft.client.Minecraft.getInstance().execute(() -> {
							authStatus = "等待用户授权... (" + (finalAttempts * 5) + "s)";
							authStatusColor = HINT_COLOR;
						});

					} catch (Exception ignored) {}
				}

				net.minecraft.client.Minecraft.getInstance().execute(() -> {
					if (!isAuthCompleted) {
						authStatus = "认证超时，请重新尝试";
						authStatusColor = ERROR_COLOR;
						pollStatusButton.active = true;
						isPolling = false;
					}
				});
			} catch (Exception e) {
				net.minecraft.client.Minecraft.getInstance().execute(() -> {
					authStatus = "认证出错: " + e.getMessage();
					authStatusColor = ERROR_COLOR;
					pollStatusButton.active = true;
					isPolling = false;
				});
			}
		}).start();
	}

	private void handleLogout() {
		AuthStateStorage.logout();
		authStatus = "已登出";
		authStatusColor = HINT_COLOR;
		if (logoutButton != null) logoutButton.active = false;
		isPolling = false;
		isAuthCompleted = false;
		urlDisplayBox.setValue("");
		urlDisplayBox.setVisible(false);
		switchTab(currentTab);
	}

	private void handleUpload() {
		String title = titleField.getValue().trim();
		@SuppressWarnings("unused")
		String description = descriptionField.getValue().trim();

		if (title.isEmpty()) {
			uploadStatus = "请输入作品标题";
			uploadStatusColor = ERROR_COLOR;
			return;
		}

		AuthState authState = AuthStateStorage.getState();
		if (!authState.isAccessTokenValid()) {
			uploadStatus = "请先登录";
			uploadStatusColor = ERROR_COLOR;
			return;
		}

		uploadStatus = "正在上传作品...";
		uploadStatusColor = HINT_COLOR;
		uploadButton.active = false;

		new Thread(() -> {
			try {
				Thread.sleep(2000);
				net.minecraft.client.Minecraft.getInstance().execute(() -> {
					uploadStatus = "作品上传成功！";
					uploadStatusColor = SUCCESS_COLOR;
					uploadButton.active = true;
					titleField.setValue("");
					descriptionField.setValue("");
				});
			} catch (InterruptedException ignored) {
				Thread.currentThread().interrupt();
				net.minecraft.client.Minecraft.getInstance().execute(() -> {
					uploadStatus = "上传被中断";
					uploadStatusColor = ERROR_COLOR;
					uploadButton.active = true;
				});
			}
		}).start();
	}

	private void toggleBooleanSetting(
			Button button,
			Supplier<Boolean> getter,
			Consumer<Boolean> setter,
			String feedbackMessage) {

		boolean newValue = !getter.get();
		setter.accept(newValue);
		button.setMessage(Component.literal(newValue ? STATUS_ON : STATUS_OFF));
		showFeedback(feedbackMessage);
	}

	private void resetDefaults() {
		config.setCompatibleDownload(true);
		refreshAllButtonLabels();
		showFeedback("已重置为默认配置");
	}

	private void refreshAllButtonLabels() {
		if (downloadCompatButton != null) {
			downloadCompatButton.setMessage(Component.literal(config.compatibleDownload() ? STATUS_ON : STATUS_OFF));
		}
	}

	private void showFeedback(String message) {
		this.saveFeedback = message;
		this.lastSaveTime = System.currentTimeMillis();
	}

	private String truncateText(String text, int maxWidth) {
		if (font.width(text) <= maxWidth) {
			return text;
		}
		return font.plainSubstrByWidth(text, maxWidth - 20) + "...";
	}

	// ==================== 渲染 ====================

	@Override
	public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float delta) {
		super.render(graphics, mouseX, mouseY, delta);

		graphics.drawCenteredString(font, title, width / 2, PADDING + 4, LABEL_COLOR);
		drawTabHighlight(graphics);

		switch (currentTab) {
			case LOGIN -> renderLoginTab(graphics);
			case UPLOAD -> renderUploadTab(graphics);
			case SETTINGS -> renderSettingsTab(graphics);
		}

		if (System.currentTimeMillis() - lastSaveTime < FEEDBACK_DURATION) {
			int feedbackX = (width - font.width(saveFeedback)) / 2;
			int feedbackY = backButtonY - 30;
			graphics.drawString(font, saveFeedback, feedbackX, feedbackY, HINT_COLOR, false);
		}
	}

	private void drawTabHighlight(GuiGraphics graphics) {
		int totalTabWidth = TAB_WIDTH * 3 + 8;
		int tabX = (width - totalTabWidth) / 2;
		int y = tabStartY;

		int x = switch (currentTab) {
			case LOGIN -> tabX;
			case UPLOAD -> tabX + TAB_WIDTH + 4;
			case SETTINGS -> tabX + (TAB_WIDTH + 4) * 2;
		};

		graphics.fill(x, y, x + TAB_WIDTH, y + TAB_HEIGHT, TAB_BG_SELECTED);
		graphics.fill(x, y + TAB_HEIGHT - 2, x + TAB_WIDTH, y + TAB_HEIGHT, TAB_SELECTED_COLOR);
	}

	// ==================== 渲染各 Tab ====================

	private void renderLoginTab(GuiGraphics graphics) {
		int rowY = contentStartY;

		AuthState authState = AuthStateStorage.getState();
		boolean isLoggedIn = authState.isAccessTokenValid();

		// 登录状态（与按钮同行，左对齐）
		String statusText = isLoggedIn
				? "[已登录] " + authState.getUsername()
				: "[未登录]";
		int statusColor = isLoggedIn ? SUCCESS_COLOR : ERROR_COLOR;
		graphics.drawString(font, "登录状态: " + statusText, labelX, rowY + 4, statusColor, false);

		// 信息显示（按钮下方）
		int infoY = rowY + BUTTON_HEIGHT + ROW_GAP + 10;

		if (isLoggedIn) {
			graphics.drawString(font, "用户ID: " + authState.getUserId(), labelX, infoY + 4, HINT_COLOR, false);
			infoY += ROW_HEIGHT + ROW_GAP;

			long daysLeft = (authState.getExpiresAt() - System.currentTimeMillis()) / 86400000;
			graphics.drawString(font, "Token有效期: " + Math.max(0, daysLeft) + " 天", labelX, infoY + 4, HINT_COLOR, false);

			if (!authState.getDeviceCode().isEmpty()) {
				infoY += ROW_HEIGHT + ROW_GAP;
				String displayCode = truncateText(authState.getDeviceCode(), width - labelX - 60);
				graphics.drawString(font, "设备码: " + displayCode, labelX, infoY + 4, HINT_COLOR, false);
			}
		} else {
			// URL 输入框标签
			if (urlDisplayBox.visible) {
				graphics.drawString(font, "授权链接 (选中后 Ctrl+C 复制):", labelX, infoY + 4, LABEL_COLOR, false);
				infoY += ROW_HEIGHT + ROW_GAP + 4 + EDIT_BOX_HEIGHT + ROW_GAP + 4;
			}

			// 认证状态
			if (!authStatus.isEmpty()) {
				String[] lines = authStatus.split("\n");
				for (int i = 0; i < lines.length; i++) {
					graphics.drawString(font, lines[i], labelX, infoY + 4 + i * (font.lineHeight + 2), authStatusColor, false);
				}
				infoY += (authStatus.split("\n").length * (font.lineHeight + 2) + ROW_GAP + 8);
			}

			// 设备码和过期时间
			if (!authState.getDeviceCode().isEmpty()) {
				String displayCode = truncateText(authState.getDeviceCode(), width - labelX - 60);
				graphics.drawString(font, "设备码: " + displayCode, labelX, infoY + 4, HINT_COLOR, false);
				infoY += ROW_HEIGHT + ROW_GAP;

				if (authState.isDeviceValid()) {
					long secondsLeft = (authState.getDeviceExpiresAt() - System.currentTimeMillis()) / 1000;
					graphics.drawString(font, "剩余时间: " + Math.max(0, secondsLeft / 60) + " 分钟 " + (secondsLeft % 60) + " 秒",
							labelX, infoY + 4, HINT_COLOR, false);
				} else {
					graphics.drawString(font, "设备码已过期，请重新启动认证", labelX, infoY + 4, ERROR_COLOR, false);
				}
			}
		}
	}

	private void renderUploadTab(GuiGraphics graphics) {
		int rowY = contentStartY;

		AuthState authState = AuthStateStorage.getState();
		boolean isLoggedIn = authState.isAccessTokenValid();

		if (!isLoggedIn) {
			String tip = "请先到「登录认证」Tab 登录后再上传作品";
			int tipX = (width - font.width(tip)) / 2;
			int tipY = (height - font.lineHeight) / 2 - 30;
			graphics.drawString(font, tip, tipX, tipY, ERROR_COLOR, false);
			return;
		}

		graphics.drawString(font, "当前用户: " + authState.getUsername(), labelX, rowY + 4, SUCCESS_COLOR, false);

		int infoY = rowY + ROW_HEIGHT + ROW_GAP + 8;
		if (!uploadStatus.isEmpty()) {
			graphics.drawString(font, uploadStatus, labelX, infoY + ROW_HEIGHT + ROW_GAP + 8 + 4, uploadStatusColor, false);
		}

		int formY = contentStartY + ROW_HEIGHT + ROW_GAP + ROW_HEIGHT + ROW_GAP + 8;
		if (!uploadStatus.isEmpty()) {
			formY += ROW_HEIGHT + ROW_GAP + 8;
		}

		graphics.drawString(font, "作品标题:", labelX, formY + 4, HINT_COLOR, false);
		formY += ROW_HEIGHT + ROW_GAP;
		graphics.drawString(font, "作品描述:", labelX, formY + 4, HINT_COLOR, false);
	}

	private void renderSettingsTab(GuiGraphics graphics) {
		int rowY = contentStartY;

		graphics.drawString(font, "兼容下载", labelX, rowY + 4, LABEL_COLOR, false);
		graphics.drawString(font, "(启用后回退到旧版下载接口)", labelX + 80, rowY + 4, HINT_COLOR, false);
		rowY += ROW_HEIGHT + ROW_GAP + 12;

		// 重置按钮文字在 init 中创建，不需要绘制
	}

	// ==================== 事件处理 ====================

	@Override
	public boolean mouseClicked(@NotNull MouseButtonEvent mouseButtonEvent, boolean doubleClick) {
		return super.mouseClicked(mouseButtonEvent, doubleClick);
	}

	@Override
	public boolean keyPressed(@NotNull KeyEvent keyEvent) {
		return super.keyPressed(keyEvent);
	}

	@Override
	public void onClose() {
		if (minecraft != null) {
			minecraft.setScreen(parent);
		}
	}
}