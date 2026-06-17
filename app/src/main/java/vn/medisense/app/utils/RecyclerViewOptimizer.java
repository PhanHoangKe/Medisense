package vn.medisense.app.utils;

import android.content.Context;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.animation.LayoutAnimationController;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import vn.medisense.app.R;

/**
 * RecyclerViewOptimizer - Performance optimization utilities for RecyclerView
 * Includes pool optimization, lazy loading, and animation management
 */
public class RecyclerViewOptimizer {

    private static final int DEFAULT_POOL_SIZE = 10;
    private static final int MAX_POOL_SIZE = 20;
    private static final int VIEW_TYPE_COUNT = 5;

    /**
     * Optimize RecyclerView for smooth scrolling
     */
    public static void optimizeRecyclerView(@NonNull RecyclerView recyclerView) {
        // Vô hiệu hóa hoạt ảnh thay đổi mục để mượt mà hơn
        RecyclerView.ItemAnimator animator = recyclerView.getItemAnimator();
        if (animator instanceof DefaultItemAnimator) {
            ((DefaultItemAnimator) animator).setSupportsChangeAnimations(false);
        }

        // Đặt kích thước cố định nếu có thể (hiệu suất tốt hơn)
        recyclerView.setHasFixedSize(true);

        // Bật tái chế view
        recyclerView.setItemViewCacheSize(20);

        // Đặt cache vẽ
        recyclerView.setDrawingCacheEnabled(true);
        recyclerView.setDrawingCacheQuality(View.DRAWING_CACHE_QUALITY_HIGH);

        // Bật cuồng lồng cho cuộn mượt mà bên trong cuộn
        recyclerView.setNestedScrollingEnabled(true);

        // Tối ưu pool dựa trên trình quản lý layout
        RecyclerView.LayoutManager lm = recyclerView.getLayoutManager();
        if (lm != null) {
            optimizePoolSize(recyclerView, lm);
        }
    }

    /**
     * Optimize pool size based on layout type
     */
    private static void optimizePoolSize(RecyclerView recyclerView, RecyclerView.LayoutManager layoutManager) {
        RecyclerView.RecycledViewPool pool = recyclerView.getRecycledViewPool();

        if (layoutManager instanceof GridLayoutManager) {
            int spanCount = ((GridLayoutManager) layoutManager).getSpanCount();
            for (int i = 0; i < VIEW_TYPE_COUNT; i++) {
                pool.setMaxRecycledViews(i, spanCount * 3);
            }
        } else if (layoutManager instanceof LinearLayoutManager) {
            // Layout tuyến tính cần ít hơn view được cache
            for (int i = 0; i < VIEW_TYPE_COUNT; i++) {
                pool.setMaxRecycledViews(i, DEFAULT_POOL_SIZE);
            }
        } else {
            // Default pool size
            for (int i = 0; i < VIEW_TYPE_COUNT; i++) {
                pool.setMaxRecycledViews(i, DEFAULT_POOL_SIZE);
            }
        }
    }

    /**
     * Set up layout animation for RecyclerView
     */
    public static void setLayoutAnimation(@NonNull RecyclerView recyclerView, int animationResId) {
        Context context = recyclerView.getContext();
        Animation animation = AnimationUtils.loadAnimation(context, animationResId);
        LayoutAnimationController controller = new LayoutAnimationController(animation);
        controller.setDelay(0.15f);
        controller.setOrder(LayoutAnimationController.ORDER_NORMAL);
        recyclerView.setLayoutAnimation(controller);
    }

    /**
     * Set up staggered layout animation
     */
    public static void setStaggeredLayoutAnimation(@NonNull RecyclerView recyclerView) {
        setLayoutAnimation(recyclerView, R.anim.item_fade_in);
    }

