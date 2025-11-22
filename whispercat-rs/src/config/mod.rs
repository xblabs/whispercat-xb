use crate::error::Result;
use serde::{Deserialize, Serialize};
use std::path::PathBuf;

#[derive(Clone, Debug, Serialize, Deserialize)]
pub struct Config {
    pub whisper: WhisperConfig,
    pub audio: AudioConfig,
    pub silence_removal: SilenceRemovalConfig,
    pub hotkeys: HotkeyConfig,
    pub ui: UiConfig,
}

impl Default for Config {
    fn default() -> Self {
        Self {
            whisper: WhisperConfig::default(),
            audio: AudioConfig::default(),
            silence_removal: SilenceRemovalConfig::default(),
            hotkeys: HotkeyConfig::default(),
            ui: UiConfig::default(),
        }
    }
}

impl Config {
    pub fn load() -> Result<Self> {
        let path = Self::default_path();

        if !path.exists() {
            tracing::info!("Config file not found, creating default at {:?}", path);
            let config = Self::default();
            config.save()?;
            return Ok(config);
        }

        let content = std::fs::read_to_string(&path)?;
        let config: Config = toml::from_str(&content)?;
        tracing::info!("Config loaded from {:?}", path);
        Ok(config)
    }

    pub fn save(&self) -> Result<()> {
        let path = Self::default_path();

        if let Some(parent) = path.parent() {
            std::fs::create_dir_all(parent)?;
        }

        let content = toml::to_string_pretty(self)?;
        std::fs::write(&path, content)?;

        tracing::info!("Config saved to {:?}", path);
        Ok(())
    }

    pub fn default_path() -> PathBuf {
        dirs::config_dir()
            .unwrap_or_else(|| PathBuf::from("."))
            .join("whispercat")
            .join("config.toml")
    }
}

#[derive(Clone, Debug, Serialize, Deserialize)]
pub struct WhisperConfig {
    pub api_key: String,
    pub model: String,
    pub provider: String,
    pub faster_whisper_url: Option<String>,
    pub openwebui_url: Option<String>,
    pub openwebui_api_key: Option<String>,
}

impl Default for WhisperConfig {
    fn default() -> Self {
        Self {
            api_key: String::new(),
            model: "whisper-1".to_string(),
            provider: "OpenAI".to_string(),
            faster_whisper_url: None,
            openwebui_url: None,
            openwebui_api_key: None,
        }
    }
}

#[derive(Clone, Debug, Serialize, Deserialize)]
pub struct AudioConfig {
    pub sample_rate: u32,
    pub channels: u16,
    pub device_name: Option<String>,
}

impl Default for AudioConfig {
    fn default() -> Self {
        Self {
            sample_rate: 16000,
            channels: 1,
            device_name: None,
        }
    }
}

#[derive(Clone, Debug, Serialize, Deserialize)]
pub struct SilenceRemovalConfig {
    pub enabled: bool,
    pub threshold: f32,
    pub min_duration_ms: u32,
    /// Only apply silence removal if recording is longer than this (in seconds)
    pub min_recording_duration_sec: u32,
}

impl Default for SilenceRemovalConfig {
    fn default() -> Self {
        Self {
            enabled: true,
            threshold: 0.01,
            min_duration_ms: 1500,
            min_recording_duration_sec: 10, // Only remove silence for recordings >= 10 seconds
        }
    }
}

#[derive(Clone, Debug, Serialize, Deserialize)]
pub struct HotkeyConfig {
    pub record_toggle: String,
}

impl Default for HotkeyConfig {
    fn default() -> Self {
        Self {
            record_toggle: "Ctrl+Shift+R".to_string(),
        }
    }
}

#[derive(Clone, Debug, Serialize, Deserialize)]
pub struct UiConfig {
    pub theme: String,
    pub auto_paste: bool,
}

impl Default for UiConfig {
    fn default() -> Self {
        Self {
            theme: "Dark".to_string(),
            auto_paste: false,
        }
    }
}
