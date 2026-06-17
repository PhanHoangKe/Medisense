package vn.medisense.app.utils;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;

/**
 * ShimmerFrameLayout - Facebook-style shimmer loading effect
 * Animates a shimmer gradient across placeholder content
 */
public class ShimmerFrameLayout extends FrameLayout {

    private static final int DEFAULT_ANIMATION_DURATION = 1500;
    private static final int DEFAULT_SHIMMER_COLOR = 0xFFD4D4D4;
    private static final float DEFAULT_SHIMMER_WIDTH = 0.5f;
    
    private Paint shimmerPaint;
    private Paint maskPaint;
    private View maskView;
    private LinearGradient gradient;
    private Matrix gradientMatrix;
    private ValueAnimator animator;
    
    private int shimmerColor = DEFAULT_SHIMMER_COLOR;
    private int shimmerDuration = DEFAULT_ANIMATION_DURATION;
    private float shimmerWidth = DEFAULT_SHIMMER_WIDTH;
    private int shimmerDirection = 0; // 0: trái sang phải, 1: phải sang trái, 2: trên xuống dưới, 3: dưới lên trên
    
    private boolean isShimmering = false;
    private float animationProgress = 0f;

    public ShimmerFrameLayout(Context context) {
        super(context);
        init();
    }

    public ShimmerFrameLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public ShimmerFrameLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setWillNotDraw(false);
        
        shimmerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        maskPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        maskPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
        
        gradientMatrix = new Matrix();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        updateGradient(w, h);
    }

    /**
     * Tạo gradient shimmer
     */
    private void updateGradient(int width, int height) {
        int startColor = (shimmerColor & 0x00FFFFFF) | 0x4D000000; // 30% alpha
        int midColor = shimmerColor;
        int endColor = (shimmerColor & 0x00FFFFFF) | 0x4D000000;
        
        float[] positions = new float[]{0f, 0.5f, 1f};
        int[] colors = new int[]{startColor, midColor, endColor};
        
        switch (shimmerDirection) {
            case 1: // Phải sang trái
                gradient = new LinearGradient(width, 0, 0, 0, colors, positions, Shader.TileMode.CLAMP);
                break;
            case 2: // Trên xuống dưới
                gradient = new LinearGradient(0, 0, 0, height, colors, positions, Shader.TileMode.CLAMP);
                break;
            case 3: // Dưới lên trên
                gradient = new LinearGradient(0, height, 0, 0, colors, positions, Shader.TileMode.CLAMP);
                break;
            default: // Trái sang phải
                gradient = new LinearGradient(0, 0, width, 0, colors, positions, Shader.TileMode.CLAMP);
        }
        
        shimmerPaint.setShader(gradient);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (!isShimmering || getWidth() == 0 || getHeight() == 0) {
            super.onDraw(canvas);
            return;
        }

        // Lưu layer để che mặt nạ
        int saveCount = canvas.saveLayer(0, 0, getWidth(), getHeight(), null);
        
        // Draw content
        super.onDraw(canvas);
        
        // Draw shimmer
        float translateX = 0;
        float translateY = 0;
        
        switch (shimmerDirection) {
            case 1: // Phải sang trái
                translateX = (1 - animationProgress) * getWidth() * 2 - getWidth() * 0.5f;
                break;
            case 2: // Trên xuống dưới
                translateY = animationProgress * getHeight() * 2 - getHeight() * 0.5f;
                break;
            case 3: // Dưới lên trên
                translateY = (1 - animationProgress) * getHeight() * 2 - getHeight() * 0.5f;
                break;
            default: // Trái sang phải
                translateX = animationProgress * getWidth() * 2 - getWidth() * 0.5f;
        }
        
        gradientMatrix.reset();
        gradientMatrix.setTranslate(translateX, translateY);
        gradient.setLocalMatrix(gradientMatrix);
        
        canvas.drawRect(0, 0, getWidth(), getHeight(), shimmerPaint);
        
        // Restore
        canvas.restoreToCount(saveCount);
    }

    /**
     * Start shimmer animation
     */
    public void startShimmer() {
        if (isShimmering) return;
        
        isShimmering = true;
        
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(shimmerDuration);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(animation -> {
            animationProgress = (float) animation.getAnimatedValue();
            invalidate();
        });
        
        animator.start();
    }

    /**
     * Stop shimmer animation
     */
    public void stopShimmer() {
        isShimmering = false;
        if (animator != null) {
            animator.cancel();
        }
        invalidate();
    }

    /**
     * Kiểm tra xem shimmer có đang chạy không
     */
    public boolean isShimmering() {
        return isShimmering;
    }

    /**
     * Set shimmer duration in milliseconds
     */
    public void setShimmerDuration(int durationMs) {
        this.shimmerDuration = durationMs;
        if (isShimmering) {
            stopShimmer();
            startShimmer();
        }
    }

    /**
     * Set shimmer color
     */
    public void setShimmerColor(int color) {
        this.shimmerColor = color;
        updateGradient(getWidth(), getHeight());
        invalidate();
    }

    /**
     * Set shimmer direction
     */
    public void setShimmerDirection(int direction) {
        this.shimmerDirection = direction;
        updateGradient(getWidth(), getHeight());
        invalidate();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        stopShimmer();
    }

    /**
     * Build skeleton view for a specific layout
     */
    public static View buildSkeletonView(Context context, int layoutRes) {
        // Điều này sẽ inflate layout và xám hóa tất cả nội dung
        // Hiện tại, trả về một shimmer placeholder đơn giản
        View shimmerView = new View(context);
        shimmerView.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        shimmerView.setBackgroundColor(DEFAULT_SHIMMER_COLOR);
        return shimmerView;
    }
}
