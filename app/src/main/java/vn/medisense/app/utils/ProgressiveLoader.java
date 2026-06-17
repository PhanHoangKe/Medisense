package vn.medisense.app.utils;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

/**
 * ProgressiveLoader - Manages staged loading with skeleton and fade transitions
 * 0-30%: Skeleton shimmer
 * 30-70%: Text labels appear
 * 70-100%: Images & charts load
 * 100%: Fade to real content
 */
public class ProgressiveLoader {

    public interface LoadingCallback {
        void onStageChanged(LoadingStage stage, int progress);
        void onLoadingComplete();
    }

    public enum LoadingStage {
        SKELETON,      // 0-30%
        TEXT_CONTENT,  // 30-70%
        IMAGES,        // 70-100%
        COMPLETE       // 100%
    }

    private View skeletonView;
    private View contentView;
    private ProgressBar progressBar;
    private LoadingCallback callback;
    
    private int currentProgress = 0;
    private LoadingStage currentStage = LoadingStage.SKELETON;
    private boolean isLoading = false;
    
    private List<View> textViews = new ArrayList<>();
    private List<View> imageViews = new ArrayList<>();
    private List<View> chartViews = new ArrayList<>();

    /**
     * Set up progressive loader with skeleton and content views
     */
    public void setup(View skeleton, View content, ProgressBar progress) {
        this.skeletonView = skeleton;
        this.contentView = content;
        this.progressBar = progress;
        
        // Ban đầu ẩn nội dung
        contentView.setAlpha(0f);
        contentView.setVisibility(View.GONE);
        
        // Show skeleton
        skeletonView.setAlpha(1f);
        skeletonView.setVisibility(View.VISIBLE);
        
        // Kích hoạt shimmer trên skeleton
        startSkeletonShimmer();
        
        // Phân loại view theo loại
        categorizeViews(contentView);
    }

    /**
     * Set loading callback
     */
    public void setLoadingCallback(LoadingCallback callback) {
        this.callback = callback;
    }

    /**
     * Start progressive loading
     */
    public void startLoading() {
        if (isLoading) return;
        isLoading = true;
        currentProgress = 0;
        currentStage = LoadingStage.SKELETON;
        
        // Hoạt ảnh qua các giai đoạn
        simulateLoadingProgress();
    }

