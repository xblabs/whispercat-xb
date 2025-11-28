/// Keyboard recorder for capturing hotkey combinations
/// NOTE: This is currently a stub implementation.
///
/// To enable full functionality:
/// 1. Install system libraries:
///    - Linux: sudo apt-get install libxi-dev libxtst-dev
///    - macOS: No additional libraries needed
///    - Windows: No additional libraries needed
///
/// 2. In Cargo.toml, uncomment the rdev dependency:
///    rdev = "0.5"
///
/// 3. Replace the stub implementation below with the full implementation
///    provided in the comments at the end of this file.

pub struct KeyboardRecorder {
    // Stub - no actual recording
}

impl KeyboardRecorder {
    pub fn new() -> Self {
        tracing::info!("Keyboard recording is disabled (stub implementation)");
        tracing::info!("To enable: install system libraries and uncomment rdev dependency in Cargo.toml");
        Self {}
    }

    /// Start recording keyboard input (stub)
    pub fn start_recording(&mut self) {
        tracing::warn!("Keyboard recording not available - using stub implementation");
    }

    /// Stop recording and return empty string (stub)
    pub fn stop_recording(&mut self) -> String {
        String::new()
    }

    /// Always returns false in stub mode
    pub fn is_recording(&self) -> bool {
        false
    }
}

