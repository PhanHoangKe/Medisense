package vn.medisense.app.ui;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import vn.medisense.app.R;
import vn.medisense.app.database.VitalSign;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * PatientVitalAdapter - Adapter hiển thị các nhóm chỉ số sức khỏe của bệnh nhân kèm sparkline
 */
public class PatientVitalAdapter extends RecyclerView.Adapter<PatientVitalAdapter.ViewHolder> {

    private List<PatientVitalData> patientDataList;
    private SimpleDateFormat timeFormat = new SimpleDateFormat("dd/MM HH:mm", Locale.getDefault());

    public PatientVitalAdapter(List<PatientVitalData> patientDataList) {
        this.patientDataList = patientDataList;
    }

    public void setData(List<PatientVitalData> newData) {
        this.patientDataList = newData;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_patient_vital, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        PatientVitalData data = patientDataList.get(position);
        
        if (data.vitals == null || data.vitals.isEmpty()) {
            holder.tvPatientId.setText(data.typeDisplayName);
            holder.tvStatus.setText("Không có dữ liệu");
            holder.tvStatus.setTextColor(Color.GRAY);
            holder.tvVitalType.setText("Trạng thái");
            holder.tvVitalValue.setText("--");
            holder.tvVitalValue.setTextColor(Color.DKGRAY);
            holder.tvVitalTime.setText("Chưa cập nhật");
            holder.sparklineChart.clear();
            holder.sparklineChart.setNoDataText("Không có dữ liệu vẽ đồ thị");
            return;
        }

        // Sắp xếp theo timestamp tăng dần để đồ thị vẽ đúng thứ tự thời gian
        List<VitalSign> sortedVitals = new ArrayList<>(data.vitals);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            sortedVitals.sort((v1, v2) -> Long.compare(v1.timestamp, v2.timestamp));
        }

        VitalSign latest = sortedVitals.get(sortedVitals.size() - 1);
        holder.tvPatientId.setText(latest.getDisplayName());
        
        boolean abnormal = latest.isAbnormal();
        int alertColor = Color.parseColor("#E53935"); // Đỏ cảnh báo
        int normalColor = Color.parseColor("#4CAF50"); // Xanh lá
        
        if (abnormal) {
            holder.tvStatus.setText("Bất thường");
            holder.tvStatus.setTextColor(alertColor);
            holder.tvVitalValue.setTextColor(alertColor);
        } else {
            holder.tvStatus.setText("Bình thường");
            holder.tvStatus.setTextColor(normalColor);
            holder.tvVitalValue.setTextColor(Color.parseColor("#2E7D32")); // Xanh đậm dễ đọc
        }

        holder.tvVitalType.setText("Lần đo mới nhất");
        holder.tvVitalValue.setText(latest.getDisplayValue());
        holder.tvVitalTime.setText("Cập nhật: " + timeFormat.format(latest.timestamp));

        // Cấu hình Sparkline Chart
        holder.sparklineChart.getDescription().setEnabled(false);
        holder.sparklineChart.getLegend().setEnabled(false);
        holder.sparklineChart.getXAxis().setEnabled(false);
        holder.sparklineChart.getAxisLeft().setEnabled(false);
        holder.sparklineChart.getAxisRight().setEnabled(false);
        holder.sparklineChart.setTouchEnabled(false);
        holder.sparklineChart.getAxisLeft().setDrawGridLines(false);
        holder.sparklineChart.getXAxis().setDrawGridLines(false);

        // Tạo entries cho chart
        List<Entry> entries = new ArrayList<>();
        for (int i = 0; i < sortedVitals.size(); i++) {
            entries.add(new Entry(i, sortedVitals.get(i).value));
        }

        LineDataSet dataSet = new LineDataSet(entries, "Xu hướng");
        dataSet.setColor(abnormal ? alertColor : normalColor);
        dataSet.setLineWidth(2.5f);
        dataSet.setDrawCircles(sortedVitals.size() < 10); // Chỉ vẽ vòng tròn điểm nếu ít điểm dữ liệu
        dataSet.setCircleColor(abnormal ? alertColor : normalColor);
        dataSet.setCircleRadius(3f);
        dataSet.setDrawValues(false);
        dataSet.setMode(LineDataSet.Mode.CUBIC_BEZIER); // Đường cong mượt mà

        LineData lineData = new LineData(dataSet);
        holder.sparklineChart.setData(lineData);
        holder.sparklineChart.invalidate(); // Làm mới đồ thị
    }

    @Override
    public int getItemCount() {
        return patientDataList != null ? patientDataList.size() : 0;
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvPatientId, tvStatus, tvVitalType, tvVitalValue, tvVitalTime;
        LineChart sparklineChart;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvPatientId = itemView.findViewById(R.id.tvPatientId);
            tvStatus = itemView.findViewById(R.id.tvStatus);
            tvVitalType = itemView.findViewById(R.id.tvVitalType);
            tvVitalValue = itemView.findViewById(R.id.tvVitalValue);
            tvVitalTime = itemView.findViewById(R.id.tvVitalTime);
            sparklineChart = itemView.findViewById(R.id.sparklineChart);
        }
    }

    // Model dữ liệu chứa ID Bệnh nhân + các chỉ số sức khỏe của loại này
    public static class PatientVitalData {
        public String type;
        public String typeDisplayName;
        public List<VitalSign> vitals;

        public PatientVitalData(String type, String typeDisplayName, List<VitalSign> vitals) {
            this.type = type;
            this.typeDisplayName = typeDisplayName;
            this.vitals = vitals;
        }
    }
}
