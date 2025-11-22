use crate::audio::{AudioBuffer, AudioRecorder, SilenceRemover};
use crate::config::Config;
use crate::pipeline::{ExecutionResult, Pipeline, PipelineExecutor};
use crate::transcription::{TranscriptionClient, TranscriptionProvider, TranscriptionRequest};
use crate::ui::{RecordingAction, RecordingScreen, SettingsAction, SettingsScreen};
use std::path::PathBuf;
use std::sync::mpsc;

pub struct App {
    // Core state
    current_screen: Screen,

    // Recording state
    recorder: Option<AudioRecorder>,
    last_recording_path: Option<PathBuf>,

    // UI screens
    recording_screen: RecordingScreen,
    settings_screen: SettingsScreen,

    // Configuration
    config: Config,

    // Communication channels
    message_rx: mpsc::Receiver<AppMessage>,
    message_tx: mpsc::Sender<AppMessage>,

    // Logging
    execution_log: Vec<String>,

    // Pipeline state
    pipeline_executor: Option<PipelineExecutor>,
}

#[derive(Debug, Clone, Copy, PartialEq)]
pub enum Screen {
    Recording,
    Settings,
}

pub enum AppMessage {
    RecordingStarted,
    RecordingStopped { path: PathBuf },
    TranscriptionComplete { text: String },
    PipelineComplete { result: ExecutionResult },
    Error { message: String },
    Log { message: String },
}

impl App {
    pub fn new(_cc: &eframe::CreationContext) -> Self {
        let (tx, rx) = mpsc::channel();

        // Load config
        let config = Config::load().unwrap_or_default();

        let settings_screen = SettingsScreen::from_config(&config);

        // Create pipeline executor if API key is set
        let pipeline_executor = if !config.whisper.api_key.is_empty() {
            Some(PipelineExecutor::new(config.whisper.api_key.clone()))
        } else {
            None
        };

        Self {
            current_screen: Screen::Recording,
            recorder: None,
            last_recording_path: None,
            recording_screen: RecordingScreen::default(),
            settings_screen,
            config,
            message_rx: rx,
            message_tx: tx,
            execution_log: Vec::new(),
            pipeline_executor,
        }
    }

    fn start_recording(&mut self) {
        let config = self.config.audio.clone();
        let output_dir = PathBuf::from("./recordings");

        match AudioRecorder::new(config, output_dir) {
            Ok(mut recorder) => {
                if let Err(e) = recorder.start_recording() {
                    self.recording_screen.error_message =
                        Some(format!("Failed to start recording: {}", e));
                    self.add_log(format!("Error: {}", e));
                } else {
                    self.recorder = Some(recorder);
                    self.recording_screen.is_recording = true;
                    self.recording_screen.error_message = None;
                    self.add_log("Recording started".to_string());
                }
            }
            Err(e) => {
                self.recording_screen.error_message =
                    Some(format!("Failed to create recorder: {}", e));
                self.add_log(format!("Error: {}", e));
            }
        }
    }

    fn stop_recording(&mut self) {
        if let Some(mut recorder) = self.recorder.take() {
            let tx = self.message_tx.clone();
            self.recording_screen.is_recording = false;

            // Stop recording synchronously (drops the audio stream)
            match recorder.stop_recording() {
                Ok(path) => {
                    tx.send(AppMessage::RecordingStopped { path }).ok();
                }
                Err(e) => {
                    tx.send(AppMessage::Error {
                        message: format!("Failed to stop recording: {}", e),
                    })
                    .ok();
                }
            }
        }
    }

