pub mod audio;
pub mod autopaste;
pub mod config;
pub mod error;
pub mod hotkey;
pub mod keyboard_recorder;
pub mod logger;
pub mod pipeline;
pub mod transcription;

pub use error::{Result, WhisperCatError};
