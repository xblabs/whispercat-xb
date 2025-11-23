use whispercat::logger::{LogLevel, LogEntry, StructuredLogger};

#[test]
fn test_log_level_colors() {
    use egui::Color32;

    let info_color = LogLevel::Info.color();
    let success_color = LogLevel::Success.color();
    let warning_color = LogLevel::Warning.color();
    let error_color = LogLevel::Error.color();

    // Verify they are different colors
    assert_ne!(info_color, success_color);
    assert_ne!(success_color, warning_color);
    assert_ne!(warning_color, error_color);
}

#[test]
fn test_log_level_icons() {
    assert_eq!(LogLevel::Info.icon(), "ℹ");
    assert_eq!(LogLevel::Success.icon(), "✓");
    assert_eq!(LogLevel::Warning.icon(), "⚠");
    assert_eq!(LogLevel::Error.icon(), "✗");
}

#[test]
fn test_structured_logger_creation() {
    let logger = StructuredLogger::new();
    assert_eq!(logger.entries().len(), 0);
}

#[test]
fn test_structured_logger_add_entries() {
    let mut logger = StructuredLogger::new();

    logger.info("Info message".to_string());
    logger.success("Success message".to_string());
    logger.warning("Warning message".to_string());
    logger.error("Error message".to_string());

    assert_eq!(logger.entries().len(), 4);
}

#[test]
fn test_structured_logger_entry_levels() {
    let mut logger = StructuredLogger::new();

    logger.info("Info".to_string());
    logger.success("Success".to_string());
    logger.warning("Warning".to_string());
    logger.error("Error".to_string());

    let entries = logger.entries();
    assert_eq!(entries[0].level, LogLevel::Info);
    assert_eq!(entries[1].level, LogLevel::Success);
    assert_eq!(entries[2].level, LogLevel::Warning);
    assert_eq!(entries[3].level, LogLevel::Error);
}

#[test]
fn test_structured_logger_clear() {
    let mut logger = StructuredLogger::new();

    logger.info("Test 1".to_string());
    logger.info("Test 2".to_string());
    logger.info("Test 3".to_string());

    assert_eq!(logger.entries().len(), 3);

    logger.clear();
    assert_eq!(logger.entries().len(), 0);
}

#[test]
fn test_structured_logger_capacity() {
    let mut logger = StructuredLogger::new();

    // Add more than the max capacity (1000 entries)
    for i in 0..1100 {
        logger.info(format!("Message {}", i));
    }

    // Should be capped at 1000
    assert_eq!(logger.entries().len(), 1000);
}

#[test]
fn test_structured_logger_oldest_removed() {
    let mut logger = StructuredLogger::new();

    // Add exactly 1000 entries
    for i in 0..1000 {
        logger.info(format!("Message {}", i));
    }

    // Add one more
    logger.info("Message 1000".to_string());

    // Should still be 1000
    assert_eq!(logger.entries().len(), 1000);

    // The oldest (Message 0) should be removed
    let first_message = &logger.entries()[0].message;
    assert_ne!(first_message, "Message 0");
}

#[test]
fn test_log_entry_timestamps() {
    let mut logger = StructuredLogger::new();

    logger.info("First".to_string());
    std::thread::sleep(std::time::Duration::from_millis(10));
    logger.info("Second".to_string());

    let entries = logger.entries();
    assert!(entries[1].timestamp >= entries[0].timestamp);
}

#[test]
fn test_log_entry_message_content() {
    let mut logger = StructuredLogger::new();

    let test_message = "This is a test message with special chars: !@#$%^&*()".to_string();
    logger.info(test_message.clone());

    assert_eq!(logger.entries()[0].message, test_message);
}
