use whispercat::pipeline::{Pipeline, Unit, Provider, PipelineExecutor};
use uuid::Uuid;

#[test]
fn test_pipeline_creation() {
    let pipeline = Pipeline::new("Test Pipeline".to_string());

    assert_eq!(pipeline.name, "Test Pipeline");
    assert!(pipeline.description.is_none());
    assert_eq!(pipeline.units.len(), 0);
}

#[test]
fn test_pipeline_add_unit() {
    let mut pipeline = Pipeline::new("Test".to_string());

    let unit = Unit::Prompt {
        id: Uuid::new_v4(),
        name: "Prompt 1".to_string(),
        provider: Provider::OpenAI,
        model: "gpt-4".to_string(),
        system_prompt: "You are a helpful assistant".to_string(),
        user_prompt_template: "Process: {{input}}".to_string(),
    };

    pipeline.add_unit(unit);
    assert_eq!(pipeline.units.len(), 1);
    assert_eq!(pipeline.units[0].name(), "Prompt 1");
}

#[test]
fn test_unit_text_replacement() {
    let unit = Unit::TextReplacement {
        id: Uuid::new_v4(),
        name: "Replace test".to_string(),
        find: "hello".to_string(),
        replace: "hi".to_string(),
        regex: false,
        case_sensitive: true,
    };

    assert_eq!(unit.name(), "Replace test");
}

#[test]
fn test_pipeline_unit_ordering() {
    let mut pipeline = Pipeline::new("Multi-unit".to_string());

    // Add multiple units
    for i in 1..=3 {
        pipeline.add_unit(Unit::Prompt {
            id: Uuid::new_v4(),
            name: format!("Unit {}", i),
            provider: Provider::OpenAI,
            model: "gpt-4".to_string(),
            system_prompt: String::new(),
            user_prompt_template: "{{input}}".to_string(),
        });
    }

    assert_eq!(pipeline.units.len(), 3);
    assert_eq!(pipeline.units[0].name(), "Unit 1");
    assert_eq!(pipeline.units[1].name(), "Unit 2");
    assert_eq!(pipeline.units[2].name(), "Unit 3");
}

#[test]
fn test_pipeline_with_description() {
    let mut pipeline = Pipeline::new("Test".to_string());
    pipeline.description = Some("This is a test pipeline".to_string());

    assert_eq!(pipeline.description, Some("This is a test pipeline".to_string()));
}

#[test]
fn test_pipeline_mixed_units() {
    let mut pipeline = Pipeline::new("Mixed".to_string());

    // Add prompt unit
    pipeline.add_unit(Unit::Prompt {
        id: Uuid::new_v4(),
        name: "Prompt".to_string(),
        provider: Provider::OpenAI,
        model: "gpt-4".to_string(),
        system_prompt: String::new(),
        user_prompt_template: "{{input}}".to_string(),
    });

    // Add text replacement unit
    pipeline.add_unit(Unit::TextReplacement {
        id: Uuid::new_v4(),
        name: "Replace".to_string(),
        find: "old".to_string(),
        replace: "new".to_string(),
        regex: false,
        case_sensitive: true,
    });

    assert_eq!(pipeline.units.len(), 2);
}

#[test]
fn test_pipeline_updated_at_changes() {
    let mut pipeline = Pipeline::new("Test".to_string());
    let initial_time = pipeline.updated_at;

    // Sleep briefly to ensure time difference
    std::thread::sleep(std::time::Duration::from_millis(10));

    // Add a unit which should update the timestamp
    pipeline.add_unit(Unit::Prompt {
        id: Uuid::new_v4(),
        name: "Test".to_string(),
        provider: Provider::OpenAI,
        model: "gpt-4".to_string(),
        system_prompt: String::new(),
        user_prompt_template: "{{input}}".to_string(),
    });

    assert!(pipeline.updated_at > initial_time);
}

#[test]
fn test_pipeline_executor_creation() {
    let executor = PipelineExecutor::new("test_api_key".to_string());

    // Just verify it can be created - we can't test much without a real API
    // This tests the basic instantiation
    drop(executor);
}

#[test]
fn test_pipeline_executor_with_optimization() {
    let executor = PipelineExecutor::new("test_key".to_string())
        .with_optimization(false);

    drop(executor);
}
