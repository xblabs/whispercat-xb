# Manual Pipeline Runner Feature Specification

## Context & Current State

### Branch Information
**Current Branch:** `claude/windows-installer-setup-01VyrVetnQ39q5zxkygXhVdP`

**Latest Commit:** `ae6a56d` - "fix: run post-processing asynchronously to prevent UI blocking"

**To Continue This Work:**
```bash
# For next Claude session:
git fetch origin
git checkout claude/windows-installer-setup-01VyrVetnQ39q5zxkygXhVdP
git pull origin claude/windows-installer-setup-01VyrVetnQ39q5zxkygXhVdP

# Create new branch for manual pipeline feature:
git checkout -b claude/manual-pipeline-runner-[NEW_SESSION_ID]

# After work is complete, merge this branch:
git checkout claude/windows-installer-setup-01VyrVetnQ39q5zxkygXhVdP
git merge claude/manual-pipeline-runner-[NEW_SESSION_ID]
```

### Recent Fixes Completed
✅ **Async Post-Processing Fix** (Commit: ae6a56d)
- Transcription text now appears immediately when transcription completes
- Post-processing runs asynchronously in separate `PostProcessingWorker`
- UI stays responsive during pipeline execution
- Users can toggle settings during post-processing

✅ **Toast Notification Fix** (Commit: f39afe2)
- Set `alwaysOnTop(true)` so notifications appear when app is minimized

✅ **Smart Settings Save** (Commit: 3e66020)
- Only saves when settings actually modified (dirty flag tracking)
- Visual RMS threshold indicator on volume bar

✅ **Hotkey Save Order Fix** (Commit: e6b6e00)
- Settings saved before hotkeys reloaded to prevent data loss

---

## Feature Request: Manual Pipeline Runner

### Problem Statement

**Current Behavior:**
- Post-processing only runs automatically if checkbox is enabled BEFORE transcription
- Once transcription completes, user cannot run pipelines on the existing text
- No way to try different pipelines on same transcription
- No history of pipeline results

**User Scenario:**
> "I finished recording and got distracted while talking, started rambling. The transcription appeared but I didn't have post-processing enabled. Now I want to run my 'Remove Rambling' pipeline on this text, but I can't."

### Proposed Solution

Add manual pipeline execution capability with full history tracking per recording session.

---

## Detailed Feature Specification

### 1. Manual "Run Pipeline" Button

**Location:** Below the "Post-processed text" field, next to pipeline selector

**UI Layout:**
```
┌─────────────────────────────────────────────────┐
│ Transcription                             [Copy]│
│ ┌─────────────────────────────────────────────┐ │
│ │ [Original transcription from Whisper]       │ │
│ │                                             │ │
│ │                                             │ │
│ └─────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────┐
│ Post-processed text                       [Copy]│
│ ┌─────────────────────────────────────────────┐ │
│ │ [Result of pipeline execution]              │ │
│ │                                             │ │
│ └─────────────────────────────────────────────┘ │
│                                                   │
│ [Enable Post Processing ☐]                      │
│ Pipeline: [Select Pipeline ▼] [Run Pipeline]    │
│ [▼ Show Pipeline History (3 results)]           │
└─────────────────────────────────────────────────┘
```

**Behavior:**
- **"Run Pipeline" button** is ALWAYS enabled when:
  - Transcription text field has content (not empty)
  - A pipeline is selected in dropdown
- Button runs selected pipeline on transcription text
- Works regardless of "Enable Post Processing" checkbox state
- Shows loading indicator while pipeline runs (blue status circle)
- Non-blocking - runs in `PostProcessingWorker` (already implemented)

### 2. Pipeline History Tracking

**Data Structure:**
```java
class PipelineExecutionHistory {
    private String recordingId;  // UUID generated when transcription completes
    private String originalTranscription;
    private List<PipelineResult> results;

    static class PipelineResult {
        String pipelineUuid;
        String pipelineName;
        String resultText;
        long timestamp;
        int executionTimeMs;
    }
}
```

**Behavior:**
- New recording session starts when transcription completes
- Each manual pipeline run adds result to history
- Automatic pipeline (via checkbox) also tracked in history
- History is session-scoped (cleared on new recording)
- NOT persisted to disk (in-memory only)

### 3. Result Stacking Behavior

**When Running New Pipeline:**

**If post-processed text field is empty:**
- Result appears in post-processed text field

