/// System tray events that the app should handle
#[derive(Debug, Clone)]
pub enum TrayEvent {
    Show,
    Hide,
    ToggleRecording,
    Exit,
}

/// System tray manager for WhisperCat
///
/// NOTE: This is currently a stub implementation.
/// Full system tray support requires the tray-icon crate and platform-specific
/// system libraries (GTK3 on Linux, native APIs on Windows/macOS).
///
/// To enable system tray:
/// 1. Uncomment the tray-icon dependency in Cargo.toml
/// 2. Install required system libraries (if on Linux: libgtk-3-dev, libappindicator3-dev)
/// 3. Replace this stub implementation with the full tray-icon integration
pub struct TrayManager {
    // Stub implementation - no actual tray
}

impl TrayManager {
    pub fn new() -> Result<Self, Box<dyn std::error::Error>> {
        tracing::info!("System tray is disabled (stub implementation)");
        tracing::info!("To enable: install system libraries and uncomment tray-icon dependency");
        Ok(Self {})
    }

    /// Check for tray events (non-blocking)
    /// Returns None in stub implementation
    pub fn check_events(&self) -> Option<TrayEvent> {
        None
    }

    /// Update menu item states based on app state
    /// No-op in stub implementation
    pub fn update_recording_state(&mut self, _is_recording: bool) {
        // Stub - no actual tray to update
    }

    /// Update menu item states based on window visibility
    /// No-op in stub implementation
    pub fn update_visibility_state(&mut self, _is_visible: bool) {
        // Stub - no actual tray to update
    }
}

/*
 * Below is the full implementation that can be enabled when tray-icon is available
 * and system dependencies are installed:
 *
 * Uncomment this code and comment out the stub above to enable full tray support.
 *

#[cfg(feature = "tray")]
use tray_icon::{
    menu::{Menu, MenuEvent, MenuItem},
    TrayIcon, TrayIconBuilder,
};

use std::sync::mpsc;

#[cfg(feature = "tray")]
pub struct TrayManager {
    _tray_icon: TrayIcon,
    menu_event_rx: mpsc::Receiver<TrayEvent>,
    show_item: MenuItem,
    hide_item: MenuItem,
    toggle_recording_item: MenuItem,
    _exit_item: MenuItem,
}

#[cfg(feature = "tray")]
impl TrayManager {
    pub fn new() -> Result<Self, Box<dyn std::error::Error>> {
        let show_item = MenuItem::new("Show WhisperCat", true, None);
        let hide_item = MenuItem::new("Hide WhisperCat", false, None);
        let toggle_recording_item = MenuItem::new("Start Recording", true, None);
        let separator = MenuItem::new("", false, None);
        let exit_item = MenuItem::new("Exit", true, None);

        let menu = Menu::new();
        menu.append(&show_item)?;
        menu.append(&hide_item)?;
        menu.append(&toggle_recording_item)?;
        menu.append(&separator)?;
        menu.append(&exit_item)?;

        let icon = Self::create_icon()?;
        let tray_icon = TrayIconBuilder::new()
            .with_menu(Box::new(menu))
            .with_tooltip("WhisperCat - AI Transcription")
            .with_icon(icon)
            .build()?;

        let (tx, rx) = mpsc::channel();

        let show_item_clone = show_item.clone();
        let hide_item_clone = hide_item.clone();
        let toggle_recording_item_clone = toggle_recording_item.clone();
        let exit_item_clone = exit_item.clone();

        let menu_channel = MenuEvent::receiver();
        std::thread::spawn(move || {
            loop {
                if let Ok(event) = menu_channel.recv() {
                    if event.id == show_item_clone.id() {
                        tx.send(TrayEvent::Show).ok();
                    } else if event.id == hide_item_clone.id() {
                        tx.send(TrayEvent::Hide).ok();
                    } else if event.id == toggle_recording_item_clone.id() {
                        tx.send(TrayEvent::ToggleRecording).ok();
                    } else if event.id == exit_item_clone.id() {
                        tx.send(TrayEvent::Exit).ok();
                    }
                }
            }
        });

        Ok(Self {
            _tray_icon: tray_icon,
            menu_event_rx: rx,
            show_item,
            hide_item,
            toggle_recording_item,
            _exit_item: exit_item,
        })
    }

    pub fn check_events(&self) -> Option<TrayEvent> {
        self.menu_event_rx.try_recv().ok()
    }

    pub fn update_recording_state(&mut self, is_recording: bool) {
        if is_recording {
            self.toggle_recording_item.set_text("Stop Recording");
        } else {
            self.toggle_recording_item.set_text("Start Recording");
        }
    }

    pub fn update_visibility_state(&mut self, is_visible: bool) {
        self.show_item.set_enabled(!is_visible);
        self.hide_item.set_enabled(is_visible);
    }

    #[cfg(feature = "tray")]
    fn create_icon() -> Result<tray_icon::Icon, Box<dyn std::error::Error>> {
        let size = 32;
        let mut rgba = vec![0u8; size * size * 4];

        for y in 0..size {
            for x in 0..size {
                let idx = (y * size + x) * 4;

                let cx = size as f32 / 2.0;
                let cy = size as f32 / 2.0;
                let dx = x as f32 - cx;
                let dy = y as f32 - cy;
                let distance = (dx * dx + dy * dy).sqrt();

                if distance < 10.0 {
                    rgba[idx] = 76;
                    rgba[idx + 1] = 175;
                    rgba[idx + 2] = 80;
                    rgba[idx + 3] = 255;
                } else if y > 20 && y < 28 && x > 14 && x < 18 {
                    rgba[idx] = 76;
                    rgba[idx + 1] = 175;
                    rgba[idx + 2] = 80;
                    rgba[idx + 3] = 255;
                } else if y > 26 && y < 30 && x > 10 && x < 22 {
                    rgba[idx] = 76;
                    rgba[idx + 1] = 175;
                    rgba[idx + 2] = 80;
                    rgba[idx + 3] = 255;
                } else {
                    rgba[idx + 3] = 0;
                }
            }
        }

        tray_icon::Icon::from_rgba(rgba, size, size)
            .map_err(|e| format!("Failed to create icon: {}", e).into())
    }
}

*/