/*
================================================================================
FULL IMPLEMENTATION - Uncomment when rdev dependency is enabled
================================================================================

use rdev::{Event, EventType, Key};
use std::sync::{Arc, Mutex};
use std::thread;
use std::collections::HashSet;

pub struct KeyboardRecorder {
    pressed_keys: Arc<Mutex<HashSet<Key>>>,
    recording: Arc<Mutex<bool>>,
    listener_handle: Option<thread::JoinHandle<()>>,
}

impl KeyboardRecorder {
    pub fn new() -> Self {
        Self {
            pressed_keys: Arc::new(Mutex::new(HashSet::new())),
            recording: Arc::new(Mutex::new(false)),
            listener_handle: None,
        }
    }

    pub fn start_recording(&mut self) {
        *self.recording.lock().unwrap() = true;
        self.pressed_keys.lock().unwrap().clear();

        let pressed_keys = Arc::clone(&self.pressed_keys);
        let recording = Arc::clone(&self.recording);

        let handle = thread::spawn(move || {
            let callback = move |event: Event| {
                if !*recording.lock().unwrap() {
                    return;
                }

                match event.event_type {
                    EventType::KeyPress(key) => {
                        pressed_keys.lock().unwrap().insert(key);
                    }
                    EventType::KeyRelease(key) => {
                        // Don't remove - capture full combination
                    }
                    _ => {}
                }
            };

            if let Err(error) = rdev::listen(callback) {
                tracing::error!("Error listening to keyboard events: {:?}", error);
            }
        });

        self.listener_handle = Some(handle);
    }

    pub fn stop_recording(&mut self) -> String {
        *self.recording.lock().unwrap() = false;
        thread::sleep(std::time::Duration::from_millis(100));

        let keys = self.pressed_keys.lock().unwrap();
        let hotkey_string = Self::format_hotkey(&keys);

        drop(keys);
        self.pressed_keys.lock().unwrap().clear();

        hotkey_string
    }

    fn format_hotkey(keys: &HashSet<Key>) -> String {
        if keys.is_empty() {
            return String::new();
        }

        let mut modifiers = Vec::new();
        let mut regular_keys = Vec::new();

        for key in keys {
            match key {
                Key::ControlLeft | Key::ControlRight => {
                    if !modifiers.contains(&"Ctrl") {
                        modifiers.push("Ctrl");
                    }
                }
                Key::ShiftLeft | Key::ShiftRight => {
                    if !modifiers.contains(&"Shift") {
                        modifiers.push("Shift");
                    }
                }
                Key::Alt | Key::AltGr => {
                    if !modifiers.contains(&"Alt") {
                        modifiers.push("Alt");
                    }
                }
                Key::MetaLeft | Key::MetaRight => {
                    if !modifiers.contains(&"Super") {
                        modifiers.push("Super");
                    }
                }
                _ => {
                    if let Some(key_name) = Self::key_to_string(key) {
                        regular_keys.push(key_name);
                    }
                }
            }
        }

        let mut parts = Vec::new();

        if modifiers.contains(&"Ctrl") { parts.push("Ctrl"); }
        if modifiers.contains(&"Shift") { parts.push("Shift"); }
        if modifiers.contains(&"Alt") { parts.push("Alt"); }
        if modifiers.contains(&"Super") { parts.push("Super"); }

        regular_keys.sort();
        parts.extend(regular_keys);

        parts.join("+")
    }

    fn key_to_string(key: &Key) -> Option<String> {
        match key {
            Key::KeyA => Some("A".to_string()),
            Key::KeyB => Some("B".to_string()),
            Key::KeyC => Some("C".to_string()),
            Key::KeyD => Some("D".to_string()),
            Key::KeyE => Some("E".to_string()),
            Key::KeyF => Some("F".to_string()),
            Key::KeyG => Some("G".to_string()),
            Key::KeyH => Some("H".to_string()),
            Key::KeyI => Some("I".to_string()),
            Key::KeyJ => Some("J".to_string()),
            Key::KeyK => Some("K".to_string()),
            Key::KeyL => Some("L".to_string()),
            Key::KeyM => Some("M".to_string()),
            Key::KeyN => Some("N".to_string()),
            Key::KeyO => Some("O".to_string()),
            Key::KeyP => Some("P".to_string()),
            Key::KeyQ => Some("Q".to_string()),
            Key::KeyR => Some("R".to_string()),
            Key::KeyS => Some("S".to_string()),
            Key::KeyT => Some("T".to_string()),
            Key::KeyU => Some("U".to_string()),
            Key::KeyV => Some("V".to_string()),
            Key::KeyW => Some("W".to_string()),
            Key::KeyX => Some("X".to_string()),
            Key::KeyY => Some("Y".to_string()),
            Key::KeyZ => Some("Z".to_string()),
            Key::Num0 => Some("0".to_string()),
            Key::Num1 => Some("1".to_string()),
            Key::Num2 => Some("2".to_string()),
            Key::Num3 => Some("3".to_string()),
            Key::Num4 => Some("4".to_string()),
            Key::Num5 => Some("5".to_string()),
            Key::Num6 => Some("6".to_string()),
            Key::Num7 => Some("7".to_string()),
            Key::Num8 => Some("8".to_string()),
            Key::Num9 => Some("9".to_string()),
            Key::F1 => Some("F1".to_string()),
            Key::F2 => Some("F2".to_string()),
            Key::F3 => Some("F3".to_string()),
            Key::F4 => Some("F4".to_string()),
            Key::F5 => Some("F5".to_string()),
            Key::F6 => Some("F6".to_string()),
            Key::F7 => Some("F7".to_string()),
            Key::F8 => Some("F8".to_string()),
            Key::F9 => Some("F9".to_string()),
            Key::F10 => Some("F10".to_string()),
            Key::F11 => Some("F11".to_string()),
            Key::F12 => Some("F12".to_string()),
            Key::Space => Some("Space".to_string()),
            Key::Return => Some("Enter".to_string()),
            Key::Escape => Some("Escape".to_string()),
            Key::Tab => Some("Tab".to_string()),
            Key::Backspace => Some("Backspace".to_string()),
            Key::Delete => Some("Delete".to_string()),
            Key::Insert => Some("Insert".to_string()),
            Key::Home => Some("Home".to_string()),
            Key::End => Some("End".to_string()),
            Key::PageUp => Some("PageUp".to_string()),
            Key::PageDown => Some("PageDown".to_string()),
            Key::UpArrow => Some("Up".to_string()),
            Key::DownArrow => Some("Down".to_string()),
            Key::LeftArrow => Some("Left".to_string()),
            Key::RightArrow => Some("Right".to_string()),
            Key::ControlLeft | Key::ControlRight => None,
            Key::ShiftLeft | Key::ShiftRight => None,
            Key::Alt | Key::AltGr => None,
            Key::MetaLeft | Key::MetaRight => None,
            _ => None,
        }
    }

    pub fn is_recording(&self) -> bool {
        *self.recording.lock().unwrap()
    }
}

impl Drop for KeyboardRecorder {
    fn drop(&mut self) {
        *self.recording.lock().unwrap() = false;
    }
}

*/
