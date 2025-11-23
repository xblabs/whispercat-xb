use crate::config::Config;
use crate::error::Result;
use crate::pipeline::{Pipeline, Unit, Provider};
use egui::{Context, RichText, Ui};
use uuid::Uuid;

pub struct RecordingScreen {
    pub is_recording: bool,
    pub recording_duration: Option<std::time::Duration>,
    pub transcription_result: Option<String>,
    pub is_transcribing: bool,
    pub error_message: Option<String>,
    pub selected_pipeline_id: Option<Uuid>,
    pub available_pipelines: Vec<Pipeline>,
}

impl Default for RecordingScreen {
    fn default() -> Self {
        Self {
            is_recording: false,
            recording_duration: None,
            transcription_result: None,
            is_transcribing: false,
            error_message: None,
            selected_pipeline_id: None,
            available_pipelines: Vec::new(),
        }
    }
}

impl RecordingScreen {
    pub fn refresh_pipelines(&mut self, config: &Config) {
        self.available_pipelines = config.get_pipelines().to_vec();
        self.selected_pipeline_id = config.get_last_used_pipeline();
    }
}

impl RecordingScreen {
    pub fn ui(&mut self, ui: &mut Ui) -> RecordingAction {
        let mut action = RecordingAction::None;

        ui.heading("🎤 WhisperCat - Audio Transcription");
        ui.add_space(20.0);

        // Recording controls
        ui.horizontal(|ui| {
            if self.is_recording {
                if ui.button(RichText::new("⏹ Stop Recording").size(18.0)).clicked() {
                    action = RecordingAction::StopRecording;
                }

                if let Some(duration) = self.recording_duration {
                    ui.label(
                        RichText::new(format!("🔴 Recording: {:.1}s", duration.as_secs_f64()))
                            .size(16.0)
                            .color(egui::Color32::RED),
                    );
                }
            } else {
                if ui
                    .add_enabled(
                        !self.is_transcribing,
                        egui::Button::new(RichText::new("🎙 Start Recording").size(18.0)),
                    )
                    .clicked()
                {
                    action = RecordingAction::StartRecording;
                }
            }
        });

        ui.add_space(10.0);

        // Status indicator
        if self.is_transcribing {
            ui.label(RichText::new("⏳ Transcribing...").size(14.0));
        }

        // Error message
        if let Some(error) = &self.error_message {
            ui.add_space(10.0);
            ui.colored_label(egui::Color32::RED, format!("❌ Error: {}", error));
        }

        ui.add_space(20.0);

        // Transcription result
        if let Some(text) = &self.transcription_result {
            ui.group(|ui| {
                ui.vertical(|ui| {
                    ui.label(RichText::new("Transcription Result:").strong());
                    ui.add_space(5.0);

                    egui::ScrollArea::vertical()
                        .max_height(300.0)
                        .show(ui, |ui| {
                            ui.label(text);
                        });

                    ui.add_space(5.0);

                    ui.horizontal(|ui| {
                        if ui.button("📋 Copy to Clipboard").clicked() {
                            ui.output_mut(|o| o.copied_text = text.clone());
                        }

                        // Pipeline selector
                        if !self.available_pipelines.is_empty() {
                            ui.separator();
                            ui.label("Pipeline:");

                            egui::ComboBox::from_id_source("pipeline_selector")
                                .selected_text(
                                    self.selected_pipeline_id
                                        .and_then(|id| {
                                            self.available_pipelines
                                                .iter()
                                                .find(|p| p.id == id)
                                                .map(|p| p.name.as_str())
                                        })
                                        .unwrap_or("Select pipeline...")
                                )
                                .show_ui(ui, |ui| {
                                    for pipeline in &self.available_pipelines {
                                        ui.selectable_value(
                                            &mut self.selected_pipeline_id,
                                            Some(pipeline.id),
                                            &pipeline.name
                                        );
                                    }
                                });

                            if ui.button("▶ Run Pipeline").clicked() && self.selected_pipeline_id.is_some() {
                                action = RecordingAction::ProcessPipeline;
                            }
                        }
                    });
                });
            });
        }

        action
    }
}

pub enum RecordingAction {
    None,
    StartRecording,
    StopRecording,
    ProcessPipeline,
}

