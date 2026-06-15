# CHANGELOG

## [v0.1.2] — 2026-06-15

### Added
- **设置界面 (`RedenConfigScreen`)** —— 通过 ModMenu 或搜索界面的"设置"按钮进入。
  支持三类设置卡片：
  1. **图片加载方式** —— `自动 / 直连 / 代理`（解决 redenmc 反爬导致图片无法显示）
  2. **页脚致谢** —— 控制搜索页底部 `数据源: RedenMC | 作者` 显示开关
  3. **兼容旧版下载** —— 回退到旧版下载接口，适用低版本客户端
  卡片含"恢复默认"和"返回"按钮。

- **配置持久化 (`RedenConfig`)** —— 用 Gson 读写 `minecraft/litematicasearcher/config.json`，
  启动时自动加载，配置变更时自动保存。

- **图片反爬三级 fallback (`RedenImageCache`)**
  1. **直连**：带正确反爬头（`User-Agent` / `Referer: https://redenmc.com/` / `Accept: image/*`）
  2. **PROXY 备用**：`images.weserv.nl/?url=` + `wsrv.nl/?url=` 公共图片代理（去 Referer）
  3. **AUTO 模式**：先尝试直连 3 次（指数退避 600ms / 1.2s / 2.4s），失败自动切代理
  同一 URL 通过 SHA-1 缓存到 `litematicasearcher/image_cache/<hash>.png`。

- **多格式图片支持** —— `redenmc.com` 混用 PNG 和 JPEG 附件。
  - PNG 走 `NativeImage.read(InputStream)`（原生）
  - JPEG / GIF / WebP 走 `ImageIO.read` → `BufferedImage` → `NativeImage` 转换
  通过 magic byte 嗅探自动识别格式（`FF D8 FF` / `89 50 4E 47` / `47 49 46` / `RIFF`）。

- **快捷键 O 键打开搜索界面 (`LitematicaSearcherClient`)** —— 默认绑定 `GLFW_KEY_O`，
  按下直接在游戏内打开 `RedenSearchScreen`（不可配置）。

### Fixed
- **图片字节序错误 (红蓝颠倒) —— `setPixelRGBA` → `setPixelABGR`**
  1.21.11 `NativeImage` 有两个 3-int 像素写入器：
  - `setPixelRGBA(int, int, int)` → `MemoryUtil.memPutInt(0xAARRGGBB)` 直接写入，
    little-endian 内存布局为 `BB GG RR AA`，OpenGL 读为 `R=0xBB, B=0xRR` → **红蓝颠倒**。
  - `setPixelABGR(int, int, int)` → 内部调用 `bel.q()` 把 `0xAARRGGBB` reshuffle 成 `0xAABBGGRR`
    再 `memPutInt`，内存布局 `RR GG BB AA`，OpenGL 读为 `R=0xRR, B=0xBB` → **正确**。
  `BufferedImage.getRGB()` 返回的 `0xAARRGGBB` 配合 `setPixelABGR` 直通，零位运算。
  关联：`RedenImageCache.java:276`

- **`1.21.11 Mojang official mapping 导入路径修正`**
  旧代码（v0.1.0 之前）使用 yarn 路径 `net.minecraft.client.texture.NativeImage`，
  在 loom 1.16-SNAPSHOT + `loom.officialMojangMappings()` 下编译报"包不存在"。
  Mojang official 1.21.11 把 `NativeImage` 留在 `com.mojang.blaze3d.platform` 老路径未迁移。
  `NativeImageBackedTexture` 在 Mojang official 1.21.11 中被合并进 `DynamicTexture`（具体 class，
  构造器 `(Supplier<String> nameSupplier, NativeImage pixels)`）。
  关联：`RedenImageCache.java:8, 219`

- **UI Bug —— 详情页滚动清空输入框**
  详情页 `mouseScrolled` 时不再调用 `refreshDownloadWidgets()`，修复滚动后尺寸输入框被清空的问题。
  关联：`RedenDetailsScreen.java:106-108`

- **UI Bug —— `plainSubstrByWidth` 空指针风险**
  搜索列表行中对 `machine.name()` 加 null 保护，避免个别机器名 null 导致崩溃。
  关联：`RedenSearchScreen.java:239`

- **UI Bug —— `machine.author()` 多层 null 安全**
  搜索列表和详情页的作者渲染路径均增加 null 安全判断，author null / username null / blank 统一显示 `-`。

