package vn.medisense.app.utils;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

/**
 * DepthShadowHelper - Creates dynamic shadows that respond to scroll direction
 * Shadows move and change intensity based on scroll velocity and direction
 */
public class DepthShadowHelper {

    private static final float MAX_SHADOW_DP = 24f;
    private static final float MIN_SHADOW_DP = 4f;
    private static final float MAX_ELEVATION_DP = 16f;
    
    private final Context context;
    private final float density;
    
    public DepthShadowHelper(Context context) {
        this.context = context;
        this.density = context.getResources().getDisplayMetrics().density;
    }

    /**
     * Apply dynamic shadow to a view that responds to scroll
     */
    public void applyScrollShadow(View view, float scrollVelocity, float scrollDirection) {
        float shadowScale = Math.abs(scrollVelocity) / 1000f;
        shadowScale = Math.min(1f, shadowScale);
        
        // Tính toán offset bóng dựa trên hướng cuộn
        float shadowX = scrollDirection * density * 4f * shadowScale;
        float shadowY = Math.abs(scrollDirection) * density * 8f * shadowScale;
        
        // Calculate elevation
        float elevation = MIN_SHADOW_DP + (MAX_ELEVATION_DP - MIN_SHADOW_DP) * shadowScale;
        
        // Áp dụng cho view
        view.setElevation(elevation * density);
        
        // Nếu view hỗ trợ vẽ bóng tùy chỉnh
        if (view instanceof ShadowDrawableView) {
            ((ShadowDrawableView) view).setShadowOffset(shadowX, shadowY);
            ((ShadowDrawableView) view).setShadowAlpha(0.3f + shadowScale * 0.4f);
        }
    }

    /**
     * Animate shadow on view press
     */
    public void animatePressShadow(View view) {
        ObjectAnimator elevationAnim = ObjectAnimator.ofFloat(view, "elevation", 
                view.getElevation(), MAX_ELEVATION_DP * density);
        elevationAnim.setDuration(100);
        elevationAnim.start();
        
        // Scale down slightly
        ObjectAnimator scaleXAnim = ObjectAnimator.ofFloat(view, "scaleX", 1f, 0.97f);
        ObjectAnimator scaleYAnim = ObjectAnimator.ofFloat(view, "scaleY", 1f, 0.97f);
        
        AnimatorSet pressSet = new AnimatorSet();
        pressSet.playTogether(elevationAnim, scaleXAnim, scaleYAnim);
        pressSet.setDuration(100);
        pressSet.start();
    }

    /**
     * Animate shadow on view release
     */
    public void animateReleaseShadow(View view) {
        ObjectAnimator elevationAnim = ObjectAnimator.ofFloat(view, "elevation", 
                view.getElevation(), MIN_SHADOW_DP * density);
        elevationAnim.setDuration(200);
        elevationAnim.setInterpolator(new OvershootInterpolator());
        elevationAnim.start();
        
        ObjectAnimator scaleXAnim = ObjectAnimator.ofFloat(view, "scaleX", 0.97f, 1f);
        ObjectAnimator scaleYAnim = ObjectAnimator.ofFloat(view, "scaleY", 0.97f, 1f);
        
        AnimatorSet releaseSet = new AnimatorSet();
        releaseSet.playTogether(elevationAnim, scaleXAnim, scaleYAnim);
        releaseSet.setDuration(200);
        releaseSet.setInterpolator(new OvershootInterpolator());
        releaseSet.start();
    }

