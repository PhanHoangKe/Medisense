package vn.medisense.app.ui.helper;

import android.content.Context;
import android.graphics.Color;
import android.util.TypedValue;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import vn.medisense.app.R;
import vn.medisense.app.databinding.ActivityMainBinding;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

/**
 * Quản lý giao diện và tương tác của thanh lịch tuần (Calendar Strip).
 * Tách rời logic hiển thị này khỏi MainActivity để tăng độ sạch và khả năng tái sử dụng.
 */
public class CalendarStripManager {

    public interface CalendarSelectionListener {
        void onDateSelected(long dateMillis);
    }

    private final ActivityMainBinding binding;
    private final Context context;
    private final CalendarSelectionListener listener;

    private long selectedDateMillis = System.currentTimeMillis();
    private final long[] calendarDayMillis = new long[7];

    private LinearLayout[] dayLayouts;
    private TextView[] tvDayNames;
    private FrameLayout[] frameDayNums;
    private TextView[] tvDayNums;

    public CalendarStripManager(@NonNull ActivityMainBinding binding, @NonNull Context context, @NonNull CalendarSelectionListener listener) {
        this.binding = binding;
        this.context = context;
        this.listener = listener;
        initViews();
    }

    private void initViews() {
        dayLayouts = new LinearLayout[7];
        tvDayNames = new TextView[7];
        frameDayNums = new FrameLayout[7];
        tvDayNums = new TextView[7];

        dayLayouts[0] = binding.layoutDay0;
        dayLayouts[1] = binding.layoutDay1;
        dayLayouts[2] = binding.layoutDay2;
        dayLayouts[3] = binding.layoutDay3;
        dayLayouts[4] = binding.layoutDay4;
        dayLayouts[5] = binding.layoutDay5;
        dayLayouts[6] = binding.layoutDay6;

        tvDayNames[0] = binding.tvDayName0;
        tvDayNames[1] = binding.tvDayName1;
        tvDayNames[2] = binding.tvDayName2;
        tvDayNames[3] = binding.tvDayName3;
        tvDayNames[4] = binding.tvDayName4;
        tvDayNames[5] = binding.tvDayName5;
        tvDayNames[6] = binding.tvDayName6;

        frameDayNums[0] = binding.frameDayNum0;
        frameDayNums[1] = binding.frameDayNum1;
        frameDayNums[2] = binding.frameDayNum2;
        frameDayNums[3] = binding.frameDayNum3;
        frameDayNums[4] = binding.frameDayNum4;
        frameDayNums[5] = binding.frameDayNum5;
        frameDayNums[6] = binding.frameDayNum6;

        tvDayNums[0] = binding.tvDayNum0;
        tvDayNums[1] = binding.tvDayNum1;
        tvDayNums[2] = binding.tvDayNum2;
        tvDayNums[3] = binding.tvDayNum3;
        tvDayNums[4] = binding.tvDayNum4;
        tvDayNums[5] = binding.tvDayNum5;
        tvDayNums[6] = binding.tvDayNum6;
    }

    public void setupCalendarStrip() {
        // Định dạng thứ tự trong tuần bằng tiếng Việt
        SimpleDateFormat dayFormat = new SimpleDateFormat("EE", new Locale("vi", "VN"));
        SimpleDateFormat numFormat = new SimpleDateFormat("d", Locale.getDefault());

        for (int i = 0; i < 7; i++) {
            Calendar dayCal = Calendar.getInstance();
            // 3 ngày trước, hôm nay (vị trí số 3), 3 ngày sau
            dayCal.add(Calendar.DAY_OF_YEAR, i - 3);
            calendarDayMillis[i] = dayCal.getTimeInMillis();

            String dayName = dayFormat.format(dayCal.getTime());
            // Rút gọn thứ: Thứ Hai -> T2, Chủ Nhật -> CN
            if (dayName.toLowerCase().contains("hai") || dayName.contains("2")) {
                dayName = "T2";
            } else if (dayName.toLowerCase().contains("ba") || dayName.contains("3")) {
                dayName = "T3";
            } else if (dayName.toLowerCase().contains("tư") || dayName.toLowerCase().contains("tu") || dayName.contains("4")) {
                dayName = "T4";
            } else if (dayName.toLowerCase().contains("năm") || dayName.toLowerCase().contains("nam") || dayName.contains("5")) {
                dayName = "T5";
            } else if (dayName.toLowerCase().contains("sáu") || dayName.toLowerCase().contains("sau") || dayName.contains("6")) {
                dayName = "T6";
            } else if (dayName.toLowerCase().contains("bảy") || dayName.toLowerCase().contains("bay") || dayName.contains("7")) {
                dayName = "T7";
            } else {
                dayName = "CN";
            }
            tvDayNames[i].setText(dayName);
            tvDayNums[i].setText(numFormat.format(dayCal.getTime()));

            final int index = i;
            dayLayouts[i].setOnClickListener(v -> {
                selectedDateMillis = calendarDayMillis[index];
                updateCalendarSelection();
                listener.onDateSelected(selectedDateMillis);
            });
        }

        updateCalendarSelection();
    }

    public void updateCalendarSelection() {
        Calendar selectedCal = Calendar.getInstance();
        selectedCal.setTimeInMillis(selectedDateMillis);
        int selectedDay = selectedCal.get(Calendar.DAY_OF_YEAR);
        int selectedYear = selectedCal.get(Calendar.YEAR);

        int colorOnSurfaceVariant = getThemeColor(com.google.android.material.R.attr.colorOnSurfaceVariant);
        int colorOnSurface = getThemeColor(com.google.android.material.R.attr.colorOnSurface);

        for (int i = 0; i < 7; i++) {
            Calendar dayCal = Calendar.getInstance();
            dayCal.setTimeInMillis(calendarDayMillis[i]);
            boolean isSelected = (dayCal.get(Calendar.DAY_OF_YEAR) == selectedDay &&
                                  dayCal.get(Calendar.YEAR) == selectedYear);

            if (isSelected) {
                tvDayNames[i].setBackgroundResource(R.drawable.bg_calendar_day_name_selected);
                tvDayNames[i].setTextColor(ContextCompat.getColor(context, R.color.white));
                frameDayNums[i].setBackgroundResource(R.drawable.bg_calendar_day_num_selected);
                tvDayNums[i].setTextColor(colorOnSurface);
            } else {
                tvDayNames[i].setBackgroundColor(Color.TRANSPARENT);
                tvDayNames[i].setTextColor(colorOnSurfaceVariant);
                frameDayNums[i].setBackgroundColor(Color.TRANSPARENT);
                tvDayNums[i].setTextColor(colorOnSurface);
            }
        }

        // Cập nhật phụ đề ngày tháng tương ứng với ngày được chọn trên lịch tuần
        SimpleDateFormat sdf = new SimpleDateFormat("EEEE, 'ngày' d 'tháng' M", new Locale("vi", "VN"));
        String dateStr = sdf.format(selectedCal.getTime());
        if (dateStr != null && !dateStr.isEmpty()) {
            dateStr = dateStr.substring(0, 1).toUpperCase() + dateStr.substring(1);
        }
        binding.tvDate.setText(dateStr);
    }

    public long getSelectedDateMillis() {
        return selectedDateMillis;
    }

    public void setSelectedDateMillis(long selectedDateMillis) {
        this.selectedDateMillis = selectedDateMillis;
        updateCalendarSelection();
    }

    private int getThemeColor(int attrId) {
        TypedValue typedValue = new TypedValue();
        context.getTheme().resolveAttribute(attrId, typedValue, true);
        return typedValue.data;
    }
}
