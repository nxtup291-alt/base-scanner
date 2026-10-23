package com.basescanner.score;

import java.util.List;

/**
 * Output of the Base Likelihood heuristic.
 *
 * @param percent         0..100 heuristic estimate (NOT a calibrated probability)
 * @param rating          descriptive band ("Very unlikely" .. "Very likely")
 * @param ratingColor     ARGB color for the band
 * @param evidence        evidence items that contributed, strongest first
 * @param rawPoints       weighted points before synergy and saturation
 * @param indicators      number of strong, independent indicators
 * @param confidence      "High", "Medium" or "Low"
 * @param confidenceText  explanation of the confidence level
 * @param coverage        fraction of the requested area that was loaded and examined
 */
public record ScoreResult(int percent, String rating, int ratingColor, List<Evidence> evidence,
                          double rawPoints, int indicators, String confidence, String confidenceText,
                          int confidenceColor, double coverage) {

    public record Evidence(String text, double points, double maxPoints) {
    }
}
