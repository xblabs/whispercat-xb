use crate::error::Result;
use std::path::{Path, PathBuf};
use std::process::Command;

/// Maximum file size for OpenAI API (24 MB, leaving buffer under 25MB limit)
pub const MAX_FILE_SIZE_MB: f64 = 24.0;

/// Audio file compressor using ffmpeg
pub struct AudioCompressor;

impl AudioCompressor {
    /// Compresses audio file to MP3 format using ffmpeg to reduce file size.
    /// This is much more effective than downsampling - can reduce size by 10x or more.
    ///
    /// Parameters used:
    /// - `-codec:a libmp3lame`: Use LAME MP3 encoder
    /// - `-q:a 4`: VBR quality (0-9, where 0 is best, 9 is worst; 4 is good for speech ~140kbps)
    /// - `-ac 1`: Mono audio (speech doesn't need stereo, halves file size)
    /// - `-ar 16000`: 16kHz sample rate (good for speech recognition)
    ///
    /// # Arguments
    /// * `input_path` - Path to the original audio file
    ///
    /// # Returns
    /// * `Ok(PathBuf)` - Path to the compressed MP3 file
    /// * `Err` - If compression fails
    pub fn compress_to_mp3(input_path: &Path) -> Result<PathBuf> {
        let input_size_mb = Self::file_size_mb(input_path)?;

        tracing::info!(
            "Compressing audio file to MP3: {:?} (size: {:.2} MB)",
            input_path.file_name().unwrap_or_default(),
            input_size_mb
        );

        // Create temporary MP3 file
        let output_path = std::env::temp_dir().join(format!(
            "whispercat_compressed_{}.mp3",
            chrono::Utc::now().timestamp()
        ));

        // Use ffmpeg to convert to MP3 with good compression
        let output = Command::new("ffmpeg")
            .arg("-y") // Overwrite output file
            .arg("-i")
            .arg(input_path)
            .arg("-codec:a")
            .arg("libmp3lame")
            .arg("-q:a")
            .arg("4") // VBR quality 4 (~140kbps, good for speech)
            .arg("-ac")
            .arg("1") // Mono audio
            .arg("-ar")
            .arg("16000") // 16kHz sample rate
            .arg(&output_path)
            .output()?;

        if !output.status.success() {
            let stderr = String::from_utf8_lossy(&output.stderr);
            tracing::error!("ffmpeg compression failed: {}", stderr);
            return Err(crate::error::WhisperCatError::AudioProcessingError(format!(
                "ffmpeg compression failed: {}",
                stderr
            )));
        }

        let output_size_mb = Self::file_size_mb(&output_path)?;
        let reduction_percent = ((input_size_mb - output_size_mb) / input_size_mb) * 100.0;

        tracing::info!(
            "Compressed audio file created: {:?} (size: {:.2} MB, {:.1}% reduction)",
            output_path.file_name().unwrap_or_default(),
            output_size_mb,
            reduction_percent
        );

        // Check if MP3 is still too large
        if output_size_mb > MAX_FILE_SIZE_MB {
            tracing::warn!(
                "MP3 file still exceeds size limit ({:.2} MB). File may be too long for OpenAI.",
                output_size_mb
            );
        }

        Ok(output_path)
    }

    /// Checks if ffmpeg is available on the system
    pub fn is_ffmpeg_available() -> bool {
        Command::new("ffmpeg")
            .arg("-version")
            .output()
            .map(|output| output.status.success())
            .unwrap_or(false)
    }

    /// Gets file size in megabytes
    pub fn file_size_mb(path: &Path) -> Result<f64> {
        let metadata = std::fs::metadata(path)?;
        Ok(metadata.len() as f64 / (1024.0 * 1024.0))
    }

    /// Checks if a file needs compression based on size
    pub fn needs_compression(path: &Path) -> Result<bool> {
        let size_mb = Self::file_size_mb(path)?;
        Ok(size_mb > MAX_FILE_SIZE_MB)
    }

    /// Compresses audio file if it exceeds the size limit
    ///
    /// # Arguments
    /// * `input_path` - Path to the original audio file
    ///
    /// # Returns
    /// * `Ok(Some(PathBuf))` - Path to compressed file if compression was needed and successful
    /// * `Ok(None)` - If file doesn't need compression
    /// * `Err` - If compression was needed but failed
    pub fn compress_if_needed(input_path: &Path) -> Result<Option<PathBuf>> {
        if !Self::needs_compression(input_path)? {
            return Ok(None);
        }

        let size_mb = Self::file_size_mb(input_path)?;
        tracing::warn!(
            "Audio file size ({:.2} MB) exceeds OpenAI limit ({:.0} MB). Compressing...",
            size_mb,
            MAX_FILE_SIZE_MB
        );

        // Check if ffmpeg is available
        if !Self::is_ffmpeg_available() {
            tracing::error!("ffmpeg is not available. Cannot compress audio file.");
            return Err(crate::error::WhisperCatError::AudioProcessingError(
                "ffmpeg is not available for audio compression. Please install ffmpeg.".to_string()
            ));
        }

        let compressed_path = Self::compress_to_mp3(input_path)?;
        Ok(Some(compressed_path))
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_ffmpeg_availability() {
        // Just test that the check doesn't crash
        let available = AudioCompressor::is_ffmpeg_available();
        println!("ffmpeg available: {}", available);
    }

    #[test]
    fn test_file_size_check() {
        // Create a small test file
        let test_path = std::env::temp_dir().join("test_size.txt");
        std::fs::write(&test_path, b"hello world").unwrap();

        let needs_compression = AudioCompressor::needs_compression(&test_path).unwrap();
        assert!(!needs_compression);

        std::fs::remove_file(test_path).ok();
    }
}
