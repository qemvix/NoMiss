package com.nomiss.config;

public final class HudSettings {
    public enum Corner { TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }
    private Corner corner = Corner.TOP_LEFT;
    private int scale = 100;
    private int opacity = 100;
    public Corner corner() { return corner; }
    public int scale() { return scale; }
    public int opacity() { return opacity; }
    public void setCorner(Corner value) { corner = java.util.Objects.requireNonNull(value); }
    public void setScale(int value) { if (value < 50 || value > 200) throw new IllegalArgumentException("HUD size must be 50–200%"); scale = value; }
    public void setOpacity(int value) { if (value < 20 || value > 100) throw new IllegalArgumentException("HUD opacity must be 20–100%"); opacity = value; }
    public void reset() { corner = Corner.TOP_LEFT; scale = opacity = 100; }
}
