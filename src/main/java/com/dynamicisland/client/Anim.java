package com.dynamicisland.client;

/** 弹簧动画值：用于胶囊宽度/高度/位置等，带轻微过冲，接近 iOS 手感。 */
public class Anim {
    private float value, velocity, target;
    public float stiffness = 260f, damping = 27f;

    public Anim(float v) { value = target = v; }

    public void to(float t) { target = t; }
    public void snap(float v) { value = target = v; velocity = 0; }
    public float get() { return value; }
    public float target() { return target; }
    public boolean settled() { return Math.abs(target - value) < 0.05f && Math.abs(velocity) < 0.05f; }

    public void update(float dt) {
        dt = Math.min(dt, 0.05f);
        float d = target - value;
        velocity += d * stiffness * dt;
        velocity *= (float) Math.exp(-damping * dt);
        value += velocity * dt;
        if (Math.abs(target - value) < 0.002f && Math.abs(velocity) < 0.002f) { value = target; velocity = 0; }
    }

    /** 线性插值平滑，用于透明度等不需要回弹的量 */
    public static float lerp(float a, float b, float t) { return a + (b - a) * t; }

    public static float smooth(float cur, float target, float dt, float tau) {
        return cur + (target - cur) * (1f - (float) Math.exp(-dt / Math.max(0.0001f, tau)));
    }

    public static float clamp(float v, float lo, float hi) { return v < lo ? lo : (v > hi ? hi : v); }
    public static float easeOutCubic(float t) { float f = 1 - t; return 1 - f * f * f; }
}