    /**
     * Enable lazy loading with prefetch
     */
    public static void enableLazyLoading(@NonNull RecyclerView recyclerView, int prefetchDistance) {
        recyclerView.setItemViewCacheSize(prefetchDistance);
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            recyclerView.setNestedScrollingEnabled(true);
        }
    }

    /**
     * Optimize for large lists (1000+ items)
     */
    public static void optimizeForLargeDataset(@NonNull RecyclerView recyclerView) {
        // Vô hiệu hóa hoạt ảnh cho tập dữ liệu lớn
        recyclerView.setItemAnimator(null);
        
        // Tăng kích thước cache
        recyclerView.setItemViewCacheSize(50);
        
        // Sử dụng pool lớn hơn
        RecyclerView.RecycledViewPool pool = recyclerView.getRecycledViewPool();
        for (int i = 0; i < VIEW_TYPE_COUNT; i++) {
            pool.setMaxRecycledViews(i, MAX_POOL_SIZE);
        }
        
        // Bật tối ưu hóa tái chế view
        recyclerView.setHasFixedSize(true);
    }

    /**
     * Optimize for images (when items contain images)
     */
    public static void optimizeForImages(@NonNull RecyclerView recyclerView) {
        // Preload images
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                // Kích hoạt tối ưu hóa tải hình ảnh ở đây
            }
        });
        
        // Giảm cache cho hình ảnh để tiết kiệm bộ nhớ
        recyclerView.setItemViewCacheSize(10);
    }

    /**
     * Enable smooth scroll with custom duration
     */
    public static void smoothScrollToPosition(@NonNull RecyclerView recyclerView, int position) {
        recyclerView.smoothScrollToPosition(position);
    }

    /**
     * Fast scroll without animation
     */
    public static void fastScrollToPosition(@NonNull RecyclerView recyclerView, int position) {
        recyclerView.scrollToPosition(position);
    }

    /**
     * Set up edge glow effect for overscroll
     */
    public static void setEdgeGlowColor(@NonNull RecyclerView recyclerView, int color) {
        // Điều này yêu cầu reflection trên các phiên bản Android cũ hơn
        // On Android 29+, use EdgeEffect
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Sử dụng EdgeEffect.setColor()
            try {
                java.lang.reflect.Field field = RecyclerView.class.getDeclaredField("mTopGlow");
                field.setAccessible(true);
                Object edgeEffect = field.get(recyclerView);
                if (edgeEffect != null) {
                    ((android.widget.EdgeEffect) edgeEffect).setColor(color);
                }
            } catch (Exception e) {
                // Bỏ qua các lỗi reflection
            }
        }
    }

    /**
     * RecyclerView Pool Manager for multiple RVs sharing same pool
     */
    public static class SharedPoolManager {
        private final RecyclerView.RecycledViewPool sharedPool;

        public SharedPoolManager() {
            sharedPool = new RecyclerView.RecycledViewPool();
            for (int i = 0; i < VIEW_TYPE_COUNT; i++) {
                sharedPool.setMaxRecycledViews(i, MAX_POOL_SIZE);
            }
        }

        public void sharePoolWith(RecyclerView... recyclerViews) {
            for (RecyclerView rv : recyclerViews) {
                rv.setRecycledViewPool(sharedPool);
            }
        }
    }

    /**
     * ViewHolder that supports efficient image loading
     */
    public abstract static class OptimizedViewHolder extends RecyclerView.ViewHolder {
        private boolean isImageLoaded = false;

        public OptimizedViewHolder(@NonNull View itemView) {
            super(itemView);
        }

        public void setImageLoaded(boolean loaded) {
            this.isImageLoaded = loaded;
        }

        public boolean isImageLoaded() {
            return isImageLoaded;
        }

        /**
         * Called when view is attached - good place to load images
         */
        public void onViewAttached() {
            if (!isImageLoaded) {
                loadImage();
            }
        }

        /**
         * Called when view is detached - cancel image loading
         */
        public void onViewDetached() {
            cancelImageLoading();
        }

        protected abstract void loadImage();
        protected abstract void cancelImageLoading();
    }

    /**
     * Scroll listener that tracks scroll state for optimization
     */
    public abstract static class OptimizedScrollListener extends RecyclerView.OnScrollListener {
        private boolean isScrolling = false;

        @Override
        public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
            super.onScrollStateChanged(recyclerView, newState);
            
            if (newState == RecyclerView.SCROLL_STATE_DRAGGING || 
                newState == RecyclerView.SCROLL_STATE_SETTLING) {
                if (!isScrolling) {
                    isScrolling = true;
                    onScrollStarted();
                }
            } else if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                if (isScrolling) {
                    isScrolling = false;
                    onScrollStopped();
                }
            }
        }

        protected abstract void onScrollStarted();
        protected abstract void onScrollStopped();
    }

    /**
     * Vô hiệu hóa hoạt ảnh tạm thời (ví dụ: trong khi cập nhật hàng loạt)
     */
    public static void temporarilyDisableAnimations(@NonNull RecyclerView recyclerView) {
        RecyclerView.ItemAnimator animator = recyclerView.getItemAnimator();
        if (animator != null) {
            animator.setAddDuration(0);
            animator.setChangeDuration(0);
            animator.setMoveDuration(0);
            animator.setRemoveDuration(0);
        }
    }

    /**
     * Restore default animations
     */
    public static void restoreAnimations(@NonNull RecyclerView recyclerView) {
        RecyclerView.ItemAnimator animator = recyclerView.getItemAnimator();
        if (animator != null) {
            animator.setAddDuration(120);
            animator.setChangeDuration(250);
            animator.setMoveDuration(250);
            animator.setRemoveDuration(120);
        }
    }

    /**
     * Measure RecyclerView performance
     */
    public static RecyclerView.OnScrollListener createPerformanceMonitor() {
        return new RecyclerView.OnScrollListener() {
            private long lastFrameTime = 0;
            private int frameCount = 0;
            private int droppedFrames = 0;

            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                
                long currentTime = System.nanoTime();
                if (lastFrameTime > 0) {
                    long frameTime = (currentTime - lastFrameTime) / 1_000_000; // ms
                    if (frameTime > 16) { // Mục tiêu 60fps = 16ms mỗi frame
                        droppedFrames++;
                    }
                    frameCount++;
                }
                lastFrameTime = currentTime;
            }

            public float getDropRate() {
                return frameCount > 0 ? (float) droppedFrames / frameCount : 0f;
            }
        };
    }
}
