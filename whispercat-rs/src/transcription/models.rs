use crate::error::{Result, WhisperCatError};
use crate::transcription::types::TranscriptionProvider;
use serde::{Deserialize, Serialize};

/// A model available for transcription or chat completion
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ModelInfo {
    pub id: String,
    pub name: String,
    pub provider: TranscriptionProvider,
}

impl ModelInfo {
    pub fn new(id: impl Into<String>, provider: TranscriptionProvider) -> Self {
        let id = id.into();
        let name = id.clone();
        Self { id, name, provider }
    }

    pub fn with_name(mut self, name: impl Into<String>) -> Self {
        self.name = name.into();
        self
    }
}

/// Response from OpenAI models endpoint
#[derive(Debug, Deserialize)]
struct OpenAIModelsResponse {
    data: Vec<OpenAIModel>,
}

#[derive(Debug, Deserialize)]
struct OpenAIModel {
    id: String,
}

/// Response from Open WebUI models endpoint
#[derive(Debug, Deserialize)]
struct OpenWebUIModelsResponse {
    data: Vec<OpenWebUIModel>,
}

#[derive(Debug, Deserialize)]
struct OpenWebUIModel {
    id: String,
    name: Option<String>,
}

/// Response from Faster-Whisper models endpoint
#[derive(Debug, Deserialize)]
struct FasterWhisperModelsResponse {
    models: Vec<String>,
}

/// Client for fetching available models from various providers
#[derive(Clone)]
pub struct ModelsClient {
    openai_api_key: String,
    faster_whisper_url: Option<String>,
    openwebui_url: Option<String>,
    openwebui_api_key: Option<String>,
    client: reqwest::Client,
}

