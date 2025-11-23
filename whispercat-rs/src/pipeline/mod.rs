use crate::error::Result;
use crate::transcription::TranscriptionClient;
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

#[derive(Clone, Debug, Serialize, Deserialize, PartialEq)]
pub enum Provider {
    OpenAI,
}

/// A batch of units that can potentially be optimized into a single API call
#[derive(Clone, Debug)]
struct UnitBatch {
    units: Vec<Unit>,
    optimizable: bool,
    provider: Option<Provider>,
    model: Option<String>,
}

#[derive(Clone)]
pub struct PipelineExecutor {
    client: TranscriptionClient,
    optimization_enabled: bool,
}

impl PipelineExecutor {
    pub fn new(openai_api_key: String) -> Self {
        Self {
            client: TranscriptionClient::new(openai_api_key, None, None, None),
            optimization_enabled: true,
        }
    }

    pub fn with_optimization(mut self, enabled: bool) -> Self {
        self.optimization_enabled = enabled;
        self
    }

    pub async fn execute(&self, input: String, pipeline: &Pipeline) -> Result<ExecutionResult> {
        let mut current_text = input.clone();
        let mut log_entries = Vec::new();
        let start_time = Instant::now();

        tracing::info!("Executing pipeline: {} ({} units)", pipeline.name, pipeline.units.len());

        // Group units into optimizable batches
        let batches = if self.optimization_enabled {
            self.create_batches(&pipeline.units)
        } else {
            // No optimization: each unit is its own batch
            pipeline.units.iter().map(|u| UnitBatch {
                units: vec![u.clone()],
                optimizable: false,
                provider: None,
                model: None,
            }).collect()
        };

        let total_batches = batches.len();

        for (batch_idx, batch) in batches.iter().enumerate() {
            let batch_start = Instant::now();

            if batch.optimizable {
                // Optimized execution: chain prompts into single API call
                current_text = self.execute_batch_optimized(
                    &current_text,
                    batch,
                    batch_idx + 1,
                    total_batches,
                    &mut log_entries,
                ).await?;
            } else {
                // Normal execution: run units individually
                for unit in &batch.units {
                    let unit_start = Instant::now();
                    tracing::info!("  [{}/{}] {}", batch_idx + 1, total_batches, unit.name());

                    let output = self.execute_unit(&current_text, unit).await?;
                    let duration = unit_start.elapsed();

                    log_entries.push(ExecutionLogEntry {
                        timestamp: Utc::now(),
                        unit_name: unit.name().to_string(),
                        input: current_text.clone(),
                        output: output.clone(),
                        duration,
                        optimized: false,
                    });

                    current_text = output;
                }
            }
        }

        let total_duration = start_time.elapsed();
        tracing::info!("Pipeline execution complete in {:?}", total_duration);

        Ok(ExecutionResult {
            output: current_text,
            log_entries,
            total_duration,
        })
    }

    /// Groups consecutive same-model prompt units into optimizable batches
    fn create_batches(&self, units: &[Unit]) -> Vec<UnitBatch> {
        let mut batches = Vec::new();
        let mut current_batch: Vec<Unit> = Vec::new();
        let mut current_provider: Option<Provider> = None;
        let mut current_model: Option<String> = None;

        for unit in units {
            match unit {
                Unit::Prompt { provider, model, .. } => {
                    // Check if this unit can be added to current batch
                    if let (Some(batch_provider), Some(batch_model)) = (&current_provider, &current_model) {
                        if provider == batch_provider && model == batch_model {
                            // Same provider/model: add to batch
                            current_batch.push(unit.clone());
                        } else {
                            // Different provider/model: finalize current batch and start new one
                            if !current_batch.is_empty() {
                                batches.push(UnitBatch {
                                    units: current_batch.clone(),
                                    optimizable: current_batch.len() >= 2,
                                    provider: current_provider.clone(),
                                    model: current_model.clone(),
                                });
                            }
                            current_batch = vec![unit.clone()];
                            current_provider = Some(provider.clone());
                            current_model = Some(model.clone());
                        }
                    } else {
                        // First unit in batch
                        current_batch.push(unit.clone());
                        current_provider = Some(provider.clone());
                        current_model = Some(model.clone());
                    }
                }
                Unit::TextReplacement { .. } => {
                    // Text replacement: finalize current batch
                    if !current_batch.is_empty() {
                        batches.push(UnitBatch {
                            units: current_batch.clone(),
                            optimizable: current_batch.len() >= 2,
                            provider: current_provider.clone(),
                            model: current_model.clone(),
                        });
                        current_batch.clear();
                        current_provider = None;
                        current_model = None;
                    }
                    // Add text replacement as its own batch
                    batches.push(UnitBatch {
                        units: vec![unit.clone()],
                        optimizable: false,
                        provider: None,
                        model: None,
                    });
                }
            }
        }

        // Finalize last batch
        if !current_batch.is_empty() {
            batches.push(UnitBatch {
                units: current_batch,
                optimizable: current_provider.is_some() && current_model.is_some(),
                provider: current_provider,
                model: current_model,
            });
        }

        batches
    }