pub struct SettingsScreen {
    pub api_key_input: String,
    pub provider_selection: usize,
    pub model_input: String,
    pub faster_whisper_url: String,
    pub openwebui_url: String,
    pub openwebui_api_key: String,
    pub silence_threshold: f32,
    pub min_silence_duration: u32,
    pub silence_removal_enabled: bool,
    pub auto_paste: bool,
    pub available_models: Vec<String>,
    pub fetching_models: bool,
    pub show_model_dropdown: bool,
    pub hotkey_input: String,
    pub recording_hotkey: bool,
    pub testing_audio: bool,
    pub test_audio_level: f32,
}

impl SettingsScreen {
    pub fn from_config(config: &Config) -> Self {
        let provider_idx = match config.whisper.provider.as_str() {
            "FasterWhisper" | "Faster-Whisper" => 1,
            "OpenWebUI" | "Open WebUI" => 2,
            _ => 0,
        };

        Self {
            api_key_input: config.whisper.api_key.clone(),
            provider_selection: provider_idx,
            model_input: config.whisper.model.clone(),
            faster_whisper_url: config.whisper.faster_whisper_url.clone().unwrap_or_default(),
            openwebui_url: config.whisper.openwebui_url.clone().unwrap_or_default(),
            openwebui_api_key: config.whisper.openwebui_api_key.clone().unwrap_or_default(),
            silence_threshold: config.silence_removal.threshold,
            min_silence_duration: config.silence_removal.min_duration_ms,
            silence_removal_enabled: config.silence_removal.enabled,
            auto_paste: config.ui.auto_paste,
            available_models: Vec::new(),
            fetching_models: false,
            show_model_dropdown: false,
            hotkey_input: config.hotkeys.record_toggle.clone(),
            recording_hotkey: false,
            testing_audio: false,
            test_audio_level: 0.0,
        }
    }

    pub fn set_available_models(&mut self, models: Vec<String>) {
        self.available_models = models;
        self.fetching_models = false;
        self.show_model_dropdown = !self.available_models.is_empty();
    }

    pub fn set_fetching_models(&mut self, fetching: bool) {
        self.fetching_models = fetching;
    }

