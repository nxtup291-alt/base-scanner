package com.basescanner.scan;

/** Top-level sections shown in the HUD and the detail screen. */
public enum CategoryGroup {
    STORAGE("Storage", 0xFFFFC857),
    INFRASTRUCTURE("Infrastructure", 0xFF7FD1FF),
    ENTITIES("Entities", 0xFFFF8A80),
    OTHER("Other objects", 0xFFC3A6FF);

    public final String label;
    public final int color;

    CategoryGroup(String label, int color) {
        this.label = label;
        this.color = color;
    }
}