    fn transcribe(&mut self, audio_path: PathBuf) {
        let api_key = self.config.whisper.api_key.clone();
        let provider_str = self.config.whisper.provider.clone();
        let model = self.config.whisper.model.clone();
        let faster_whisper_url = self.config.whisper.faster_whisper_url.clone();
        let openwebui_url = self.config.whisper.openwebui_url.clone();
        let openwebui_api_key = self.config.whisper.openwebui_api_key.clone();

        // Parse provider from config
        let provider = match provider_str.as_str() {
            "FasterWhisper" | "Faster-Whisper" => TranscriptionProvider::FasterWhisper,
            "OpenWebUI" | "Open WebUI" => TranscriptionProvider::OpenWebUI,
            _ => TranscriptionProvider::OpenAI,
        };

        // Validate configuration based on provider
        let validation_error = match provider {
            TranscriptionProvider::OpenAI => {
                if api_key.is_empty() {
                    Some("Please set your OpenAI API key in Settings".to_string())
                } else {
                    None
                }
            }
            TranscriptionProvider::FasterWhisper => {
                if faster_whisper_url.is_none() {
                    Some("Please set Faster-Whisper server URL in Settings".to_string())
                } else {
                    None
                }
            }
            TranscriptionProvider::OpenWebUI => {
                if openwebui_url.is_none() || openwebui_api_key.is_none() {
                    Some("Please set Open WebUI URL and API key in Settings".to_string())
                } else {
                    None
                }
            }
        };

        if let Some(error) = validation_error {
            self.recording_screen.error_message = Some(error);
            return;
        }

        let tx = self.message_tx.clone();
        let silence_config = self.config.silence_removal.clone();

        self.recording_screen.is_transcribing = true;
        self.add_log(format!("Starting transcription with {}...", provider.as_str()));

        tokio::spawn(async move {
            // Step 1: Load audio
            let mut audio = match AudioBuffer::from_wav(&audio_path) {
                Ok(a) => a,
                Err(e) => {
                    tx.send(AppMessage::Error {
                        message: format!("Failed to load audio: {}", e),
                    })
                    .ok();
                    return;
                }
            };

            // Step 2: Conditional silence removal (only for longer recordings)
            let recording_duration_sec = audio.duration().as_secs();

            if silence_config.enabled && recording_duration_sec >= silence_config.min_recording_duration_sec as u64 {
                tx.send(AppMessage::Log {
                    message: format!("Recording duration: {:.1}s - applying silence removal...", recording_duration_sec),
                })
                .ok();

                let remover = SilenceRemover::new(
                    silence_config.threshold,
                    silence_config.min_duration_ms,
                );

                match remover.remove_silence(&audio) {
                    Ok((processed, analysis)) => {
                        let msg = format!(
                            "Silence removed: {:.1}% reduction (RMS: min={:.4}, max={:.4}, avg={:.4})",
                            analysis.reduction_percent,
                            analysis.min_rms,
                            analysis.max_rms,
                            analysis.avg_rms
                        );

                        tx.send(AppMessage::Log { message: msg }).ok();

                        audio = processed;

                        // Save processed audio
                        let processed_path = audio_path.with_extension("processed.wav");
                        audio.to_wav(&processed_path).ok();
                    }
                    Err(e) => {
                        tx.send(AppMessage::Error {
                            message: format!("Silence removal failed: {}", e),
                        })
                        .ok();
                        return;
                    }
                }
            } else if silence_config.enabled {
                tx.send(AppMessage::Log {
                    message: format!(
                        "⚠ Recording too short ({:.1}s < {}s) - skipping silence removal",
                        recording_duration_sec,
                        silence_config.min_recording_duration_sec
                    ),
                })
                .ok();
            }

            // Step 3: Transcribe
            tx.send(AppMessage::Log {
                message: format!("Sending to {} API...", provider.as_str()),
            })
            .ok();

            let client = TranscriptionClient::new(
                api_key,
                faster_whisper_url,
                openwebui_url,
                openwebui_api_key,
            );
            let request = TranscriptionRequest::new(audio_path, provider)
                .with_model(model);

            match client.transcribe(request).await {
                Ok(response) => {
                    tx.send(AppMessage::TranscriptionComplete {
                        text: response.text,
                    })
                    .ok();
                }
                Err(e) => {
                    tx.send(AppMessage::Error {
                        message: format!("Transcription failed: {}", e),
                    })
                    .ok();
                }
            }
        });
    }