    pub fn ui(&mut self, ui: &mut Ui) -> SettingsAction {
        let mut action = SettingsAction::None;

        ui.heading("⚙️ Settings");
        ui.add_space(20.0);

        // Provider Selection
        ui.group(|ui| {
            ui.vertical(|ui| {
                ui.label(RichText::new("Transcription Provider").strong());
                ui.add_space(5.0);

                let providers = ["OpenAI", "Faster-Whisper", "Open WebUI"];

                ui.horizontal(|ui| {
                    for (idx, provider) in providers.iter().enumerate() {
                        if ui.selectable_label(self.provider_selection == idx, *provider).clicked() {
                            self.provider_selection = idx;
                            action = SettingsAction::SaveConfig;
                        }
                    }
                });
            });
        });

        ui.add_space(10.0);

        // Provider-specific settings
        match self.provider_selection {
            0 => {
                // OpenAI
                ui.group(|ui| {
                    ui.vertical(|ui| {
                        ui.label(RichText::new("OpenAI Configuration").strong());
                        ui.add_space(5.0);

                        ui.label("API Key:");
                        let response = ui.add(
                            egui::TextEdit::singleline(&mut self.api_key_input)
                                .password(true)
                                .hint_text("sk-..."),
                        );
                        if response.changed() {
                            action = SettingsAction::SaveConfig;
                        }

                        ui.add_space(5.0);

                        ui.label("Model:");
                        ui.horizontal(|ui| {
                            if self.show_model_dropdown && !self.available_models.is_empty() {
                                egui::ComboBox::from_id_source("model_selector_openai")
                                    .selected_text(&self.model_input)
                                    .show_ui(ui, |ui| {
                                        for model in &self.available_models {
                                            if ui.selectable_value(&mut self.model_input, model.clone(), model).clicked() {
                                                action = SettingsAction::SaveConfig;
                                            }
                                        }
                                    });
                            } else {
                                let response = ui.add(
                                    egui::TextEdit::singleline(&mut self.model_input)
                                        .hint_text("whisper-1"),
                                );
                                if response.changed() {
                                    action = SettingsAction::SaveConfig;
                                }
                            }

                            if ui.add_enabled(!self.fetching_models, egui::Button::new("🔄 Fetch Models")).clicked() {
                                action = SettingsAction::FetchModels;
                            }

                            if self.fetching_models {
                                ui.spinner();
                            }
                        });
                    });
                });
            }
            1 => {
                // Faster-Whisper
                ui.group(|ui| {
                    ui.vertical(|ui| {
                        ui.label(RichText::new("Faster-Whisper Configuration").strong());
                        ui.add_space(5.0);

                        ui.label("Server URL:");
                        let response = ui.add(
                            egui::TextEdit::singleline(&mut self.faster_whisper_url)
                                .hint_text("http://localhost:8000"),
                        );
                        if response.changed() {
                            action = SettingsAction::SaveConfig;
                        }

                        ui.add_space(5.0);

                        ui.label("Model:");
                        ui.horizontal(|ui| {
                            if self.show_model_dropdown && !self.available_models.is_empty() {
                                egui::ComboBox::from_id_source("model_selector_faster_whisper")
                                    .selected_text(&self.model_input)
                                    .show_ui(ui, |ui| {
                                        for model in &self.available_models {
                                            if ui.selectable_value(&mut self.model_input, model.clone(), model).clicked() {
                                                action = SettingsAction::SaveConfig;
                                            }
                                        }
                                    });
                            } else {
                                let response = ui.add(
                                    egui::TextEdit::singleline(&mut self.model_input)
                                        .hint_text("Systran/faster-whisper-large-v3"),
                                );
                                if response.changed() {
                                    action = SettingsAction::SaveConfig;
                                }
                            }

                            if ui.add_enabled(!self.fetching_models && !self.faster_whisper_url.is_empty(), egui::Button::new("🔄 Fetch Models")).clicked() {
                                action = SettingsAction::FetchModels;
                            }

                            if self.fetching_models {
                                ui.spinner();
                            }
                        });
                    });
                });
            }
            2 => {
                // Open WebUI
                ui.group(|ui| {
                    ui.vertical(|ui| {
                        ui.label(RichText::new("Open WebUI Configuration").strong());
                        ui.add_space(5.0);

                        ui.label("Server URL:");
                        let response = ui.add(
                            egui::TextEdit::singleline(&mut self.openwebui_url)
                                .hint_text("https://localhost:8080"),
                        );
                        if response.changed() {
                            action = SettingsAction::SaveConfig;
                        }

                        ui.add_space(5.0);

                        ui.label("API Key:");
                        let response = ui.add(
                            egui::TextEdit::singleline(&mut self.openwebui_api_key)
                                .password(true)
                                .hint_text("owui-..."),
                        );
                        if response.changed() {
                            action = SettingsAction::SaveConfig;
                        }

                        ui.add_space(5.0);

                        ui.label("Model:");
                        ui.horizontal(|ui| {
                            if self.show_model_dropdown && !self.available_models.is_empty() {
                                egui::ComboBox::from_id_source("model_selector_openwebui")
                                    .selected_text(&self.model_input)
                                    .show_ui(ui, |ui| {
                                        for model in &self.available_models {
                                            if ui.selectable_value(&mut self.model_input, model.clone(), model).clicked() {
                                                action = SettingsAction::SaveConfig;
                                            }
                                        }
                                    });
                            } else {
                                let response = ui.add(
                                    egui::TextEdit::singleline(&mut self.model_input)
                                        .hint_text("whisper-1"),
                                );
                                if response.changed() {
                                    action = SettingsAction::SaveConfig;
                                }
                            }

                            if ui.add_enabled(!self.fetching_models && !self.openwebui_url.is_empty() && !self.openwebui_api_key.is_empty(), egui::Button::new("🔄 Fetch Models")).clicked() {
                                action = SettingsAction::FetchModels;
                            }

                            if self.fetching_models {
                                ui.spinner();
                            }
                        });
                    });
                });
            }
            _ => {}
        }

        ui.add_space(20.0);

        // Silence Removal Settings
        ui.group(|ui| {
            ui.vertical(|ui| {
                ui.label(RichText::new("Silence Removal").strong());
                ui.add_space(5.0);

                let checkbox = ui.checkbox(&mut self.silence_removal_enabled, "Enable silence removal");
                if checkbox.changed() {
                    action = SettingsAction::SaveConfig;
                }

                ui.add_space(5.0);

                ui.add_enabled_ui(self.silence_removal_enabled, |ui| {
                    ui.label("RMS Threshold:");
                    let slider = ui.add(
                        egui::Slider::new(&mut self.silence_threshold, 0.001..=0.050)
                            .step_by(0.001),
                    );
                    if slider.changed() {
                        action = SettingsAction::SaveConfig;
                    }

                    ui.label(format!("Current: {:.3}", self.silence_threshold));

                    ui.add_space(10.0);

                    ui.label("Minimum Silence Duration (ms):");
                    let slider = ui.add(
                        egui::Slider::new(&mut self.min_silence_duration, 500..=5000)
                            .step_by(100.0),
                    );
                    if slider.changed() {
                        action = SettingsAction::SaveConfig;
                    }

                    ui.label(format!("Current: {}ms", self.min_silence_duration));
                });
            });
        });

        ui.add_space(20.0);

        // Auto-paste Settings
        ui.group(|ui| {
            ui.vertical(|ui| {
                ui.label(RichText::new("Auto-Paste").strong());
                ui.add_space(5.0);

                let checkbox = ui.checkbox(&mut self.auto_paste, "Automatically paste transcription results");
                if checkbox.changed() {
                    action = SettingsAction::SaveConfig;
                }

                ui.add_space(5.0);
                ui.label("When enabled, transcription results will be automatically copied to clipboard and pasted using Ctrl+V (Cmd+V on macOS)");
            });
        });

        ui.add_space(20.0);

        // Hotkey Settings
        ui.group(|ui| {
            ui.vertical(|ui| {
                ui.label(RichText::new("Hotkey Configuration").strong());
                ui.add_space(5.0);

                ui.label("Record Toggle Hotkey:");
                ui.add_space(3.0);

                ui.horizontal(|ui| {
                    // Display current hotkey
                    let hotkey_display = if self.recording_hotkey {
                        "Press keys... (ESC to cancel)".to_string()
                    } else {
                        self.hotkey_input.clone()
                    };

                    let text_response = ui.add(
                        egui::TextEdit::singleline(&mut self.hotkey_input)
                            .hint_text("Ctrl+Shift+R")
                            .text_color(if self.recording_hotkey {
                                egui::Color32::YELLOW
                            } else {
                                egui::Color32::WHITE
                            })
                    );

                    if text_response.changed() && !self.recording_hotkey {
                        action = SettingsAction::SaveConfig;
                    }

                    // Record button
                    let button_text = if self.recording_hotkey {
                        "⏹ Stop Recording"
                    } else {
                        "🎹 Record Hotkey"
                    };

                    if ui.button(button_text).clicked() {
                        self.recording_hotkey = !self.recording_hotkey;
                        if self.recording_hotkey {
                            self.hotkey_input.clear();
                        }
                    }
                });

                ui.add_space(5.0);
                ui.label("Supported formats:");
                ui.label("  • Single key: Ctrl+Shift+R, Alt+F1, F5");
                ui.label("  • Sequences: Ctrl+K, Ctrl+S (VS Code style)");
                ui.label("  • Modifiers: Ctrl, Shift, Alt, Super/Win/Cmd");
            });
        });

        ui.add_space(20.0);

        // Audio Testing
        ui.group(|ui| {
            ui.vertical(|ui| {
                ui.label(RichText::new("Audio Testing").strong());
                ui.add_space(5.0);

                ui.label("Test your microphone to ensure it's working correctly:");
                ui.add_space(5.0);

                ui.horizontal(|ui| {
                    let button_text = if self.testing_audio {
                        "⏹ Stop Test"
                    } else {
                        "🎤 Test Microphone"
                    };

                    if ui.button(button_text).clicked() {
                        self.testing_audio = !self.testing_audio;
                        if !self.testing_audio {
                            self.test_audio_level = 0.0;
                        }
                        action = SettingsAction::TestAudio;
                    }

                    if self.testing_audio {
                        ui.spinner();
                        ui.label("Recording...");
                    }
                });

                ui.add_space(5.0);

                // Audio level indicator
                ui.horizontal(|ui| {
                    ui.label("Level:");
                    let level_bar_width = 200.0;
                    let level_normalized = self.test_audio_level.clamp(0.0, 1.0);

                    // Draw audio level bar
                    let (rect, _) = ui.allocate_exact_size(
                        egui::vec2(level_bar_width, 20.0),
                        egui::Sense::hover()
                    );

                    // Background
                    ui.painter().rect_filled(
                        rect,
                        2.0,
                        egui::Color32::from_gray(40)
                    );

                    // Level fill
                    if level_normalized > 0.0 {
                        let fill_width = rect.width() * level_normalized;
                        let fill_rect = egui::Rect::from_min_size(
                            rect.min,
                            egui::vec2(fill_width, rect.height())
                        );

                        let color = if level_normalized > 0.8 {
                            egui::Color32::from_rgb(255, 100, 100) // Red for high
                        } else if level_normalized > 0.5 {
                            egui::Color32::from_rgb(100, 255, 100) // Green for good
                        } else {
                            egui::Color32::from_rgb(100, 200, 255) // Blue for low
                        };

                        ui.painter().rect_filled(fill_rect, 2.0, color);
                    }

                    // Border
                    ui.painter().rect_stroke(
                        rect,
                        2.0,
                        egui::Stroke::new(1.0, egui::Color32::from_gray(100))
                    );
                });

                ui.add_space(3.0);
                ui.label("  • Speak into your microphone to see the level indicator");
                ui.label("  • The bar should turn green when you speak at normal volume");
                ui.label("  • If the bar stays low, check your microphone settings");
            });
        });

        action
    }

