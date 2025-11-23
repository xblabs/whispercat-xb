use crate::error::Result;
use arboard::Clipboard;

/// Auto-paste manager for automatically pasting transcription results
pub struct AutoPaster {
    clipboard: Clipboard,
}

impl AutoPaster {
    /// Creates a new auto-paster
    pub fn new() -> Result<Self> {
        let clipboard = Clipboard::new()
            .map_err(|e| crate::error::WhisperCatError::ConfigError(format!("Failed to access clipboard: {}", e)))?;

        Ok(Self { clipboard })
    }

    /// Copies text to clipboard automatically
    ///
    /// # Arguments
    /// * `text` - The text to copy
    /// * `enabled` - If true, copies to clipboard
    ///
    /// Note: This copies to clipboard. User can manually paste with Ctrl+V (Cmd+V on macOS).
    /// Keyboard simulation would require additional system dependencies.
    pub fn copy_and_paste(&mut self, text: &str, enabled: bool) -> Result<()> {
        if !enabled {
            return Ok(());
        }

        // Copy to clipboard
        self.clipboard.set_text(text)
            .map_err(|e| crate::error::WhisperCatError::ConfigError(format!("Failed to set clipboard text: {}", e)))?;

        tracing::info!("Copied {} characters to clipboard - ready to paste", text.len());

        Ok(())
    }

    /// Just copies text to clipboard without checking flag
    pub fn copy_only(&mut self, text: &str) -> Result<()> {
        self.copy_and_paste(text, true)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    #[ignore] // Skip in headless environments (requires X11/Wayland/display server)
    fn test_clipboard_copy() {
        let mut paster = AutoPaster::new().unwrap();
        let test_text = "Test transcription result";

        // Just test copying
        paster.copy_only(test_text).unwrap();

        // Verify it was copied (read back from clipboard)
        let clipboard_text = paster.clipboard.get_text().unwrap();
        assert_eq!(clipboard_text, test_text);
    }
}
