use crate::audio::{AudioBuffer, AudioCompressor};
use crate::error::{Result, WhisperCatError};
use crate::transcription::types::{
    TranscriptionProvider, TranscriptionRequest, TranscriptionResponse,
};
use std::path::{Path, PathBuf};

/// Unified transcription client that routes to different providers
#[derive(Clone)]
pub struct TranscriptionClient {
    openai_api_key: String,
    faster_whisper_url: Option<String>,
    openwebui_url: Option<String>,
    openwebui_api_key: Option<String>,
    client: reqwest::Client,
}

impl TranscriptionClient {
    pub fn new(
        openai_api_key: String,
        faster_whisper_url: Option<String>,
        openwebui_url: Option<String>,
        openwebui_api_key: Option<String>,
    ) -> Self {
        Self {
            openai_api_key,
            faster_whisper_url,
            openwebui_url,
            openwebui_api_key,
            client: reqwest::Client::builder()
                .danger_accept_invalid_certs(true) // For Open WebUI self-signed certs
                .build()
                .unwrap(),
        }
    }

    pub async fn transcribe(&self, mut request: TranscriptionRequest) -> Result<TranscriptionResponse> {
        // Check if file needs compression and compress if necessary
        let compressed_file = if AudioCompressor::needs_compression(&request.file_path)? {
            match AudioCompressor::compress_if_needed(&request.file_path)? {
                Some(compressed_path) => {
                    tracing::info!("Using compressed file for transcription: {:?}", compressed_path);
                    Some(compressed_path)
                }
                None => None,
            }
        } else {
            None
        };

        // Use compressed file if available
        if let Some(ref compressed_path) = compressed_file {
            request.file_path = compressed_path.clone();
        }

        // Validate file size (should be within limits after compression)
        self.validate_file_size(&request.file_path)?;

        // Perform transcription
        let result = match request.provider {
            TranscriptionProvider::OpenAI => self.transcribe_openai(request).await,
            TranscriptionProvider::FasterWhisper => self.transcribe_faster_whisper(request).await,
            TranscriptionProvider::OpenWebUI => self.transcribe_openwebui(request).await,
        };

        // Clean up compressed file if it was created
        if let Some(compressed_path) = compressed_file {
            std::fs::remove_file(&compressed_path).ok();
            tracing::debug!("Cleaned up compressed file: {:?}", compressed_path);
        }

        result
    }

    async fn transcribe_openai(&self, request: TranscriptionRequest) -> Result<TranscriptionResponse> {
        let form = self.build_openai_form(&request).await?;

        tracing::info!("Sending transcription request to OpenAI API");

        let response = self
            .client
            .post("https://api.openai.com/v1/audio/transcriptions")
            .header("Authorization", format!("Bearer {}", self.openai_api_key))
            .multipart(form)
            .send()
            .await?;

        if !response.status().is_success() {
            let status = response.status();
            let body = response.text().await.unwrap_or_default();
            tracing::error!("OpenAI API error {}: {}", status, body);
            return Err(WhisperCatError::ApiError {
                status: status.as_u16(),
                message: body,
            });
        }

        let result: TranscriptionResponse = response.json().await?;
        tracing::info!("OpenAI transcription successful: {} characters", result.text.len());
        Ok(result)
    }

    async fn transcribe_faster_whisper(&self, request: TranscriptionRequest) -> Result<TranscriptionResponse> {
        let base_url = self.faster_whisper_url.as_ref()
            .ok_or_else(|| WhisperCatError::ConfigError("Faster-Whisper server URL not configured".into()))?;

        let url = format!("{}/v1/audio/transcriptions", base_url.trim_end_matches('/'));

        let form = self.build_faster_whisper_form(&request).await?;

        tracing::info!("Sending transcription request to Faster-Whisper: {}", url);

        let response = self
            .client
            .post(&url)
            .header("Accept", "application/json")
            .multipart(form)
            .send()
            .await?;

        if !response.status().is_success() {
            let status = response.status();
            let body = response.text().await.unwrap_or_default();
            tracing::error!("Faster-Whisper API error {}: {}", status, body);
            return Err(WhisperCatError::ApiError {
                status: status.as_u16(),
                message: body,
            });
        }

        // Faster-Whisper may return plain text or JSON
        let body = response.text().await?;
        let text = if body.starts_with('{') {
            // JSON response
            let json: serde_json::Value = serde_json::from_str(&body)?;
            json["text"].as_str().unwrap_or(&body).to_string()
        } else {
            // Plain text response
            body
        };

        tracing::info!("Faster-Whisper transcription successful: {} characters", text.len());
        Ok(TranscriptionResponse {
            text,
            duration: None,
            language: None,
        })
    }

