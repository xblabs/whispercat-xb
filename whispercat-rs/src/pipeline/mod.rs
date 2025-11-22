use crate::error::Result;
use crate::transcription::WhisperClient;
use chrono::{DateTime, Utc};
use serde::{Deserialize, Serialize};
use std::time::Instant;
use uuid::Uuid;

#[derive(Clone, Debug, Serialize, Deserialize)]
pub struct Pipeline {
    pub id: Uuid,
    pub name: String,
    pub description: Option<String>,
    pub units: Vec<Unit>,
    pub created_at: DateTime<Utc>,
    pub updated_at: DateTime<Utc>,
}

impl Pipeline {
    pub fn new(name: String) -> Self {
        let now = Utc::now();
        Self {
            id: Uuid::new_v4(),
            name,
            description: None,
            units: Vec::new(),
            created_at: now,
            updated_at: now,
        }
    }

    pub fn add_unit(&mut self, unit: Unit) {
        self.units.push(unit);
        self.updated_at = Utc::now();
    }
}

#[derive(Clone, Debug, Serialize, Deserialize)]
#[serde(tag = "type")]
pub enum Unit {
    Prompt {
        id: Uuid,
        name: String,
        provider: Provider,
        model: String,
        system_prompt: String,
        user_prompt_template: String,
    },
    TextReplacement {
        id: Uuid,
        name: String,
        find: String,
        replace: String,
        regex: bool,
        case_sensitive: bool,
    },
}

impl Unit {
    pub fn name(&self) -> &str {
        match self {
            Unit::Prompt { name, .. } => name,
            Unit::TextReplacement { name, .. } => name,
        }
    }

    pub fn id(&self) -> Uuid {
        match self {
            Unit::Prompt { id, .. } => *id,
            Unit::TextReplacement { id, .. } => *id,
        }
    }
}

#[derive(Clone, Debug, Serialize, Deserialize)]
pub enum Provider {
    OpenAI,
}

pub struct PipelineExecutor {
    openai_client: WhisperClient,
}

impl PipelineExecutor {
    pub fn new(openai_api_key: String) -> Self {
        Self {
            openai_client: WhisperClient::new(openai_api_key),
        }
    }

    pub async fn execute(&self, input: String, pipeline: &Pipeline) -> Result<ExecutionResult> {
        let mut current_text = input.clone();
        let mut log_entries = Vec::new();
        let start_time = Instant::now();

        tracing::info!("Executing pipeline: {} ({} units)", pipeline.name, pipeline.units.len());

        for (idx, unit) in pipeline.units.iter().enumerate() {
            let unit_start = Instant::now();
            tracing::info!("  [{}/{}] {}", idx + 1, pipeline.units.len(), unit.name());

            let output = self.execute_unit(&current_text, unit).await?;
            let duration = unit_start.elapsed();

            log_entries.push(ExecutionLogEntry {
                timestamp: Utc::now(),
                unit_name: unit.name().to_string(),
                input: current_text.clone(),
                output: output.clone(),
                duration,
            });

            current_text = output;
        }

        let total_duration = start_time.elapsed();
        tracing::info!("Pipeline execution complete in {:?}", total_duration);

        Ok(ExecutionResult {
            output: current_text,
            log_entries,
            total_duration,
        })
    }

    async fn execute_unit(&self, input: &str, unit: &Unit) -> Result<String> {
        match unit {
            Unit::Prompt {
                system_prompt,
                user_prompt_template,
                model,
                ..
            } => {
                let user_prompt = user_prompt_template.replace("{{input}}", input);
                self.openai_client
                    .chat_completion(system_prompt, &user_prompt, model)
                    .await
            }
            Unit::TextReplacement {
                find,
                replace,
                regex,
                case_sensitive,
                ..
            } => {
                if *regex {
                    let re = if *case_sensitive {
                        regex::Regex::new(find)?
                    } else {
                        regex::RegexBuilder::new(find)
                            .case_insensitive(true)
                            .build()?
                    };
                    Ok(re.replace_all(input, replace.as_str()).to_string())
                } else {
                    Ok(if *case_sensitive {
                        input.replace(find, replace)
                    } else {
                        let re = regex::RegexBuilder::new(&regex::escape(find))
                            .case_insensitive(true)
                            .build()?;
                        re.replace_all(input, replace.as_str()).to_string()
                    })
                }
            }
        }
    }
}

#[derive(Debug, Clone)]
pub struct ExecutionResult {
    pub output: String,
    pub log_entries: Vec<ExecutionLogEntry>,
    pub total_duration: std::time::Duration,
}

#[derive(Clone, Debug)]
pub struct ExecutionLogEntry {
    pub timestamp: DateTime<Utc>,
    pub unit_name: String,
    pub input: String,
    pub output: String,
    pub duration: std::time::Duration,
}