**If post-processed text field already has content:**
- Current content is added to history
- New result replaces content in post-processed text field

**Visual Feedback:**
- "Previous result saved to history" toast notification
- History count updates: "Show Pipeline History (3 results)"

### 4. Collapsible History Panel

**Initial State:** Hidden (collapsed)

**Toggle Button:** `[▼ Show Pipeline History (0 results)]`

**Expanded State:**
```
┌─────────────────────────────────────────────────┐
│ [▲ Hide Pipeline History (3 results)]           │
│ ┌───────────────────────────────────────────────┐│
│ │ Pipeline: Remove Rambling │ 2:34 PM │ [Copy] ││
│ │ ┌───────────────────────────────────────────┐ ││
│ │ │ [Result text from first pipeline run]    │ ││
│ │ └───────────────────────────────────────────┘ ││
│ ├───────────────────────────────────────────────┤│
│ │ Pipeline: Fix Grammar │ 2:36 PM │ [Copy]     ││
│ │ ┌───────────────────────────────────────────┐ ││
│ │ │ [Result text from second pipeline run]   │ ││
│ │ └───────────────────────────────────────────┘ ││
│ ├───────────────────────────────────────────────┤│
│ │ Pipeline: Summarize │ 2:38 PM │ [Copy]       ││
│ │ ┌───────────────────────────────────────────┐ ││
│ │ │ [Result text from third pipeline run]    │ ││
│ │ └───────────────────────────────────────────┘ ││
│ └───────────────────────────────────────────────┘│
└─────────────────────────────────────────────────┘
```

**History Panel Features:**
- Shows all previous results in reverse chronological order (newest at top)
- Each entry shows: pipeline name, timestamp, copy button
- Text areas read-only
- Scrollable if many results
- Collapses/expands smoothly (animated if possible)

### 5. Edge Cases & Validation

**Empty Transcription Field:**
- "Run Pipeline" button disabled
- Tooltip: "Record audio first"

**No Pipeline Selected:**
- "Run Pipeline" button disabled
- Tooltip: "Select a pipeline"

**Pipeline Running:**
- Button shows "Running..." with spinner
- Button disabled during execution
- Status indicator shows blue

**Pipeline Fails:**
- Error toast notification
- Console log shows error
- Previous result NOT replaced
- History NOT updated

**New Recording Starts:**
- History cleared
- Post-processed text cleared
- "Show History" button shows "(0 results)"

---

## Technical Implementation Guide

### Files to Modify

**1. RecorderForm.java** (Main changes)
- Add `PipelineExecutionHistory` instance variable
- Add "Run Pipeline" button
- Add history panel UI components
- Add `runManualPipeline()` method
- Modify `PostProcessingWorker.done()` to update history
- Add history panel toggle logic

**2. New Classes to Create**

**PipelineExecutionHistory.java:**
```java
package org.whispercat.recording;

public class PipelineExecutionHistory {
    private String recordingId;
    private String originalTranscription;
    private List<PipelineResult> results = new ArrayList<>();

    public static class PipelineResult {
        public String pipelineUuid;
        public String pipelineName;
        public String resultText;
        public long timestamp;
        public int executionTimeMs;

        public PipelineResult(String uuid, String name, String text) {
            this.pipelineUuid = uuid;
            this.pipelineName = name;
            this.resultText = text;
            this.timestamp = System.currentTimeMillis();
        }
    }

    public void startNewSession(String transcription) {
        this.recordingId = UUID.randomUUID().toString();
        this.originalTranscription = transcription;
        this.results.clear();
    }

    public void addResult(String pipelineUuid, String pipelineName, String resultText) {
        results.add(0, new PipelineResult(pipelineUuid, pipelineName, resultText));
    }

    public List<PipelineResult> getResults() {
        return new ArrayList<>(results);
    }

    public int getResultCount() {
        return results.size();
    }

    public boolean hasResults() {
        return !results.isEmpty();
    }
}
```

