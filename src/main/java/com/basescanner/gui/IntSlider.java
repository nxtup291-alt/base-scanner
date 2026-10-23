package com.basescanner.gui;

import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;

import java.util.function.IntConsumer;
import java.util.function.IntFunction;

/** Integer slider with a step, a label and a value formatter. */
public class IntSlider extends SliderWidget {
    private final String label;
    private final int min;
    private final int max;
    private final int step;
    private final IntFunction<String> format;
    private final IntConsumer setter;

    public IntSlider(int x, int y, int width, int height, String label, int min, int max, int step, int value,
                     IntFunction<String> format, IntConsumer setter) {
        super(x, y, width, height, Text.empty(), toSlider(value, min, max));
        this.label = label;
        this.min = min;
        this.max = max;
        this.step = Math.max(1, step);
        this.format = format;
        this.setter = setter;
        updateMessage();
    }

    private static double toSlider(int value, int min, int max) {
        if (max <= min) return 0;
        return Math.max(0, Math.min(1, (value - min) / (double) (max - min)));
    }

    public int current() {
        int raw = min + (int) Math.round(this.value * (max - min));
        int stepped = min + Math.round((raw - min) / (float) step) * step;
        return Math.max(min, Math.min(max, stepped));
    }

    @Override
    protected void updateMessage() {
        if (format == null) return; // called before fields are set on some versions
        String v = format.apply(current());
        setMessage(Text.literal(label == null || label.isEmpty() ? v : label + ": " + v));
    }

    @Override
    protected void applyValue() {
        if (setter != null) setter.accept(current());
    }
}
