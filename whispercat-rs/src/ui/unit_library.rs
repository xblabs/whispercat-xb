use crate::config::Config;
use crate::pipeline::{ProcessingUnit, UnitType, Provider};
use egui::{RichText, Ui};
use uuid::Uuid;
use chrono::Utc;

pub struct UnitLibraryScreen {
    pub units: Vec<ProcessingUnit>,
    pub editing_unit: Option<ProcessingUnit>,
    pub show_editor: bool,

    // Editor state
    pub unit_name: String,
    pub unit_description: String,
    pub unit_type_selection: usize, // 0 = Prompt, 1 = Text Replacement

    // Prompt unit fields
    pub provider_selection: usize, // 0 = OpenAI, 1 = FasterWhisper, 2 = OpenWebUI
    pub model: String,
    pub system_prompt: String,
    pub user_prompt: String,

    // Text replacement fields
    pub find_text: String,
    pub replace_text: String,
    pub is_regex: bool,
    pub is_case_sensitive: bool,
}

impl UnitLibraryScreen {
    pub fn from_config(config: &Config) -> Self {
        Self {
            units: config.get_processing_units().to_vec(),
            editing_unit: None,
            show_editor: false,
            unit_name: String::new(),
            unit_description: String::new(),
            unit_type_selection: 0,
            provider_selection: 0,
            model: "gpt-4o".to_string(),
            system_prompt: String::new(),
            user_prompt: "{{input}}".to_string(),
            find_text: String::new(),
            replace_text: String::new(),
            is_regex: false,
            is_case_sensitive: true,
        }
    }

    pub fn refresh_from_config(&mut self, config: &Config) {
        self.units = config.get_processing_units().to_vec();
        // If editing a unit, refresh it from config
        if let Some(ref unit) = self.editing_unit {
            if let Some(updated_unit) = config.get_processing_unit(unit.id) {
                self.editing_unit = Some(updated_unit.clone());
                self.load_unit_into_editor(updated_unit);
            }
        }
    }

    fn load_unit_into_editor(&mut self, unit: &ProcessingUnit) {
        self.unit_name = unit.name.clone();
        self.unit_description = unit.description.clone().unwrap_or_default();

        match &unit.unit_type {
            UnitType::Prompt { provider, model, system_prompt, user_prompt_template } => {
                self.unit_type_selection = 0;
                self.provider_selection = match provider {
                    Provider::OpenAI => 0,
                    Provider::FasterWhisper => 1,
                    Provider::OpenWebUI => 2,
                };
                self.model = model.clone();
                self.system_prompt = system_prompt.clone();
                self.user_prompt = user_prompt_template.clone();
            }
            UnitType::TextReplacement { find, replace, regex, case_sensitive } => {
                self.unit_type_selection = 1;
                self.find_text = find.clone();
                self.replace_text = replace.clone();
                self.is_regex = *regex;
                self.is_case_sensitive = *case_sensitive;
            }
        }
    }

    fn clear_editor(&mut self) {
        self.unit_name.clear();
        self.unit_description.clear();
        self.unit_type_selection = 0;
        self.provider_selection = 0;
        self.model = "gpt-4o".to_string();
        self.system_prompt.clear();
        self.user_prompt = "{{input}}".to_string();
        self.find_text.clear();
        self.replace_text.clear();
        self.is_regex = false;
        self.is_case_sensitive = true;
    }

    pub fn ui(&mut self, ui: &mut Ui) -> UnitLibraryAction {
        let mut action = UnitLibraryAction::None;

        if self.show_editor {
            action = self.render_editor(ui);
        } else {
            action = self.render_list(ui);
        }

        action
    }

    fn render_list(&mut self, ui: &mut Ui) -> UnitLibraryAction {
        let mut action = UnitLibraryAction::None;

        ui.heading("📚 Processing Unit Library");
        ui.add_space(10.0);

        ui.label("Create reusable processing units that can be used across multiple pipelines.");
        ui.add_space(10.0);

        ui.horizontal(|ui| {
            if ui.button("➕ New Processing Unit").clicked() {
                self.clear_editor();
                self.editing_unit = None;
                self.show_editor = true;
            }
        });

        ui.add_space(10.0);
        ui.separator();
        ui.add_space(10.0);

        // Sort units by name
        let mut sorted_units = self.units.clone();
        sorted_units.sort_by(|a, b| a.name.to_lowercase().cmp(&b.name.to_lowercase()));

        if sorted_units.is_empty() {
            ui.label("No processing units yet. Create one to get started!");
        } else {
            for unit in &sorted_units {
                ui.group(|ui| {
                    ui.set_min_width(ui.available_width());

                    ui.vertical(|ui| {
                        ui.horizontal(|ui| {
                            ui.heading(&unit.name);
                            ui.add_space(10.0);

                            // Show unit type badge
                            let (type_text, type_color) = match &unit.unit_type {
                                UnitType::Prompt { provider, .. } => {
                                    (format!("Prompt ({:?})", provider), egui::Color32::from_rgb(100, 150, 255))
                                }
                                UnitType::TextReplacement { .. } => {
                                    ("Text Replacement".to_string(), egui::Color32::from_rgb(150, 255, 100))
                                }
                            };
                            ui.colored_label(type_color, type_text);
                        });

                        if let Some(desc) = &unit.description {
                            ui.label(desc);
                        }

                        ui.add_space(5.0);

                        ui.horizontal(|ui| {
                            if ui.button("✏️ Edit").clicked() {
                                self.editing_unit = Some(unit.clone());
                                self.load_unit_into_editor(unit);
                                self.show_editor = true;
                            }

                            if ui.button("🗑️ Delete").clicked() {
                                action = UnitLibraryAction::DeleteUnit(unit.id);
                            }
                        });
                    });
                });
                ui.add_space(5.0);
            }
        }

        action
    }