**HistoryPanel.java:** (Optional - could be inner class)
```java
package org.whispercat.recording;

public class HistoryPanel extends JPanel {
    private JButton toggleButton;
    private JPanel contentPanel;
    private boolean expanded = false;

    public HistoryPanel() {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        toggleButton = new JButton("▼ Show Pipeline History (0 results)");
        toggleButton.addActionListener(e -> toggleExpanded());
        add(toggleButton);

        contentPanel = new JPanel();
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
        contentPanel.setVisible(false);
        add(contentPanel);
    }

    public void updateResults(List<PipelineExecutionHistory.PipelineResult> results) {
        contentPanel.removeAll();

        for (PipelineResult result : results) {
            JPanel resultPanel = createResultPanel(result);
            contentPanel.add(resultPanel);
        }

        toggleButton.setText((expanded ? "▲ Hide" : "▼ Show") +
                            " Pipeline History (" + results.size() + " results)");
        contentPanel.revalidate();
        contentPanel.repaint();
    }

    private void toggleExpanded() {
        expanded = !expanded;
        contentPanel.setVisible(expanded);
        toggleButton.setText((expanded ? "▲ Hide" : "▼ Show") +
                            " Pipeline History (...)");
    }

    private JPanel createResultPanel(PipelineResult result) {
        // Create panel with pipeline name, timestamp, copy button, and text area
        // ...
    }
}
```

### Implementation Steps

**Phase 1: Basic Manual Runner**
1. Add "Run Pipeline" button to UI
2. Wire button to call `runManualPipeline()` method
3. Create method that:
   - Gets text from transcription field
   - Gets selected pipeline from dropdown
   - Creates `PostProcessingWorker` with transcript text
   - Executes worker
4. Test basic functionality

**Phase 2: History Tracking**
1. Create `PipelineExecutionHistory` class
2. Add instance variable to `RecorderForm`
3. Call `startNewSession()` when transcription completes
4. Modify `PostProcessingWorker.done()` to add result to history
5. Test history accumulation

**Phase 3: Result Stacking**
1. Before running new pipeline, check if post-processed field has content
2. If yes, get current content and add to history
3. Show "Previous result saved" toast
4. Let new result replace field content
5. Test stacking behavior

**Phase 4: History Panel UI**
1. Create `HistoryPanel` component
2. Add to RecorderForm below post-processed text
3. Wire toggle button
4. Implement `updateResults()` to rebuild panel
5. Test expand/collapse

**Phase 5: Polish**
1. Add copy buttons to history entries
2. Add timestamps formatting
3. Improve styling/spacing
4. Add smooth animations (optional)
5. Test all edge cases

---

## Current Code Context

### Key Files

**RecorderForm.java** - Main recording UI
- Line 36: `processedText` field (where results go)
- Line 47: `transcriptionTextArea` (source text)
- Line 54: `postProcessingSelectComboBox` (pipeline selector)
- Line 37: `enablePostProcessingCheckBox` (auto-run toggle)
- Lines 907-957: `PostProcessingWorker` class (async pipeline runner)

**PostProcessingService.java** - Pipeline execution
- `applyPipeline(String text, Pipeline pipeline)` - Main execution method
- Handles pipeline optimization, logging, etc.

**Pipeline.java** - Pipeline data structure
- `uuid`, `name`, `units` fields
- Stored in ConfigManager

**ConfigManager.java** - Settings persistence
- `getPipelineByUuid(String uuid)` - Get pipeline by UUID
- `getAllPipelines()` - Get all pipelines

### UI Layout Context

**Current RecorderForm Structure:**
```
RecorderForm (JPanel)
├── Center Panel (BoxLayout.Y_AXIS)
│   ├── Status Indicator + Record Button
│   ├── Transcription Text Area (JScrollPane)
│   │   └── transcriptionTextArea (JTextArea)
│   ├── Controls Panel (FlowLayout)
│   │   ├── Copy Button
│   │   ├── Paste from Clipboard Button
│   │   ├── Enable Post Processing Checkbox
│   │   └── Pipeline Selector ComboBox
│   ├── Post-processed Text Area (JScrollPane)
│   │   └── processedText (JTextArea)
│   └── Console Log Area (JScrollPane)
└── [NEW: Add History Panel here]
```

**Where to Insert New Components:**
- "Run Pipeline" button → Add to Controls Panel after pipeline selector
- History Panel → Add after processedText scroll pane, before console log

---

## Testing Checklist

### Manual Pipeline Runner
- [ ] Button enabled when transcription has content + pipeline selected
- [ ] Button disabled when transcription empty
- [ ] Button disabled when no pipeline selected
- [ ] Button shows "Running..." during execution
- [ ] Pipeline runs successfully on existing transcription
- [ ] Result appears in post-processed field
- [ ] Works when "Enable Post Processing" is unchecked
- [ ] Works when "Enable Post Processing" is checked
- [ ] Can run different pipelines sequentially
- [ ] Console log shows pipeline execution details

