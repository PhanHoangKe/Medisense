package vn.medisense.app.utils;

import android.animation.ObjectAnimator;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

/**
 * ParallaxScrollListener - Creates depth effect with parallax scrolling
 * Header moves slower than content, creating illusion of depth
 */
public class ParallaxScrollListener extends RecyclerView.OnScrollListener {

    private final View parallaxHeader;
    private final View[] parallaxLayers;
    private final float[] parallaxSpeeds;
    
    private int totalScrollY = 0;
    private final int maxHeaderHeight;
    private final int fadeStartDistance;
    private final int fadeEndDistance;
    
    private OnParallaxScrollListener callback;

    public interface OnParallaxScrollListener {
        void onScroll(int scrollY, float progress);
        void onHeaderCollapsed();
        void onHeaderExpanded();
    }

    /**
     * Simple parallax with single header
     */
    public ParallaxScrollListener(View header, int maxHeaderHeight) {
        this(header, maxHeaderHeight, 500, 800);
    }

    /**
     * Parallax with fade effect
     */
    public ParallaxScrollListener(View header, int maxHeaderHeight, int fadeStart, int fadeEnd) {
        this.parallaxHeader = header;
        this.maxHeaderHeight = maxHeaderHeight;
        this.fadeStartDistance = fadeStart;
        this.fadeEndDistance = fadeEnd;
        this.parallaxLayers = null;
        this.parallaxSpeeds = null;
    }

    /**
     * Multi-layer parallax for complex depth
     */
    public ParallaxScrollListener(View[] layers, float[] speeds) {
        if (layers.length != speeds.length) {
            throw new IllegalArgumentException("Layers and speeds must have same length");
        }
        this.parallaxHeader = null;
        this.parallaxLayers = layers;
        this.parallaxSpeeds = speeds;
        this.maxHeaderHeight = 0;
        this.fadeStartDistance = 0;
        this.fadeEndDistance = 0;
    }

    public void setOnParallaxScrollListener(OnParallaxScrollListener listener) {
        this.callback = listener;
    }

    @Override
    public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
        super.onScrolled(recyclerView, dx, dy);
        
        totalScrollY += dy;
        
        if (parallaxHeader != null) {
            applyHeaderParallax(dy);
        }
        
        if (parallaxLayers != null) {
            applyMultiLayerParallax(dy);
        }
        
        // Calculate progress (0 = expanded, 1 = collapsed)
        float progress = Math.min(1f, (float) totalScrollY / fadeEndDistance);
        
        if (callback != null) {
            callback.onScroll(totalScrollY, progress);
            
            if (totalScrollY >= fadeEndDistance && dy > 0) {
                callback.onHeaderCollapsed();
            } else if (totalScrollY <= 0 && dy < 0) {
                callback.onHeaderExpanded();
            }
        }
    }

    /**
     * Apply parallax effect to header with fade
     */
    private void applyHeaderParallax(int dy) {
        // Dịch chuyển - header di chuyển với tốc độ 0.5x
        float currentTranslation = parallaxHeader.getTranslationY();
        float newTranslation = currentTranslation - (dy * 0.5f);
        
        // Giới hạn dịch chuyển để ngăn cuộn quá mức
        newTranslation = Math.max(-maxHeaderHeight / 2f, Math.min(0, newTranslation));
        parallaxHeader.setTranslationY(newTranslation);
        
        // Alpha mờ dần dựa trên khoảng cách cuộn
        if (fadeEndDistance > 0) {
            float fadeProgress = (float) totalScrollY / fadeEndDistance;
            fadeProgress = Math.max(0f, Math.min(1f, fadeProgress));
            float alpha = 1f - fadeProgress;
            parallaxHeader.setAlpha(alpha);
        }
        
        // Hiệu ứng scale - thu nhỏ một chút khi người dùng cuộn
        float scale = 1f - (Math.abs(totalScrollY) / (float) fadeEndDistance) * 0.1f;
        scale = Math.max(0.9f, Math.min(1f, scale));
        parallaxHeader.setScaleX(scale);
        parallaxHeader.setScaleY(scale);
    }

    /**
     * Apply parallax to multiple layers at different speeds
     */
    private void applyMultiLayerParallax(int dy) {
        for (int i = 0; i < parallaxLayers.length; i++) {
            View layer = parallaxLayers[i];
            float speed = parallaxSpeeds[i];
            
            float currentTranslation = layer.getTranslationY();
            float newTranslation = currentTranslation - (dy * speed);
            layer.setTranslationY(newTranslation);
        }
    }

    /**
     * Reset parallax to initial state with animation
     */
    public void resetWithAnimation() {
        totalScrollY = 0;
        
        if (parallaxHeader != null) {
            ObjectAnimator translationAnim = ObjectAnimator.ofFloat(
                    parallaxHeader, "translationY", parallaxHeader.getTranslationY(), 0f);
            translationAnim.setDuration(300);
            translationAnim.setInterpolator(new DecelerateInterpolator());
            translationAnim.start();
            
            ObjectAnimator alphaAnim = ObjectAnimator.ofFloat(parallaxHeader, "alpha", parallaxHeader.getAlpha(), 1f);
            alphaAnim.setDuration(300);
            alphaAnim.start();
            
            ObjectAnimator scaleXAnim = ObjectAnimator.ofFloat(parallaxHeader, "scaleX", parallaxHeader.getScaleX(), 1f);
            scaleXAnim.setDuration(300);
            scaleXAnim.start();
            
            ObjectAnimator scaleYAnim = ObjectAnimator.ofFloat(parallaxHeader, "scaleY", parallaxHeader.getScaleY(), 1f);
            scaleYAnim.setDuration(300);
            scaleYAnim.start();
        }
        
        if (parallaxLayers != null) {
            for (View layer : parallaxLayers) {
                ObjectAnimator anim = ObjectAnimator.ofFloat(layer, "translationY", layer.getTranslationY(), 0f);
                anim.setDuration(300);
                anim.setInterpolator(new DecelerateInterpolator());
                anim.start();
            }
        }
    }

    /**
     * Quick reset without animation
     */
    public void reset() {
        totalScrollY = 0;
        
        if (parallaxHeader != null) {
            parallaxHeader.setTranslationY(0f);
            parallaxHeader.setAlpha(1f);
            parallaxHeader.setScaleX(1f);
            parallaxHeader.setScaleY(1f);
        }
        
        if (parallaxLayers != null) {
            for (View layer : parallaxLayers) {
                layer.setTranslationY(0f);
            }
        }
    }

    /**
     * Get current scroll position
     */
    public int getScrollY() {
        return totalScrollY;
    }

    /**
     * Check if header is collapsed
     */
    public boolean isHeaderCollapsed() {
        return totalScrollY >= fadeEndDistance;
    }
}