impl ModelsClient {
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
                .timeout(std::time::Duration::from_secs(10))
                .build()
                .unwrap(),
        }
    }

    /// Fetch available models for the specified provider
    pub async fn fetch_models(&self, provider: TranscriptionProvider) -> Result<Vec<ModelInfo>> {
        match provider {
            TranscriptionProvider::OpenAI => self.fetch_openai_models().await,
            TranscriptionProvider::FasterWhisper => self.fetch_faster_whisper_models().await,
            TranscriptionProvider::OpenWebUI => self.fetch_openwebui_models().await,
        }
    }

    async fn fetch_openai_models(&self) -> Result<Vec<ModelInfo>> {
        tracing::info!("Fetching OpenAI models");

        let response = self
            .client
            .get("https://api.openai.com/v1/models")
            .header("Authorization", format!("Bearer {}", self.openai_api_key))
            .send()
            .await?;

        if !response.status().is_success() {
            let status = response.status();
            let body = response.text().await.unwrap_or_default();
            tracing::error!("OpenAI models API error {}: {}", status, body);
            return Err(WhisperCatError::ApiError {
                status: status.as_u16(),
                message: body,
            });
        }

        let models_response: OpenAIModelsResponse = response.json().await?;

        // Filter for Whisper models
        let whisper_models: Vec<ModelInfo> = models_response
            .data
            .into_iter()
            .filter(|m| m.id.starts_with("whisper"))
            .map(|m| ModelInfo::new(m.id, TranscriptionProvider::OpenAI))
            .collect();

        tracing::info!("Found {} OpenAI Whisper models", whisper_models.len());
        Ok(whisper_models)
    }

    async fn fetch_faster_whisper_models(&self) -> Result<Vec<ModelInfo>> {
        let base_url = self.faster_whisper_url.as_ref()
            .ok_or_else(|| WhisperCatError::ConfigError("Faster-Whisper server URL not configured".into()))?;

        let url = format!("{}/v1/models", base_url.trim_end_matches('/'));

        tracing::info!("Fetching Faster-Whisper models from {}", url);

        let response = self
            .client
            .get(&url)
            .header("Accept", "application/json")
            .send()
            .await?;

        if !response.status().is_success() {
            let status = response.status();
            let body = response.text().await.unwrap_or_default();
            tracing::error!("Faster-Whisper models API error {}: {}", status, body);
            return Err(WhisperCatError::ApiError {
                status: status.as_u16(),
                message: body,
            });
        }

        // Try to parse as JSON first
        let body = response.text().await?;

        // Faster-Whisper might return different formats
        let models: Vec<ModelInfo> = if let Ok(json_response) = serde_json::from_str::<FasterWhisperModelsResponse>(&body) {
            json_response.models
                .into_iter()
                .map(|id| ModelInfo::new(id, TranscriptionProvider::FasterWhisper))
                .collect()
        } else if let Ok(openai_format) = serde_json::from_str::<OpenAIModelsResponse>(&body) {
            // Some Faster-Whisper servers use OpenAI-compatible format
            openai_format.data
                .into_iter()
                .map(|m| ModelInfo::new(m.id, TranscriptionProvider::FasterWhisper))
                .collect()
        } else {
            // Fallback to default models if we can't parse the response
            tracing::warn!("Could not parse Faster-Whisper models response, using defaults");
            vec![
                ModelInfo::new("Systran/faster-whisper-large-v3", TranscriptionProvider::FasterWhisper),
                ModelInfo::new("Systran/faster-whisper-medium", TranscriptionProvider::FasterWhisper),
                ModelInfo::new("Systran/faster-whisper-small", TranscriptionProvider::FasterWhisper),
            ]
        };

        tracing::info!("Found {} Faster-Whisper models", models.len());
        Ok(models)
    }

    async fn fetch_openwebui_models(&self) -> Result<Vec<ModelInfo>> {
        let base_url = self.openwebui_url.as_ref()
            .ok_or_else(|| WhisperCatError::ConfigError("Open WebUI server URL not configured".into()))?;

        let api_key = self.openwebui_api_key.as_ref()
            .ok_or_else(|| WhisperCatError::ConfigError("Open WebUI API key not configured".into()))?;

        let url = format!("{}/api/models", base_url.trim_end_matches('/'));

        tracing::info!("Fetching Open WebUI models from {}", url);

        let response = self
            .client
            .get(&url)
            .header("Authorization", format!("Bearer {}", api_key))
            .send()
            .await?;

        if !response.status().is_success() {
            let status = response.status();
            let body = response.text().await.unwrap_or_default();
            tracing::error!("Open WebUI models API error {}: {}", status, body);
            return Err(WhisperCatError::ApiError {
                status: status.as_u16(),
                message: body,
            });
        }

        let models_response: OpenWebUIModelsResponse = response.json().await?;

        let models: Vec<ModelInfo> = models_response
            .data
            .into_iter()
            .map(|m| {
                let name = m.name.unwrap_or_else(|| m.id.clone());
                ModelInfo::new(m.id, TranscriptionProvider::OpenWebUI).with_name(name)
            })
            .collect();

        tracing::info!("Found {} Open WebUI models", models.len());
        Ok(models)
    }

    /// Get default/fallback models for a provider
    pub fn get_default_models(provider: TranscriptionProvider) -> Vec<ModelInfo> {
        match provider {
            TranscriptionProvider::OpenAI => vec![
                ModelInfo::new("whisper-1", TranscriptionProvider::OpenAI),
            ],
            TranscriptionProvider::FasterWhisper => vec![
                ModelInfo::new("Systran/faster-whisper-large-v3", TranscriptionProvider::FasterWhisper)
                    .with_name("Large V3"),
                ModelInfo::new("Systran/faster-whisper-medium", TranscriptionProvider::FasterWhisper)
                    .with_name("Medium"),
                ModelInfo::new("Systran/faster-whisper-small", TranscriptionProvider::FasterWhisper)
                    .with_name("Small"),
                ModelInfo::new("Systran/faster-whisper-base", TranscriptionProvider::FasterWhisper)
                    .with_name("Base"),
                ModelInfo::new("Systran/faster-whisper-tiny", TranscriptionProvider::FasterWhisper)
                    .with_name("Tiny"),
            ],
            TranscriptionProvider::OpenWebUI => vec![
                ModelInfo::new("whisper-1", TranscriptionProvider::OpenWebUI),
            ],
        }
    }
}
