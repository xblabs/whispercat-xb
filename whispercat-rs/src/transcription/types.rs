use serde::{Deserialize, Serialize};
use std::path::PathBuf;

#[derive(Debug, Clone, Copy, PartialEq, Serialize, Deserialize)]
pub enum TranscriptionProvider {
    OpenAI,
    FasterWhisper,
    OpenWebUI,
}

impl TranscriptionProvider {
    pub fn as_str(&self) -> &'static str {
        match self {
            TranscriptionProvider::OpenAI => "OpenAI",
            TranscriptionProvider::FasterWhisper => "Faster-Whisper",
            TranscriptionProvider::OpenWebUI => "Open WebUI",
        }
    }
}

#[derive(Debug, Clone)]
pub struct TranscriptionRequest {
    pub file_path: PathBuf,
    pub provider: TranscriptionProvider,
    pub model: String,
    pub language: Option<String>,
    pub prompt: Option<String>,
    pub temperature: Option<f32>,
    pub response_format: Option<String>,
}

impl TranscriptionRequest {
    pub fn new(file_path: PathBuf, provider: TranscriptionProvider) -> Self {
        let model = match provider {
            TranscriptionProvider::OpenAI => "whisper-1".to_string(),
            TranscriptionProvider::FasterWhisper => "Systran/faster-whisper-large-v3".to_string(),
            TranscriptionProvider::OpenWebUI => "whisper-1".to_string(),
        };

        Self {
            file_path,
            provider,
            model,
            language: None,
            prompt: None,
            temperature: None,
            response_format: Some("json".to_string()),
        }
    }

    pub fn with_model(mut self, model: impl Into<String>) -> Self {
        self.model = model.into();
        self
    }

    pub fn with_language(mut self, language: impl Into<String>) -> Self {
        self.language = Some(language.into());
        self
    }

    pub fn with_prompt(mut self, prompt: impl Into<String>) -> Self {
        self.prompt = Some(prompt.into());
        self
    }

    pub fn with_temperature(mut self, temperature: f32) -> Self {
        self.temperature = Some(temperature);
        self
    }
}

#[derive(Debug, Clone, Deserialize)]
pub struct TranscriptionResponse {
    pub text: String,

    #[serde(default)]
    pub duration: Option<f64>,

    #[serde(default)]
    pub language: Option<String>,
}

#[derive(Debug, Clone, Serialize)]
pub struct ChatCompletionRequest {
    pub model: String,
    pub messages: Vec<ChatMessage>,

    #[serde(skip_serializing_if = "Option::is_none")]
    pub temperature: Option<f32>,

    #[serde(skip_serializing_if = "Option::is_none")]
    pub max_tokens: Option<u32>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ChatMessage {
    pub role: String,
    pub content: String,
}

#[derive(Debug, Clone, Deserialize)]
pub struct ChatCompletionResponse {
    pub choices: Vec<ChatChoice>,
}

#[derive(Debug, Clone, Deserialize)]
pub struct ChatChoice {
    pub message: ChatMessage,
    pub finish_reason: Option<String>,
}