    fn render_editor(&mut self, ui: &mut Ui) -> UnitLibraryAction {
        let mut action = UnitLibraryAction::None;
        let mut should_close = false;
        let mut should_save = false;

        let is_new = self.editing_unit.is_none();
        let title = if is_new { "✨ New Processing Unit" } else { "✏️ Edit Processing Unit" };

        ui.heading(title);
        ui.add_space(10.0);

        ui.horizontal(|ui| {
            if ui.button("⬅ Back to Library").clicked() {
                should_close = true;
            }

            let save_enabled = !self.unit_name.trim().is_empty();
            let save_button = ui.add_enabled(save_enabled, egui::Button::new("💾 Save Unit"));
            if save_button.clicked() {
                should_save = true;
            }
        });

        ui.add_space(10.0);
        ui.separator();
        ui.add_space(10.0);

        // Unit metadata
        ui.group(|ui| {
            ui.vertical(|ui| {
                ui.label(RichText::new("Unit Details").strong());
                ui.add_space(5.0);

                ui.label("Name:");
                ui.add(
                    egui::TextEdit::singleline(&mut self.unit_name)
                        .desired_width(ui.available_width())
                );

                ui.add_space(5.0);

                ui.label("Description (optional):");
                ui.add(
                    egui::TextEdit::multiline(&mut self.unit_description)
                        .desired_width(ui.available_width())
                        .desired_rows(3)
                );
            });
        });

        ui.add_space(10.0);

        // Unit type selection
        ui.group(|ui| {
            ui.vertical(|ui| {
                ui.label(RichText::new("Unit Type").strong());
                ui.add_space(5.0);

                ui.horizontal(|ui| {
                    ui.selectable_value(&mut self.unit_type_selection, 0, "Prompt (AI Processing)");
                    ui.selectable_value(&mut self.unit_type_selection, 1, "Text Replacement");
                });

                ui.add_space(10.0);

                // Type-specific fields
                if self.unit_type_selection == 0 {
                    // Prompt unit fields
                    ui.label(RichText::new("Prompt Configuration").strong());
                    ui.add_space(5.0);

                    ui.label("Provider:");
                    ui.horizontal(|ui| {
                        ui.selectable_value(&mut self.provider_selection, 0, "OpenAI");
                        ui.selectable_value(&mut self.provider_selection, 1, "Faster-Whisper");
                        ui.selectable_value(&mut self.provider_selection, 2, "Open WebUI");
                    });

                    ui.add_space(5.0);

                    ui.label("Model:");
                    ui.add(
                        egui::TextEdit::singleline(&mut self.model)
                            .desired_width(ui.available_width())
                    );

                    ui.add_space(5.0);

                    ui.label("System Prompt:");
                    ui.add(
                        egui::TextEdit::multiline(&mut self.system_prompt)
                            .desired_width(ui.available_width())
                            .desired_rows(4)
                    );

                    ui.add_space(5.0);

                    ui.label("User Prompt Template (use {{input}} for the input text):");
                    ui.add(
                        egui::TextEdit::multiline(&mut self.user_prompt)
                            .desired_width(ui.available_width())
                            .desired_rows(3)
                    );

                } else {
                    // Text replacement fields
                    ui.label(RichText::new("Text Replacement Configuration").strong());
                    ui.add_space(5.0);

                    ui.label("Find:");
                    ui.add(
                        egui::TextEdit::singleline(&mut self.find_text)
                            .desired_width(ui.available_width())
                    );

                    ui.add_space(5.0);

                    ui.label("Replace:");
                    ui.add(
                        egui::TextEdit::singleline(&mut self.replace_text)
                            .desired_width(ui.available_width())
                    );

                    ui.add_space(5.0);

                    ui.checkbox(&mut self.is_regex, "Use Regular Expression");
                    ui.checkbox(&mut self.is_case_sensitive, "Case Sensitive");
                }
            });
        });

        if should_save {
            // Build the unit
            let unit_type = if self.unit_type_selection == 0 {
                UnitType::Prompt {
                    provider: match self.provider_selection {
                        1 => Provider::FasterWhisper,
                        2 => Provider::OpenWebUI,
                        _ => Provider::OpenAI,
                    },
                    model: self.model.clone(),
                    system_prompt: self.system_prompt.clone(),
                    user_prompt_template: self.user_prompt.clone(),
                }
            } else {
                UnitType::TextReplacement {
                    find: self.find_text.clone(),
                    replace: self.replace_text.clone(),
                    regex: self.is_regex,
                    case_sensitive: self.is_case_sensitive,
                }
            };

            let unit = if let Some(ref existing) = self.editing_unit {
                // Update existing unit
                ProcessingUnit {
                    id: existing.id,
                    name: self.unit_name.clone(),
                    description: if self.unit_description.is_empty() {
                        None
                    } else {
                        Some(self.unit_description.clone())
                    },
                    unit_type,
                    created_at: existing.created_at,
                    updated_at: Utc::now(),
                }
            } else {
                // Create new unit
                ProcessingUnit {
                    id: Uuid::new_v4(),
                    name: self.unit_name.clone(),
                    description: if self.unit_description.is_empty() {
                        None
                    } else {
                        Some(self.unit_description.clone())
                    },
                    unit_type,
                    created_at: Utc::now(),
                    updated_at: Utc::now(),
                }
            };

            action = UnitLibraryAction::SaveUnit(unit);
            should_close = true;
        }

        if should_close {
            self.show_editor = false;
            self.editing_unit = None;
            self.clear_editor();
        }

        action
    }
}

pub enum UnitLibraryAction {
    None,
    SaveUnit(ProcessingUnit),
    DeleteUnit(Uuid),
}
