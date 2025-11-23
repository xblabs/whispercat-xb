use crate::audio::{AudioBuffer, AudioRecorder, SilenceRemover};
use crate::autopaste::AutoPaster;
use crate::config::Config;
use crate::hotkey::HotkeyManager;
use crate::logger::StructuredLogger;
use crate::pipeline::{ExecutionResult, Pipeline, PipelineExecutor};
use crate::transcription::{TranscriptionClient, TranscriptionProvider, TranscriptionRequest};
use crate::ui::{RecordingAction, RecordingScreen, SettingsAction, SettingsScreen, PipelinesAction, PipelinesScreen};
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
    pipelines_screen: PipelinesScreen,

    // Configuration
    config: Config,

    // Communication channels
    message_rx: mpsc::Receiver<AppMessage>,
    message_tx: mpsc::Sender<AppMessage>,

    // Logging
    logger: StructuredLogger,

    // Pipeline state
    pipeline_executor: Option<PipelineExecutor>,

    // Global hotkeys
    hotkey_manager: Option<HotkeyManager>,

    // Auto-paste
    auto_paster: Option<AutoPaster>,
}

#[derive(Debug, Clone, Copy, PartialEq)]
pub enum Screen {
    Recording,
    Settings,
    Pipelines,
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

        // Initialize hotkey manager
        let mut hotkey_manager = HotkeyManager::new().ok();
        if let Some(ref mut manager) = hotkey_manager {
            match manager.register_record_toggle(&config.hotkeys.record_toggle) {
                Ok(()) => {
                    tracing::info!("Global hotkey registered: {}", config.hotkeys.record_toggle);
                }
                Err(e) => {
                    tracing::warn!("Failed to register hotkey: {}", e);
                    hotkey_manager = None;
                }
            }
        }

        // Initialize auto-paster
        let auto_paster = AutoPaster::new().ok();
        if auto_paster.is_none() {
            tracing::warn!("Failed to initialize auto-paster - clipboard functionality may be limited");
        }

