package vn.medisense.app.utils;

import android.app.Activity;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.android.material.card.MaterialCardView;

import java.util.Calendar;

import vn.medisense.app.R;
import vn.medisense.app.database.ReminderWithMedication;

/**
 * ContextualSuggestionManager - Smart contextual banners and alerts
 * Provides intelligent suggestions based on user context and behavior
 */
public class ContextualSuggestionManager {

    public enum SuggestionType {
        MISSED_MEDICATION,
        HIGH_BLOOD_PRESSURE,
        WEEKEND_SUMMARY,
        MEDICATION_DUE,
        LOW_STOCK,
        WEATHER_HEALTH,
        APPOINTMENT_REMINDER
    }

    public enum Priority {
        LOW,      // Subtle tooltip
        MEDIUM,   // Floating banner
        HIGH      // Alert card (modal-like)
    }

    private final Context context;
    private final ViewGroup rootContainer;
    private final Handler handler;
    private View currentBanner;
    private OnSuggestionActionListener actionListener;

    public interface OnSuggestionActionListener {
        void onSuggestionAction(SuggestionType type, String action);
        void onSuggestionDismiss(SuggestionType type);
    }

    public ContextualSuggestionManager(Context context, ViewGroup rootContainer) {
        this.context = context;
        this.rootContainer = rootContainer;
        this.handler = new Handler(Looper.getMainLooper());
    }

    public void setOnSuggestionActionListener(OnSuggestionActionListener listener) {
        this.actionListener = listener;
    }

    /**
     * Show missed medication suggestion
     */
    public void showMissedMedicationSuggestion(String medicationName, int hoursLate) {
        View banner = createBanner(
                SuggestionType.MISSED_MEDICATION,
                Priority.MEDIUM,
                R.drawable.ic_medication,
                "Bổ sung ngay?",
                "Bạn quên uống " + medicationName + " (" + hoursLate + " giờ trễ)",
                "Bổ sung",
                "Bỏ qua"
        );
        showBanner(banner, 10000); // 10 seconds
    }

    /**
     * Show high blood pressure alert
     */
    public void showHighBloodPressureAlert(int systolic, int diastolic) {
        View banner = createAlertCard(
                SuggestionType.HIGH_BLOOD_PRESSURE,
                R.drawable.ic_health,
                "Huyết áp cao",
                "Số đo: " + systolic + "/" + diastolic + " mmHg\nĐo lại sau 15 phút",
                "Đo lại",
                "Đã hiểu"
        );
        showBanner(banner, 15000); // 15 seconds
    }

    /**
     * Show weekend summary tooltip
     */
    public void showWeekendSummaryTooltip() {
        View tooltip = createTooltip(
                SuggestionType.WEEKEND_SUMMARY,
                R.drawable.ic_health,
                "Xem tổng kết tuần",
                "Báo cáo tuần này đã sẵn sàng"
        );
        showBanner(tooltip, 8000); // 8 seconds
    }

    /**
     * Show medication due reminder
     */
    public void showMedicationDueReminder(ReminderWithMedication reminder) {
        String medName = reminder.medication != null ? reminder.medication.name : "Thuốc";
        View banner = createBanner(
                SuggestionType.MEDICATION_DUE,
                Priority.MEDIUM,
                R.drawable.ic_notification,
                "Đến giờ uống thuốc",
                medName + " - " + reminder.medication.dosage,
                "Đã uống",
                "Nhắc sau"
        );
        showBanner(banner, 30000); // 30 seconds
    }

    /**
     * Show low stock warning
     */
    public void showLowStockWarning(String medicationName, int remainingDays) {
        View banner = createBanner(
                SuggestionType.LOW_STOCK,
                Priority.MEDIUM,
                R.drawable.ic_info,
                "Thuốc sắp hết",
                medicationName + " chỉ còn đủ " + remainingDays + " ngày",
                "Mua thêm",
                "Đã biết"
        );
        showBanner(banner, 12000); // 12 seconds
    }