    /// Executes an optimized batch by chaining prompts into a single API call
    async fn execute_batch_optimized(
        &self,
        input: &str,
        batch: &UnitBatch,
        batch_number: usize,
        total_batches: usize,
        log_entries: &mut Vec<ExecutionLogEntry>,
    ) -> Result<String> {
        let batch_start = Instant::now();
        let saved_calls = batch.units.len() - 1;
        let cost_reduction = (saved_calls * 100) / batch.units.len();

        tracing::info!("⚡ PIPELINE OPTIMIZATION ACTIVE");
        tracing::info!("  Merging {} consecutive {:?}/{} units",
            batch.units.len(),
            batch.provider,
            batch.model.as_ref().unwrap_or(&"unknown".to_string())
        );
        tracing::info!("  Benefit: {} API call{} saved, {}% cost reduction",
            saved_calls,
            if saved_calls > 1 { "s" } else { "" },
            cost_reduction
        );

        // Compile chained prompt
        let (system_prompt, user_prompt) = self.compile_chained_prompt(input, batch);

        tracing::info!("  Compiled system prompt: {} chars", system_prompt.len());
        tracing::info!("  Compiled user prompt: {} chars", user_prompt.len());

        // Execute single API call
        let model = batch.model.as_ref().unwrap();
        let output = self.client
            .chat_completion(&system_prompt, &user_prompt, model)
            .await?;

        let duration = batch_start.elapsed();

        // Log as optimized execution
        log_entries.push(ExecutionLogEntry {
            timestamp: Utc::now(),
            unit_name: format!("OPTIMIZED CHAIN ({} units)", batch.units.len()),
            input: input.to_string(),
            output: output.clone(),
            duration,
            optimized: true,
        });

        tracing::info!("✓ Optimized chain completed - {} API call{} saved!",
            saved_calls,
            if saved_calls > 1 { "s" } else { "" }
        );

        Ok(output)
    }

    /// Compiles multiple prompts into a single chained prompt
    fn compile_chained_prompt(&self, input: &str, batch: &UnitBatch) -> (String, String) {
        let mut system_parts = Vec::new();
        let mut user_parts = Vec::new();

        for (idx, unit) in batch.units.iter().enumerate() {
            if let Unit::Prompt { name, system_prompt, user_prompt_template, .. } = unit {
                // Add system prompt part
                system_parts.push(format!("## Step {}: {}\n{}", idx + 1, name, system_prompt));

                // Replace {{input}} with placeholder for chaining
                let step_user_prompt = if idx == 0 {
                    user_prompt_template.replace("{{input}}", input)
                } else {
                    user_prompt_template.replace("{{input}}", &format!("{{STEP_{}_OUTPUT}}", idx))
                };

                user_parts.push(format!("### Step {}: {}\n{}", idx + 1, name, step_user_prompt));
            }
        }

        let system_prompt = format!(
            "You are executing a chained processing pipeline. Process each step sequentially, using the output from each step as input to the next.\n\n{}",
            system_parts.join("\n\n")
        );

        let user_prompt = format!(
            "Execute the following steps in order:\n\n{}\n\nProvide ONLY the final output from the last step.",
            user_parts.join("\n\n")
        );

        (system_prompt, user_prompt)
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
                self.client
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
    pub optimized: bool,
}
