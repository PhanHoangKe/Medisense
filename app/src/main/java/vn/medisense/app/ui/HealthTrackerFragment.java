package vn.medisense.app.ui;

import vn.medisense.app.R;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import vn.medisense.app.BuildConfig;
import vn.medisense.app.api.GeminiApiService;
import vn.medisense.app.api.GeminiRequest;
import vn.medisense.app.api.GeminiResponse;
import vn.medisense.app.database.Reminder;
import vn.medisense.app.database.VitalSign;
import vn.medisense.app.database.MeasurementTask;
import vn.medisense.app.models.RiskReport;
import vn.medisense.app.databinding.FragmentHealthTrackerBinding;
import vn.medisense.app.utils.HealthChartHelper;
import vn.medisense.app.utils.NetworkModule;
import vn.medisense.app.utils.NetworkUtils;

public class HealthTrackerFragment extends Fragment {

    private FragmentHealthTrackerBinding binding;
    private HealthTrackerViewModel viewModel;
    private vn.medisense.app.repository.GeminiRepository geminiRepository;
    private String currentVitalType = "blood_pressure";

    private final BroadcastReceiver vitalSignReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (MeasurementInputDialogActivity.ACTION_VITAL_SIGN_ADDED.equals(intent.getAction())) {
                if (isAdded()) {
                    viewModel.loadLatestVitalSigns();
                    viewModel.loadChartData(currentVitalType);
                    viewModel.loadRiskAssessment();
                }
            }
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        binding = FragmentHealthTrackerBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this).get(HealthTrackerViewModel.class);
        geminiRepository = new vn.medisense.app.repository.GeminiRepository();

        setupUI();
        observeViewModel();

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            requireContext().registerReceiver(vitalSignReceiver,
                    new IntentFilter(MeasurementInputDialogActivity.ACTION_VITAL_SIGN_ADDED),
                    Context.RECEIVER_NOT_EXPORTED);
        } else {
            requireContext().registerReceiver(vitalSignReceiver,
                    new IntentFilter(MeasurementInputDialogActivity.ACTION_VITAL_SIGN_ADDED));
        }

        viewModel.loadLatestVitalSigns();
        viewModel.loadChartData(currentVitalType);
        viewModel.loadRiskAssessment();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        try {
            requireContext().unregisterReceiver(vitalSignReceiver);
        } catch (IllegalArgumentException e) {
            // Unregistered
        }
        binding = null;
    }

    private void setupUI() {
        // Tab Layout Selection
        binding.tabLayout
                .addOnTabSelectedListener(new com.google.android.material.tabs.TabLayout.OnTabSelectedListener() {
                    @Override
                    public void onTabSelected(com.google.android.material.tabs.TabLayout.Tab tab) {
                        if (tab.getPosition() == 0) {
                            binding.layoutCharts.setVisibility(View.VISIBLE);
                            binding.layoutList.setVisibility(View.GONE);
                            binding.fabAddMeasurement.hide();
                        } else {
                            binding.layoutCharts.setVisibility(View.GONE);
                            binding.layoutList.setVisibility(View.VISIBLE);
                            binding.fabAddMeasurement.show();
                        }
                    }

                    @Override
                    public void onTabUnselected(com.google.android.material.tabs.TabLayout.Tab tab) {
                    }

                    @Override
                    public void onTabReselected(com.google.android.material.tabs.TabLayout.Tab tab) {
                    }
                });

        // Ẩn FAB trên các Charts tab initially
        binding.fabAddMeasurement.hide();

        // Export PDF
        binding.buttonHeaderExport.setOnClickListener(v -> {
            Toast.makeText(requireContext(), "Đang tạo báo cáo PDF...", Toast.LENGTH_SHORT).show();
            vn.medisense.app.utils.PdfGenerator.generateAndShareReport(requireActivity());
        });

        // Connect đến Health Connect
        updateHealthConnectUI();
        binding.buttonHealthConnect.setOnClickListener(v -> {
            android.content.SharedPreferences prefs = requireContext().getSharedPreferences("MediSensePrefs",
                    Context.MODE_PRIVATE);
            boolean isConnected = prefs.getBoolean("health_connect_connected", false);
            if (isConnected) {
                new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                        .setTitle("Ngắt kết nối Health Connect")
                        .setMessage(
                                "Bạn có chắc chắn muốn ngắt kết nối MediSense với Google Health Connect? Số bước chân và giấc ngủ sẽ không được đồng bộ tự động nữa.")
                        .setPositiveButton("Ngắt kết nối", (dialog, which) -> {
                            prefs.edit().putBoolean("health_connect_connected", false).apply();
                            updateHealthConnectUI();
                            Toast.makeText(requireContext(), "Đã ngắt kết nối Health Connect", Toast.LENGTH_SHORT)
                                    .show();
                        })
                        .setNegativeButton("Hủy", null)
                        .show();
            } else {
                new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                        .setTitle("Kết nối Health Connect")
                        .setMessage(
                                "Bạn có muốn kết nối MediSense với Google Health Connect để đồng bộ số bước chân, nhịp tim và giấc ngủ tự động không?")
                        .setPositiveButton("Đồng ý", (dialog, which) -> {
                            prefs.edit().putBoolean("health_connect_connected", true).apply();
                            updateHealthConnectUI();
                            Toast.makeText(requireContext(), "Kết nối thành công! Đang đồng bộ dữ liệu...",
                                    Toast.LENGTH_LONG).show();
                        })
                        .setNegativeButton("Hủy", null)
                        .show();
            }
        });

        // Travel Medication Plan
        binding.buttonTravelPlan.setOnClickListener(v -> {
            showTravelPlanInputDialog();
        });

        binding.cardBloodPressure.setOnClickListener(v -> {
            currentVitalType = "blood_pressure";
            showInputDialog("Huyết áp", "blood_pressure");
        });

        binding.cardHeartRate.setOnClickListener(v -> {
            currentVitalType = "heart_rate";
            showInputDialog("Nhịp tim", "heart_rate");
        });

        binding.cardBloodSugar.setOnClickListener(v -> {
            currentVitalType = "blood_sugar";
            showInputDialog("Đường huyết", "blood_sugar");
        });

        // Thêm sự kiện click cho card Cân nặng
        binding.cardWeight.setOnClickListener(v -> {
            currentVitalType = "weight";
            showInputDialog("Cân nặng", "weight");
        });

        // Segmented Toggle buttons listener
        binding.chartTabToggleGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) {
                if (checkedId == vn.medisense.app.R.id.buttonBloodPressureChart) {
                    currentVitalType = "blood_pressure";
                } else if (checkedId == vn.medisense.app.R.id.buttonHeartRateChart) {
                    currentVitalType = "heart_rate";
                } else if (checkedId == vn.medisense.app.R.id.buttonBloodSugarChart) {
                    currentVitalType = "blood_sugar";
                }
                // Bỏ xử lý buttonWeightChart vì không có trong toggle group nữa
                viewModel.loadChartData(currentVitalType);
            }
        });

        // FAB Quick Add
        binding.fabAddMeasurement.setOnClickListener(v -> showQuickAddSelectionDialog());

        binding.buttonAiAnalysis.setOnClickListener(v -> performAIAnalysis());

        binding.buttonAddMeasurementTask.setOnClickListener(v -> showAddMeasurementTaskDialog());

        binding.buttonToggleAiDetails.setOnClickListener(v -> {
            toggleCollapsible(binding.layoutAiDetails, binding.imageChevronAi);
        });

        // Button phân tích xu hướng AI
        binding.buttonAnalyzeTrends.setOnClickListener(v -> showHealthTrendsBottomSheet());

        binding.recyclerMeasurementTasks.setLayoutManager(new LinearLayoutManager(requireContext()));
    }

    /**
     * Hiển thị bottom sheet phân tích xu hướng sức khỏe
     */
    private void showHealthTrendsBottomSheet() {
        HealthTrendsBottomSheet bottomSheet = HealthTrendsBottomSheet.newInstance();
        bottomSheet.show(getChildFragmentManager(), "HealthTrendsBottomSheet");
    }

    private void observeViewModel() {
        viewModel.getLatestVitalSigns().observe(getViewLifecycleOwner(), signs -> {
            // Cập nhật để xử lý 4 loại vital signs (bao gồm weight)
            if (signs != null && signs.length == 4) {
                // Huyết áp
                if (signs[0] != null) {
                    binding.textBloodPressureValue.setText(signs[0].getDisplayValue());
                    binding.textBloodPressureTime.setText(formatTime(signs[0].timestamp));
                    updateBadgeStatus(binding.badgeBloodPressureStatus, signs[0]);
                } else {
                    binding.textBloodPressureValue.setText("--/--");
                    binding.textBloodPressureTime.setText("Chưa đo");
                    binding.badgeBloodPressureStatus.setText("Chưa đo");
                    binding.badgeBloodPressureStatus.setTextColor(Color.GRAY);
                    binding.badgeBloodPressureStatus.setBackgroundTintList(
                            android.content.res.ColorStateList.valueOf(Color.parseColor("#3A3A3C")));
                }
                
                // Nhịp tim
                if (signs[1] != null) {
                    binding.textHeartRateValue.setText(signs[1].getDisplayValue());
                    binding.textHeartRateTime.setText(formatTime(signs[1].timestamp));
                    updateBadgeStatus(binding.badgeHeartRateStatus, signs[1]);
                } else {
                    binding.textHeartRateValue.setText("--");
                    binding.textHeartRateTime.setText("Chưa đo");
                    binding.badgeHeartRateStatus.setText("Chưa đo");
                    binding.badgeHeartRateStatus.setTextColor(Color.GRAY);
                    binding.badgeHeartRateStatus.setBackgroundTintList(
                            android.content.res.ColorStateList.valueOf(Color.parseColor("#3A3A3C")));
                }
                
                // Đường huyết
                if (signs[2] != null) {
                    binding.textBloodSugarValue.setText(signs[2].getDisplayValue());
                    binding.textBloodSugarTime.setText(formatTime(signs[2].timestamp));
                    updateBadgeStatus(binding.badgeBloodSugarStatus, signs[2]);
                } else {
                    binding.textBloodSugarValue.setText("--");
                    binding.textBloodSugarTime.setText("Chưa đo");
                    binding.badgeBloodSugarStatus.setText("Chưa đo");
                    binding.badgeBloodSugarStatus.setTextColor(Color.GRAY);
                    binding.badgeBloodSugarStatus.setBackgroundTintList(
                            android.content.res.ColorStateList.valueOf(Color.parseColor("#3A3A3C")));
                }
                
                // Cân nặng - Thêm xử lý mới
                if (signs[3] != null) {
                    binding.textWeightValue.setText(signs[3].getDisplayValue());
                    binding.textWeightTime.setText(formatTime(signs[3].timestamp));
                } else {
                    binding.textWeightValue.setText("-- kg");
                    binding.textWeightTime.setText("Chưa đo");
                }

                // Cập nhật summary updated text
                long latestTime = 0;
                for (VitalSign vs : signs) {
                    if (vs != null && vs.timestamp > latestTime) {
                        latestTime = vs.timestamp;
                    }
                }
                if (latestTime > 0) {
                    binding.textSummaryUpdated.setText("Cập nhật: " + formatTime(latestTime));
                } else {
                    binding.textSummaryUpdated.setText("Cập nhật: Chưa đo");
                }
            }
        });

        viewModel.getRiskReport().observe(getViewLifecycleOwner(), report -> {
            if (report == null)
                return;
            binding.cardRiskAssessment.setVisibility(View.VISIBLE);
            binding.textRiskAdvice.setText(report.getAdvice());
            binding.textRiskDetails.setText(report.getDetails());
            binding.textRiskDisclaimer.setText(getString(vn.medisense.app.R.string.ai_medical_disclaimer));

            // Dynamic Health Score computation
            double adherence = report.getAdherenceRate();
            int score = (int) (adherence * 100);
            if (score < 10)
                score = 10;
            if (score > 100)
                score = 100;
            binding.textHealthScore.setText(String.valueOf(score));
            binding.progressHealthScore.setProgress(score);

            // Risk Level mapping
            String levelText = "Thấp";
            int colorRes = vn.medisense.app.R.color.status_success;

            switch (report.getLevel()) {
                case LOW:
                    levelText = "Thấp";
                    colorRes = vn.medisense.app.R.color.status_success;
                    break;
                case MEDIUM:
                    levelText = "Trung bình";
                    colorRes = vn.medisense.app.R.color.status_warning;
                    break;
                case HIGH:
                    levelText = "Cao";
                    colorRes = vn.medisense.app.R.color.status_error;
                    break;
            }

            binding.textSummaryRiskBadge.setText("Rủi ro: " + levelText);
            binding.textSummaryRiskBadge
                    .setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(), colorRes));

            boolean isNightMode = (getResources().getConfiguration().uiMode
                    & android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES;
            int riskBgColor;
            if (isNightMode) {
                riskBgColor = report.getLevel() == RiskReport.RiskLevel.LOW ? Color.parseColor("#1B3A24")
                        : report.getLevel() == RiskReport.RiskLevel.MEDIUM ? Color.parseColor("#3E2723")
                                : Color.parseColor("#3E1F1F");
            } else {
                riskBgColor = report.getLevel() == RiskReport.RiskLevel.LOW ? Color.parseColor("#E8F5E9")
                        : report.getLevel() == RiskReport.RiskLevel.MEDIUM ? Color.parseColor("#FFF3E0")
                                : Color.parseColor("#FFEBEE");
            }
            binding.textSummaryRiskBadge.setBackgroundTintList(android.content.res.ColorStateList.valueOf(riskBgColor));

            binding.textRiskLevel.setText("Mức độ: " + levelText.toUpperCase());
            binding.textRiskLevel
                    .setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(), colorRes));
            binding.viewRiskIndicator.setBackgroundTintList(android.content.res.ColorStateList
                    .valueOf(androidx.core.content.ContextCompat.getColor(requireContext(), colorRes)));

            binding.textSummaryAdvice.setText(report.getAdvice());

            // Adherence progress indicator
            int adherencePercent = (int) (adherence * 100);
            binding.progressAdherence.setProgress(adherencePercent);
            binding.textAdherencePercent.setText(adherencePercent + "%");
        });

        viewModel.getChartData().observe(getViewLifecycleOwner(), data -> {
            binding.progressBar.setVisibility(View.GONE);
            if (data.vitalSigns == null || data.vitalSigns.isEmpty()) {
                binding.lineChart.setVisibility(View.GONE);
                binding.layoutChartEmptyState.setVisibility(View.VISIBLE);
                binding.layoutChartStats.setVisibility(View.GONE);
                updateChartStats(null, data.type);
            } else {
                binding.lineChart.setVisibility(View.VISIBLE);
                binding.layoutChartEmptyState.setVisibility(View.GONE);
                binding.layoutChartStats.setVisibility(View.VISIBLE);
                HealthChartHelper.drawChart(binding.lineChart, data.vitalSigns, data.medicationTimes, data.type);
                updateChartStats(data.vitalSigns, data.type);
            }
        });

        viewModel.getAllMeasurementTasks().observe(getViewLifecycleOwner(), tasks -> {
            if (tasks == null || tasks.isEmpty()) {
                binding.textNoMeasurementTasks.setVisibility(View.VISIBLE);
                binding.recyclerMeasurementTasks.setVisibility(View.GONE);
                binding.layoutNextMeasurementInfo.setVisibility(View.GONE);
            } else {
                binding.textNoMeasurementTasks.setVisibility(View.GONE);
                binding.recyclerMeasurementTasks.setVisibility(View.VISIBLE);
                binding.layoutNextMeasurementInfo.setVisibility(View.VISIBLE);

                String nextTimeStr = findNextMeasurementTime(tasks);
                binding.textNextMeasurementSchedule.setText("Lịch đo tiếp theo: " + nextTimeStr);
                binding.textMeasurementFrequency.setText("Tần suất: Hàng ngày");
                binding.textMeasurementStatus.setText("Đã bật");
                binding.textMeasurementStatus.setTextColor(androidx.core.content.ContextCompat
                        .getColor(requireContext(), vn.medisense.app.R.color.status_success));

                boolean isNightMode = (getResources().getConfiguration().uiMode
                        & android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES;
                int activeBgColor = isNightMode ? Color.parseColor("#1B3A24") : Color.parseColor("#E8F5E9");
                binding.textMeasurementStatus
                        .setBackgroundTintList(android.content.res.ColorStateList.valueOf(activeBgColor));

                MeasurementTaskAdapter adapter = new MeasurementTaskAdapter(tasks, task -> {
                    viewModel.deleteMeasurementTask(task);
                });
                binding.recyclerMeasurementTasks.setAdapter(adapter);
            }
        });
    }

    private void showQuickAddSelectionDialog() {
        String[] options = { "Huyết áp", "Nhịp tim", "Đường huyết", "Cân nặng" }; // Thêm Cân nặng
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Chọn chỉ số muốn nhập")
                .setItems(options, (dialog, which) -> {
                    switch (which) {
                        case 0:
                            currentVitalType = "blood_pressure";
                            showInputDialog("Huyết áp", "blood_pressure");
                            break;
                        case 1:
                            currentVitalType = "heart_rate";
                            showInputDialog("Nhịp tim", "heart_rate");
                            break;
                        case 2:
                            currentVitalType = "blood_sugar";
                            showInputDialog("Đường huyết", "blood_sugar");
                            break;
                        case 3:
                            // Thêm trường hợp cân nặng
                            currentVitalType = "weight";
                            showInputDialog("Cân nặng", "weight");
                            break;
                    }
                })
                .show();
    }

    private String findNextMeasurementTime(List<MeasurementTask> tasks) {
        if (tasks == null || tasks.isEmpty())
            return "Chưa có lịch";
        java.util.Calendar now = java.util.Calendar.getInstance();
        long currentMs = ((now.get(java.util.Calendar.HOUR_OF_DAY) * 60L + now.get(java.util.Calendar.MINUTE)) * 60L
                + now.get(java.util.Calendar.SECOND)) * 1000L + now.get(java.util.Calendar.MILLISECOND);
        long minDiff = Long.MAX_VALUE;
        MeasurementTask nextTask = null;
        for (MeasurementTask task : tasks) {
            long diff = task.timeOfDay - currentMs;
            if (diff < 0) {
                diff += 24 * 60 * 60 * 1000L;
            }
            if (diff < minDiff) {
                minDiff = diff;
                nextTask = task;
            }
        }
        if (nextTask != null) {
            long hours = nextTask.timeOfDay / (60 * 60 * 1000);
            long minutes = (nextTask.timeOfDay % (60 * 60 * 1000)) / (60 * 1000);
            return String.format(Locale.getDefault(), "%02d:%02d (%s)", hours, minutes, nextTask.title);
        }
        return "Chưa có lịch";
    }

    private void updateChartStats(List<VitalSign> list, String type) {
        if (list == null || list.isEmpty()) {
            binding.textStatAverage.setText("--");
            binding.textStatMax.setText("--");
            binding.textStatMin.setText("--");
            return;
        }
        if ("blood_pressure".equals(type)) {
            double sumSys = 0;
            double sumDia = 0;
            float maxSys = -1;
            float maxDia = -1;
            float minSys = Float.MAX_VALUE;
            float minDia = Float.MAX_VALUE;
            int count = 0;
            for (VitalSign vs : list) {
                if (vs.value > 0 && vs.value2 > 0) {
                    sumSys += vs.value;
                    sumDia += vs.value2;
                    if (vs.value > maxSys)
                        maxSys = vs.value;
                    if (vs.value2 > maxDia)
                        maxDia = vs.value2;
                    if (vs.value < minSys)
                        minSys = vs.value;
                    if (vs.value2 < minDia)
                        minDia = vs.value2;
                    count++;
                }
            }
            if (count > 0) {
                int avgSys = (int) (sumSys / count);
                int avgDia = (int) (sumDia / count);
                binding.textStatAverage.setText(String.format(Locale.getDefault(), "%d/%d", avgSys, avgDia));
                binding.textStatMax.setText(String.format(Locale.getDefault(), "%d/%d", (int) maxSys, (int) maxDia));
                binding.textStatMin.setText(String.format(Locale.getDefault(), "%d/%d", (int) minSys, (int) minDia));
            } else {
                binding.textStatAverage.setText("--/--");
                binding.textStatMax.setText("--/--");
                binding.textStatMin.setText("--/--");
            }
        } else {
            double sum = 0;
            float max = -1;
            float min = Float.MAX_VALUE;
            for (VitalSign vs : list) {
                sum += vs.value;
                if (vs.value > max)
                    max = vs.value;
                if (vs.value < min)
                    min = vs.value;
            }
            double avg = sum / list.size();
            
            // Xác định đơn vị dựa trên loại vital sign
            String unit;
            if ("heart_rate".equals(type)) {
                unit = " bpm";
            } else if ("weight".equals(type)) {
                unit = " kg"; // Thêm đơn vị cho cân nặng
            } else {
                unit = " mg/dL"; // Đường huyết
            }
            
            // Format hiển thị
            if ("heart_rate".equals(type)) {
                binding.textStatAverage.setText(String.format(Locale.getDefault(), "%d%s", (int) avg, unit));
                binding.textStatMax.setText(String.format(Locale.getDefault(), "%d%s", (int) max, unit));
                binding.textStatMin.setText(String.format(Locale.getDefault(), "%d%s", (int) min, unit));
            } else {
                // Cho blood_sugar và weight - hiển thị 1 chữ số thập phân
                binding.textStatAverage.setText(String.format(Locale.getDefault(), "%.1f%s", avg, unit));
                binding.textStatMax.setText(String.format(Locale.getDefault(), "%.1f%s", max, unit));
                binding.textStatMin.setText(String.format(Locale.getDefault(), "%.1f%s", min, unit));
            }
        }
    }

    private void updateBadgeStatus(android.widget.TextView badge, VitalSign vs) {
        if (vs == null)
            return;
        boolean isNightMode = (getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        String status;
        int textColor;
        int bgColor;
        if ("blood_pressure".equals(vs.type)) {
            float sys = vs.value;
            float dia = vs.value2;
            if (sys < 120 && dia < 80) {
                status = "Bình thường";
                textColor = isNightMode ? Color.parseColor("#81C784") : Color.parseColor("#2E7D32");
                bgColor = isNightMode ? Color.parseColor("#1B3A24") : Color.parseColor("#E8F5E9");
            } else if (sys >= 130 || dia >= 80) {
                status = "Cao";
                textColor = isNightMode ? Color.parseColor("#E57373") : Color.parseColor("#C62828");
                bgColor = isNightMode ? Color.parseColor("#3E1F1F") : Color.parseColor("#FFEBEE");
            } else {
                status = "Tiền tăng HA";
                textColor = isNightMode ? Color.parseColor("#FFB74D") : Color.parseColor("#EF6C00");
                bgColor = isNightMode ? Color.parseColor("#3E2723") : Color.parseColor("#FFF3E0");
            }
        } else if ("heart_rate".equals(vs.type)) {
            float val = vs.value;
            if (val < 60) {
                status = "Chậm";
                textColor = isNightMode ? Color.parseColor("#FFB74D") : Color.parseColor("#EF6C00");
                bgColor = isNightMode ? Color.parseColor("#3E2723") : Color.parseColor("#FFF3E0");
            } else if (val > 100) {
                status = "Nhanh";
                textColor = isNightMode ? Color.parseColor("#E57373") : Color.parseColor("#C62828");
                bgColor = isNightMode ? Color.parseColor("#3E1F1F") : Color.parseColor("#FFEBEE");
            } else {
                status = "Bình thường";
                textColor = isNightMode ? Color.parseColor("#81C784") : Color.parseColor("#2E7D32");
                bgColor = isNightMode ? Color.parseColor("#1B3A24") : Color.parseColor("#E8F5E9");
            }
        } else if ("blood_sugar".equals(vs.type)) {
            float val = vs.value;
            if (val < 70) {
                status = "Thấp";
                textColor = isNightMode ? Color.parseColor("#FFB74D") : Color.parseColor("#EF6C00");
                bgColor = isNightMode ? Color.parseColor("#3E2723") : Color.parseColor("#FFF3E0");
            } else if (val > 140) {
                status = "Cao";
                textColor = isNightMode ? Color.parseColor("#E57373") : Color.parseColor("#C62828");
                bgColor = isNightMode ? Color.parseColor("#3E1F1F") : Color.parseColor("#FFEBEE");
            } else {
                status = "Bình thường";
                textColor = isNightMode ? Color.parseColor("#81C784") : Color.parseColor("#2E7D32");
                bgColor = isNightMode ? Color.parseColor("#1B3A24") : Color.parseColor("#E8F5E9");
            }
        } else {
            status = "Bình thường";
            textColor = isNightMode ? Color.parseColor("#81C784") : Color.parseColor("#2E7D32");
            bgColor = isNightMode ? Color.parseColor("#1B3A24") : Color.parseColor("#E8F5E9");
        }
        badge.setText(status);
        badge.setTextColor(textColor);
        badge.setBackgroundTintList(android.content.res.ColorStateList.valueOf(bgColor));
    }

    private void showInputDialog(String title, String type) {
        VitalSignInputDialogFragment dialog = VitalSignInputDialogFragment.newInstance(title, type);
        dialog.setListener((returnedType, value1, value2, note) -> {
            VitalSign vitalSign = new VitalSign(returnedType, value1, value2, System.currentTimeMillis(), note);
            viewModel.insertVitalSign(vitalSign, returnedType);
            Toast.makeText(requireContext(), "Đã lưu chỉ số", Toast.LENGTH_SHORT).show();
        });
        dialog.show(getChildFragmentManager(), "VitalSignInputDialog");
    }

    private void showAddMeasurementTaskDialog() {
        MeasurementTaskDialogFragment dialog = MeasurementTaskDialogFragment.newInstance();
        dialog.setListener(task -> {
            viewModel.insertMeasurementTask(task);
            Toast.makeText(requireContext(), "Đã thêm lịch nhắc", Toast.LENGTH_SHORT).show();
        });
        dialog.show(getChildFragmentManager(), "MeasurementTaskDialog");
    }

    private void performAIAnalysis() {
        if (!NetworkUtils.isNetworkAvailable(requireContext())) {
            showOfflineAnalysisFallback();
            return;
        }

        binding.progressBar.setVisibility(View.VISIBLE);
        binding.buttonAiAnalysis.setEnabled(false);

        viewModel.getAIAnalysisData((vitalSigns, reminders) -> {
            // Cảnh báo nếu dữ liệu ít hơn 3 ngày
            boolean hasLowData = vitalSigns == null || vitalSigns.size() < 3;

            String prompt = buildAIPrompt(
                    vitalSigns != null ? vitalSigns : new java.util.ArrayList<>(),
                    reminders != null ? reminders : new java.util.ArrayList<>());

            geminiRepository.assessHealthTrends(prompt, new vn.medisense.app.repository.GeminiRepository.GeminiCallback() {
                @Override
                public void onSuccess(@NonNull String result) {
                    if (!isAdded()) return;
                    requireActivity().runOnUiThread(() -> {
                        binding.progressBar.setVisibility(View.GONE);
                        binding.buttonAiAnalysis.setEnabled(true);
                        showAnalysisDialog(result, hasLowData);
                    });
                }

                @Override
                public void onError(@NonNull String errorMessage) {
                    if (!isAdded()) return;
                    requireActivity().runOnUiThread(() -> {
                        binding.progressBar.setVisibility(View.GONE);
                        binding.buttonAiAnalysis.setEnabled(true);
                        Toast.makeText(requireContext(), errorMessage, Toast.LENGTH_SHORT).show();
                    });
                }
            });
        });
    }

    private void showOfflineAnalysisFallback() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Không có kết nối mạng")
                .setMessage("Phân tích chuyên sâu bằng AI tạm thời vô hiệu hóa do mất mạng.\n\n" +
                        "Vui lòng tham khảo nhận định rủi ro cơ bản nội bộ trong thẻ Đánh Giá Rủi Ro bên dưới.")
                .setPositiveButton("Đã hiểu", null)
                .show();
    }

    private String buildAIPrompt(List<VitalSign> vitalSigns, List<Reminder> reminders) {
        StringBuilder prompt = new StringBuilder();

        // ── Hướng dẫn an toàn y tế ───────────────────────────────────────────
        prompt.append("Bạn là trợ lý hỗ trợ nhận xét xu hướng sức khỏe.\n");
        prompt.append("QUAN TRỌNG — Bạn KHÔNG ĐƯỢC:\n");
        prompt.append("- Chẩn đoán bệnh\n");
        prompt.append("- Kê đơn thuốc\n");
        prompt.append("- Khuyên tự ý thay đổi liều thuốc\n");
        prompt.append("- Đưa ra kết luận y tế tuyệt đối\n");
        prompt.append("Chỉ nhận xét xu hướng từ dữ liệu và gợi ý người dùng hỏi bác sĩ nếu có bất thường.\n\n");

        // ── Dữ liệu chỉ số sức khỏe ──────────────────────────────────────────
        prompt.append("CHỈ SỐ SỨC KHỎE (7 ngày gần nhất):\n");
        if (vitalSigns.isEmpty()) {
            prompt.append("- Chưa có dữ liệu\n");
        } else {
            for (VitalSign vs : vitalSigns) {
                prompt.append(String.format("- %s: %s (%s)\n",
                        formatTime(vs.timestamp), vs.getDisplayName(), vs.getDisplayValue()));
            }
        }

        // ── Dữ liệu tuân thủ thuốc ───────────────────────────────────────────
        prompt.append("\nTUÂN THỦ UỐNG THUỐC:\n");
        int takenCount = 0, totalCount = 0;
        for (Reminder reminder : reminders) {
            totalCount++;
            if (reminder.isTaken)
                takenCount++;
        }
        if (totalCount > 0) {
            prompt.append(String.format(Locale.getDefault(),
                    "Tỷ lệ tuân thủ: %d/%d (%.1f%%)\n",
                    takenCount, totalCount, (takenCount * 100.0 / totalCount)));
        } else {
            prompt.append("Chưa có dữ liệu nhắc nhở.\n");
        }

        // ── Yêu cầu phân tích ────────────────────────────────────────────────
        prompt.append("\nHÃY NHẬN XÉT (không chẩn đoán, không kê đơn):\n");
        prompt.append("1. Xu hướng chỉ số sức khỏe có ổn định không?\n");
        prompt.append("2. Có điểm bất thường nào đáng lưu ý không?\n");
        prompt.append("3. Nếu có bất thường, gợi ý người dùng trao đổi với bác sĩ.\n\n");
        prompt.append("Trả lời ngắn gọn, dễ hiểu, bằng tiếng Việt (tối đa 200 từ). "
                + "Kết thúc bằng nhắc nhở hỏi bác sĩ nếu có bất kỳ lo ngại nào.");

        return prompt.toString();
    }

    private void showAnalysisDialog(String analysis, boolean hasLowData) {
        // Xây dựng nội dung dialog
        StringBuilder content = new StringBuilder();

        // Cảnh báo dữ liệu ít
        if (hasLowData) {
            content.append(getString(vn.medisense.app.R.string.ai_low_data_warning))
                    .append("\n\n");
        }

        content.append(analysis);

        // Disclaimer y tế luôn ở cuối
        content.append("\n\n─────────────────────\n")
                .append(getString(vn.medisense.app.R.string.ai_medical_disclaimer));

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(vn.medisense.app.R.string.health_ai_title))
                .setMessage(content.toString())
                .setPositiveButton("Đóng", null)
                .setNeutralButton(getString(vn.medisense.app.R.string.health_ai_share),
                        (dialog, which) -> shareAnalysis(analysis))
                .show();
    }

    private void shareAnalysis(String analysis) {
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_SUBJECT, "Phân tích sức khỏe từ MediSense");
        shareIntent.putExtra(Intent.EXTRA_TEXT, analysis);
        startActivity(Intent.createChooser(shareIntent, "Chia sẻ qua"));
    }

    private void updateHealthConnectUI() {
        android.content.SharedPreferences prefs = requireContext().getSharedPreferences("MediSensePrefs",
                Context.MODE_PRIVATE);
        boolean isConnected = prefs.getBoolean("health_connect_connected", false);
        if (isConnected) {
            binding.buttonHealthConnect.setText("✓ Đã kết nối Health Connect");
            binding.buttonHealthConnect
                    .setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#2E7D32")));
        } else {
            binding.buttonHealthConnect.setText("Kết nối Health Connect");
            binding.buttonHealthConnect
                    .setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#4E2A1F")));
        }
    }

    private void showTravelPlanInputDialog() {
        android.view.View dialogView = getLayoutInflater().inflate(R.layout.dialog_travel_plan_input, null);
        com.google.android.material.textfield.TextInputEditText edtDays = dialogView.findViewById(R.id.edtDays);
        com.google.android.material.textfield.TextInputEditText edtNotes = dialogView.findViewById(R.id.edtNotes);

        new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle("Kế hoạch mang thuốc du lịch")
                .setView(dialogView)
                .setPositiveButton("Tạo kế hoạch", (dialog, which) -> {
                    String daysStr = edtDays.getText().toString().trim();
                    String notes = edtNotes.getText().toString().trim();
                    if (daysStr.isEmpty()) {
                        android.widget.Toast
                                .makeText(requireContext(), "Vui lòng nhập số ngày", android.widget.Toast.LENGTH_SHORT)
                                .show();
                        return;
                    }
                    try {
                        int days = Integer.parseInt(daysStr);
                        if (days <= 0) {
                            android.widget.Toast.makeText(requireContext(), "Số ngày phải lớn hơn 0",
                                    android.widget.Toast.LENGTH_SHORT).show();
                            return;
                        }
                        generateTravelPlan(days, notes);
                    } catch (NumberFormatException e) {
                        android.widget.Toast
                                .makeText(requireContext(), "Số ngày không hợp lệ", android.widget.Toast.LENGTH_SHORT)
                                .show();
                    }
                })
                .setNegativeButton("Hủy", null)
                .show();
    }

    private void generateTravelPlan(int days, String notes) {
        if (!NetworkUtils.isNetworkAvailable(requireContext())) {
            showOfflineAnalysisFallback();
            return;
        }

        binding.progressBar.setVisibility(View.VISIBLE);

        viewModel.getAllMedications(medications -> {
            if (!isAdded())
                return;
            requireActivity().runOnUiThread(() -> {
                if (medications == null || medications.isEmpty()) {
                    binding.progressBar.setVisibility(View.GONE);
                    new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                            .setTitle("Kế hoạch mang thuốc du lịch")
                            .setMessage(
                                    "Danh sách tủ thuốc của bạn đang trống. Vui lòng thêm thuốc ở màn hình chính để chúng tôi có thể lập kế hoạch chuẩn bị hành lý cho bạn!")
                            .setPositiveButton("Đóng", null)
                            .show();
                    return;
                }

                geminiRepository.generateTravelPlan(days, notes, medications, new vn.medisense.app.repository.GeminiRepository.GeminiCallback() {
                    @Override
                    public void onSuccess(@NonNull String result) {
                        if (!isAdded()) return;
                        requireActivity().runOnUiThread(() -> {
                            binding.progressBar.setVisibility(View.GONE);
                            showTravelPlanDialog(result);
                        });
                    }

                    @Override
                    public void onError(@NonNull String errorMessage) {
                        if (!isAdded()) return;
                        requireActivity().runOnUiThread(() -> {
                            binding.progressBar.setVisibility(View.GONE);
                            android.widget.Toast.makeText(requireContext(), errorMessage, android.widget.Toast.LENGTH_SHORT).show();
                        });
                    }
                });
            });
        });
    }

    private void showTravelPlanDialog(String plan) {
        StringBuilder content = new StringBuilder();
        content.append(plan);
        content.append("\n\n─────────────────────\n")
                .append(getString(vn.medisense.app.R.string.ai_medical_disclaimer));

        new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle("Kế hoạch chuẩn bị thuốc du lịch")
                .setMessage(content.toString())
                .setPositiveButton("Đóng", null)
                .setNeutralButton("Chia sẻ", (dialog, which) -> shareAnalysis(plan))
                .show();
    }

    private void toggleCollapsible(android.view.View layout, android.widget.ImageView chevron) {
        if (layout.getVisibility() == android.view.View.VISIBLE) {
            layout.setVisibility(android.view.View.GONE);
            chevron.animate().rotation(-90).setDuration(200).start();
        } else {
            layout.setVisibility(android.view.View.VISIBLE);
            chevron.animate().rotation(90).setDuration(200).start();
        }
    }

    private String formatTime(long timestamp) {
        return new SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(new Date(timestamp));
    }
}