    /**
     * Check if should show weekend summary
     */
    public boolean shouldShowWeekendSummary() {
        Calendar calendar = Calendar.getInstance();
        int dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK);
        // Saturday or Sunday
        return dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY;
    }

    /**
     * Dismiss current banner
     */
    public void dismissCurrentBanner() {
        if (currentBanner != null) {
            hideBanner(currentBanner);
        }
    }

    /**
     * Create floating banner view
     */
    private View createBanner(SuggestionType type, Priority priority, int iconRes,
                             String title, String message, String primaryAction, String secondaryAction) {
        LayoutInflater inflater = LayoutInflater.from(context);
        View banner = inflater.inflate(R.layout.contextual_banner, null);

        ImageView icon = banner.findViewById(R.id.iconSuggestion);
        TextView titleText = banner.findViewById(R.id.textTitle);
        TextView messageText = banner.findViewById(R.id.textMessage);
        TextView primaryBtn = banner.findViewById(R.id.btnPrimary);
        TextView secondaryBtn = banner.findViewById(R.id.btnSecondary);

        icon.setImageResource(iconRes);
        titleText.setText(title);
        messageText.setText(message);
        primaryBtn.setText(primaryAction);
        secondaryBtn.setText(secondaryAction);

        // Primary action
        primaryBtn.setOnClickListener(v -> {
            AnimationUtils.buttonPulseAnimation(v);
            if (actionListener != null) {
                actionListener.onSuggestionAction(type, primaryAction);
            }
            hideBanner(banner);
        });

        // Secondary action / Dismiss
        secondaryBtn.setOnClickListener(v -> {
            if (actionListener != null) {
                actionListener.onSuggestionDismiss(type);
            }
            hideBanner(banner);
        });

        // Đặt nền dựa trên mức độ ưu tiên
        MaterialCardView card = banner.findViewById(R.id.cardBanner);
        if (card != null) {
            switch (priority) {
                case HIGH:
                    card.setCardBackgroundColor(context.getColor(R.color.error_50));
                    break;
                case MEDIUM:
                    card.setCardBackgroundColor(context.getColor(R.color.md_theme_light_primaryContainer));
                    break;
                case LOW:
                    card.setCardBackgroundColor(context.getColor(R.color.neutral_100));
                    break;
            }
        }

        return banner;
    }

    /**
     * Create alert card (high priority)
     */
    private View createAlertCard(SuggestionType type, int iconRes, String title,
                                 String message, String primaryAction, String secondaryAction) {
        // Sử dụng cùng layout nhưng với kiểu dáng khác
        View alert = createBanner(type, Priority.HIGH, iconRes, title, message, primaryAction, secondaryAction);
        
        // Thêm kiểu dáng cảnh báo
        MaterialCardView card = alert.findViewById(R.id.cardBanner);
        if (card != null) {
            card.setStrokeColor(context.getColor(R.color.status_error));
            card.setStrokeWidth(2);
        }
        
        return alert;
    }

    /**
     * Create tooltip (low priority)
     */
    private View createTooltip(SuggestionType type, int iconRes, String title, String message) {
        LayoutInflater inflater = LayoutInflater.from(context);
        View tooltip = inflater.inflate(R.layout.contextual_tooltip, null);

        ImageView icon = tooltip.findViewById(R.id.iconTooltip);
        TextView titleText = tooltip.findViewById(R.id.textTooltipTitle);
        TextView messageText = tooltip.findViewById(R.id.textTooltipMessage);

        icon.setImageResource(iconRes);
        titleText.setText(title);
        messageText.setText(message);

        // Click to dismiss
        tooltip.setOnClickListener(v -> {
            if (actionListener != null) {
                actionListener.onSuggestionAction(type, "click");
            }
            hideBanner(tooltip);
        });

        return tooltip;
    }

    /**
     * Show banner with animation
     */
    private void showBanner(View banner, long duration) {
        // Bỏ qua banner hiện tại nếu tồn tại
        dismissCurrentBanner();

        currentBanner = banner;

        // Add to container
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, 0, 0, 0);
        banner.setLayoutParams(params);

        rootContainer.setVisibility(View.VISIBLE);
        rootContainer.addView(banner);

        // Animate in
        Animation slideDown = android.view.animation.AnimationUtils.loadAnimation(context, R.anim.slide_in_right);
        slideDown.setDuration(300);
        banner.startAnimation(slideDown);

        // Tự động đóng sau thời lượng
        handler.postDelayed(() -> hideBanner(banner), duration);
    }

    /**
     * Hide banner with animation
     */
    private void hideBanner(View banner) {
        if (banner == null) return;

        Animation slideUp = android.view.animation.AnimationUtils.loadAnimation(context, R.anim.slide_out_left);
        slideUp.setDuration(300);
        slideUp.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {}

            @Override
            public void onAnimationEnd(Animation animation) {
                rootContainer.removeView(banner);
                if (currentBanner == banner) {
                    currentBanner = null;
                }
                if (rootContainer.getChildCount() == 0) {
                    rootContainer.setVisibility(View.GONE);
                }
            }

            @Override
            public void onAnimationRepeat(Animation animation) {}
        });

        banner.startAnimation(slideUp);
    }

    /**
     * Check for contextual triggers and show appropriate suggestions
     */
    public void checkAndShowSuggestions() {
        // Điều này sẽ được gọi định kỳ để kiểm tra điều kiện
        // và hiển thị các gợi ý phù hợp

        // Ví dụ: Hiển thị tóm tắt cuối tuần vào sáng thứ Bảy
        if (shouldShowWeekendSummary()) {
            Calendar calendar = Calendar.getInstance();
            int hour = calendar.get(Calendar.HOUR_OF_DAY);
            if (hour >= 9 && hour <= 11) {
                showWeekendSummaryTooltip();
            }
        }
    }

    /**
     * Stop all pending operations
     */
    public void stop() {
        handler.removeCallbacksAndMessages(null);
        dismissCurrentBanner();
    }
}
