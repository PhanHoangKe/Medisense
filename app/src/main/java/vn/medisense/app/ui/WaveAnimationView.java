package vn.medisense.app.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

/**
 * WaveAnimationView - Hiệu ứng sóng âm khi lắng nghe giọng nói
 */
public class WaveAnimationView extends View {
    
    private Paint paint;
    private float[] barHeights;
    private static final int BAR_COUNT = 5;
    private static final int MAX_BAR_HEIGHT = 80;
    private static final int MIN_BAR_HEIGHT = 20;
    private float currentVolume = 0f;

    public WaveAnimationView(Context context) {
        super(context);
        init();
    }

    public WaveAnimationView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public WaveAnimationView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        paint = new Paint();
        int color = androidx.core.content.ContextCompat.getColor(getContext(), vn.medisense.app.R.color.colorPrimary);
        paint.setColor(color);
        paint.setStyle(Paint.Style.FILL);
        paint.setAntiAlias(true);
        
        barHeights = new float[BAR_COUNT];
        for (int i = 0; i < BAR_COUNT; i++) {
            barHeights[i] = MIN_BAR_HEIGHT;
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        
        int width = getWidth();
        int height = getHeight();
        int barWidth = width / (BAR_COUNT * 2);
        int spacing = barWidth;
        
        for (int i = 0; i < BAR_COUNT; i++) {
            float x = i * (barWidth + spacing) + spacing;
            float barHeight = barHeights[i];
            float y = (height - barHeight) / 2f;
            
            // Vẽ thanh với góc bo tròn
            canvas.drawRoundRect(
                x, 
                y, 
                x + barWidth, 
                y + barHeight,
                barWidth / 2f,
                barWidth / 2f,
                paint
            );
        }
    }

    /**
     * Cập nhật volume từ SpeechRecognizer
     * @param rmsdB Volume level (-2 đến 10)
     */
    public void updateVolume(float rmsdB) {
        // Chuyển đổi rmsdB thành scale 0-1
        currentVolume = Math.max(0, Math.min(1, (rmsdB + 2) / 12f));
        
        // Cập nhật chiều cao các thanh
        for (int i = 0; i < BAR_COUNT; i++) {
            // Tạo hiệu ứng sóng với độ trễ khác nhau
            float delay = i * 0.1f;
            float targetHeight = MIN_BAR_HEIGHT + 
                (MAX_BAR_HEIGHT - MIN_BAR_HEIGHT) * currentVolume * 
                (1 - delay);
            
            // Smooth transition
            barHeights[i] += (targetHeight - barHeights[i]) * 0.3f;
        }
        
        invalidate();
    }

    /**
     * Reset animation
     */
    public void reset() {
        for (int i = 0; i < BAR_COUNT; i++) {
            barHeights[i] = MIN_BAR_HEIGHT;
        }
        invalidate();
    }

    /**
     * Bắt đầu animation idle (khi không có giọng nói)
     */
    public void startIdleAnimation() {
        post(new Runnable() {
            private int frame = 0;
            
            @Override
            public void run() {
                if (getVisibility() != VISIBLE) return;
                
                for (int i = 0; i < BAR_COUNT; i++) {
                    float wave = (float) Math.sin((frame + i * 20) * 0.1) * 0.5f + 0.5f;
                    barHeights[i] = MIN_BAR_HEIGHT + wave * 20;
                }
                
                invalidate();
                frame++;
                postDelayed(this, 50);
            }
        });
    }
}
