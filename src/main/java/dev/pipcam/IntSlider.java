package dev.pipcam;

import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.function.IntConsumer;
import java.util.function.IntFunction;

/** Integer slider with a formatted label. */
final class IntSlider extends AbstractSliderButton {
    private final int min, max;
    private final IntFunction<String> label;
    private final IntConsumer onChange;

    IntSlider(int x, int y, int w, int h, int min, int max, int value, IntFunction<String> label, IntConsumer onChange) {
        super(x, y, w, h, Component.empty(), (double) (Mth.clamp(value, min, max) - min) / (max - min));
        this.min = min;
        this.max = max;
        this.label = label;
        this.onChange = onChange;
        updateMessage();
    }

    int intValue() {
        return min + (int) Math.round(value * (max - min));
    }

    void setIntValue(int v) {
        this.value = (double) (Mth.clamp(v, min, max) - min) / (max - min);
        updateMessage();
    }

    @Override
    protected void updateMessage() {
        setMessage(Component.literal(label.apply(intValue())));
    }

    @Override
    protected void applyValue() {
        onChange.accept(intValue());
    }
}
