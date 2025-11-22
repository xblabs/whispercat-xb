use crate::config::Config;
use crate::error::Result;
use egui::{Context, RichText, Ui};

pub struct RecordingScreen {
    pub is_recording: bool,
    pub recording_duration: Option<std::time::Duration>,
    pub transcription_result: Option<String>,
    pub is_transcribing: bool,
    pub error_message: Option<String>,
}

impl Default for RecordingScreen {
    fn default() -> Self {
        Self {
            is_recording: false,
            recording_duration: None,
            transcription_result: None,
            is_transcribing: false,
            error_message: None,
        }
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

                        if ui.button("🔄 Process with Pipeline").clicked() {
                            action = RecordingAction::ProcessPipeline;
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
    pub silence_threshold: f32,
    pub min_silence_duration: u32,
    pub silence_removal_enabled: bool,
}

impl SettingsScreen {
    pub fn from_config(config: &Config) -> Self {
        Self {
            api_key_input: config.whisper.api_key.clone(),
            silence_threshold: config.silence_removal.threshold,
            min_silence_duration: config.silence_removal.min_duration_ms,
            silence_removal_enabled: config.silence_removal.enabled,
        }
    }

    pub fn ui(&mut self, ui: &mut Ui) -> SettingsAction {
        let mut action = SettingsAction::None;

        ui.heading("⚙️ Settings");
        ui.add_space(20.0);

        // API Key
        ui.group(|ui| {
            ui.vertical(|ui| {
                ui.label(RichText::new("OpenAI API Key").strong());
                ui.add_space(5.0);

                let response = ui.add(
                    egui::TextEdit::singleline(&mut self.api_key_input)
                        .password(true)
                        .hint_text("sk-..."),
                );

                if response.changed() {
                    action = SettingsAction::SaveConfig;
                }
            });
        });

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

        action
    }

    pub fn to_config(&self, config: &mut Config) {
        config.whisper.api_key = self.api_key_input.clone();
        config.silence_removal.threshold = self.silence_threshold;
        config.silence_removal.min_duration_ms = self.min_silence_duration;
        config.silence_removal.enabled = self.silence_removal_enabled;
    }
}

pub enum SettingsAction {
    None,
    SaveConfig,
}
