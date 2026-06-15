# LitematicaSearcher

RedenMC Litematica machine search and download integration for Minecraft 1.21.11 Fabric.

## Features

- Opens a RedenMC search screen from the Litematica main menu, Mod Menu config button, or the default `O` keybinding.
- Searches `redenmc.com` asynchronously without blocking the client render thread.
- Shows machine thumbnails, names, authors, upvotes, downloads, and the target `1.21.x` version marker.
- Opens a detail screen with image, description, author, upload date, versions, likes, downloads, and download controls.
- Downloads ordinary `LitematicaShare` attachments to `.minecraft/schematics/Downloads`.
- Supports generated `LitematicaGen` downloads with dynamic X/Y/Z size inputs and `min(n)`, `max(n)`, `mod(n1,n2)` validation.
- Includes English and Simplified Chinese translations.

## Requirements

- Minecraft `1.21.11`
- Fabric Loader `0.19.2` or newer
- Java `21`
- Fabric API
- MaLiLib
- Litematica
- Mod Menu is optional, but enables the config button entrypoint.

## Build

```powershell
.\gradlew.bat build
```

The built mod jar is written to:

```text
build/libs/litematicasearcher-<version>.jar
```

For a local client launch:

```powershell
.\gradlew.bat runClient
```

## Usage

- Press `O` in game to open RedenMC Search.
- Open Litematica's main menu and click `RedenMC Website Search`.
- With Mod Menu installed, open this mod's config screen.
- Use the top search field and `Search` button to refresh results.
- Click a machine row to open details.
- For ordinary machines, click `Download` next to the attachment.
- For generated machines, enter the required X/Y/Z sizes, then click `Generate and Download`.

Downloads are saved under:

```text
.minecraft/schematics/Downloads
```

Existing files are not overwritten; a numeric suffix is appended when needed.

## Git Workflow

Suggested commit after this feature stage:

```text
feat: add RedenMC search and download UI
```

Push the current feature branch with:

```powershell
git push -u origin feature/redenmc-searcher
```

## License

This project currently uses the license declared in `LICENSE`.