    pub fn to_config(&self, config: &mut Config) {
        // Provider selection
        config.whisper.provider = match self.provider_selection {
            1 => "Faster-Whisper".to_string(),
            2 => "Open WebUI".to_string(),
            _ => "OpenAI".to_string(),
        };

        // Common settings
        config.whisper.model = self.model_input.clone();
        config.whisper.api_key = self.api_key_input.clone();

        // Provider-specific settings
        config.whisper.faster_whisper_url = if self.faster_whisper_url.is_empty() {
            None
        } else {
            Some(self.faster_whisper_url.clone())
        };

        config.whisper.openwebui_url = if self.openwebui_url.is_empty() {
            None
        } else {
            Some(self.openwebui_url.clone())
        };

        config.whisper.openwebui_api_key = if self.openwebui_api_key.is_empty() {
            None
        } else {
            Some(self.openwebui_api_key.clone())
        };

        // Silence removal settings
        config.silence_removal.threshold = self.silence_threshold;
        config.silence_removal.min_duration_ms = self.min_silence_duration;
        config.silence_removal.enabled = self.silence_removal_enabled;

        // UI settings
        config.ui.auto_paste = self.auto_paste;

        // Hotkey settings
        config.hotkeys.record_toggle = self.hotkey_input.clone();
    }
}

