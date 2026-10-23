package com.basescanner.config;

/**
 * Weights for the Base Likelihood heuristic. Each weight is the maximum number of
 * points that one evidence type can contribute (contributions saturate). See
 * README.md, section "How the Base Likelihood score works".
 */
public class ScoreWeights {
    public int storageCluster = 22;
    public int storageVariety = 10;
    public int playerOnlyStorage = 14;
    public int beds = 12;
    public int crafting = 8;
    public int smelting = 6;
    public int advancedUtility = 14;
    public int automation = 10;
    public int signs = 6;
    public int signText = 8;
    public int organization = 8;
    public int farming = 10;
    public int villagers = 6;
    public int pets = 12;
    public int players = 10;
    public int defensive = 8;
    public int valuables = 10;
    public int lighting = 7;
    public int building = 8;
    public int portal = 8;

    /** Raw points needed for ~63% (1 - 1/e). Larger = harder to reach high percentages. */
    public int scale = 45;
    /** Synergy multiplier with zero strong indicators, in percent. */
    public int synergyBase = 50;
    /** Synergy added per strong indicator, in percent (capped at 100% total). */
    public int synergyPerIndicator = 13;
    /** Maximum percentage allowed when the only evidence is a single storage block. */
    public int singleStorageCap = 15;
}