        Self {
            current_screen: Screen::Recording,
            recorder: None,
            last_recording_path: None,
            recording_screen: RecordingScreen::default(),
            settings_screen,
            pipelines_screen: PipelinesScreen::from_config(&config),
            config,
            message_rx: rx,
            message_tx: tx,
            logger: StructuredLogger::new(),
            pipeline_executor,
            hotkey_manager,
            auto_paster,
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

        // Determine log level based on message content
        if message.to_lowercase().contains("error") || message.to_lowercase().contains("failed") {
            self.logger.error(message);
        } else if message.to_lowercase().contains("warning") || message.to_lowercase().contains("⚠") {
            self.logger.warning(message);
        } else if message.to_lowercase().contains("complete") || message.to_lowercase().contains("success") || message.to_lowercase().contains("✓") {
            self.logger.success(message);
        } else {
            self.logger.info(message);
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
                    self.recording_screen.transcription_result = Some(text.clone());
                    self.recording_screen.is_transcribing = false;
                    self.add_log("Transcription complete".to_string());

                    // Auto-paste if enabled
                    if self.config.ui.auto_paste {
                        if let Some(ref mut paster) = self.auto_paster {
                            match paster.copy_and_paste(&text, true) {
                                Ok(()) => {
                                    self.add_log("Auto-pasted transcription result".to_string());
                                }
                                Err(e) => {
                                    tracing::warn!("Auto-paste failed: {}", e);
                                    self.add_log(format!("Auto-paste failed: {}", e));
                                }
                            }
                        }
                    }
                }
                AppMessage::PipelineComplete { result } => {
                    self.recording_screen.transcription_result = Some(result.output.clone());
                    self.add_log(format!(
                        "Pipeline complete in {:?}",
                        result.total_duration
                    ));

                    // Auto-paste if enabled
                    if self.config.ui.auto_paste {
                        if let Some(ref mut paster) = self.auto_paster {
                            match paster.copy_and_paste(&result.output, true) {
                                Ok(()) => {
                                    self.add_log("Auto-pasted pipeline result".to_string());
                                }
                                Err(e) => {
                                    tracing::warn!("Auto-paste failed: {}", e);
                                    self.add_log(format!("Auto-paste failed: {}", e));
                                }
                            }
                        }
                    }
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

        // Check for global hotkey events
        if let Some(ref hotkey_manager) = self.hotkey_manager {
            if hotkey_manager.check_events() {
                // Toggle recording on hotkey press
                if self.recording_screen.is_recording {
                    self.stop_recording();
                } else if !self.recording_screen.is_transcribing {
                    self.start_recording();
                }
            }
        }

        // Top panel with navigation
        egui::TopBottomPanel::top("top_panel").show(ctx, |ui| {
            ui.horizontal(|ui| {
                ui.selectable_value(&mut self.current_screen, Screen::Recording, "🎤 Recording");
                ui.selectable_value(&mut self.current_screen, Screen::Pipelines, "📋 Pipelines");
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
                    Screen::Pipelines => {
                        let action = self.pipelines_screen.ui(ui);
                        match action {
                            PipelinesAction::Save(pipeline) => {
                                self.config.save_pipeline(pipeline);
                                if let Err(e) = self.config.save() {
                                    self.add_log(format!("Failed to save pipeline: {}", e));
                                } else {
                                    self.add_log("Pipeline saved successfully".to_string());
                                    self.pipelines_screen.refresh_from_config(&self.config);
                                }
                            }
                            PipelinesAction::Delete(id) => {
                                self.config.delete_pipeline(id);
                                if let Err(e) = self.config.save() {
                                    self.add_log(format!("Failed to delete pipeline: {}", e));
                                } else {
                                    self.add_log("Pipeline deleted successfully".to_string());
                                    self.pipelines_screen.refresh_from_config(&self.config);
                                }
                            }
                            PipelinesAction::Execute(id) => {
                                if let Some(pipeline) = self.config.get_pipeline(id).cloned() {
                                    if let Some(text) = &self.recording_screen.transcription_result {
                                        if let Some(ref executor) = self.pipeline_executor {
                                            let executor_clone = executor.clone();
                                            let pipeline_clone = pipeline.clone();
                                            let text_clone = text.clone();
                                            let tx = self.message_tx.clone();

                                            self.add_log(format!("Executing pipeline: {}", pipeline.name));

                                            tokio::spawn(async move {
                                                match executor_clone.execute(text_clone, &pipeline_clone).await {
                                                    Ok(result) => {
                                                        tx.send(AppMessage::PipelineComplete { result }).ok();
                                                    }
                                                    Err(e) => {
                                                        tx.send(AppMessage::Error {
                                                            message: format!("Pipeline failed: {}", e),
                                                        }).ok();
                                                    }
                                                }
                                            });
                                        } else {
                                            self.add_log("No pipeline executor available".to_string());
                                        }
                                    } else {
                                        self.add_log("No transcription result to process".to_string());
                                    }
                                } else {
                                    self.add_log("Pipeline not found".to_string());
                                }
                            }
                            PipelinesAction::RemoveUnit(idx) => {
                                if let Some(ref mut pipeline) = self.pipelines_screen.editing_pipeline {
                                    if idx < pipeline.units.len() {
                                        pipeline.units.remove(idx);
                                    }
                                }
                            }
                            PipelinesAction::None => {}
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

        // Bottom panel with structured execution log
        egui::TopBottomPanel::bottom("log_panel")
            .min_height(150.0)
            .show(ctx, |ui| {
                ui.horizontal(|ui| {
                    ui.heading("Execution Log");
                    ui.add_space(10.0);
                    if ui.button("Clear").clicked() {
                        self.logger.clear();
                    }
                });
                ui.separator();

                egui::ScrollArea::vertical()
                    .max_height(120.0)
                    .auto_shrink([false, false])
                    .show(ui, |ui| {
                        self.logger.render(ui);
                    });
            });

        // Request repaint if recording to update duration
        if self.recording_screen.is_recording {
            ctx.request_repaint();
        }
    }
}