pub enum SettingsAction {
    None,
    SaveConfig,
    FetchModels,
    TestAudio,
}

pub struct PipelinesScreen {
    pub pipelines: Vec<Pipeline>,
    pub selected_pipeline: Option<Uuid>,
    pub editing_pipeline: Option<Pipeline>,
    pub show_editor: bool,
}

impl PipelinesScreen {
    pub fn from_config(config: &Config) -> Self {
        Self {
            pipelines: config.get_pipelines().to_vec(),
            selected_pipeline: config.get_last_used_pipeline(),
            editing_pipeline: None,
            show_editor: false,
        }
    }

    pub fn refresh_from_config(&mut self, config: &Config) {
        self.pipelines = config.get_pipelines().to_vec();
        self.selected_pipeline = config.get_last_used_pipeline();
    }

    pub fn ui(&mut self, ui: &mut Ui) -> PipelinesAction {
        let mut action = PipelinesAction::None;

        if self.show_editor {
            // Pipeline Editor View
            action = self.render_editor(ui);
        } else {
            // Pipeline List View
            action = self.render_list(ui);
        }

        action
    }

    fn render_list(&mut self, ui: &mut Ui) -> PipelinesAction {
        let mut action = PipelinesAction::None;

        ui.heading("📋 Pipelines");
        ui.add_space(10.0);

        ui.horizontal(|ui| {
            if ui.button("➕ New Pipeline").clicked() {
                self.editing_pipeline = Some(Pipeline::new("New Pipeline".to_string()));
                self.show_editor = true;
            }
        });

        ui.add_space(10.0);
        ui.separator();
        ui.add_space(10.0);

        // Sort pipelines by name
        let mut sorted_pipelines = self.pipelines.clone();
        sorted_pipelines.sort_by(|a, b| a.name.to_lowercase().cmp(&b.name.to_lowercase()));

        if sorted_pipelines.is_empty() {
            ui.label("No pipelines yet. Create one to get started!");
        } else {
            for pipeline in &sorted_pipelines {
                ui.group(|ui| {
                    ui.set_min_width(ui.available_width());

                    ui.vertical(|ui| {
                        ui.horizontal(|ui| {
                            ui.heading(&pipeline.name);
                            ui.add_space(10.0);

                            // Show unit count
                            ui.label(format!("({} units)", pipeline.units.len()));
                        });

                        if let Some(desc) = &pipeline.description {
                            ui.label(desc);
                        }

                        ui.add_space(5.0);

                        ui.horizontal(|ui| {
                            if ui.button("✏ Edit").clicked() {
                                self.editing_pipeline = Some(pipeline.clone());
                                self.show_editor = true;
                            }

                            if ui.button("🗑 Delete").clicked() {
                                action = PipelinesAction::Delete(pipeline.id);
                            }

                            if ui.button("▶ Run").clicked() {
                                action = PipelinesAction::Execute(pipeline.id);
                            }
                        });
                    });
                });

                ui.add_space(5.0);
            }
        }

        action
    }