- **UI Bug —— 详情页 Footer**
  新增底部 Footer，显示 `数据源: RedenMC | 作者: albertchen857&shiyi`（左对齐橙字 + 右对齐灰字）。

- **UI Bug —— 设置界面卡片文本拦按钮**
  旧版 (`v0.1.0` 之前) 副标题与按钮在卡片内同行 + 同 y 范围，完全重叠。
  重构后布局：标题占左上半区，按钮占右中线，副标题限制在卡片左侧 55% 宽度内裁剪（`plainSubstrByWidth`），
  互不干扰。卡片高度 52 → 36，间距 10 → 8，更紧凑。
  关联：`RedenConfigScreen.java`

- **UI Bug —— 搜索页底部 Footer 过高**
  `RedenSearchScreen.FOOTER_HEIGHT` 从 56 缩到 32，分页按钮 + 致谢行紧凑显示。
  分页按钮尺寸 28×20 → 24×14，间距 4 → 3。

### Removed
- **可配置快捷键** —— 移除 `RedenConfig.openSearchKey` 字段、`openSearchKey()` getter、
  `setOpenSearchKey()` setter，以及设置界面"打开快捷键"卡片。
  搜索入口固定为 O 键，简化配置。

### Changed
- **作者署名** `fabric.mod.json` 作者数组改为 `["Albertchen", "shiyi"]`。
- **作者署名** GUI Footer 使用 `albertchen857&shiyi`（英文 `&`，中英文语言文件均已处理）。
- **构建乱码** `build.gradle` 为 `JavaCompile` 任务显式设置 `encoding=UTF-8`，防止中文资源文件打包乱码。

### Files Changed
- `src/main/java/com/litematicasearcher/client/LitematicaSearcherClient.java` (O 键注册)
- `src/main/java/com/litematicasearcher/client/config/RedenConfig.java` (新增)
- `src/main/java/com/litematicasearcher/client/gui/RedenImageCache.java` (Mojang mapping + ABGR + JPEG fallback + PROXY)
- `src/main/java/com/litematicasearcher/client/gui/RedenConfigScreen.java` (新增)
- `src/main/java/com/litematicasearcher/client/gui/RedenSearchScreen.java` (footer 缩高 + 分页缩尺寸)
- `src/main/java/com/litematicasearcher/client/gui/RedenDetailsScreen.java` (滚动 null 修复)
- `src/main/resources/assets/litematicasearcher/lang/zh_cn.json`
- `src/main/resources/assets/litematicasearcher/lang/en_us.json`
- `build.gradle`
- `src/main/resources/fabric.mod.json`

---

## [v0.1.1] — 2026-06-14

### Fixed
- **UI Bug — 详情页滚动清空输入框**
  详情页 `mouseScrolled` 时不再调用 `refreshDownloadWidgets()`，修复滚动后尺寸输入框被清空的问题。
  关联：`RedenDetailsScreen.java:106-108`

- **UI Bug — `plainSubstrByWidth` 空指针风险**
  搜索列表行中对 `machine.name()` 加 null 保护，避免个别机器名 null 导致崩溃。
  关联：`RedenSearchScreen.java:239`

- **UI Bug — `machine.author()` 多层 null 安全**
  搜索列表和详情页的作者渲染路径均增加 null 安全判断，author null / username null / blank 统一显示 `-`。

- **UI Bug — 详情页 Footer**
  新增底部 Footer，显示 `数据源: RedenMC | 作者: albertchen857&shiyi`（左对齐橙字 + 右对齐灰字）。

### Changed
- **作者署名** `fabric.mod.json` 作者数组改为 `["Albertchen", "shiyi"]`。
- **作者署名** GUI Footer 使用 `albertchen857&shiyi`（英文 `&`，中英文语言文件均已处理）。
- **构建乱码** `build.gradle` 为 `JavaCompile` 任务显式设置 `encoding=UTF-8`，防止中文资源文件打包乱码。

### Files Changed
- `src/main/java/com/litematicasearcher/client/gui/RedenSearchScreen.java`
- `src/main/java/com/litematicasearcher/client/gui/RedenDetailsScreen.java`
- `src/main/resources/assets/litematicasearcher/lang/zh_cn.json`
- `src/main/resources/assets/litematicasearcher/lang/en_us.json`
- `build.gradle`
- `src/main/resources/fabric.mod.json`