    async fn transcribe_openwebui(&self, request: TranscriptionRequest) -> Result<TranscriptionResponse> {
        let base_url = self.openwebui_url.as_ref()
            .ok_or_else(|| WhisperCatError::ConfigError("Open WebUI server URL not configured".into()))?;

        let api_key = self.openwebui_api_key.as_ref()
            .ok_or_else(|| WhisperCatError::ConfigError("Open WebUI API key not configured".into()))?;

        let url = format!("{}/api/v1/audio/transcriptions", base_url.trim_end_matches('/'));

        let form = self.build_openwebui_form(&request).await?;

        tracing::info!("Sending transcription request to Open WebUI: {}", url);

        let response = self
            .client
            .post(&url)
            .header("Authorization", format!("Bearer {}", api_key))
            .multipart(form)
            .send()
            .await?;

        if !response.status().is_success() {
            let status = response.status();
            let body = response.text().await.unwrap_or_default();
            tracing::error!("Open WebUI API error {}: {}", status, body);
            return Err(WhisperCatError::ApiError {
                status: status.as_u16(),
                message: body,
            });
        }

        let result: TranscriptionResponse = response.json().await?;
        tracing::info!("Open WebUI transcription successful: {} characters", result.text.len());
        Ok(result)
    }

    fn validate_file_size(&self, path: &Path) -> Result<()> {
        let size_mb = AudioBuffer::file_size_mb(path)?;

        if size_mb > 25.0 {
            tracing::error!("File too large: {:.2} MB", size_mb);
            return Err(WhisperCatError::FileSizeError { size: size_mb });
        }

        tracing::info!("File size: {:.2} MB", size_mb);
        Ok(())
    }

    async fn build_openai_form(&self, request: &TranscriptionRequest) -> Result<reqwest::multipart::Form> {
        let file_bytes = tokio::fs::read(&request.file_path).await?;
        let file_name = request
            .file_path
            .file_name()
            .and_then(|n| n.to_str())
            .unwrap_or("audio.wav")
            .to_string();

        let file_part = reqwest::multipart::Part::bytes(file_bytes)
            .file_name(file_name)
            .mime_str("audio/wav")?;

        let mut form = reqwest::multipart::Form::new()
            .part("file", file_part)
            .text("model", request.model.clone());

        if let Some(lang) = &request.language {
            form = form.text("language", lang.clone());
        }
        if let Some(prompt) = &request.prompt {
            form = form.text("prompt", prompt.clone());
        }
        if let Some(temp) = request.temperature {
            form = form.text("temperature", temp.to_string());
        }
        if let Some(format) = &request.response_format {
            form = form.text("response_format", format.clone());
        }

        Ok(form)
    }

    async fn build_faster_whisper_form(&self, request: &TranscriptionRequest) -> Result<reqwest::multipart::Form> {
        let file_bytes = tokio::fs::read(&request.file_path).await?;
        let file_name = request
            .file_path
            .file_name()
            .and_then(|n| n.to_str())
            .unwrap_or("audio.wav")
            .to_string();

        let file_part = reqwest::multipart::Part::bytes(file_bytes)
            .file_name(file_name)
            .mime_str("audio/wav")?;

        let mut form = reqwest::multipart::Form::new()
            .part("file", file_part)
            .text("model", request.model.clone());

        if let Some(lang) = &request.language {
            form = form.text("language", lang.clone());
        }

        Ok(form)
    }

    async fn build_openwebui_form(&self, request: &TranscriptionRequest) -> Result<reqwest::multipart::Form> {
        let file_bytes = tokio::fs::read(&request.file_path).await?;
        let file_name = request
            .file_path
            .file_name()
            .and_then(|n| n.to_str())
            .unwrap_or("audio.wav")
            .to_string();

        let file_part = reqwest::multipart::Part::bytes(file_bytes)
            .file_name(file_name)
            .mime_str("audio/wav")?;

        let form = reqwest::multipart::Form::new()
            .part("file", file_part);

        Ok(form)
    }

    /// For pipeline post-processing (kept from original WhisperClient)
    pub async fn chat_completion(
        &self,
        system_prompt: &str,
        user_prompt: &str,
        model: &str,
    ) -> Result<String> {
        use crate::transcription::types::{ChatCompletionRequest, ChatCompletionResponse, ChatMessage};

        let request = ChatCompletionRequest {
            model: model.to_string(),
            messages: vec![
                ChatMessage {
                    role: "system".to_string(),
                    content: system_prompt.to_string(),
                },
                ChatMessage {
                    role: "user".to_string(),
                    content: user_prompt.to_string(),
                },
            ],
            temperature: Some(0.7),
            max_tokens: Some(2000),
        };

        tracing::info!("Sending chat completion request (model: {})", model);

        let response = self
            .client
            .post("https://api.openai.com/v1/chat/completions")
            .header("Authorization", format!("Bearer {}", self.openai_api_key))
            .header("Content-Type", "application/json")
            .json(&request)
            .send()
            .await?;

        if !response.status().is_success() {
            let status = response.status();
            let body = response.text().await.unwrap_or_default();
            tracing::error!("API error {}: {}", status, body);
            return Err(WhisperCatError::ApiError {
                status: status.as_u16(),
                message: body,
            });
        }

        let result: ChatCompletionResponse = response.json().await?;

        if let Some(choice) = result.choices.first() {
            Ok(choice.message.content.clone())
        } else {
            Err(WhisperCatError::ApiError {
                status: 500,
                message: "No response from API".to_string(),
            })
        }
    }
}