    fn render_editor(&mut self, ui: &mut Ui) -> PipelinesAction {
        let mut action = PipelinesAction::None;
        let mut should_close = false;
        let mut should_save = false;

        if let Some(ref mut pipeline) = self.editing_pipeline {
            ui.heading("✏ Pipeline Editor");
            ui.add_space(10.0);

            ui.horizontal(|ui| {
                if ui.button("⬅ Back to List").clicked() {
                    should_close = true;
                }

                if ui.button("💾 Save Pipeline").clicked() {
                    should_save = true;
                }
            });

            ui.add_space(10.0);
            ui.separator();
            ui.add_space(10.0);

            // Pipeline metadata
            ui.group(|ui| {
                ui.vertical(|ui| {
                    ui.label(RichText::new("Pipeline Details").strong());
                    ui.add_space(5.0);

                    ui.label("Name:");
                    ui.text_edit_singleline(&mut pipeline.name);

                    ui.add_space(5.0);

                    ui.label("Description (optional):");
                    let mut desc = pipeline.description.clone().unwrap_or_default();
                    ui.text_edit_multiline(&mut desc);
                    pipeline.description = if desc.is_empty() { None } else { Some(desc) };
                });
            });

            ui.add_space(10.0);

            // Units list
            ui.group(|ui| {
                ui.vertical(|ui| {
                    ui.horizontal(|ui| {
                        ui.label(RichText::new("Processing Units").strong());
                        ui.add_space(10.0);

                        if ui.button("➕ Add Prompt Unit").clicked() {
                            pipeline.add_unit(Unit::Prompt {
                                id: Uuid::new_v4(),
                                name: "New Prompt".to_string(),
                                provider: Provider::OpenAI,
                                model: "gpt-4".to_string(),
                                system_prompt: String::new(),
                                user_prompt_template: "{{input}}".to_string(),
                            });
                        }

                        if ui.button("➕ Add Text Replacement").clicked() {
                            pipeline.add_unit(Unit::TextReplacement {
                                id: Uuid::new_v4(),
                                name: "New Replacement".to_string(),
                                find: String::new(),
                                replace: String::new(),
                                regex: false,
                                case_sensitive: true,
                            });
                        }
                    });

                    ui.add_space(10.0);

                    if pipeline.units.is_empty() {
                        ui.label("No units yet. Add a prompt or text replacement unit.");
                    } else {
                        // Render each unit
                        for (idx, unit) in pipeline.units.iter().enumerate() {
                            ui.separator();
                            ui.add_space(5.0);

                            ui.horizontal(|ui| {
                                ui.label(format!("{}. {}", idx + 1, unit.name()));

                                if ui.button("🗑").clicked() {
                                    action = PipelinesAction::RemoveUnit(idx);
                                }
                            });

                            // Show unit type
                            match unit {
                                Unit::Prompt { provider, model, .. } => {
                                    ui.label(format!("Type: Prompt ({:?} / {})", provider, model));
                                }
                                Unit::TextReplacement { find, replace, .. } => {
                                    ui.label(format!("Type: Replace '{}' → '{}'", find, replace));
                                }
                            }
                        }
                    }
                });
            });
        }

        // Handle actions after borrowing ends
        if should_save {
            if let Some(pipeline) = self.editing_pipeline.clone() {
                action = PipelinesAction::Save(pipeline);
                self.show_editor = false;
                self.editing_pipeline = None;
            }
        } else if should_close {
            self.show_editor = false;
            self.editing_pipeline = None;
        }

        action
    }
}

pub enum PipelinesAction {
    None,
    Save(Pipeline),
    Delete(Uuid),
    Execute(Uuid),
    RemoveUnit(usize),
}
