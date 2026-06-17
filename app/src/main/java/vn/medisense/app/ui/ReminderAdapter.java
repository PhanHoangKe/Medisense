package vn.medisense.app.ui;

import android.content.Context;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import vn.medisense.app.databinding.ItemReminderBinding;
import vn.medisense.app.database.ReminderWithMedication;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ReminderAdapter extends ListAdapter<ReminderWithMedication, ReminderAdapter.ViewHolder> {

    public interface OnReminderCheckedListener {
        void onReminderChecked(ReminderWithMedication item, boolean isChecked);
    }

    public interface OnItemClickListener {
        void onItemClick(ReminderWithMedication item);
    }

    public interface OnEditClickListener {
        void onEditClick(int prescriptionId);
    }

    public interface OnPrescriptionActionListener {
        void onArchive(int prescriptionId);
        void onClone(int prescriptionId);
    }

    private final OnReminderCheckedListener checkListener;
    private final OnItemClickListener clickListener;
    private final OnEditClickListener editListener;
    private final OnPrescriptionActionListener actionListener;
    private final DateFormat timeFormatter = new SimpleDateFormat("HH:mm", Locale.getDefault());

    private java.util.Map<Integer, vn.medisense.app.database.PrescriptionData> prescriptionMap = new java.util.HashMap<>();

    private static final DiffUtil.ItemCallback<ReminderWithMedication> DIFF_CALLBACK = new DiffUtil.ItemCallback<ReminderWithMedication>() {
        @Override
        public boolean areItemsTheSame(@NonNull ReminderWithMedication oldItem,
                @NonNull ReminderWithMedication newItem) {
            return oldItem.reminder != null && newItem.reminder != null
                    && oldItem.reminder.id == newItem.reminder.id;
        }

        @Override
        public boolean areContentsTheSame(@NonNull ReminderWithMedication oldItem,
                @NonNull ReminderWithMedication newItem) {
            if (oldItem.reminder == null || newItem.reminder == null) return false;
            // So sánh cả status mới để UI refresh đúng khi trạng thái thay đổi
            String oldStatus = oldItem.reminder.status != null ? oldItem.reminder.status : "";
            String newStatus = newItem.reminder.status != null ? newItem.reminder.status : "";
            boolean sameReminder = oldItem.reminder.reminderTime == newItem.reminder.reminderTime
                    && oldItem.reminder.isTaken == newItem.reminder.isTaken
                    && oldItem.reminder.medicationId == newItem.reminder.medicationId
                    && oldStatus.equals(newStatus)
                    && oldItem.reminder.snoozeUntil == newItem.reminder.snoozeUntil;
            String oldName = oldItem.medication != null ? oldItem.medication.name : "";
            String newName = newItem.medication != null ? newItem.medication.name : "";
            return sameReminder && oldName.equals(newName);
        }
    };

    public ReminderAdapter(OnReminderCheckedListener checkListener, OnItemClickListener clickListener,
            OnEditClickListener editListener, OnPrescriptionActionListener actionListener) {
        super(DIFF_CALLBACK);
        this.checkListener = checkListener;
        this.clickListener = clickListener;
        this.editListener = editListener;
        this.actionListener = actionListener;
    }

    public void setPrescriptionMap(java.util.Map<Integer, vn.medisense.app.database.PrescriptionData> map) {
        this.prescriptionMap = map;
        notifyDataSetChanged();
    }

    public void setItems(List<ReminderWithMedication> newItems) {
        List<ReminderWithMedication> snapshot = newItems != null
                ? new ArrayList<>(newItems)
                : new ArrayList<>();
        submitList(snapshot);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemReminderBinding binding = ItemReminderBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ReminderWithMedication currentItem = getItem(position);
        ReminderWithMedication previousItem = position > 0 ? getItem(position - 1) : null;

        // Xác định Buổi (Sáng/Trưa/Chiều/Tối)
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(currentItem.reminder.reminderTime);
        int hour = cal.get(Calendar.HOUR_OF_DAY);
        String currentSession = getSessionName(hour);

        String previousSession = "";
        if (previousItem != null) {
            Calendar pCal = Calendar.getInstance();
            pCal.setTimeInMillis(previousItem.reminder.reminderTime);
            previousSession = getSessionName(pCal.get(Calendar.HOUR_OF_DAY));
        }

        boolean showHeader = !currentSession.equals(previousSession);

        holder.bind(currentItem, timeFormatter, showHeader, currentSession, checkListener, clickListener, editListener, actionListener);
    }

    private String getSessionName(int hour) {
        if (hour < 11)
            return "Buổi Sáng";
        if (hour < 14)
            return "Buổi Trưa";
        if (hour < 18)
            return "Buổi Chiều";
        return "Buổi Tối";
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        private final ItemReminderBinding binding;

        ViewHolder(@NonNull ItemReminderBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(ReminderWithMedication item, DateFormat timeFormatter, boolean showHeader,
                String sessionName, OnReminderCheckedListener checkListener,
                OnItemClickListener clickListener, OnEditClickListener editListener,
                OnPrescriptionActionListener actionListener) {

            if (showHeader) {
                binding.layoutGroupHeader.setVisibility(android.view.View.VISIBLE);
                binding.textGroupTitle.setText(sessionName);

                // Logic biểu tượng cho Header Buổi uống thuốc
                if ("Buổi Sáng".equals(sessionName)) {
                    binding.iconGroupHeader.setImageResource(android.R.drawable.ic_menu_day);
                } else if ("Buổi Tối".equals(sessionName)) {
                    binding.iconGroupHeader.setImageResource(android.R.drawable.ic_menu_myplaces); // Chỉ là phần giữ chỗ, tùy chỉnh nếu cần
                } else {
                    binding.iconGroupHeader.setImageResource(android.R.drawable.ic_menu_recent_history);
                }
            } else {
                binding.layoutGroupHeader.setVisibility(android.view.View.GONE);
            }

            if (item.medication != null) {
                binding.textMedicationName.setText(item.medication.name);
                binding.textReminderTime.setText(timeFormatter.format(new Date(item.reminder.reminderTime)));

                binding.btnEdit.setOnClickListener(v -> {
                    if (editListener != null) {
                        editListener.onEditClick(item.medication.prescriptionId);
                    }
                });

                Context ctx = binding.getRoot().getContext();
                
                // Xóa sạch any image tint lists và color filters set trên các image
                binding.imgMedicationIcon.setImageTintList(null);
                binding.imgMedicationIcon.setColorFilter(null);
                
                // Detect nếu các app is trong Dark Mode
                boolean isDarkMode = (ctx.getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK) 
                                     == android.content.res.Configuration.UI_MODE_NIGHT_YES;
                
                // Select MyTherapy style icon và background based trên dosage unit
                String dosageLower = item.medication.dosage != null ? item.medication.dosage.toLowerCase() : "";
                String nameLower = item.medication.name != null ? item.medication.name.toLowerCase() : "";
                
                if (dosageLower.contains("viên") || nameLower.contains("viên") || dosageLower.contains("capsule") || dosageLower.contains("tablet")) {
                    binding.imgMedicationIcon.setImageResource(vn.medisense.app.R.drawable.medication_icon_capsule);
                    int bgColor = isDarkMode ? android.graphics.Color.parseColor("#1A4CAF50") : android.graphics.Color.parseColor("#E8F5E9"); // 10% green / light green
                    binding.cardMedicationIcon.setCardBackgroundColor(bgColor);
                } else if (dosageLower.contains("ml") || dosageLower.contains("chai") || dosageLower.contains("siro") || dosageLower.contains("liquid") || dosageLower.contains("giọt")) {
                    binding.imgMedicationIcon.setImageResource(vn.medisense.app.R.drawable.medication_icon_liquid);
                    int bgColor = isDarkMode ? android.graphics.Color.parseColor("#1A2196F3") : android.graphics.Color.parseColor("#E3F2FD"); // 10% blue / light blue
                    binding.cardMedicationIcon.setCardBackgroundColor(bgColor);
                } else {
                    binding.imgMedicationIcon.setImageResource(vn.medisense.app.R.drawable.medication_icon_tablet);
                    int bgColor = isDarkMode ? android.graphics.Color.parseColor("#1AFF9800") : android.graphics.Color.parseColor("#FFF3E0"); // 10% orange / light orange
                    binding.cardMedicationIcon.setCardBackgroundColor(bgColor);
                }
                
                // Thiết lập dosage và instructions (AI warning)
                String fullInstructions = item.medication.instructions != null ? item.medication.instructions : "";
                String dosageText = item.medication.dosage;
                
                String userInstructions = "";
                String aiWarningText = "";
                
                if (fullInstructions.contains("[AI cảnh báo tương tác]")) {
                    int idx = fullInstructions.indexOf("[AI cảnh báo tương tác]");
                    userInstructions = fullInstructions.substring(0, idx).trim();
                    aiWarningText = fullInstructions.substring(idx).trim();
                } else {
                    userInstructions = fullInstructions;
                }
                
                if (!userInstructions.isEmpty()) {
                    dosageText += " • " + userInstructions;
                }
                binding.textDosage.setText(dosageText);
                
                if (!aiWarningText.isEmpty()) {
                    binding.layoutAiWarning.setVisibility(android.view.View.VISIBLE);
                    binding.textAiWarningContent.setText(aiWarningText);
                    
                    // Hiển thị toggle button cho long warnings
                    binding.buttonToggleInstructions.setVisibility(aiWarningText.length() > 80 ? android.view.View.VISIBLE : android.view.View.GONE);
                    binding.textAiWarningContent.setMaxLines(3);
                    binding.textAiWarningContent.setEllipsize(android.text.TextUtils.TruncateAt.END);
                    
                    binding.buttonToggleInstructions.setOnClickListener(v -> {
                        if (binding.textAiWarningContent.getMaxLines() == 3) {
                            binding.textAiWarningContent.setMaxLines(Integer.MAX_VALUE);
                            binding.textAiWarningContent.setEllipsize(null);
                            binding.buttonToggleInstructions.setText("Thu gọn");
                        } else {
                            binding.textAiWarningContent.setMaxLines(3);
                            binding.textAiWarningContent.setEllipsize(android.text.TextUtils.TruncateAt.END);
                            binding.buttonToggleInstructions.setText("Xem thêm");
                        }
                    });
                } else {
                    binding.layoutAiWarning.setVisibility(android.view.View.GONE);
                    binding.buttonToggleInstructions.setVisibility(android.view.View.GONE);
                }
            } else {
                binding.textMedicationName.setText("Thuốc không xác định");
                binding.textReminderTime.setText(timeFormatter.format(new Date(item.reminder.reminderTime)));
                binding.textDosage.setText("");
                binding.layoutAiWarning.setVisibility(android.view.View.GONE);
                binding.buttonToggleInstructions.setVisibility(android.view.View.GONE);
                binding.btnEdit.setOnClickListener(null);
            }

            // Tạm thời gỡ listener để tránh lặp vô hạn khi view được tái sử dụng (recycled)
            binding.checkboxTaken.setOnCheckedChangeListener(null);
            binding.checkboxTaken.setChecked(item.reminder.isTaken);
            binding.checkboxTaken.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (checkListener != null) {
                    checkListener.onReminderChecked(item, isChecked);
                }
            });

            // ── Hiển thị trạng thái theo status mới ──────────────────────────
            String status = item.reminder.status != null
                    ? item.reminder.status
                    : (item.reminder.isTaken
                            ? vn.medisense.app.database.Reminder.STATUS_TAKEN
                            : vn.medisense.app.database.Reminder.STATUS_PENDING);

            Context ctx = binding.getRoot().getContext();
            binding.iconStatus.setImageTintList(null);
            
            binding.checkboxTaken.setButtonTintList(null);
            switch (status) {
                case vn.medisense.app.database.Reminder.STATUS_TAKEN: {
                    // Mờ đi, không gạch ngang tên
                    float dim = 0.5f;
                    binding.cardMedicationIcon.setAlpha(dim);
                    binding.textMedicationName.setAlpha(dim);
                    binding.textDosage.setAlpha(dim);
                    binding.layoutTime.setAlpha(dim);
                    binding.cardStatus.setAlpha(dim);
                    binding.textMedicationName.setPaintFlags(
                            binding.textMedicationName.getPaintFlags()
                                    & ~android.graphics.Paint.STRIKE_THRU_TEXT_FLAG);
                    String takenAt = item.reminder.takenTime > 0
                            ? timeFormatter.format(new Date(item.reminder.takenTime))
                            : timeFormatter.format(new Date(item.reminder.reminderTime));
                    binding.textStatus.setText("Đã uống lúc " + takenAt);
                    binding.textStatus.setTextColor(android.graphics.Color.parseColor("#8E8E93"));
                    binding.iconStatus.setImageResource(vn.medisense.app.R.drawable.ic_log_status_taken);
                    binding.iconStatus.setColorFilter(null);
                    binding.cardStatus.setCardBackgroundColor(android.graphics.Color.parseColor("#1A8E8E93"));
                    binding.checkboxTaken.setButtonDrawable(vn.medisense.app.R.drawable.ic_mytherapy_checked);
                    binding.checkboxTaken.setChecked(true);
                    break;
                }
                case vn.medisense.app.database.Reminder.STATUS_MISSED: {
                    // Màu đỏ — đã bỏ lỡ
                    float full = 1.0f;
                    binding.cardMedicationIcon.setAlpha(full);
                    binding.textMedicationName.setAlpha(full);
                    binding.textDosage.setAlpha(full);
                    binding.layoutTime.setAlpha(full);
                    binding.cardStatus.setAlpha(full);
                    binding.textMedicationName.setPaintFlags(
                            binding.textMedicationName.getPaintFlags()
                                    & ~android.graphics.Paint.STRIKE_THRU_TEXT_FLAG);
                    binding.textStatus.setText("Đã bỏ lỡ");
                    binding.textStatus.setTextColor(android.graphics.Color.parseColor("#FF5252"));
                    binding.iconStatus.setImageResource(vn.medisense.app.R.drawable.ic_log_status_missed);
                    binding.iconStatus.setColorFilter(null);
                    binding.cardStatus.setCardBackgroundColor(android.graphics.Color.parseColor("#1AFF5252"));
                    binding.checkboxTaken.setButtonDrawable(vn.medisense.app.R.drawable.ic_log_status_missed);
                    binding.checkboxTaken.setChecked(false);
                    break;
                }
                case vn.medisense.app.database.Reminder.STATUS_SKIPPED: {
                    // Màu cam — bỏ qua có lý do
                    float full = 1.0f;
                    binding.cardMedicationIcon.setAlpha(full);
                    binding.textMedicationName.setAlpha(full);
                    binding.textDosage.setAlpha(full);
                    binding.layoutTime.setAlpha(full);
                    binding.cardStatus.setAlpha(full);
                    binding.textMedicationName.setPaintFlags(
                            binding.textMedicationName.getPaintFlags()
                                    & ~android.graphics.Paint.STRIKE_THRU_TEXT_FLAG);
                    String skipLabel = "Đã bỏ qua";
                    if (item.reminder.skipReason != null && !item.reminder.skipReason.isEmpty()) {
                        skipLabel += " — " + item.reminder.skipReason;
                    }
                    binding.textStatus.setText(skipLabel);
                    binding.textStatus.setTextColor(android.graphics.Color.parseColor("#FF9800"));
                    binding.iconStatus.setImageResource(vn.medisense.app.R.drawable.ic_log_status_skipped);
                    binding.iconStatus.setColorFilter(null);
                    binding.cardStatus.setCardBackgroundColor(android.graphics.Color.parseColor("#1AFF9800"));
                    binding.checkboxTaken.setButtonDrawable(vn.medisense.app.R.drawable.ic_log_status_skipped);
                    binding.checkboxTaken.setChecked(false);
                    break;
                }
                case vn.medisense.app.database.Reminder.STATUS_SNOOZED: {
                    // Màu xanh dương — đang nhắc lại
                    float full = 1.0f;
                    binding.cardMedicationIcon.setAlpha(full);
                    binding.textMedicationName.setAlpha(full);
                    binding.textDosage.setAlpha(full);
                    binding.layoutTime.setAlpha(full);
                    binding.cardStatus.setAlpha(full);
                    binding.textMedicationName.setPaintFlags(
                            binding.textMedicationName.getPaintFlags()
                                    & ~android.graphics.Paint.STRIKE_THRU_TEXT_FLAG);
                    String snoozeLabel = "Nhắc lại";
                    if (item.reminder.snoozeUntil > 0) {
                        snoozeLabel += " lúc " + timeFormatter.format(new Date(item.reminder.snoozeUntil));
                    }
                    binding.textStatus.setText(snoozeLabel);
                    binding.textStatus.setTextColor(android.graphics.Color.parseColor("#2196F3"));
                    binding.iconStatus.setImageResource(vn.medisense.app.R.drawable.ic_log_status_snoozed);
                    binding.iconStatus.setColorFilter(null);
                    binding.cardStatus.setCardBackgroundColor(android.graphics.Color.parseColor("#1A2196F3"));
                    binding.checkboxTaken.setButtonDrawable(vn.medisense.app.R.drawable.ic_log_status_snoozed);
                    binding.checkboxTaken.setChecked(false);
                    break;
                }
                default: {
                    // PENDING — màu primary, chưa uống
                    float full = 1.0f;
                    binding.cardMedicationIcon.setAlpha(full);
                    binding.textMedicationName.setAlpha(full);
                    binding.textDosage.setAlpha(full);
                    binding.layoutTime.setAlpha(full);
                    binding.cardStatus.setAlpha(full);
                    binding.textMedicationName.setPaintFlags(
                            binding.textMedicationName.getPaintFlags()
                                    & ~android.graphics.Paint.STRIKE_THRU_TEXT_FLAG);
                    binding.textStatus.setText("Chưa uống");
                    
                    android.util.TypedValue typedValue = new android.util.TypedValue();
                    ctx.getTheme().resolveAttribute(com.google.android.material.R.attr.colorPrimary, typedValue, true);
                    int primaryColor = typedValue.data;
                    
                    binding.textStatus.setTextColor(primaryColor);
                    binding.iconStatus.setImageResource(vn.medisense.app.R.drawable.ic_log_status_pending);
                    binding.iconStatus.setColorFilter(primaryColor, android.graphics.PorterDuff.Mode.SRC_IN);
                    
                    int alphaColor = (primaryColor & 0x00FFFFFF) | 0x1A000000;
                    binding.cardStatus.setCardBackgroundColor(alphaColor);
                    
                    binding.checkboxTaken.setButtonDrawable(vn.medisense.app.R.drawable.ic_mytherapy_unchecked);
                    binding.checkboxTaken.setChecked(false);
                    break;
                }
            }

            binding.getRoot().setOnClickListener(v -> {
                if (clickListener != null) {
                    clickListener.onItemClick(item);
                }
            });

            // Nhấn giữ: hiển thị menu popup cho các hành động cấp độ đơn thuốc
            binding.getRoot().setOnLongClickListener(v -> {
                if (item.medication == null || actionListener == null) return false;
                int prescriptionId = item.medication.prescriptionId;
                android.widget.PopupMenu popup = new android.widget.PopupMenu(v.getContext(), v);
                popup.getMenu().add(0, 1, 0, "Lưu trữ đơn thuốc");
                popup.getMenu().add(0, 2, 1, "Nhân bản đơn thuốc");
                popup.setOnMenuItemClickListener(menuItem -> {
                    if (menuItem.getItemId() == 1) {
                        actionListener.onArchive(prescriptionId);
                        return true;
                    } else if (menuItem.getItemId() == 2) {
                        actionListener.onClone(prescriptionId);
                        return true;
                    }
                    return false;
                });
                popup.show();
                return true;
            });
        }
    }
}