    /**
     * Attach shadow listener to RecyclerView
     */
    public void attachToRecyclerView(RecyclerView recyclerView, ShadowProvider provider) {
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            private float lastVelocity = 0f;
            
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                
                // Calculate velocity
                float velocity = (float) Math.sqrt(dx * dx + dy * dy);
                float direction = dy > 0 ? 1f : -1f;
                
                // Smooth velocity
                velocity = velocity * 0.3f + lastVelocity * 0.7f;
                lastVelocity = velocity;
                
                // Áp dụng bóng cho các mục hiển thị
                for (int i = 0; i < recyclerView.getChildCount(); i++) {
                    View child = recyclerView.getChildAt(i);
                    if (child != null) {
                        applyScrollShadow(child, velocity, direction);
                        
                        // Apply tilt to 3D icons
                        if (provider != null) {
                            provider.onShadowApplied(child, velocity, direction, dy);
                        }
                    }
                }
            }
        });
    }

    /**
     * Create custom shadow drawable
     */
    public ShadowDrawable createShadowDrawable(float shadowRadius, float offsetX, float offsetY, int shadowColor) {
        return new ShadowDrawable(shadowRadius * density, offsetX * density, offsetY * density, shadowColor);
    }

    /**
     * Interface for custom shadow handling
     */
    public interface ShadowProvider {
        void onShadowApplied(View view, float velocity, float direction, int scrollDelta);
    }

    /**
     * Interface for views that can draw custom shadows
     */
    public interface ShadowDrawableView {
        void setShadowOffset(float x, float y);
        void setShadowAlpha(float alpha);
    }

    /**
     * Custom shadow drawable
     */
    public static class ShadowDrawable {
        private final Paint shadowPaint;
        private final RectF shadowBounds;
        private float offsetX, offsetY;
        private float radius;
        
        public ShadowDrawable(float radius, float offsetX, float offsetY, int color) {
            this.radius = radius;
            this.offsetX = offsetX;
            this.offsetY = offsetY;
            
            shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            shadowPaint.setColor(color);
            shadowPaint.setStyle(Paint.Style.FILL);
            shadowPaint.setShadowLayer(radius, offsetX, offsetY, color);
            
            shadowBounds = new RectF();
        }

        public void setBounds(float left, float top, float right, float bottom) {
            shadowBounds.set(left + offsetX - radius, top + offsetY - radius,
                    right + offsetX + radius, bottom + offsetY + radius);
        }

        public void draw(Canvas canvas) {
            canvas.drawRoundRect(shadowBounds, radius, radius, shadowPaint);
        }

        public void setShadowOffset(float x, float y) {
            this.offsetX = x;
            this.offsetY = y;
            shadowPaint.setShadowLayer(radius, offsetX, offsetY, shadowPaint.getColor());
        }

        public void setShadowAlpha(float alpha) {
            int color = shadowPaint.getColor();
            int newColor = (color & 0x00FFFFFF) | ((int)(alpha * 255) << 24);
            shadowPaint.setColor(newColor);
        }
    }

    /**
     * Animate depth transition between states
     */
    public void animateDepth(View view, float targetElevation, long duration) {
        float currentElevation = view.getElevation();
        
        ValueAnimator animator = ValueAnimator.ofFloat(currentElevation, targetElevation * density);
        animator.setDuration(duration);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(animation -> {
            float value = (float) animation.getAnimatedValue();
            view.setElevation(value);
        });
        animator.start();
    }

    /**
     * Calculate parallax depth factor based on scroll
     */
    public float calculateDepthFactor(int scrollY, int maxScroll) {
        float factor = (float) scrollY / maxScroll;
        return Math.max(0f, Math.min(1f, factor));
    }

    /**
     * Apply depth-based elevation to a group of views
     */
    public void applyDepthGroup(ViewGroup container, int baseScroll, int maxScroll) {
        float depthFactor = calculateDepthFactor(baseScroll, maxScroll);
        
        for (int i = 0; i < container.getChildCount(); i++) {
            View child = container.getChildAt(i);
            
            // Tính toán độ sâu riêng lẻ dựa trên vị trí
            float childDepth = (float) i / container.getChildCount();
            float totalDepth = (depthFactor + childDepth) / 2f;
            
            // Apply elevation
            float elevation = (MIN_SHADOW_DP + (MAX_ELEVATION_DP - MIN_SHADOW_DP) * totalDepth) * density;
            child.setElevation(elevation);
            
            // Áp dụng dịch chuyển nhẹ cho parallax
            child.setTranslationZ(elevation * 0.3f);
        }
    }
}
