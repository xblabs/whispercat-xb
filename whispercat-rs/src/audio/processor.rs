use crate::audio::buffer::AudioBuffer;
use crate::error::Result;
use std::time::Duration;

pub struct SilenceRemover {
    threshold: f32,
    min_duration_ms: u32,
    window_size_ms: u32,
}

impl SilenceRemover {
    pub fn new(threshold: f32, min_duration_ms: u32) -> Self {
        Self {
            threshold,
            min_duration_ms,
            window_size_ms: 100, // Fixed at 100ms per Java implementation
        }
    }

    pub fn remove_silence(&self, audio: &AudioBuffer) -> Result<(AudioBuffer, SilenceAnalysis)> {
        // Step 1: Calculate RMS for each window
        let window_size_samples =
            (audio.sample_rate as f64 * self.window_size_ms as f64 / 1000.0) as usize;
        let windows = audio.samples.chunks(window_size_samples);

        let mut rms_values = Vec::new();
        let mut min_rms = f32::MAX;
        let mut max_rms = f32::MIN;
        let mut sum_rms = 0.0f32;

        for window in windows {
            let rms = calculate_rms(window);
            rms_values.push(rms);
            min_rms = min_rms.min(rms);
            max_rms = max_rms.max(rms);
            sum_rms += rms;
        }

        let avg_rms = if !rms_values.is_empty() {
            sum_rms / rms_values.len() as f32
        } else {
            0.0
        };

        tracing::info!(
            "RMS analysis: min={:.4}, max={:.4}, avg={:.4}, threshold={:.4}",
            min_rms,
            max_rms,
            avg_rms,
            self.threshold
        );

        // Step 2: Mark silence regions
        let min_silence_windows = (self.min_duration_ms / self.window_size_ms) as usize;
        let mut silence_regions = Vec::new();
        let mut silence_start: Option<usize> = None;

        for (i, &rms) in rms_values.iter().enumerate() {
            if rms < self.threshold {
                // Start or continue silence region
                if silence_start.is_none() {
                    silence_start = Some(i);
                }
            } else {
                // End silence region
                if let Some(start) = silence_start {
                    let duration_windows = i - start;
                    if duration_windows >= min_silence_windows {
                        silence_regions.push(SilenceRegion {
                            start_frame: start * window_size_samples,
                            end_frame: i * window_size_samples,
                        });
                    }
                    silence_start = None;
                }
            }
        }

        // Handle trailing silence
        if let Some(start) = silence_start {
            let duration_windows = rms_values.len() - start;
            if duration_windows >= min_silence_windows {
                silence_regions.push(SilenceRegion {
                    start_frame: start * window_size_samples,
                    end_frame: audio.samples.len(),
                });
            }
        }

        tracing::info!("Found {} silence regions to remove", silence_regions.len());

        // Step 3: Build new audio without silence regions
        let mut new_samples = Vec::new();
        let mut last_end = 0;

        for region in &silence_regions {
            // Copy audio before this silence region
            new_samples.extend_from_slice(&audio.samples[last_end..region.start_frame]);
            last_end = region.end_frame;
        }

        // Copy remaining audio after last silence region
        new_samples.extend_from_slice(&audio.samples[last_end..]);

        let original_duration = audio.duration();
        let new_buffer = AudioBuffer {
            samples: new_samples,
            sample_rate: audio.sample_rate,
            channels: audio.channels,
        };
        let new_duration = new_buffer.duration();

        let reduction_percent = if original_duration.as_secs_f64() > 0.0 {
            ((original_duration.as_secs_f64() - new_duration.as_secs_f64())
                / original_duration.as_secs_f64()
                * 100.0) as f32
        } else {
            0.0
        };

        let analysis = SilenceAnalysis {
            min_rms,
            max_rms,
            avg_rms,
            silence_regions,
            reduction_percent,
            original_duration,
            new_duration,
        };

        tracing::info!(
            "Silence removal complete: {:.1}% reduction ({:.2}s → {:.2}s)",
            reduction_percent,
            original_duration.as_secs_f64(),
            new_duration.as_secs_f64()
        );

        Ok((new_buffer, analysis))
    }
}

fn calculate_rms(samples: &[f32]) -> f32 {
    if samples.is_empty() {
        return 0.0;
    }

    let sum_squares: f32 = samples.iter().map(|&s| s * s).sum();
    (sum_squares / samples.len() as f32).sqrt()
}

#[derive(Debug, Clone)]
pub struct SilenceAnalysis {
    pub min_rms: f32,
    pub max_rms: f32,
    pub avg_rms: f32,
    pub silence_regions: Vec<SilenceRegion>,
    pub reduction_percent: f32,
    pub original_duration: Duration,
    pub new_duration: Duration,
}

#[derive(Debug, Clone)]
pub struct SilenceRegion {
    pub start_frame: usize,
    pub end_frame: usize,
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_rms_calculation() {
        let samples = vec![0.0, 0.1, 0.2, 0.1, 0.0];
        let rms = calculate_rms(&samples);
        assert!((rms - 0.1095).abs() < 0.001);
    }

    #[test]
    fn test_rms_silent() {
        let samples = vec![0.0, 0.0, 0.0];
        let rms = calculate_rms(&samples);
        assert_eq!(rms, 0.0);
    }

    #[test]
    fn test_silence_removal() {
        // Create audio with silence in the middle
        let mut samples = Vec::new();
        // 1 second of sound
        samples.extend(vec![0.5; 16000]);
        // 2 seconds of silence
        samples.extend(vec![0.0; 32000]);
        // 1 second of sound
        samples.extend(vec![0.5; 16000]);

        let audio = AudioBuffer {
            samples,
            sample_rate: 16000,
            channels: 1,
        };

        let remover = SilenceRemover::new(0.01, 1500);
        let (result, analysis) = remover.remove_silence(&audio).unwrap();

        assert!(result.duration() < audio.duration());
        assert!(analysis.reduction_percent > 0.0);
        assert!(!analysis.silence_regions.is_empty());
    }
}
