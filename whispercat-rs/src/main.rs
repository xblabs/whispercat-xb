mod app;
mod audio;
mod autopaste;
mod config;
mod error;
mod hotkey;
mod pipeline;
mod transcription;
mod ui;

use app::App;

#[tokio::main]
async fn main() -> Result<(), Box<dyn std::error::Error>> {
    // Initialize logging
    tracing_subscriber::fmt()
        .with_env_filter(
            tracing_subscriber::EnvFilter::try_from_default_env()
                .unwrap_or_else(|_| tracing_subscriber::EnvFilter::new("info")),
        )
        .init();

    tracing::info!("Starting WhisperCat Rust PoC v0.1.0");

    // Run the GUI application
    let native_options = eframe::NativeOptions {
        viewport: egui::ViewportBuilder::default()
            .with_inner_size([800.0, 600.0])
            .with_min_inner_size([600.0, 400.0]),
        ..Default::default()
    };

    eframe::run_native(
        "WhisperCat",
        native_options,
        Box::new(|cc| Box::new(App::new(cc))),
    )
    .map_err(|e| e.into())
}
