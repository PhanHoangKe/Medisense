package vn.medisense.app.ui.helper;

import android.app.Activity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;

import vn.medisense.app.R;
import vn.medisense.app.databinding.ActivityMainBinding;
import vn.medisense.app.utils.ProgressiveLoader;
import vn.medisense.app.utils.ShimmerFrameLayout;

/**
 * Lớp trợ giúp quản lý hiệu ứng tải giả lập (Skeleton / Shimmer Loading) trên Dashboard.
 * Tách biệt logic chuyển đổi UI này khỏi MainActivity.
 */
public class SkeletonLoadingHelper {

    private final Activity activity;
    private final ActivityMainBinding binding;
    private ShimmerFrameLayout skeletonView;
    private View contentView;
    private ProgressiveLoader progressiveLoader;

    public SkeletonLoadingHelper(@NonNull Activity activity, @NonNull ActivityMainBinding binding) {
        this.activity = activity;
        this.binding = binding;
    }

    /**
     * Khởi tạo Skeleton Loading
     */
    public void setupSkeletonLoading() {
        // Nạp layout skeleton
        skeletonView = (ShimmerFrameLayout) LayoutInflater.from(activity).inflate(R.layout.skeleton_dashboard, null);
        contentView = binding.getRoot().findViewById(R.id.main);
        
        // Tạo trình tải động tiến trình
        progressiveLoader = new ProgressiveLoader();
        
        // Hiển thị skeleton lúc bắt đầu
        showSkeletonLoading();
        
        // Cấu hình callback chuyển đổi giai đoạn tải
        progressiveLoader.setLoadingCallback(new ProgressiveLoader.LoadingCallback() {
            @Override
            public void onStageChanged(ProgressiveLoader.LoadingStage stage, int progress) {
                // Có thể mở rộng để cập nhật thêm trạng thái UI chi tiết ở đây nếu cần
            }
            
            @Override
            public void onLoadingComplete() {
                // Ẩn skeleton khi tải xong
                hideSkeletonLoading();
            }
        });
    }

    /**
     * Hiển thị giao diện skeleton shimmer
     */
    public void showSkeletonLoading() {
        if (skeletonView != null && contentView != null) {
            ViewGroup mainContainer = (ViewGroup) contentView.getParent();
            if (mainContainer != null) {
                if (skeletonView.getParent() == null) {
                    mainContainer.addView(skeletonView, 0);
                }
            }
            
            skeletonView.startShimmer();
            
            // Giả lập tiến trình tải động sau 500ms
            skeletonView.postDelayed(() -> {
                if (progressiveLoader != null) {
                    progressiveLoader.startLoading();
                }
            }, 500);
        }
    }

    /**
     * Ẩn skeleton và thực hiện hiệu ứng mờ dần (Fade Out)
     */
    public void hideSkeletonLoading() {
        if (skeletonView != null) {
            skeletonView.stopShimmer();
            
            skeletonView.animate()
                    .alpha(0f)
                    .setDuration(300)
                    .withEndAction(() -> {
                        ViewGroup parent = (ViewGroup) skeletonView.getParent();
                        if (parent != null) {
                            parent.removeView(skeletonView);
                        }
                    })
                    .start();
        }
    }

    /**
     * Kích hoạt tải lại dữ liệu kèm hiệu ứng skeleton
     */
    public void refreshWithSkeleton() {
        if (progressiveLoader != null) {
            showSkeletonLoading();
        }
    }

    /**
     * Hoàn thành tiến trình tải và ẩn skeleton
     */
    public void finishLoading() {
        if (progressiveLoader != null && progressiveLoader.isLoading()) {
            progressiveLoader.finishLoading();
        }
    }

    /**
     * Dừng hiệu ứng Shimmer (Dành cho việc giải phóng tài nguyên)
     */
    public void stopShimmer() {
        if (skeletonView != null) {
            skeletonView.stopShimmer();
        }
    }
}
