package vn.medisense.app.utils;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.OvershootInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.core.content.ContextCompat;

import vn.medisense.app.R;

/**
 * Medication3DIcon - Custom view for 3D medication icons with depth effects
 * Features: Tilt on scroll, dynamic shadows, 3D appearance
 */
public class Medication3DIcon extends AppCompatImageView {

    private float tiltX = 0f;
    private float tiltY = 0f;
    private float maxTilt = 15f;
    private float shadowOffsetX = 0f;
    private float shadowOffsetY = 0f;
    private float shadowRadius = 20f;
    private float shadowAlpha = 0.3f;
    
    private Paint shadowPaint;
    private Paint glowPaint;
    private RectF iconBounds;
    
    private boolean isPressed = false;
    private float pressScale = 0.95f;
    
    private int iconType = 0; // 0: pill, 1: capsule, 2: tablet, 3: liquid
    private int baseColor = 0xFF00BCD4;
    private int secondaryColor = 0xFF00E5FF;

    public Medication3DIcon(@NonNull Context context) {
        super(context);
        init();
    }

    public Medication3DIcon(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public Medication3DIcon(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setLayerType(LAYER_TYPE_HARDWARE, null);
        
        shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        shadowPaint.setStyle(Paint.Style.FILL);
        
        glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        glowPaint.setStyle(Paint.Style.FILL);
        
        iconBounds = new RectF();
        
        // Set default 3D background
        set3DBackground();
    }

    /**
     * Set the type of medication icon
     */
    public void setIconType(int type) {
        this.iconType = type;
        updateIconColors();
        invalidate();
    }

    /**
     * Set custom colors for the icon
     */
    public void setIconColors(int base, int secondary) {
        this.baseColor = base;
        this.secondaryColor = secondary;
        updateIconColors();
    }

    /**
     * Update icon based on medication category
     */
    public void setMedicationCategory(String category) {
        if (category == null) {
            setIconType(0);
            return;
        }
        
        String cat = category.toLowerCase();
        if (cat.contains("pain") || cat.contains("relief")) {
            setIconType(0); // Pill
            setIconColors(0xFF4CAF50, 0xFF81C784); // Green
        } else if (cat.contains("heart") || cat.contains("blood")) {
            setIconType(1); // Capsule
            setIconColors(0xFFE91E63, 0xFFF48FB1); // Pink
        } else if (cat.contains("vitamin")) {
            setIconType(2); // Tablet
            setIconColors(0xFFFF9800, 0xFFFFCC80); // Orange
        } else if (cat.contains("syrup") || cat.contains("liquid")) {
            setIconType(3); // Liquid
            setIconColors(0xFF2196F3, 0xFF90CAF9); // Blue
        } else {
            setIconType(0);
            setIconColors(0xFF00BCD4, 0xFF00E5FF); // Teal default
        }
    }

    private void updateIconColors() {
        // Tạo gradient nền với hiệu ứng 3D
        GradientDrawable baseDrawable = new GradientDrawable();
        baseDrawable.setShape(GradientDrawable.OVAL);
        baseDrawable.setColors(new int[]{secondaryColor, baseColor});
        baseDrawable.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        baseDrawable.setGradientRadius(100f);
        
        // Thêm lớp bóng
        GradientDrawable shadowDrawable = new GradientDrawable();
        shadowDrawable.setShape(GradientDrawable.OVAL);
        shadowDrawable.setColor(Color.BLACK);
        
        LayerDrawable layerDrawable = new LayerDrawable(new Drawable[]{shadowDrawable, baseDrawable});
        layerDrawable.setLayerInset(0, 4, 8, 4, 0); // Shadow offset
        layerDrawable.setLayerInset(1, 0, 0, 0, 0); // Base
        
        setBackground(layerDrawable);
    }

    private void set3DBackground() {
        // Default 3D pill background
        updateIconColors();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        iconBounds.set(0, 0, w, h);
        
        // Tạo gradient bóng
        RadialGradient shadowGradient = new RadialGradient(
                w / 2f, h / 2f + shadowRadius,
                shadowRadius,
                Color.BLACK, Color.TRANSPARENT,
                Shader.TileMode.CLAMP
        );
        shadowPaint.setShader(shadowGradient);
        shadowPaint.setAlpha((int)(255 * shadowAlpha));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        // Lưu trạng thái canvas
        canvas.save();
        
        // Áp dụng biến đổi nghiêng
        float centerX = getWidth() / 2f;
        float centerY = getHeight() / 2f;
        
        // Xoay dựa trên độ nghiêng
        canvas.rotate(tiltX, centerX, centerY);
        canvas.rotate(tiltY, centerX, centerY);
        
        // Vẽ bóng với offset dựa trên độ nghiêng
        float shadowX = centerX + (tiltX / maxTilt) * shadowRadius + shadowOffsetX;
        float shadowY = centerY + (tiltY / maxTilt) * shadowRadius + shadowOffsetY;
        
        canvas.drawCircle(shadowX, shadowY, getWidth() / 2f - 8, shadowPaint);
        
        // Vẽ hiệu ứng phát sáng
        if (!isPressed) {
            glowPaint.setAlpha(50);
            canvas.drawCircle(centerX - tiltX, centerY - tiltY, getWidth() / 2f - 4, glowPaint);
        }
        
        // Khôi phục và vẽ hình ảnh
        canvas.restore();
        
        super.onDraw(canvas);
    }

    /**
     * Apply tilt based on scroll direction
     */
    public void applyTilt(float scrollX, float scrollY) {
        // Tính toán độ nghiêng dựa trên tốc độ cuộn
        float targetTiltX = Math.max(-maxTilt, Math.min(maxTilt, scrollX * 0.1f));
        float targetTiltY = Math.max(-maxTilt, Math.min(maxTilt, scrollY * 0.1f));
        
        // Hiệu ứng đến độ nghiêng mục tiêu
        ObjectAnimator tiltXAnim = ObjectAnimator.ofFloat(this, "tiltX", tiltX, targetTiltX);
        tiltXAnim.setDuration(150);
        tiltXAnim.start();
        
        ObjectAnimator tiltYAnim = ObjectAnimator.ofFloat(this, "tiltY", tiltY, targetTiltY);
        tiltYAnim.setDuration(150);
        tiltYAnim.start();
        
        // Cập nhật độ lệch bóng
        shadowOffsetX = targetTiltX * 0.5f;
        shadowOffsetY = targetTiltY * 0.5f;
        
        invalidate();
    }

    /**
     * Reset tilt to neutral
     */
    public void resetTilt() {
        ObjectAnimator tiltXAnim = ObjectAnimator.ofFloat(this, "tiltX", tiltX, 0f);
        tiltXAnim.setDuration(300);
        tiltXAnim.setInterpolator(new OvershootInterpolator());
        tiltXAnim.start();
        
        ObjectAnimator tiltYAnim = ObjectAnimator.ofFloat(this, "tiltY", tiltY, 0f);
        tiltYAnim.setDuration(300);
        tiltYAnim.setInterpolator(new OvershootInterpolator());
        tiltYAnim.start();
        
        shadowOffsetX = 0f;
        shadowOffsetY = 0f;
        
        invalidate();
    }

    /**
     * Animate press effect
     */
    public void animatePress() {
        isPressed = true;
        
        AnimatorSet pressSet = new AnimatorSet();
        pressSet.playTogether(
                ObjectAnimator.ofFloat(this, "scaleX", 1f, pressScale),
                ObjectAnimator.ofFloat(this, "scaleY", 1f, pressScale)
        );
        pressSet.setDuration(100);
        pressSet.start();
        
        invalidate();
    }

    /**
     * Animate release effect
     */
    public void animateRelease() {
        isPressed = false;
        
        AnimatorSet releaseSet = new AnimatorSet();
        releaseSet.playTogether(
                ObjectAnimator.ofFloat(this, "scaleX", pressScale, 1f),
                ObjectAnimator.ofFloat(this, "scaleY", pressScale, 1f)
        );
        releaseSet.setDuration(200);
        releaseSet.setInterpolator(new OvershootInterpolator());
        releaseSet.start();
        
        invalidate();
    }

    /**
     * Setters for animator
     */
    public void setTiltX(float tilt) {
        this.tiltX = tilt;
        invalidate();
    }

    public float getTiltX() {
        return tiltX;
    }

    public void setTiltY(float tilt) {
        this.tiltY = tilt;
        invalidate();
    }

    public float getTiltY() {
        return tiltY;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                animatePress();
                return true;
                
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                animateRelease();
                return true;
                
            case MotionEvent.ACTION_MOVE:
                // Tính toán độ nghiêng dựa trên vị trí chạm
                float x = event.getX();
                float y = event.getY();
                float centerX = getWidth() / 2f;
                float centerY = getHeight() / 2f;
                
                float touchTiltX = ((y - centerY) / centerY) * maxTilt * 0.5f;
                float touchTiltY = ((centerX - x) / centerX) * maxTilt * 0.5f;
                
                applyTilt(touchTiltX * 10, touchTiltY * 10);
                return true;
        }
        return super.onTouchEvent(event);
    }

    /**
     * Create static 3D icon programmatically
     */
    public static LayerDrawable create3DIcon(Context context, int baseColor, int secondaryColor) {
        GradientDrawable baseDrawable = new GradientDrawable();
        baseDrawable.setShape(GradientDrawable.OVAL);
        baseDrawable.setColors(new int[]{secondaryColor, baseColor});
        baseDrawable.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        baseDrawable.setGradientRadius(100f);
        
        GradientDrawable shadowDrawable = new GradientDrawable();
        shadowDrawable.setShape(GradientDrawable.OVAL);
        shadowDrawable.setColor(Color.BLACK);
        
        LayerDrawable layerDrawable = new LayerDrawable(new Drawable[]{shadowDrawable, baseDrawable});
        layerDrawable.setLayerInset(0, 4, 8, 4, 0);
        layerDrawable.setLayerInset(1, 0, 0, 0, 0);
        
        return layerDrawable;
    }
}
