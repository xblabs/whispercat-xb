package org.whispercat.settings;

import javax.swing.*;
import java.awt.*;

/**
 * A custom JProgressBar that displays a visual threshold line
 * to show users where the silence removal threshold is in relation to current audio levels.
 */
public class ThresholdProgressBar extends JProgressBar {
    private int thresholdValue = 0;
    private Color thresholdColor = new Color(255, 100, 100); // Red indicator line

    public ThresholdProgressBar(int min, int max) {
        super(min, max);
    }

    /**
     * Set the threshold value to display as a line on the progress bar.
     * The threshold represents the minimum RMS level that will NOT be considered silence.
     *
     * @param thresholdValue Value between 0-100 representing threshold percentage
     */
    public void setThreshold(int thresholdValue) {
        this.thresholdValue = Math.max(0, Math.min(100, thresholdValue));
        repaint();
    }

    public int getThreshold() {
        return thresholdValue;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        if (thresholdValue > 0) {
            Graphics2D g2d = (Graphics2D) g.create();
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            // Calculate threshold position
            int barWidth = getWidth() - 2; // Account for border
            int thresholdX = (int) ((thresholdValue / 100.0) * barWidth) + 1;

            // Draw threshold line
            g2d.setColor(thresholdColor);
            g2d.setStroke(new BasicStroke(2));
            g2d.drawLine(thresholdX, 2, thresholdX, getHeight() - 2);

            // Draw small arrow/triangle at top to make it more visible
            int[] xPoints = {thresholdX - 4, thresholdX + 4, thresholdX};
            int[] yPoints = {2, 2, 8};
            g2d.fillPolygon(xPoints, yPoints, 3);

            g2d.dispose();
        }
    }
}
