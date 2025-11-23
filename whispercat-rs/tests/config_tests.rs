use whispercat::config::Config;
use whispercat::pipeline::{Pipeline, Unit, Provider};
use uuid::Uuid;
use std::fs;

fn cleanup_test_config() {
    // Clean up any test config files
    if let Some(config_dir) = dirs::config_dir() {
        let test_path = config_dir.join("whispercat").join("config.toml");
        if test_path.exists() {
            let _ = fs::remove_file(test_path);
        }
    }
}

#[test]
fn test_config_default() {
    let config = Config::default();

    assert_eq!(config.whisper.provider, "OpenAI");
    assert_eq!(config.whisper.model, "whisper-1");
    assert_eq!(config.audio.sample_rate, 16000);
    assert_eq!(config.audio.channels, 1);
    assert!(config.silence_removal.enabled);
    assert_eq!(config.pipelines.len(), 0);
    assert_eq!(config.last_used_pipeline, None);
}

#[test]
fn test_config_pipeline_crud() {
    let mut config = Config::default();

    // Create a pipeline
    let mut pipeline = Pipeline::new("Test Pipeline".to_string());
    pipeline.add_unit(Unit::Prompt {
        id: Uuid::new_v4(),
        name: "Test Unit".to_string(),
        provider: Provider::OpenAI,
        model: "gpt-4".to_string(),
        system_prompt: "Test".to_string(),
        user_prompt_template: "{{input}}".to_string(),
    });
    let pipeline_id = pipeline.id;

    // Test save
    config.save_pipeline(pipeline.clone());
    assert_eq!(config.get_pipelines().len(), 1);

    // Test get
    let retrieved = config.get_pipeline(pipeline_id);
    assert!(retrieved.is_some());
    assert_eq!(retrieved.unwrap().name, "Test Pipeline");

    // Test update
    let mut updated_pipeline = pipeline.clone();
    updated_pipeline.name = "Updated Name".to_string();
    config.save_pipeline(updated_pipeline);
    assert_eq!(config.get_pipelines().len(), 1); // Should still be 1
    assert_eq!(config.get_pipeline(pipeline_id).unwrap().name, "Updated Name");

    // Test delete
    config.delete_pipeline(pipeline_id);
    assert_eq!(config.get_pipelines().len(), 0);
    assert!(config.get_pipeline(pipeline_id).is_none());
}

#[test]
fn test_config_last_used_pipeline() {
    let mut config = Config::default();

    assert_eq!(config.get_last_used_pipeline(), None);

    let pipeline_id = Uuid::new_v4();
    config.set_last_used_pipeline(Some(pipeline_id));

    assert_eq!(config.get_last_used_pipeline(), Some(pipeline_id));

    config.set_last_used_pipeline(None);
    assert_eq!(config.get_last_used_pipeline(), None);
}

#[test]
fn test_config_delete_last_used_pipeline() {
    let mut config = Config::default();

    let mut pipeline = Pipeline::new("Test".to_string());
    let pipeline_id = pipeline.id;

    config.save_pipeline(pipeline);
    config.set_last_used_pipeline(Some(pipeline_id));

    // Delete the pipeline
    config.delete_pipeline(pipeline_id);

    // Last used should be cleared
    assert_eq!(config.get_last_used_pipeline(), None);
}

#[test]
fn test_config_multiple_pipelines() {
    let mut config = Config::default();

    // Add multiple pipelines
    for i in 1..=5 {
        let pipeline = Pipeline::new(format!("Pipeline {}", i));
        config.save_pipeline(pipeline);
    }

    assert_eq!(config.get_pipelines().len(), 5);
}

#[test]
fn test_config_whisper_settings() {
    let mut config = Config::default();

    config.whisper.api_key = "test_key".to_string();
    config.whisper.model = "gpt-4".to_string();
    config.whisper.provider = "OpenAI".to_string();

    assert_eq!(config.whisper.api_key, "test_key");
    assert_eq!(config.whisper.model, "gpt-4");
    assert_eq!(config.whisper.provider, "OpenAI");
}

#[test]
fn test_config_audio_settings() {
    let mut config = Config::default();

    config.audio.sample_rate = 44100;
    config.audio.channels = 2;

    assert_eq!(config.audio.sample_rate, 44100);
    assert_eq!(config.audio.channels, 2);
}

#[test]
fn test_config_silence_removal_settings() {
    let mut config = Config::default();

    config.silence_removal.enabled = false;
    config.silence_removal.threshold = 0.02;
    config.silence_removal.min_duration_ms = 2000;

    assert!(!config.silence_removal.enabled);
    assert_eq!(config.silence_removal.threshold, 0.02);
    assert_eq!(config.silence_removal.min_duration_ms, 2000);
}

#[test]
fn test_config_hotkey_settings() {
    let mut config = Config::default();

    config.hotkeys.record_toggle = "Ctrl+Alt+R".to_string();

    assert_eq!(config.hotkeys.record_toggle, "Ctrl+Alt+R");
}

#[test]
fn test_config_ui_settings() {
    let mut config = Config::default();

    config.ui.auto_paste = true;
    config.ui.theme = "Light".to_string();

    assert!(config.ui.auto_paste);
    assert_eq!(config.ui.theme, "Light");
}

#[test]
fn test_config_provider_urls() {
    let mut config = Config::default();

    config.whisper.faster_whisper_url = Some("http://localhost:8000".to_string());
    config.whisper.openwebui_url = Some("http://localhost:8080".to_string());
    config.whisper.openwebui_api_key = Some("test_key".to_string());

    assert_eq!(config.whisper.faster_whisper_url, Some("http://localhost:8000".to_string()));
    assert_eq!(config.whisper.openwebui_url, Some("http://localhost:8080".to_string()));
    assert_eq!(config.whisper.openwebui_api_key, Some("test_key".to_string()));
}
