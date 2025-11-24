# WhisperCat Windows Installation Guide

## Overview

WhisperCat provides multiple ways to package and distribute the application for Windows 10/11 users.

## Settings Persistence ✓

**Your settings are automatically saved!**
- Location: `%APPDATA%\WhisperCat\config.properties`
- Includes: API keys, hotkeys, microphone preferences, post-processing pipelines
- Settings persist across application restarts and updates

## Distribution Options

### Option 1: Full Windows Installer (Recommended)

Creates a professional Windows installer (.exe) with:
- Installation wizard with directory chooser
- Start Menu shortcuts
- Uninstaller in Add/Remove Programs
- Desktop icon (optional)

**Build locally:**
```batch
# Using batch file
build-windows-installer.bat

# OR using PowerShell (recommended for Windows 10/11)
powershell -ExecutionPolicy Bypass -File build-installer.ps1
```

**Requirements:**
- JDK 17+ (includes jpackage)
- Maven 3.6+

**Output:** `installer-output\WhisperCat-1.6.0.exe`

---

### Option 2: Portable EXE (Taskbar-Friendly) ⭐ NEW

Creates a standalone `.exe` launcher that's perfect for pinning to taskbar:
- Single .exe file (wraps the JAR)
- Can be pinned to Windows taskbar
- No installation needed
- Requires users to have Java 11+ installed

**Build:**
```batch
# Using batch file
create-exe-launcher.bat

# OR using PowerShell (recommended)
powershell -ExecutionPolicy Bypass -File create-portable-exe.ps1
```

**Output:** `WhisperCat.exe`

**Features:**
- ✅ Pin to taskbar (right-click → Pin to taskbar)
- ✅ Proper Windows icon
- ✅ Single instance (won't open multiple copies)
- ✅ Version info in file properties
- ✅ Settings persist to `%APPDATA%\WhisperCat\`

---

### Option 3: Portable ZIP Package (Legacy)

Creates a ZIP file with JAR + launcher script:
- No installation needed
- Just extract and run
- Requires users to have Java 11+ installed

**Build:**
```batch
create-portable-package.bat
```

**Output:** `WhisperCat-Portable.zip`

**Contents:**
- `WhisperCat.jar` - Application
- `WhisperCat.bat` - Launcher script
- `README.txt` - Instructions

**Note:** Use Option 2 (Portable EXE) for better taskbar integration!

---

### Option 4: Simple Launcher Script

For development or quick testing:

**Use existing JAR:**
```batch
run-whispercat.bat
```

This launches the JAR directly from the `target/` directory.

---

### Option 5: Bundled JRE (No Java Installation Required)

**Already available via GitHub Actions!**

The CI/CD automatically creates `WhisperCat-Windows-with-jre-non-installer.zip` which includes:
- Application JAR
- Bundled Zulu JRE 17
- Launch4j executable wrapper
- No Java installation required from users

**To build locally**, you would need:
1. Download Zulu JRE 17 for Windows
2. Use Launch4j to create .exe wrapper
3. Package everything together

(Complex - recommend using GitHub Actions for this)

---

## Quick Start for Users

### If you build the full installer:
1. Run `WhisperCat-1.6.0.exe`
2. Follow installation wizard
3. Launch from Start Menu
4. Settings auto-save to `%APPDATA%\WhisperCat\`

### If you provide the portable EXE:
1. Download `WhisperCat.exe`
2. Double-click to run
3. Right-click → "Pin to taskbar" (stays accessible!)
4. Settings auto-save to `%APPDATA%\WhisperCat\`
5. Requires Java 11+ installed

### If you provide the portable ZIP package:
1. Extract `WhisperCat-Portable.zip`
2. Double-click `WhisperCat.bat`
3. Settings auto-save to `%APPDATA%\WhisperCat\`

---

## How Settings Work

### ConfigManager (src/main/java/org/whispercat/ConfigManager.java)

**Storage Format:** Properties file
```properties
keyCombination=Ctrl+Alt+R
apiKey=sk-...
selectedMicrophone=Default
audioBitrate=20000
finishSound=true
whisperServer=OpenAI
postProcessingOnStartup=false
postProcessingData=[{"uuid":"...","name":"Pipeline 1",...}]
```

**What's saved:**
- Global hotkey configuration
- API credentials (OpenAI, FasterWhisper, OpenWebUI)
- Audio settings (microphone, bitrate)
- Post-processing pipelines (complete JSON serialization)
- UI preferences (finish sounds, startup behavior)

**Behavior:**
- Loaded on application startup
- Saved whenever settings are changed
- Survives application updates (as long as install location respects AppData)

---

## CI/CD Auto-Build

**Your GitHub Actions already build ALL these installers automatically!**

On every release tag (`v*`), the following are created:
- `WhisperCat-Windows-Installer.exe` (jpackage)
- `WhisperCat-Windows-with-jre-non-installer.zip` (Launch4j + JRE)
- `whispercat.deb` (Linux Debian package)
- `WhisperCat.AppImage` (Linux portable)
- `WhisperCat-macOS-Experimental.dmg` (macOS)

**To trigger a release:**
```bash
git tag v1.6.1
git push origin v1.6.1
```

All installers will be available under GitHub Releases within ~10-15 minutes.

---

## Troubleshooting

### "jpackage: command not found"
- Install JDK 17+ (not just JRE)
- Download from: https://adoptium.net/

### "Java version too old"
- WhisperCat requires Java 11+
- Installer builds require JDK 16+ (for jpackage)

### Settings not persisting
- Check `%APPDATA%\WhisperCat\` directory exists
- Look for `config.properties` file
- Check application has write permissions

### Installer fails to create
- Ensure no antivirus blocking jpackage
- Run PowerShell as Administrator (if needed)
- Check `whispercat.ico` exists in `src/main/resources/`

---

## Recommended Workflow

**For Development:**
```batch
mvn clean package
run-whispercat.bat
```

**For Distribution (Local):**
```powershell
powershell -ExecutionPolicy Bypass -File build-installer.ps1
```

**For Distribution (Automated):**
```bash
# Tag and push (triggers GitHub Actions)
git tag v1.6.1
git push origin v1.6.1

# Download from GitHub Releases after ~10 min
```

---

## Additional Resources

- Main repo: https://github.com/xblabs/whispercat-xb
- GitHub Actions workflows: `.github/workflows/build-workflow.yml`
- Build configuration: `pom.xml` (Maven)
- Settings manager: `src/main/java/org/whispercat/ConfigManager.java`
