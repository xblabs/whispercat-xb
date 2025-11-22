pub mod buffer;
pub mod processor;
pub mod recorder;
pub mod compressor;

pub use buffer::AudioBuffer;
pub use processor::{SilenceAnalysis, SilenceRemover};
pub use recorder::{AudioRecorder, RecordingState};
pub use compressor::{AudioCompressor, MAX_FILE_SIZE_MB};
