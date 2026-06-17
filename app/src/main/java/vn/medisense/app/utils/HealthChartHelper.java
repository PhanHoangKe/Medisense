package vn.medisense.app.utils;

import android.graphics.Color;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.ValueFormatter;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import vn.medisense.app.database.VitalSign;

public class HealthChartHelper {
    public static void drawChart(LineChart chart, List<VitalSign> vitalSigns, List<Long> medicationTimes, String type) {
        android.content.Context context = chart.getContext();
        boolean isDarkMode = (context.getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        int textColor = isDarkMode ? Color.parseColor("#ACACAE") : Color.parseColor("#757575");

        if (vitalSigns == null || vitalSigns.isEmpty()) {
            chart.clear();
            chart.setNoDataText("Chưa có dữ liệu");
            chart.setNoDataTextColor(textColor);
            chart.invalidate();
            return;
        }

        List<Entry> entries = new ArrayList<>();
        List<Entry> medicationMarkers = new ArrayList<>();

        long baseTime = vitalSigns.get(0).timestamp;

        for (VitalSign vs : vitalSigns) {
            float x = (vs.timestamp - baseTime) / (1000f * 60 * 60);
            float y = vs.value;
            entries.add(new Entry(x, y));
        }

        for (Long medTime : medicationTimes) {
            float x = (medTime - baseTime) / (1000f * 60 * 60);
            float y = findClosestValue(vitalSigns, medTime);
            medicationMarkers.add(new Entry(x, y));
        }



        LineDataSet dataSet = new LineDataSet(entries, getChartLabel(type));
        dataSet.setColor(getChartColor(context, type));
        dataSet.setCircleColor(getChartColor(context, type));
        dataSet.setLineWidth(2f);
        dataSet.setCircleRadius(4f);
        dataSet.setDrawValues(false);
        dataSet.setMode(LineDataSet.Mode.CUBIC_BEZIER);

        LineDataSet markerSet = new LineDataSet(medicationMarkers, "Đã uống thuốc");
        markerSet.setColor(Color.RED);
        markerSet.setCircleColor(Color.RED);
        markerSet.setCircleRadius(6f);
        markerSet.setDrawValues(false);
        markerSet.setLineWidth(0f);
        markerSet.setColor(Color.TRANSPARENT);
        markerSet.setCircleHoleRadius(3f);
        markerSet.setCircleHoleColor(Color.WHITE);

        LineData lineData = new LineData(dataSet, markerSet);
        chart.setData(lineData);

        chart.getDescription().setEnabled(false);
        chart.setTouchEnabled(true);
        chart.setDragEnabled(true);
        chart.setScaleEnabled(true);
        chart.setPinchZoom(true);

        int gridColor = isDarkMode ? Color.parseColor("#2D2C30") : Color.parseColor("#E0E0E0");

        chart.setNoDataTextColor(textColor);
        chart.getLegend().setTextColor(textColor);

        // X Axis
        XAxis xAxis = chart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setGranularity(1f);
        xAxis.setTextColor(textColor);
        xAxis.setGridColor(gridColor);
        xAxis.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                long time = baseTime + (long) (value * 60 * 60 * 1000);
                return new SimpleDateFormat("dd/MM", Locale.getDefault()).format(new Date(time));
            }
        });

        // Y Axis
        YAxis leftAxis = chart.getAxisLeft();
        leftAxis.setDrawGridLines(true);
        leftAxis.setTextColor(textColor);
        leftAxis.setGridColor(gridColor);
        chart.getAxisRight().setEnabled(false);

        chart.invalidate();
    }

    private static float findClosestValue(List<VitalSign> vitalSigns, long targetTime) {
        if (vitalSigns.isEmpty()) return 0;

        VitalSign closest = vitalSigns.get(0);
        long minDiff = Math.abs(vitalSigns.get(0).timestamp - targetTime);

        for (VitalSign vs : vitalSigns) {
            long diff = Math.abs(vs.timestamp - targetTime);
            if (diff < minDiff) {
                minDiff = diff;
                closest = vs;
            }
        }
        return closest.value;
    }

    private static String getChartLabel(String type) {
        switch (type) {
            case "blood_pressure": return "Huyết áp (mmHg)";
            case "heart_rate": return "Nhịp tim (bpm)";
            case "blood_sugar": return "Đường huyết (mg/dL)";
            case "weight": return "Cân nặng (kg)"; // Thêm label cho cân nặng
            default: return type;
        }
    }

    private static int getChartColor(android.content.Context context, String type) {
        switch (type) {
            case "blood_pressure": return androidx.core.content.ContextCompat.getColor(context, vn.medisense.app.R.color.chart_blood_pressure);
            case "heart_rate": return androidx.core.content.ContextCompat.getColor(context, vn.medisense.app.R.color.chart_heart_rate);
            case "blood_sugar": return androidx.core.content.ContextCompat.getColor(context, vn.medisense.app.R.color.chart_blood_sugar);
            case "weight": return androidx.core.content.ContextCompat.getColor(context, vn.medisense.app.R.color.chart_weight); // Màu cho biểu đồ cân nặng
            default: return androidx.core.content.ContextCompat.getColor(context, vn.medisense.app.R.color.chart_default);
        }
    }
}