    fn add_log(&mut self, message: String) {
        tracing::info!("{}", message);
        self.execution_log.push(message);

        // Keep log size manageable
        if self.execution_log.len() > 100 {
            self.execution_log.remove(0);
        }
    }

    fn process_messages(&mut self) {
        while let Ok(msg) = self.message_rx.try_recv() {
            match msg {
                AppMessage::RecordingStarted => {
                    self.add_log("Recording started".to_string());
                }
                AppMessage::RecordingStopped { path } => {
                    self.last_recording_path = Some(path.clone());
                    self.add_log(format!("Recording saved: {:?}", path));

                    // Auto-transcribe
                    self.transcribe(path);
                }
                AppMessage::TranscriptionComplete { text } => {
                    self.recording_screen.transcription_result = Some(text);
                    self.recording_screen.is_transcribing = false;
                    self.add_log("Transcription complete".to_string());
                }
                AppMessage::PipelineComplete { result } => {
                    self.recording_screen.transcription_result = Some(result.output);
                    self.add_log(format!(
                        "Pipeline complete in {:?}",
                        result.total_duration
                    ));
                }
                AppMessage::Error { message } => {
                    self.recording_screen.error_message = Some(message.clone());
                    self.recording_screen.is_transcribing = false;
                    self.add_log(format!("Error: {}", message));
                }
                AppMessage::Log { message } => {
                    self.add_log(message);
                }
            }
        }

        // Update recording duration
        if let Some(recorder) = &self.recorder {
            self.recording_screen.recording_duration = recorder.duration();
        }
    }

    fn save_config(&mut self) {
        self.settings_screen.to_config(&mut self.config);

        if let Err(e) = self.config.save() {
            tracing::error!("Failed to save config: {}", e);
            self.recording_screen.error_message = Some(format!("Failed to save config: {}", e));
        } else {
            tracing::info!("Config saved");

            // Update pipeline executor if API key changed
            if !self.config.whisper.api_key.is_empty() {
                self.pipeline_executor =
                    Some(PipelineExecutor::new(self.config.whisper.api_key.clone()));
            }
        }
    }
}

impl eframe::App for App {
    fn update(&mut self, ctx: &egui::Context, _frame: &mut eframe::Frame) {
        // Process background messages
        self.process_messages();

        // Top panel with navigation
        egui::TopBottomPanel::top("top_panel").show(ctx, |ui| {
            ui.horizontal(|ui| {
                ui.selectable_value(&mut self.current_screen, Screen::Recording, "🎤 Recording");
                ui.selectable_value(&mut self.current_screen, Screen::Settings, "⚙️ Settings");
            });
        });

        // Central panel with current screen
        egui::CentralPanel::default().show(ctx, |ui| {
            egui::ScrollArea::vertical().show(ui, |ui| {
                match self.current_screen {
                    Screen::Recording => {
                        let action = self.recording_screen.ui(ui);
                        match action {
                            RecordingAction::StartRecording => {
                                self.start_recording();
                            }
                            RecordingAction::StopRecording => {
                                self.stop_recording();
                            }
                            RecordingAction::ProcessPipeline => {
                                // TODO: Show pipeline selection dialog
                            }
                            RecordingAction::None => {}
                        }
                    }
                    Screen::Settings => {
                        let action = self.settings_screen.ui(ui);
                        match action {
                            SettingsAction::SaveConfig => {
                                self.save_config();
                            }
                            SettingsAction::None => {}
                        }
                    }
                }
            });
        });

        // Bottom panel with execution log
        egui::TopBottomPanel::bottom("log_panel")
            .min_height(100.0)
            .show(ctx, |ui| {
                ui.heading("Execution Log");
                ui.separator();

                egui::ScrollArea::vertical().max_height(80.0).show(ui, |ui| {
                    for log in self.execution_log.iter().rev().take(20) {
                        ui.label(log);
                    }
                });
            });

        // Request repaint if recording to update duration
        if self.recording_screen.is_recording {
            ctx.request_repaint();
        }
    }
}
