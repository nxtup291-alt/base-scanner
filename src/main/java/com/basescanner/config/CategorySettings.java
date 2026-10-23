package com.basescanner.config;

import com.basescanner.scan.ScanCategory;

/** Per-category toggles: detect it, list it in the HUD, outline it, and its color (RGB). */
public class CategorySettings {
    public boolean enabled = true;
    public boolean showInHud = true;
    public boolean esp = false;
    public int color = 0xFFFFFF;

    public CategorySettings() {
    }

    public static CategorySettings defaults(ScanCategory category) {
        CategorySettings s = new CategorySettings();
        s.enabled = true;
        s.showInHud = true;
        s.esp = category.defaultEsp;
        s.color = category.defaultColor & 0xFFFFFF;
        return s;
    }
}
