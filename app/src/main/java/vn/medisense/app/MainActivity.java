package vn.medisense.app;

import android.content.Intent;
import android.net.Uri;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import vn.medisense.app.database.ReminderWithMedication;
import vn.medisense.app.databinding.ActivityMainBinding;
import vn.medisense.app.ui.AddMedicationActivity;
import vn.medisense.app.ui.MainViewModel;
import vn.medisense.app.ui.ReminderAdapter;
import vn.medisense.app.ui.VoiceAssistantDialog;
import vn.medisense.app.ui.SideEffectBottomSheetFragment;
import vn.medisense.app.utils.AnimationUtils;
import vn.medisense.app.utils.ContextualSuggestionManager;
import vn.medisense.app.utils.DepthShadowHelper;
import vn.medisense.app.utils.FamilyInteractionHelper;
import vn.medisense.app.utils.Medication3DIcon;
import vn.medisense.app.utils.ParallaxScrollListener;
import vn.medisense.app.utils.ProgressiveLoader;
import vn.medisense.app.utils.ShimmerFrameLayout;
import vn.medisense.app.utils.StaggeredItemAnimator;
import vn.medisense.app.utils.SwipeActionHelper;
import vn.medisense.app.utils.VoiceAssistantManager;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.Priority;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends BaseActivity {

    private ActivityMainBinding binding;
    private ReminderAdapter adapter;
    private MainViewModel viewModel;
    
    private VoiceAssistantManager voiceAssistant;
    private VoiceAssistantDialog voiceDialog;
    private static final int RECORD_AUDIO_PERMISSION_CODE = 102;
    private static final int POST_NOTIFICATIONS_CODE = 101;
    
    // Smart UI Components
    private ContextualSuggestionManager suggestionManager;
    private vn.medisense.app.utils.PatientLocationManager locationManager;
    private vn.medisense.app.ui.helper.CalendarStripManager calendarStripManager;
    
    // 3D & Depth Components
    private ParallaxScrollListener parallaxScrollListener;
    private DepthShadowHelper depthShadowHelper;
    
    // Skeleton & Loading Components
    private vn.medisense.app.ui.helper.SkeletonLoadingHelper skeletonLoadingHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());



        ViewCompat.setOnApplyWindowInsetsListener(binding.main, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        viewModel = new ViewModelProvider(this).get(MainViewModel.class);
        voiceAssistant = new VoiceAssistantManager(this);

        setupRecyclerView();
        setupFloatingActionButtons();
        setupClickListeners();
        checkPermissionsAndOptimizations();
        startBackgroundServices();
        
        // Gọi hàm thiết lập chế độ mở rộng
        setupExtendedModes();
        
        // Lắng nghe tín hiệu động viên từ gia đình
        ImageView imgFamilyCheer = binding.getRoot().findViewById(R.id.imgFamilyCheer);
        FamilyInteractionHelper.listenForFamilyCheers(this, imgFamilyCheer);

        observeViewModel();
        
        // Kích hoạt luồng theo dõi DB
        viewModel.getTrigger().observe(this, unused -> {});
        
        // Quét và chuyển reminder quá hạn sang MISSED khi mở app
        viewModel.markOverdueAsMissed();
        
        // Khởi tạo các thành phần UI thông minh
        setupContextualSuggestions();
        locationManager = new vn.medisense.app.utils.PatientLocationManager(this);
        // Thêm click listener cho voice assistant FAB
        binding.fabVoiceAssistant.setOnClickListener(v -> startVoiceAssistant());
        
        // Khởi tạo 3D & Độ sâu
        setupParallaxScroll();
        setup3DDepth();
        
        // Khởi tạo Tải Skeleton
        skeletonLoadingHelper = new vn.medisense.app.ui.helper.SkeletonLoadingHelper(this, binding);
        skeletonLoadingHelper.setupSkeletonLoading();
        
        // Thiết lập ngày giờ lời chào động
        setupGreetingAndDate();

        // Khởi tạo thanh lịch tuần
        calendarStripManager = new vn.medisense.app.ui.helper.CalendarStripManager(binding, this, dateMillis -> {
            viewModel.setSelectedDate(dateMillis);
        });
        calendarStripManager.setupCalendarStrip();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (adapter != null && adapter.getItemCount() > 0 && skeletonLoadingHelper != null) {
            skeletonLoadingHelper.showSkeletonLoading(); 
        }
        // Kiểm tra lại các chế độ nâng cao mỗi khi quay lại
        setupExtendedModes();
        setupGreetingAndDate();
        if (calendarStripManager != null) {
            calendarStripManager.setupCalendarStrip();
        }
        if (locationManager != null) {
            locationManager.startLocationUpdates();
        }
    }



    private int getThemeColor(int attrId) {
        android.util.TypedValue typedValue = new android.util.TypedValue();
        getTheme().resolveAttribute(attrId, typedValue, true);
        return typedValue.data;
    }

    private void setupGreetingAndDate() {
        java.util.Calendar calendar = java.util.Calendar.getInstance();
        int hour = calendar.get(java.util.Calendar.HOUR_OF_DAY);
        String greeting;
        if (hour >= 5 && hour < 12) {
            greeting = "Chào buổi sáng ☀️";
        } else if (hour >= 12 && hour < 18) {
            greeting = "Chào buổi chiều 🌤️";
        } else {
            greeting = "Chào buổi tối 🌙";
        }
        binding.tvGreeting.setText(greeting);

        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("EEEE, 'ngày' d 'tháng' M", new java.util.Locale("vi", "VN"));
        String dateStr = sdf.format(new java.util.Date());
        if (dateStr != null && !dateStr.isEmpty()) {
            dateStr = dateStr.substring(0, 1).toUpperCase() + dateStr.substring(1);
        }
        binding.tvDate.setText(dateStr);
    }

    private void setupExtendedModes() {
        android.content.SharedPreferences prefs = getSharedPreferences("MediSensePrefs", android.content.Context.MODE_PRIVATE);
        String monitoringCode = prefs.getString("monitoringCode", null);

        View layoutExtendedModes = findViewById(R.id.layoutExtendedModes);
        View btnCaregiverMode = findViewById(R.id.btnCaregiverMode);

        if (layoutExtendedModes == null) return;

        boolean showLayout = false;

        if (monitoringCode != null && !monitoringCode.isEmpty()) {
            if (btnCaregiverMode != null) {
                btnCaregiverMode.setVisibility(View.VISIBLE);
                btnCaregiverMode.setOnClickListener(v -> startActivity(new android.content.Intent(this, vn.medisense.app.ui.CaregiverDashboardActivity.class)));
            }
            showLayout = true;
        } else {
            if (btnCaregiverMode != null) {
                btnCaregiverMode.setVisibility(View.GONE);
            }
        }

        layoutExtendedModes.setVisibility(showLayout ? View.VISIBLE : View.GONE);
    }

    private void setupRecyclerView() {
        adapter = new ReminderAdapter(this::onReminderChecked, this::onItemClick, this::onEditGroup,
                new ReminderAdapter.OnPrescriptionActionListener() {
                    @Override
                    public void onArchive(int prescriptionId) {
                        onArchiveGroup(prescriptionId);
                    }
                    @Override
                    public void onClone(int prescriptionId) {
                        onCloneGroup(prescriptionId);
                    }
                });
        binding.recyclerReminders.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerReminders.setAdapter(adapter);
        
        // Áp dụng staggered item animator cho hoạt ảnh vào mượt mà
        binding.recyclerReminders.setItemAnimator(new StaggeredItemAnimator());
        
        // Áp dụng parallax scroll listener cho hiệu ứng độ sâu
        View headerView = findViewById(R.id.tvGreeting);
        if (headerView != null && headerView.getParent() != null) {
            View headerContainer = (View) headerView.getParent();
            parallaxScrollListener = new ParallaxScrollListener(headerContainer, 200);
            binding.recyclerReminders.addOnScrollListener(parallaxScrollListener);
        }
        
        // Áp dụng bóng động cho các mục RecyclerView
        depthShadowHelper = new DepthShadowHelper(this);
        depthShadowHelper.attachToRecyclerView(binding.recyclerReminders, new DepthShadowHelper.ShadowProvider() {
            @Override
            public void onShadowApplied(View view, float velocity, float direction, int scrollDelta) {
                // Áp dụng nghiêng cho các biểu đồ thuốc 3D khi cuộn
                // Áp dụng nghiêng cho thẻ khi cuộn (hiệu ứng 3D)
                if (view instanceof MaterialCardView) {
                    ((MaterialCardView) view).setRotationX(scrollDelta * 0.1f);
                }
            }
        });

        SwipeActionHelper.attach(this, binding.recyclerReminders, adapter, new SwipeActionHelper.SwipeActionListener() {
            @Override
            public void onSwipeRight(ReminderWithMedication item, int position) {
                onReminderChecked(item, true);
                Toast.makeText(MainActivity.this, "Đã uống thuốc", Toast.LENGTH_SHORT).show();
                // Thu hồi thẻ về vị trí cũ ngay lập tức
                adapter.notifyItemChanged(position);
            }

            @Override
            public void onSwipeLeft(ReminderWithMedication item, int position) {
                // Thu hồi thẻ về vị trí cũ ngay lập tức
                adapter.notifyItemChanged(position);
                if (item.medication != null) {
                    onEditGroup(item.medication.prescriptionId);
                }
            }
        });
    }

    private void setupFloatingActionButtons() {
        binding.fabAddMedication.setOnClickListener(v -> {
            // Hoạt ảnh xoay FAB
            AnimationUtils.fabRotationAnimation(binding.fabAddMedication, true);
            
            // Bắt đầu AddMedicationActivity với chuyển đổi phần tử chia sẻ
            Intent intent = new Intent(this, AddMedicationActivity.class);
            AnimationUtils.startWithSharedElement(this, intent, binding.fabAddMedication, "fab_add");
            
            // Reset xoay FAB sau độ trễ
            binding.fabAddMedication.postDelayed(() -> {
                AnimationUtils.fabRotationAnimation(binding.fabAddMedication, false);
            }, 500);
        });

        // Xử lý nút Thêm thuốc ở màn hình trống
        if (binding.btnAddFirstMed != null) {
            binding.btnAddFirstMed.setOnClickListener(v -> {
                Intent intent = new Intent(this, AddMedicationActivity.class);
                startActivity(intent);
            });
        }
    }
    

    
    /**
     * Setup Contextual Suggestion Manager - Smart contextual banners
     */
    private void setupContextualSuggestions() {
        suggestionManager = new ContextualSuggestionManager(this, binding.layoutSuggestionContainer);
        suggestionManager.setOnSuggestionActionListener(new ContextualSuggestionManager.OnSuggestionActionListener() {
            @Override
            public void onSuggestionAction(ContextualSuggestionManager.SuggestionType type, String action) {
                switch (type) {
                    case MISSED_MEDICATION:
                        if (action.equals("Bổ sung")) {
                            Toast.makeText(MainActivity.this, "Mở màn hình bổ sung thuốc", Toast.LENGTH_SHORT).show();
                        }
                        break;
                    case HIGH_BLOOD_PRESSURE:
                        if (action.equals("Đo lại")) {
                            Intent intent = new Intent(MainActivity.this, vn.medisense.app.ui.HealthTrackerActivity.class);
                            startActivity(intent);
                        }
                        break;
                    case WEEKEND_SUMMARY:
                        Intent reportIntent = new Intent(MainActivity.this, vn.medisense.app.ui.HealthTrackerActivity.class);
                        startActivity(reportIntent);
                        break;
                    case MEDICATION_DUE:
                        // Mark as taken
                        Toast.makeText(MainActivity.this, "Đã đánh dấu đã uống", Toast.LENGTH_SHORT).show();
                        break;
                    case LOW_STOCK:
                        Toast.makeText(MainActivity.this, "Mở màn hình mua thuốc", Toast.LENGTH_SHORT).show();
                        break;
                }
            }
            
            @Override
            public void onSuggestionDismiss(ContextualSuggestionManager.SuggestionType type) {
                // Handle dismiss
            }
        });
        
        // Kiểm tra tóm tắt cuối tuần vào cuối tuần
        if (suggestionManager.shouldShowWeekendSummary()) {
            new Handler().postDelayed(() -> {
                suggestionManager.showWeekendSummaryTooltip();
            }, 2000);
        }
    }
    
    /**
     * Setup Floating Chat Bubble - Draggable AI assistant
     */

    
    /**
     * Card click animation - apply spring effect
     */
    private void animateCardClick(View card) {
        AnimationUtils.cardSpringAnimation(card);
    }
    
    /**
     * Show missed medication suggestion (example usage)
     */
    private void showMissedMedicationSuggestion() {
        if (suggestionManager != null) {
            suggestionManager.showMissedMedicationSuggestion("Paracetamol", 2);
        }
    }

    private void setupClickListeners() {
        // Prevent double padding from window insets since parent CoordinatorLayout already handles it
        binding.bottomNavigation.setOnApplyWindowInsetsListener(null);
        binding.bottomNavigation.setPadding(0, 0, 0, 0);

        // Bottom navigation
        binding.bottomNavigation.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_home) {
                // Already on home
                return true;
            } else if (id == R.id.nav_health) {
                startActivity(new Intent(this, vn.medisense.app.ui.HealthTrackerActivity.class));
                return false; // Trả về false để tránh lỗi kẹt highlight khi người dùng quay về từ màn hình sức khỏe
            } else if (id == R.id.nav_treatment) {
                startActivity(new Intent(this, vn.medisense.app.ui.TreatmentActivity.class));
                return false;
            } else if (id == R.id.nav_settings) {
                startActivity(new Intent(this, vn.medisense.app.ui.SettingsActivity.class));
                return false;
            }
            return false;
        });
    }

    private void showMapSearchDialog() {
        String[] options = { "Tìm hiệu thuốc gần đây", "Tìm bệnh viện gần đây" };
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle("Tìm kiếm cơ sở y tế")
                .setItems(options, (dialog, which) -> {
                    String query = (which == 0) ? "Hiệu thuốc gần đây" : "Bệnh viện gần đây";
                    Uri gmmIntentUri = Uri.parse("geo:0,0?q=" + Uri.encode(query));
                    Intent mapIntent = new Intent(Intent.ACTION_VIEW, gmmIntentUri);
                    mapIntent.setPackage("com.google.android.apps.maps");
                    if (mapIntent.resolveActivity(getPackageManager()) != null) {
                        startActivity(mapIntent);
                    } else {
                        // Fallback mở trình duyệt hoặc bản đồ bất kỳ
                        Intent webMapIntent = new Intent(Intent.ACTION_VIEW, 
                                Uri.parse("https://www.google.com/maps/search/?api=1&query=" + Uri.encode(query)));
                        startActivity(webMapIntent);
                    }
                })
                .setNegativeButton("Hủy", null)
                .show();
    }

    private void observeViewModel() {
        viewModel.getTodayReminders().observe(this, reminders -> {
            adapter.setItems(reminders);
            
            // Ẩn skeleton khi dữ liệu được tải
            if (skeletonLoadingHelper != null) {
                skeletonLoadingHelper.finishLoading();
            }
            
            if (reminders != null && !reminders.isEmpty()) {
                binding.recyclerReminders.setVisibility(View.VISIBLE);
                binding.layoutEmptyState.setVisibility(View.GONE);

                boolean allTaken = true;
                for (vn.medisense.app.database.ReminderWithMedication r : reminders) {
                    if (r.reminder != null && !r.reminder.isTaken) {
                        allTaken = false;
                        break;
                    }
                }

                binding.layoutAllDone.setVisibility(allTaken ? View.VISIBLE : View.GONE);
            } else {
                binding.recyclerReminders.setVisibility(View.GONE);
                binding.layoutAllDone.setVisibility(View.GONE);
                binding.layoutEmptyState.setVisibility(View.VISIBLE);
            }
        });

        viewModel.getNextDose().observe(this, nextDose -> {
            if (nextDose != null) {
                binding.cardNextDose.setVisibility(View.VISIBLE);
                SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
                binding.tvNextDoseTime.setText(sdf.format(new Date(nextDose.reminder.reminderTime)));
                String meds = nextDose.medication != null ? nextDose.medication.name + " (" + nextDose.medication.dosage + ")" : "Thuốc";
                binding.tvNextDoseMeds.setText(meds);
                binding.btnTakeNextDose.setOnClickListener(v -> onReminderChecked(nextDose, true));
            } else {
                binding.cardNextDose.setVisibility(View.GONE);
            }
        });

        viewModel.getComplianceStats().observe(this, stats -> {
            if (stats == null) return;
            binding.tvTodayPercentage.setText(stats.todayPercentage + "%");
            // Hiển thị số liều còn lại (PENDING)
            binding.tvRemainingDoses.setText(String.valueOf(stats.remainingDosesToday));
            binding.tvStreak.setText(String.valueOf(stats.currentStreak));
            binding.progressCircular.setProgress(stats.todayPercentage);
            
            TextView tvJourneyProgressText = findViewById(R.id.tvJourneyProgressText);
            android.widget.ProgressBar progressJourney = findViewById(R.id.progressJourney);
            TextView badge7  = findViewById(R.id.badge7);
            TextView badge30 = findViewById(R.id.badge30);
            TextView badge90 = findViewById(R.id.badge90);
            
            if (tvJourneyProgressText != null && progressJourney != null) {
                // Hiển thị chi tiết: Đã uống / Bỏ lỡ / Bỏ qua
                String detail = "Hôm nay: " + stats.takenToday + " đã uống";
                if (stats.missedToday > 0)  detail += " · " + stats.missedToday + " bỏ lỡ";
                if (stats.skippedToday > 0) detail += " · " + stats.skippedToday + " bỏ qua";
                tvJourneyProgressText.setText(detail + "\nChuỗi " + stats.currentStreak
                        + " ngày → mốc " + stats.nextMilestone + " ngày");
                progressJourney.setMax(stats.nextMilestone);
                progressJourney.setProgress(stats.progressToNextMilestone);
                
                if (badge7  != null) badge7.setAlpha(stats.earnedBadges.contains(7)  ? 1.0f : 0.3f);
                if (badge30 != null) badge30.setAlpha(stats.earnedBadges.contains(30) ? 1.0f : 0.3f);
                if (badge90 != null) badge90.setAlpha(stats.earnedBadges.contains(90) ? 1.0f : 0.3f);
            }
        });

        viewModel.getActionMessage().observe(this, message -> {
            if (message != null) {
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void onReminderChecked(ReminderWithMedication item, boolean isChecked) {
        if (isChecked) {
            // Đánh dấu TAKEN trực tiếp — ReminderStatusHelper xử lý giảm tồn kho, ghi log và thiết lập kiểm tra tác dụng phụ
            viewModel.markReminderTaken(item, true);
        } else {
            // Hỏi người dùng muốn đặt lại trạng thái chưa uống (bấm nhầm) hay thực sự bỏ qua liều thuốc
            String[] options = {
                "Tôi bấm nhầm (Đặt lại trạng thái chưa uống)",
                "Bỏ qua liều thuốc này"
            };
            new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                    .setTitle("Hủy xác nhận đã uống")
                    .setItems(options, (dialog, which) -> {
                        if (which == 0) {
                            // Đặt lại trạng thái PENDING hoặc MISSED (hoàn lại tồn kho thuốc)
                            viewModel.markReminderPending(item);
                        } else {
                            promptSkipReason(item);
                        }
                    })
                    .setOnCancelListener(dialog -> {
                        // Nếu hủy dialog, khôi phục lại trạng thái tích chọn trên giao diện
                        adapter.notifyDataSetChanged();
                    })
                    .show();
        }
    }

    private void promptSkipReason(ReminderWithMedication item) {
        // Danh sách lý do gợi ý
        final String[] reasons = {
            "Quên uống",
            "Hết thuốc",
            "Bác sĩ dặn ngưng",
            "Có tác dụng phụ",
            "Không muốn uống lúc này",
            "Lý do khác"
        };

        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle("Lý do bỏ qua liều thuốc")
                .setItems(reasons, (dialog, which) -> {
                    String selectedReason = reasons[which];

                    // Đánh dấu SKIPPED — không trừ stock
                    viewModel.markReminderSkipped(item, selectedReason);

                    // Nếu lý do là tác dụng phụ → gợi ý mở SideEffectBottomSheet
                    if ("Có tác dụng phụ".equals(selectedReason) && item.medication != null) {
                        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                                .setTitle("Báo cáo tác dụng phụ")
                                .setMessage("Bạn có muốn ghi lại tác dụng phụ ngay bây giờ không?")
                                .setPositiveButton("Ghi lại", (d, w) -> {
                                    SideEffectBottomSheetFragment sheet =
                                            SideEffectBottomSheetFragment.newInstance(
                                                    item.medication.id, item.medication.name);
                                    sheet.show(getSupportFragmentManager(), "side_effect");
                                })
                                .setNegativeButton("Bỏ qua", null)
                                .show();
                    }
                })
                .setNegativeButton("Hủy", (dialog, which) -> {
                    // Người dùng hủy → không thay đổi trạng thái, refresh UI
                    if (adapter != null) adapter.notifyDataSetChanged();
                })
                .show();
    }

    private void onItemClick(ReminderWithMedication item) {
        if (item.medication != null) {
            Intent intent = new Intent(this, vn.medisense.app.ui.MedicationDetailActivity.class);
            intent.putExtra(vn.medisense.app.ui.MedicationDetailActivity.EXTRA_MEDICATION_ID, item.medication.id);
            startActivity(intent);
        }
    }

    private void onCloneGroup(int prescriptionId) {
        if (prescriptionId <= 0) return;
        new AlertDialog.Builder(this)
                .setTitle("Mua lại đơn thuốc")
                .setMessage("Bạn có chắc muốn lặp lại toàn bộ lộ trình của đơn thuốc này bắt đầu từ hôm nay không?")
                .setPositiveButton("Đồng ý", (dialog, which) -> viewModel.cloneGroup(prescriptionId))
                .setNegativeButton("Hủy", null)
                .show();
    }

    private void onDeleteGroup(int prescriptionId) {
        if (prescriptionId <= 0) return;
        new AlertDialog.Builder(this)
                .setTitle("Xóa đơn thuốc")
                .setMessage("Bạn có chắc chắn muốn xóa đơn thuốc này và tất cả các thuốc liên quan không?")
                .setPositiveButton("Xóa", (dialog, which) -> viewModel.deleteGroup(prescriptionId))
                .setNegativeButton("Hủy", null)
                .show();
    }

    private void onArchiveGroup(int prescriptionId) {
        if (prescriptionId <= 0) return;
        new AlertDialog.Builder(this)
                .setTitle("Lưu trữ đơn thuốc")
                .setMessage("Bạn có chắc chắn muốn lưu trữ đơn thuốc này vào lịch sử và hủy các thông báo nhắc thuốc không?")
                .setPositiveButton("Lưu trữ", (dialog, which) -> viewModel.archiveGroup(prescriptionId))
                .setNegativeButton("Hủy", null)
                .show();
    }

    private void onEditGroup(int prescriptionId) {
        if (prescriptionId <= 0) return;
        Intent intent = new Intent(this, vn.medisense.app.ui.EditPrescriptionActivity.class);
        intent.putExtra("prescription_id", prescriptionId);
        startActivity(intent);
    }

    private void checkPermissionsAndOptimizations() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, POST_NOTIFICATIONS_CODE);
            }
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            android.os.PowerManager pm = (android.os.PowerManager) getSystemService(POWER_SERVICE);
            if (pm != null && !pm.isIgnoringBatteryOptimizations(getPackageName())) {
                new AlertDialog.Builder(this)
                        .setTitle("Tối ưu hóa pin")
                        .setMessage("Ứng dụng cần được loại trừ khỏi tính năng tối ưu hóa pin để đảm bảo báo thức uống thuốc luôn hoạt động chính xác. Bạn có muốn chuyển đến cài đặt để tắt tối ưu hóa cho ứng dụng này không?")
                        .setPositiveButton("Đồng ý", (dialog, which) -> {
                            Intent intent = new Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                            intent.setData(android.net.Uri.parse("package:" + getPackageName()));
                            startActivity(intent);
                        })
                        .setNegativeButton("Hủy", null)
                        .show();
            }
        }
    }

    private void openEmergencyDialer() {
        String phone = getString(R.string.emergency_phone_number).trim();
        if (phone.isEmpty()) {
            Toast.makeText(this, R.string.emergency_phone_missing, Toast.LENGTH_SHORT).show();
            return;
        }
        Intent intent = new Intent(Intent.ACTION_DIAL);
        intent.setData(Uri.parse("tel:" + phone));
        startActivity(intent);
    }

    private void startBackgroundServices() {
        SharedPreferences prefs = getSharedPreferences("MediSensePrefs", MODE_PRIVATE);
        if ("caregiver".equals(prefs.getString("userRole", null))) {
            Intent serviceIntent = new Intent(this, vn.medisense.app.services.CaregiverSyncService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent);
            } else {
                startService(serviceIntent);
            }
        }
    }

    /**
     * Bắt đầu trợ lý giọng nói
     */
    private void startVoiceAssistant() {
        boolean hasMicPermission = ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED;

        voiceDialog = new VoiceAssistantDialog(this, new VoiceAssistantDialog.VoiceDialogCallback() {
            @Override
            public void onCancel() {
                voiceAssistant.stopListening();
                voiceAssistant.stopSpeaking();
            }

            @Override
            public void onTextSubmit(String text) {
                voiceAssistant.stopListening();
                voiceAssistant.processTextQuery(text, new VoiceAssistantCallbackImpl());
            }

            @Override
            public void onMicClick() {
                voiceAssistant.stopSpeaking();
                boolean hasPermission = ContextCompat.checkSelfPermission(MainActivity.this, android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED;
                if (hasPermission) {
                    voiceDialog.showListening();
                    voiceAssistant.startListening(new VoiceAssistantCallbackImpl());
                } else {
                    ActivityCompat.requestPermissions(MainActivity.this, new String[]{android.Manifest.permission.RECORD_AUDIO}, RECORD_AUDIO_PERMISSION_CODE);
                }
            }
        });
        voiceDialog.setOnDismissListener(dialog -> {
            voiceAssistant.stopListening();
            voiceAssistant.stopSpeaking();
        });
        voiceDialog.show();

        if (hasMicPermission) {
            voiceDialog.showListening();
            voiceAssistant.startListening(new VoiceAssistantCallbackImpl());
        } else {
            voiceDialog.showError("Bạn có thể nhập tin nhắn văn bản dưới đây. Hãy cấp quyền micro nếu muốn sử dụng giọng nói.");
            ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.RECORD_AUDIO}, RECORD_AUDIO_PERMISSION_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == RECORD_AUDIO_PERMISSION_CODE && grantResults.length > 0 && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            if (voiceDialog != null && voiceDialog.isShowing()) {
                voiceDialog.showListening();
                voiceAssistant.startListening(new VoiceAssistantCallbackImpl());
            }
        } else if (requestCode == vn.medisense.app.utils.PatientLocationManager.LOCATION_PERMISSION_REQUEST_CODE && grantResults.length > 0 && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            if (locationManager != null) {
                locationManager.startLocationUpdates();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (voiceAssistant != null) {
            voiceAssistant.destroy();
        }
        
        // Cleanup Smart UI components
        if (suggestionManager != null) {
            suggestionManager.stop();
        }
        
        if (locationManager != null) {
            locationManager.stopLocationUpdates();
        }
        
        // Cleanup parallax scroll
        if (parallaxScrollListener != null && binding.recyclerReminders != null) {
            binding.recyclerReminders.removeOnScrollListener(parallaxScrollListener);
        }
        
        // Cleanup skeleton loading
        if (skeletonLoadingHelper != null) {
            skeletonLoadingHelper.stopShimmer();
        }
    }
    
    /**
     * Setup parallax scrolling effect for header
     */
    private void setupParallaxScroll() {
        // Thiết lập parallax bổ sung nếu cần
        // Listener đã được đính kèm trong setupRecyclerView()
    }
    
    /**
     * Setup 3D depth effects for medication icons
     */
    private void setup3DDepth() {
        // Độ sâu 3D được áp dụng động khi các mục được gắn
        // Xem adapter để biết cách sử dụng Medication3DIcon
    }
    
    /**
     * Apply 3D tilt effect to a view based on scroll
     */
    private void applyTiltEffect(View view, float scrollX, float scrollY) {
        if (view instanceof Medication3DIcon) {
            ((Medication3DIcon) view).applyTilt(scrollX, scrollY);
        }
    }
    


    /**
     * Triển khai VoiceAssistantCallback
     */
    private class VoiceAssistantCallbackImpl implements VoiceAssistantManager.VoiceAssistantCallback {
        @Override
        public void onListeningStarted() {
            runOnUiThread(() -> voiceDialog.showListening());
        }

        @Override
        public void onVolumeChanged(float volume) {
            runOnUiThread(() -> voiceDialog.updateVolume(volume));
        }

        @Override
        public void onPartialResult(String partialText) {
            runOnUiThread(() -> voiceDialog.showPartialResult(partialText));
        }

        @Override
        public void onTextRecognized(String text) {
            runOnUiThread(() -> voiceDialog.showRecognizedText(text));
        }

        @Override
        public void onProcessing() {
            runOnUiThread(() -> voiceDialog.showProcessing());
        }

        @Override
        public void getTodayMedications(VoiceAssistantManager.MedicationListCallback callback) {
            if (viewModel.getTodayReminders().getValue() != null) {
                callback.onMedicationsLoaded(viewModel.getTodayReminders().getValue());
            } else {
                callback.onMedicationsLoaded(new java.util.ArrayList<>());
            }
        }

        @Override
        public void onQueryResponse(String answer) {
            Log.d("MainActivity_Voice", "onQueryResponse called with answer: " + answer);
            runOnUiThread(() -> {
                if (voiceDialog != null) {
                    voiceDialog.showResponse(answer);
                }
            });
        }

        @Override
        public void onUpdateRequest(String medicationName, String answer) {
            Log.d("MainActivity_Voice", "onUpdateRequest called with medName: " + medicationName + ", answer: " + answer);
            runOnUiThread(() -> {
                if (voiceDialog != null) {
                    voiceDialog.showResponse(answer);
                    // Cập nhật trạng thái uống thuốc qua ViewModel từ danh sách tất cả các liều thuốc hôm nay
                    java.util.List<ReminderWithMedication> currentReminders = viewModel.getTodayReminders().getValue();
                    if (currentReminders != null) {
                        for (ReminderWithMedication r : currentReminders) {
                            if (r.medication != null && r.medication.name.equalsIgnoreCase(medicationName) && !r.reminder.isTaken) {
                                viewModel.markReminderTaken(r, true);
                                break;
                            }
                        }
                    }
                    new Handler().postDelayed(() -> {
                        Log.d("MainActivity_Voice", "Trying to dismiss dialog in onUpdateRequest");
                        if (voiceDialog != null && voiceDialog.isShowing()) {
                            voiceDialog.dismiss();
                            Log.d("MainActivity_Voice", "Dialog dismissed successfully");
                        }
                    }, 6000); // Tăng thời gian đóng lên 6 giây để kịp nghe hết TTS thông báo
                }
            });
        }

        @Override
        public void onOpenScreen(String screen, String answer) {
            Log.d("MainActivity_Voice", "onOpenScreen called with screen: " + screen + ", answer: " + answer);
            runOnUiThread(() -> {
                if (voiceDialog != null) {
                    voiceDialog.showResponse(answer);
                    String normalized = screen != null ? screen.trim().toUpperCase(java.util.Locale.ROOT) : "";
                    switch (normalized) {
                        case "ADD_MEDICATION":
                            startActivity(new Intent(MainActivity.this, AddMedicationActivity.class));
                            break;
                        case "HEALTH":
                            startActivity(new Intent(MainActivity.this, vn.medisense.app.ui.HealthTrackerActivity.class));
                            break;
                        case "EMERGENCY":
                            openEmergencyDialer();
                            break;
                        default:
                            Toast.makeText(MainActivity.this,
                                    getString(R.string.voice_open_screen_unknown),
                                    Toast.LENGTH_SHORT).show();
                            break;
                    }
                    new Handler().postDelayed(() -> {
                        Log.d("MainActivity_Voice", "Trying to dismiss dialog in onOpenScreen");
                        if (voiceDialog != null && voiceDialog.isShowing()) {
                            voiceDialog.dismiss();
                            Log.d("MainActivity_Voice", "Dialog dismissed successfully");
                        }
                    }, 3000); // Đóng sau 3 giây để đọc xong "Đang mở màn hình..."
                }
            });
        }

        @Override
        public void onAmbiguousMedications(java.util.List<ReminderWithMedication> options, String answer) {
            runOnUiThread(() -> {
                voiceDialog.showResponse(answer);
                if (options == null || options.isEmpty()) {
                    return;
                }

                String[] items = new String[options.size()];
                java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault());
                for (int i = 0; i < options.size(); i++) {
                    ReminderWithMedication opt = options.get(i);
                    String name = opt.medication != null ? opt.medication.name : "Thuốc";
                    String time = opt.reminder != null
                            ? sdf.format(new java.util.Date(opt.reminder.reminderTime))
                            : "";
                    items[i] = name + (time.isEmpty() ? "" : " - " + time);
                }

                new com.google.android.material.dialog.MaterialAlertDialogBuilder(MainActivity.this)
                        .setTitle(getString(R.string.voice_ambiguous_medication))
                        .setItems(items, (dialog, which) -> {
                            ReminderWithMedication selected = options.get(which);
                            onReminderChecked(selected, true);
                        })
                        .setNegativeButton("Hủy", null)
                        .show();
            });
        }

        @Override
        public void onUnknownCommand(String answer) {
            runOnUiThread(() -> {
                voiceDialog.showResponse(answer);
            });
        }

        @Override
        public void onError(String errorMessage) {
            runOnUiThread(() -> {
                voiceDialog.showError(errorMessage);
            });
        }
    }
}
