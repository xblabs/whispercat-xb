use thiserror::Error;

#[derive(Error, Debug)]
pub enum WhisperCatError {
    #[error("Audio recording error: {0}")]
    RecordingError(String),

    #[error("Transcription API error: {status} - {message}")]
    ApiError { status: u16, message: String },

    #[error("File too large: {size:.2} MB (limit: 25 MB)")]
    FileSizeError { size: f64 },

    #[error("Configuration error: {0}")]
    ConfigError(String),

    #[error("Audio processing error: {0}")]
    AudioProcessingError(String),

    #[error("Pipeline execution error: {0}")]
    PipelineError(String),

    #[error("IO error: {0}")]
    IoError(#[from] std::io::Error),

    #[error("WAV format error: {0}")]
    WavError(#[from] hound::Error),

    #[error("Audio device error: {0}")]
    AudioDeviceError(String),

    #[error("JSON error: {0}")]
    JsonError(#[from] serde_json::Error),

    #[error("TOML error: {0}")]
    TomlError(#[from] toml::de::Error),

    #[error("TOML serialization error: {0}")]
    TomlSerError(#[from] toml::ser::Error),

    #[error("HTTP error: {0}")]
    HttpError(#[from] reqwest::Error),

    #[error("Regex error: {0}")]
    RegexError(#[from] regex::Error),
}

impl From<cpal::DevicesError> for WhisperCatError {
    fn from(err: cpal::DevicesError) -> Self {
        WhisperCatError::AudioDeviceError(format!("Device enumeration failed: {}", err))
    }
}

impl From<cpal::BuildStreamError> for WhisperCatError {
    fn from(err: cpal::BuildStreamError) -> Self {
        WhisperCatError::RecordingError(format!("Failed to build audio stream: {}", err))
    }
}

impl From<cpal::PlayStreamError> for WhisperCatError {
    fn from(err: cpal::PlayStreamError) -> Self {
        WhisperCatError::RecordingError(format!("Failed to start audio stream: {}", err))
    }
}

impl From<cpal::DefaultStreamConfigError> for WhisperCatError {
    fn from(err: cpal::DefaultStreamConfigError) -> Self {
        WhisperCatError::AudioDeviceError(format!("Failed to get default config: {}", err))
    }
}

pub type Result<T> = std::result::Result<T, WhisperCatError>;