### History Tracking
- [ ] History starts empty on new recording
- [ ] Automatic pipeline run adds to history
- [ ] Manual pipeline run adds to history
- [ ] History count updates correctly
- [ ] New recording clears history
- [ ] History persists during screen navigation
- [ ] History cleared on app restart (in-memory only)

### Result Stacking
- [ ] First pipeline run: result goes to post-processed field
- [ ] Second pipeline run: previous result saved to history
- [ ] Third+ pipeline run: previous results accumulate
- [ ] "Previous result saved" toast appears
- [ ] History panel count increments

### History Panel UI
- [ ] Panel initially collapsed
- [ ] Toggle button shows correct count
- [ ] Clicking toggle expands panel
- [ ] Clicking again collapses panel
- [ ] History entries show pipeline name
- [ ] History entries show timestamp
- [ ] Copy buttons work for each entry
- [ ] Text areas are read-only
- [ ] Panel scrolls if many results
- [ ] Newest results appear at top

### Edge Cases
- [ ] Running same pipeline twice works
- [ ] Pipeline error doesn't break history
- [ ] Very long result text displays correctly
- [ ] Empty pipeline result handled gracefully
- [ ] History panel with 10+ results scrolls properly
- [ ] Rapid sequential pipeline runs don't conflict

---

## UI Mockup (Detailed)

### Before Manual Pipeline Feature
```
┌───────────────────────────────────────────────────────┐
│  ● Start Recording                                     │
├───────────────────────────────────────────────────────┤
│ Transcription                                   [Copy] │
│ ┌───────────────────────────────────────────────────┐ │
│ │ Lorem ipsum dolor sit amet, consectetur...        │ │
│ │                                                   │ │
│ └───────────────────────────────────────────────────┘ │
│                                                        │
│ [Copy] [Paste from Clipboard] [☐ Enable Post Proc.] │
│ Pipeline: [Remove Rambling ▼]                         │
│                                                        │
│ Post-processed text                             [Copy] │
│ ┌───────────────────────────────────────────────────┐ │
│ │                                                   │ │
│ │                                                   │ │
│ └───────────────────────────────────────────────────┘ │
├───────────────────────────────────────────────────────┤
│ Console Execution Log                           [Clear]│
│ ┌───────────────────────────────────────────────────┐ │
│ │ [2:30 PM] Transcription completed...              │ │
│ └───────────────────────────────────────────────────┘ │
└───────────────────────────────────────────────────────┘
```

### After Manual Pipeline Feature
```
┌───────────────────────────────────────────────────────┐
│  ● Start Recording                                     │
├───────────────────────────────────────────────────────┤
│ Transcription                                   [Copy] │
│ ┌───────────────────────────────────────────────────┐ │
│ │ Lorem ipsum dolor sit amet, consectetur...        │ │
│ │ adipiscing elit, sed do eiusmod tempor...        │ │
│ └───────────────────────────────────────────────────┘ │
│                                                        │
│ [Copy] [Paste from Clipboard] [☐ Enable Post Proc.] │
│ Pipeline: [Summarize ▼] [▶ Run Pipeline] ← NEW      │
│                                                        │
│ Post-processed text                             [Copy] │
│ ┌───────────────────────────────────────────────────┐ │
│ │ Summary: The text discusses lorem ipsum and...   │ │
│ │                                                   │ │
│ └───────────────────────────────────────────────────┘ │
│                                                        │
│ [▼ Show Pipeline History (2 results)] ← NEW          │
├───────────────────────────────────────────────────────┤
│ Console Execution Log                           [Clear]│
│ ┌───────────────────────────────────────────────────┐ │
│ │ [2:30 PM] Transcription completed (1,234 chars)   │ │
│ │ [2:31 PM] Pipeline: Remove Rambling (done)        │ │
│ │ [2:32 PM] Pipeline: Summarize (done)              │ │
│ └───────────────────────────────────────────────────┘ │
└───────────────────────────────────────────────────────┘
```

