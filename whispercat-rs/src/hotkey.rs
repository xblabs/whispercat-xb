use crate::error::Result;
use global_hotkey::{
    hotkey::{Code, HotKey, Modifiers},
    GlobalHotKeyEvent, GlobalHotKeyManager,
};

/// Hotkey manager for global keyboard shortcuts
pub struct HotkeyManager {
    manager: GlobalHotKeyManager,
    record_hotkey: Option<HotKey>,
}

impl HotkeyManager {
    /// Creates a new hotkey manager
    pub fn new() -> Result<Self> {
        let manager = GlobalHotKeyManager::new()
            .map_err(|e| crate::error::WhisperCatError::ConfigError(format!("Failed to create hotkey manager: {}", e)))?;

        Ok(Self {
            manager,
            record_hotkey: None,
        })
    }

    /// Registers a hotkey for record toggle
    ///
    /// # Arguments
    /// * `hotkey_str` - Hotkey string in format "Ctrl+Shift+R"
    pub fn register_record_toggle(&mut self, hotkey_str: &str) -> Result<()> {
        // Unregister existing hotkey if any
        if let Some(ref hotkey) = self.record_hotkey {
            self.manager.unregister(*hotkey)
                .map_err(|e| crate::error::WhisperCatError::ConfigError(format!("Failed to unregister hotkey: {}", e)))?;
            self.record_hotkey = None;
        }

        // Parse hotkey string
        let hotkey = Self::parse_hotkey(hotkey_str)?;

        // Register new hotkey
        self.manager.register(hotkey)
            .map_err(|e| crate::error::WhisperCatError::ConfigError(format!("Failed to register hotkey {}: {}", hotkey_str, e)))?;

        self.record_hotkey = Some(hotkey);

        tracing::info!("Registered record toggle hotkey: {}", hotkey_str);
        Ok(())
    }

    /// Checks for hotkey events (non-blocking)
    ///
    /// Returns `true` if record toggle was pressed
    pub fn check_events(&self) -> bool {
        if let Some(ref record_hotkey) = self.record_hotkey {
            let receiver = GlobalHotKeyEvent::receiver();
            while let Ok(event) = receiver.try_recv() {
                if event.id == record_hotkey.id() {
                    tracing::debug!("Record toggle hotkey pressed");
                    return true;
                }
            }
        }
        false
    }

    /// Parses hotkey string into HotKey
    ///
    /// Supported format: "Ctrl+Shift+R", "Alt+F1", etc.
    fn parse_hotkey(hotkey_str: &str) -> Result<HotKey> {
        let parts: Vec<&str> = hotkey_str.split('+').map(|s| s.trim()).collect();

        if parts.is_empty() {
            return Err(crate::error::WhisperCatError::ConfigError(
                "Empty hotkey string".to_string()
            ));
        }

        let mut modifiers = Modifiers::empty();
        let mut key_code = None;

        for part in &parts {
            match part.to_lowercase().as_str() {
                "ctrl" | "control" => modifiers |= Modifiers::CONTROL,
                "shift" => modifiers |= Modifiers::SHIFT,
                "alt" => modifiers |= Modifiers::ALT,
                "super" | "meta" | "win" | "cmd" => modifiers |= Modifiers::SUPER,
                // Letter keys
                "a" => key_code = Some(Code::KeyA),
                "b" => key_code = Some(Code::KeyB),
                "c" => key_code = Some(Code::KeyC),
                "d" => key_code = Some(Code::KeyD),
                "e" => key_code = Some(Code::KeyE),
                "f" => key_code = Some(Code::KeyF),
                "g" => key_code = Some(Code::KeyG),
                "h" => key_code = Some(Code::KeyH),
                "i" => key_code = Some(Code::KeyI),
                "j" => key_code = Some(Code::KeyJ),
                "k" => key_code = Some(Code::KeyK),
                "l" => key_code = Some(Code::KeyL),
                "m" => key_code = Some(Code::KeyM),
                "n" => key_code = Some(Code::KeyN),
                "o" => key_code = Some(Code::KeyO),
                "p" => key_code = Some(Code::KeyP),
                "q" => key_code = Some(Code::KeyQ),
                "r" => key_code = Some(Code::KeyR),
                "s" => key_code = Some(Code::KeyS),
                "t" => key_code = Some(Code::KeyT),
                "u" => key_code = Some(Code::KeyU),
                "v" => key_code = Some(Code::KeyV),
                "w" => key_code = Some(Code::KeyW),
                "x" => key_code = Some(Code::KeyX),
                "y" => key_code = Some(Code::KeyY),
                "z" => key_code = Some(Code::KeyZ),
                // Number keys
                "0" => key_code = Some(Code::Digit0),
                "1" => key_code = Some(Code::Digit1),
                "2" => key_code = Some(Code::Digit2),
                "3" => key_code = Some(Code::Digit3),
                "4" => key_code = Some(Code::Digit4),
                "5" => key_code = Some(Code::Digit5),
                "6" => key_code = Some(Code::Digit6),
                "7" => key_code = Some(Code::Digit7),
                "8" => key_code = Some(Code::Digit8),
                "9" => key_code = Some(Code::Digit9),
                // Function keys
                "f1" => key_code = Some(Code::F1),
                "f2" => key_code = Some(Code::F2),
                "f3" => key_code = Some(Code::F3),
                "f4" => key_code = Some(Code::F4),
                "f5" => key_code = Some(Code::F5),
                "f6" => key_code = Some(Code::F6),
                "f7" => key_code = Some(Code::F7),
                "f8" => key_code = Some(Code::F8),
                "f9" => key_code = Some(Code::F9),
                "f10" => key_code = Some(Code::F10),
                "f11" => key_code = Some(Code::F11),
                "f12" => key_code = Some(Code::F12),
                // Special keys
                "space" => key_code = Some(Code::Space),
                "enter" | "return" => key_code = Some(Code::Enter),
                "backspace" => key_code = Some(Code::Backspace),
                "tab" => key_code = Some(Code::Tab),
                "escape" | "esc" => key_code = Some(Code::Escape),
                _ => {
                    return Err(crate::error::WhisperCatError::ConfigError(
                        format!("Unknown key: {}", part)
                    ));
                }
            }
        }

        let code = key_code.ok_or_else(|| {
            crate::error::WhisperCatError::ConfigError(
                format!("No key code found in hotkey: {}", hotkey_str)
            )
        })?;

        Ok(HotKey::new(Some(modifiers), code))
    }
}

impl Drop for HotkeyManager {
    fn drop(&mut self) {
        // Unregister all hotkeys on drop
        if let Some(ref hotkey) = self.record_hotkey {
            self.manager.unregister(*hotkey).ok();
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_parse_hotkey() {
        let hotkey = HotkeyManager::parse_hotkey("Ctrl+Shift+R").unwrap();
        assert!(hotkey.mods().contains(Modifiers::CONTROL));
        assert!(hotkey.mods().contains(Modifiers::SHIFT));
    }

    #[test]
    fn test_parse_single_key() {
        let hotkey = HotkeyManager::parse_hotkey("F1").unwrap();
        assert_eq!(hotkey.key(), Code::F1);
    }

    #[test]
    fn test_parse_alt_key() {
        let hotkey = HotkeyManager::parse_hotkey("Alt+F4").unwrap();
        assert!(hotkey.mods().contains(Modifiers::ALT));
        assert_eq!(hotkey.key(), Code::F4);
    }
}