    /**
     * Simulate loading progress (in real app, this would be based on actual data loading)
     */
    private void simulateLoadingProgress() {
        ValueAnimator animator = ValueAnimator.ofInt(0, 100);
        animator.setDuration(3000); // 3 seconds total
        animator.setInterpolator(new DecelerateInterpolator());
        
        animator.addUpdateListener(animation -> {
            int progress = (int) animation.getAnimatedValue();
            updateProgress(progress);
        });
        
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                finishLoading();
            }
        });
        
        animator.start();
    }

    /**
     * Cập nhật tiến trình tải và kích hoạt thay đổi giai đoạn
     */
    public void updateProgress(int progress) {
        currentProgress = progress;
        
        if (progressBar != null) {
            progressBar.setProgress(progress);
        }
        
        LoadingStage newStage = getStageForProgress(progress);
        
        if (newStage != currentStage) {
            currentStage = newStage;
            onStageChanged(newStage);
        }
        
        if (callback != null) {
            callback.onStageChanged(currentStage, progress);
        }
    }

    /**
     * Get stage based on progress percentage
     */
    private LoadingStage getStageForProgress(int progress) {
        if (progress < 30) return LoadingStage.SKELETON;
        if (progress < 70) return LoadingStage.TEXT_CONTENT;
        if (progress < 100) return LoadingStage.IMAGES;
        return LoadingStage.COMPLETE;
    }

    /**
     * Handle stage transitions
     */
    private void onStageChanged(LoadingStage stage) {
        switch (stage) {
            case TEXT_CONTENT:
                // Hiển thị dần các view văn bản
                fadeInViews(textViews, 0);
                break;
                
            case IMAGES:
                // Hiển thị dần hình ảnh và biểu đồ
                fadeInViews(imageViews, 100);
                fadeInViews(chartViews, 200);
                break;
                
            case COMPLETE:
                finishLoading();
                break;
        }
    }

    /**
     * Fade in a list of views with stagger
     */
    private void fadeInViews(List<View> views, int startDelay) {
        for (int i = 0; i < views.size(); i++) {
            View view = views.get(i);
            view.setAlpha(0f);
            view.setVisibility(View.VISIBLE);
            
            ObjectAnimator fadeIn = ObjectAnimator.ofFloat(view, "alpha", 0f, 1f);
            fadeIn.setDuration(300);
            fadeIn.setStartDelay(startDelay + (i * 50));
            fadeIn.start();
        }
    }

    /**
     * Complete loading and show real content
     */
    public void finishLoading() {
        isLoading = false;
        
        // Stop shimmer
        stopSkeletonShimmer();
        
        // Mờ dần skeleton
        ObjectAnimator skeletonFade = ObjectAnimator.ofFloat(skeletonView, "alpha", 1f, 0f);
        skeletonFade.setDuration(300);
        
        // Mờ dần nội dung (với kiểm tra null)
        if (contentView != null) {
            contentView.setVisibility(View.VISIBLE);
        }
        ObjectAnimator contentFade = (contentView != null) 
                ? ObjectAnimator.ofFloat(contentView, "alpha", 0f, 1f)
                : ObjectAnimator.ofFloat(skeletonView, "alpha", 0f, 0f); // dummy animator
        contentFade.setDuration(400);
        
        // Đảm bảo tất cả các view đều hiển thị
        setViewsVisible(textViews);
        setViewsVisible(imageViews);
        setViewsVisible(chartViews);
        
        AnimatorSet transition = new AnimatorSet();
        transition.playTogether(skeletonFade, contentFade);
        transition.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (skeletonView != null) {
                    skeletonView.setVisibility(View.GONE);
                }
                if (callback != null) {
                    callback.onLoadingComplete();
                }
            }
        });
        transition.start();
    }

    /**
     * Set all views in list to visible
     */
    private void setViewsVisible(List<View> views) {
        for (View view : views) {
            view.setAlpha(1f);
            view.setVisibility(View.VISIBLE);
        }
    }

    /**
     * Start shimmer animation on skeleton
     */
    private void startSkeletonShimmer() {
        if (skeletonView instanceof ShimmerFrameLayout) {
            ((ShimmerFrameLayout) skeletonView).startShimmer();
        }
    }

    /**
     * Stop shimmer animation
     */
    private void stopSkeletonShimmer() {
        if (skeletonView instanceof ShimmerFrameLayout) {
            ((ShimmerFrameLayout) skeletonView).stopShimmer();
        }
    }

    /**
     * Categorize views by type for staged loading
     */
    private void categorizeViews(View root) {
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                View child = group.getChildAt(i);
                categorizeView(child);
                categorizeViews(child);
            }
        }
    }

    /**
     * Categorize a single view
     */
    private void categorizeView(View view) {
        if (view instanceof TextView) {
            textViews.add(view);
            view.setAlpha(0f);
        } else if (view instanceof ImageView) {
            imageViews.add(view);
            view.setAlpha(0f);
        } else if (isChartView(view)) {
            chartViews.add(view);
            view.setAlpha(0f);
        }
    }

    /**
     * Check if view is a chart
     */
    private boolean isChartView(View view) {
        // Kiểm tra các lớp biểu đồ phổ biến
        String className = view.getClass().getSimpleName().toLowerCase();
        return className.contains("chart") || 
               className.contains("graph") ||
               className.contains("plot");
    }

    /**
     * Show loading state immediately (without animation)
     */
    public void showLoading() {
        if (skeletonView != null) {
            skeletonView.setVisibility(View.VISIBLE);
            skeletonView.setAlpha(1f);
        }
        if (contentView != null) {
            contentView.setVisibility(View.GONE);
        }
        startSkeletonShimmer();
    }

    /**
     * Hide loading and show content immediately
     */
    public void hideLoading() {
        stopSkeletonShimmer();
        if (skeletonView != null) {
            skeletonView.setVisibility(View.GONE);
        }
        if (contentView != null) {
            contentView.setVisibility(View.VISIBLE);
            contentView.setAlpha(1f);
        }
        setViewsVisible(textViews);
        setViewsVisible(imageViews);
        setViewsVisible(chartViews);
    }

    /**
     * Check if currently loading
     */
    public boolean isLoading() {
        return isLoading;
    }

    /**
     * Get current loading stage
     */
    public LoadingStage getCurrentStage() {
        return currentStage;
    }

    /**
     * Get current progress
     */
    public int getCurrentProgress() {
        return currentProgress;
    }

    /**
     * Attach to RecyclerView for item-level skeleton loading
     */
    public static void attachToRecyclerView(RecyclerView recyclerView, SkeletonViewProvider provider) {
        // Điều này sẽ hiển thị các mục skeleton trong khi dữ liệu thực tế tải
        // Việc triển khai phụ thuộc vào thiết lập RecyclerView cụ thể
    }

    /**
     * Interface for providing skeleton views
     */
    public interface SkeletonViewProvider {
        View getSkeletonView(int viewType);
    }
}