### History Panel Expanded
```
┌───────────────────────────────────────────────────────┐
│ [▲ Hide Pipeline History (2 results)]                 │
│ ┌─────────────────────────────────────────────────────┐│
│ │ Pipeline: Summarize │ 2:32 PM            │ [Copy]  ││
│ │ ┌─────────────────────────────────────────────────┐││
│ │ │ Summary: The text discusses lorem ipsum and... │││
│ │ │ key concepts include dolor sit amet...         │││
│ │ └─────────────────────────────────────────────────┘││
│ ├─────────────────────────────────────────────────────┤│
│ │ Pipeline: Remove Rambling │ 2:31 PM    │ [Copy]  ││
│ │ ┌─────────────────────────────────────────────────┐││
│ │ │ Lorem ipsum dolor sit amet, consectetur elit.   │││
│ │ │ Sed do eiusmod tempor incididunt ut labore...  │││
│ │ └─────────────────────────────────────────────────┘││
│ └─────────────────────────────────────────────────────┘│
└───────────────────────────────────────────────────────┘
```

---

## Additional Features (Optional Enhancements)

### Priority: Low (Implement if time permits)

**1. Resizable Text Areas**
- Use `JSplitPane` for transcription/post-processed fields
- Allow dragging divider to resize
- Or add corner resize handle like HTML textarea

**2. History Export**
- Button to export all results as JSON or text file
- Useful for comparing multiple pipeline outputs

**3. History Search/Filter**
- Search box to filter history by pipeline name or content

**4. Diff View**
- Show side-by-side comparison of original vs processed text
- Highlight changes

**5. Favorite Results**
- Star/bookmark specific history entries
- Quick access to best results

**6. History Persistence**
- Save history to disk (optional setting)
- Restore on app restart

---

## Known Issues & Gotchas

### 1. SwingWorker Thread Safety
- Always update UI components on EDT
- Use `SwingUtilities.invokeLater()` if needed
- `PostProcessingWorker.done()` runs on EDT automatically ✓

### 2. Text Area Focus
- After pipeline runs, focus removed to prevent paste conflicts
- Existing code: `transcriptionTextArea.transferFocus()`
- Maintain this behavior with manual runner

### 3. Clipboard Auto-Paste
- Existing feature: auto-paste after post-processing if enabled
- Manual runner should respect `isAutoPasteEnabled()` setting
- Only paste result from manual run if user expects it

### 4. Tray Icon Updates
- `updateTrayMenu()` called after pipeline completes
- Ensure manual runner also calls this

### 5. Status Indicator
- Blue circle shows during post-processing
- Make sure it stays blue during manual pipeline runs
- Reset to green when done

---

## Questions for Next Session

1. **History Panel Placement:**
   - Should history be in main window or separate dialog?
   - Current spec: collapsible panel below post-processed field

2. **Auto-Paste Behavior:**
   - Should manual pipeline run trigger auto-paste?
   - Probably not - manual implies intentional review

3. **History Limit:**
   - Should we limit history to N results (e.g., 10)?
   - Or unlimited with scroll?

4. **Visual Design:**
   - Should history entries be cards, list items, or accordion?
   - How much spacing between entries?

5. **Performance:**
   - With 10+ pipeline runs, will history panel be sluggish?
   - Consider virtual scrolling if needed

---

## Success Criteria

The feature is complete when:

✅ User can run any pipeline on existing transcription text
✅ "Run Pipeline" button works whether checkbox is enabled or not
✅ History tracks all pipeline executions for current recording
✅ Previous results don't disappear when running new pipeline
✅ History panel shows/hides smoothly
✅ All history entries are copyable
✅ New recording clears history appropriately
✅ UI remains responsive during pipeline execution
✅ Console log shows all manual pipeline runs
✅ No regressions to existing auto-pipeline functionality

---

## Estimated Complexity

**Time Estimate:** 4-6 hours of focused development

**Breakdown:**
- Phase 1 (Manual runner button): 1 hour
- Phase 2 (History tracking): 1 hour
- Phase 3 (Result stacking): 0.5 hours
- Phase 4 (History panel UI): 2 hours
- Phase 5 (Polish & testing): 1.5 hours

**Difficulty:** Medium
- Straightforward logic
- Main challenge: UI layout for history panel
- Existing `PostProcessingWorker` makes async execution easy

---

## Contact & Handover

**Previous Session:** claude/windows-installer-setup-01VyrVetnQ39q5zxkygXhVdP
**Commit Hash:** ae6a56d
**Date:** 2025-11-25

**For Questions:**
- All code is in `/home/user/whispercat-xb/`
- Main file: `src/main/java/org/whispercat/recording/RecorderForm.java`
- Testing: `mvn clean package && java -jar target/Audiorecorder-1.0-SNAPSHOT-jar-with-dependencies.jar`

**Good Luck!** 🚀
