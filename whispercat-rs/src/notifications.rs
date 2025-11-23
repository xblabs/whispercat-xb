use chrono::{DateTime, Local};
use std::time::Duration;

/// Toast notification levels matching the logger
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum ToastLevel {
    Info,
    Success,
    Warning,
    Error,
}

impl ToastLevel {
    pub fn color(&self) -> egui::Color32 {
        match self {
            ToastLevel::Info => egui::Color32::from_rgb(33, 150, 243),
            ToastLevel::Success => egui::Color32::from_rgb(76, 175, 80),
            ToastLevel::Warning => egui::Color32::from_rgb(255, 152, 0),
            ToastLevel::Error => egui::Color32::from_rgb(244, 67, 54),
        }
    }

    pub fn icon(&self) -> &'static str {
        match self {
            ToastLevel::Info => "ℹ",
            ToastLevel::Success => "✓",
            ToastLevel::Warning => "⚠",
            ToastLevel::Error => "✗",
        }
    }
}

/// A single toast notification
#[derive(Debug, Clone)]
pub struct Toast {
    pub level: ToastLevel,
    pub message: String,
    pub created_at: DateTime<Local>,
    pub duration_secs: f32,
}

impl Toast {
    pub fn new(level: ToastLevel, message: String) -> Self {
        Self {
            level,
            message,
            created_at: Local::now(),
            duration_secs: 3.0, // Default 3 seconds
        }
    }

    pub fn with_duration(mut self, seconds: f32) -> Self {
        self.duration_secs = seconds;
        self
    }

    /// Check if this toast has expired
    pub fn is_expired(&self) -> bool {
        let elapsed = Local::now()
            .signed_duration_since(self.created_at)
            .num_milliseconds() as f32 / 1000.0;
        elapsed >= self.duration_secs
    }

    /// Get opacity based on remaining time (fade out in last 0.5s)
    pub fn opacity(&self) -> f32 {
        let elapsed = Local::now()
            .signed_duration_since(self.created_at)
            .num_milliseconds() as f32 / 1000.0;
        let remaining = self.duration_secs - elapsed;

        if remaining < 0.5 {
            (remaining / 0.5).max(0.0)
        } else {
            1.0
        }
    }
}

/// Manager for toast notifications
pub struct ToastManager {
    toasts: Vec<Toast>,
    max_toasts: usize,
}

impl Default for ToastManager {
    fn default() -> Self {
        Self::new()
    }
}

impl ToastManager {
    pub fn new() -> Self {
        Self {
            toasts: Vec::new(),
            max_toasts: 5,
        }
    }

    pub fn show(&mut self, level: ToastLevel, message: String) {
        self.toasts.push(Toast::new(level, message));
        self.cleanup();
    }

    pub fn show_with_duration(&mut self, level: ToastLevel, message: String, seconds: f32) {
        self.toasts.push(Toast::new(level, message).with_duration(seconds));
        self.cleanup();
    }

    pub fn info(&mut self, message: String) {
        self.show(ToastLevel::Info, message);
    }

    pub fn success(&mut self, message: String) {
        self.show(ToastLevel::Success, message);
    }

    pub fn warning(&mut self, message: String) {
        self.show(ToastLevel::Warning, message);
    }

    pub fn error(&mut self, message: String) {
        self.show(ToastLevel::Error, message);
    }

    /// Remove expired toasts
    fn cleanup(&mut self) {
        self.toasts.retain(|t| !t.is_expired());

        // Limit to max_toasts (keep newest)
        if self.toasts.len() > self.max_toasts {
            self.toasts.drain(0..self.toasts.len() - self.max_toasts);
        }
    }

    /// Render toasts in the top-right corner
    pub fn render(&mut self, ctx: &egui::Context) {
        // Cleanup first
        self.cleanup();

        if self.toasts.is_empty() {
            return;
        }

        let screen_rect = ctx.screen_rect();
        let margin = 10.0;
        let toast_width = 300.0;
        let toast_spacing = 5.0;

        let mut y_offset = margin;

        // Render toasts from top to bottom
        for toast in &self.toasts {
            let opacity = toast.opacity();
            if opacity <= 0.0 {
                continue;
            }

            egui::Area::new(egui::Id::new(format!("toast_{:?}", toast.created_at)))
                .fixed_pos(egui::pos2(
                    screen_rect.right() - toast_width - margin,
                    screen_rect.top() + y_offset,
                ))
                .show(ctx, |ui| {
                    egui::Frame::none()
                        .fill(egui::Color32::from_rgba_premultiplied(
                            40,
                            40,
                            40,
                            (200.0 * opacity) as u8,
                        ))
                        .stroke(egui::Stroke::new(
                            1.0,
                            toast.level.color().linear_multiply(opacity),
                        ))
                        .rounding(5.0)
                        .inner_margin(10.0)
                        .show(ui, |ui| {
                            ui.set_width(toast_width);

                            ui.horizontal(|ui| {
                                ui.label(
                                    egui::RichText::new(toast.level.icon())
                                        .size(18.0)
                                        .color(toast.level.color().linear_multiply(opacity)),
                                );

                                ui.label(
                                    egui::RichText::new(&toast.message)
                                        .color(egui::Color32::WHITE.linear_multiply(opacity)),
                                );
                            });
                        });
                });

            y_offset += 60.0 + toast_spacing; // Approximate toast height
        }
    }
}
