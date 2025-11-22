use crate::audio::AudioBuffer;
use crate::error::{Result, WhisperCatError};
use crate::transcription::types::{
    ChatCompletionRequest, ChatCompletionResponse, ChatMessage, TranscriptionRequest,
    TranscriptionResponse,
};
use std::path::Path;

#[derive(Clone)]
pub struct WhisperClient {
    api_key: String,
    client: reqwest::Client,
    base_url: String,
}

impl WhisperClient {
    pub fn new(api_key: String) -> Self {
        Self {
            api_key,
            client: reqwest::Client::new(),
            base_url: "https://api.openai.com/v1".to_string(),
        }
    }

    pub async fn transcribe(&self, request: TranscriptionRequest) -> Result<TranscriptionResponse> {
        // Pre-flight checks
        self.validate_file_size(&request.file_path)?;

        // Build multipart form
        let form = self.build_multipart_form(&request).await?;

        tracing::info!("Sending transcription request to OpenAI API");

        // Make API request
        let response = self
            .client
            .post(format!("{}/audio/transcriptions", self.base_url))
            .header("Authorization", format!("Bearer {}", self.api_key))
            .multipart(form)
            .send()
            .await?;

        // Handle response
        if !response.status().is_success() {
            let status = response.status();
            let body = response.text().await.unwrap_or_default();
            tracing::error!("API error {}: {}", status, body);
            return Err(WhisperCatError::ApiError {
                status: status.as_u16(),
                message: body,
            });
        }

        let result: TranscriptionResponse = response.json().await?;
        tracing::info!("Transcription successful: {} characters", result.text.len());
        Ok(result)
    }

    pub async fn chat_completion(
        &self,
        system_prompt: &str,
        user_prompt: &str,
        model: &str,
    ) -> Result<String> {
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
            .post(format!("{}/chat/completions", self.base_url))
            .header("Authorization", format!("Bearer {}", self.api_key))
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

    fn validate_file_size(&self, path: &Path) -> Result<()> {
        let size_mb = AudioBuffer::file_size_mb(path)?;

        if size_mb > 25.0 {
            tracing::error!("File too large: {:.2} MB", size_mb);
            return Err(WhisperCatError::FileSizeError { size: size_mb });
        }

        tracing::info!("File size: {:.2} MB", size_mb);
        Ok(())
    }

    async fn build_multipart_form(
        &self,
        request: &TranscriptionRequest,
    ) -> Result<reqwest::multipart::Form> {
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
}
