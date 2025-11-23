use chrono::{DateTime, Local};
use serde::{Deserialize, Serialize};

/// Log level for structured logging
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
pub enum LogLevel {
    Info,
    Success,
    Warning,
    Error,
}

impl LogLevel {
    /// Returns the color for this log level
    pub fn color(&self) -> egui::Color32 {
        match self {
            LogLevel::Info => egui::Color32::from_rgb(200, 200, 200),     // Light gray
            LogLevel::Success => egui::Color32::from_rgb(76, 175, 80),    // Green
            LogLevel::Warning => egui::Color32::from_rgb(255, 152, 0),    // Orange
            LogLevel::Error => egui::Color32::from_rgb(244, 67, 54),      // Red
        }
    }

    /// Returns the icon/symbol for this log level
    pub fn icon(&self) -> &'static str {
        match self {
            LogLevel::Info => "ℹ",
            LogLevel::Success => "✓",
            LogLevel::Warning => "⚠",
            LogLevel::Error => "✗",
        }
    }

    /// Returns the prefix for this log level
    pub fn prefix(&self) -> &'static str {
        match self {
            LogLevel::Info => "INFO",
            LogLevel::Success => "SUCCESS",
            LogLevel::Warning => "WARNING",
            LogLevel::Error => "ERROR",
        }
    }
}

/// A structured log entry with timestamp, level, and message
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct LogEntry {
    pub timestamp: DateTime<Local>,
    pub level: LogLevel,
    pub message: String,
}

impl LogEntry {
    /// Creates a new log entry
    pub fn new(level: LogLevel, message: impl Into<String>) -> Self {
        Self {
            timestamp: Local::now(),
            level,
            message: message.into(),
        }
    }

    /// Creates an info log entry
    pub fn info(message: impl Into<String>) -> Self {
        Self::new(LogLevel::Info, message)
    }

    /// Creates a success log entry
    pub fn success(message: impl Into<String>) -> Self {
        Self::new(LogLevel::Success, message)
    }

    /// Creates a warning log entry
    pub fn warning(message: impl Into<String>) -> Self {
        Self::new(LogLevel::Warning, message)
    }

    /// Creates an error log entry
    pub fn error(message: impl Into<String>) -> Self {
        Self::new(LogLevel::Error, message)
    }

    /// Formats the timestamp as HH:MM:SS
    pub fn time_str(&self) -> String {
        self.timestamp.format("%H:%M:%S").to_string()
    }

    /// Renders this log entry in the UI
    pub fn render(&self, ui: &mut egui::Ui) {
        ui.horizontal(|ui| {
            // Timestamp in gray
            ui.colored_label(
                egui::Color32::from_rgb(128, 128, 128),
                self.time_str()
            );

            ui.add_space(5.0);

            // Icon and level in level-specific color
            ui.colored_label(
                self.level.color(),
                format!("{} {}", self.level.icon(), self.level.prefix())
            );

            ui.add_space(5.0);

            // Message
            ui.label(&self.message);
        });
    }
}

/// Logger that maintains a history of log entries
pub struct StructuredLogger {
    entries: Vec<LogEntry>,
    max_entries: usize,
}

impl Default for StructuredLogger {
    fn default() -> Self {
        Self::new()
    }
}

impl StructuredLogger {
    /// Creates a new logger with default capacity
    pub fn new() -> Self {
        Self::with_capacity(1000)
    }

    /// Creates a new logger with specified capacity
    pub fn with_capacity(max_entries: usize) -> Self {
        Self {
            entries: Vec::new(),
            max_entries,
        }
    }

    /// Adds a log entry
    pub fn log(&mut self, entry: LogEntry) {
        self.entries.push(entry);

        // Keep only the most recent entries
        if self.entries.len() > self.max_entries {
            self.entries.remove(0);
        }
    }

    /// Adds an info log
    pub fn info(&mut self, message: impl Into<String>) {
        self.log(LogEntry::info(message));
    }

    /// Adds a success log
    pub fn success(&mut self, message: impl Into<String>) {
        self.log(LogEntry::success(message));
    }

    /// Adds a warning log
    pub fn warning(&mut self, message: impl Into<String>) {
        self.log(LogEntry::warning(message));
    }

    /// Adds an error log
    pub fn error(&mut self, message: impl Into<String>) {
        self.log(LogEntry::error(message));
    }

    /// Returns all log entries
    pub fn entries(&self) -> &[LogEntry] {
        &self.entries
    }

    /// Clears all log entries
    pub fn clear(&mut self) {
        self.entries.clear();
    }

    /// Renders the log in the UI
    pub fn render(&self, ui: &mut egui::Ui) {
        egui::ScrollArea::vertical()
            .stick_to_bottom(true)
            .auto_shrink([false, false])
            .show(ui, |ui| {
                if self.entries.is_empty() {
                    ui.colored_label(
                        egui::Color32::from_rgb(128, 128, 128),
                        "No log entries yet..."
                    );
                } else {
                    for entry in &self.entries {
                        entry.render(ui);
                    }
                }
            });
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_log_entry_creation() {
        let entry = LogEntry::info("Test message");
        assert_eq!(entry.level, LogLevel::Info);
        assert_eq!(entry.message, "Test message");
    }

    #[test]
    fn test_logger_capacity() {
        let mut logger = StructuredLogger::with_capacity(3);
        logger.info("Message 1");
        logger.info("Message 2");
        logger.info("Message 3");
        logger.info("Message 4");

        assert_eq!(logger.entries().len(), 3);
        assert_eq!(logger.entries()[0].message, "Message 2");
    }

    #[test]
    fn test_log_levels() {
        let mut logger = StructuredLogger::new();
        logger.info("Info");
        logger.success("Success");
        logger.warning("Warning");
        logger.error("Error");

        assert_eq!(logger.entries().len(), 4);
        assert_eq!(logger.entries()[0].level, LogLevel::Info);
        assert_eq!(logger.entries()[1].level, LogLevel::Success);
        assert_eq!(logger.entries()[2].level, LogLevel::Warning);
        assert_eq!(logger.entries()[3].level, LogLevel::Error);
    }
}
