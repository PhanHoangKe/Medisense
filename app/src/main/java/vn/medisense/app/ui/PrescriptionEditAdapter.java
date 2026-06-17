package vn.medisense.app.ui;

import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import vn.medisense.app.api.ParsedMedicationInfo;
import vn.medisense.app.databinding.ItemPrescriptionEditBinding;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Adapter cho danh sach thuoc co the chinh sua
 */
public class PrescriptionEditAdapter extends RecyclerView.Adapter<PrescriptionEditAdapter.ViewHolder> {

    private final List<ParsedMedicationInfo> items = new ArrayList<>();
    private static final Pattern TIME_PATTERN = Pattern.compile("^([01]\\d|2[0-3]):[0-5]\\d$");
    private OnEditItemListener onEditItemListener;

    public interface OnEditItemListener {
        void onEditItem(ParsedMedicationInfo info);
    }

    public void setOnEditItemListener(OnEditItemListener listener) {
        this.onEditItemListener = listener;
    }

    public void setItems(List<ParsedMedicationInfo> newItems) {
        items.clear();
        if (newItems != null) {
            items.addAll(newItems);
        }
        notifyDataSetChanged();
    }

    public List<ParsedMedicationInfo> getItems() {
        return items;
    }

    public boolean validateAll(RecyclerView recyclerView) {
        boolean isValid = true;
        for (int i = 0; i < getItemCount(); i++) {
            RecyclerView.ViewHolder holder = recyclerView.findViewHolderForAdapterPosition(i);
            if (holder instanceof ViewHolder) {
                if (!((ViewHolder) holder).validate()) {
                    isValid = false;
                }
            } else {
                ParsedMedicationInfo info = items.get(i);
                if (info == null || info.name == null || info.name.trim().isEmpty()) {
                    isValid = false;
                }
                if (info == null || info.times == null || info.times.isEmpty()) {
                    isValid = false;
                }
            }
        }
        return isValid;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemPrescriptionEditBinding binding = ItemPrescriptionEditBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(binding, onEditItemListener);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(items.get(position));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        private final ItemPrescriptionEditBinding binding;
        private final OnEditItemListener onEditItemListener;
        private TextWatcher nameWatcher;
        private TextWatcher dosageWatcher;
        private TextWatcher totalQuantityWatcher;
        private TextWatcher notesWatcher;

        ViewHolder(@NonNull ItemPrescriptionEditBinding binding, OnEditItemListener listener) {
            super(binding.getRoot());
            this.binding = binding;
            this.onEditItemListener = listener;
        }

        void bind(ParsedMedicationInfo info) {
            if (nameWatcher != null) {
                binding.inputName.removeTextChangedListener(nameWatcher);
            }
            if (dosageWatcher != null) {
                binding.inputDosage.removeTextChangedListener(dosageWatcher);
            }
            if (totalQuantityWatcher != null) {
                binding.inputTotalQuantity.removeTextChangedListener(totalQuantityWatcher);
            }
            if (notesWatcher != null) {
                binding.inputNotes.removeTextChangedListener(notesWatcher);
            }

            binding.inputName.setText(info.name != null ? info.name : "");
            binding.inputDosage.setText(info.dosage != null ? info.dosage : "");
            binding.inputTotalQuantity.setText(info.totalQuantity > 0 ? String.valueOf(info.totalQuantity) : "");
            binding.inputNotes.setText(info.notes != null ? info.notes : "");

            // Hiển thị trạng thái AI (needsReview/missingFields/confidence)
            StringBuilder status = new StringBuilder();
            if (info.needsReview) {
                status.append(binding.getRoot().getContext().getString(vn.medisense.app.R.string.ai_needs_review));
            }
            if (info.missingFields != null && !info.missingFields.isEmpty()) {
                if (status.length() > 0) status.append("\n");
                status.append(binding.getRoot().getContext().getString(vn.medisense.app.R.string.ai_missing_fields_prefix))
                        .append(" ")
                        .append(android.text.TextUtils.join(", ", info.missingFields));
            }
            if (binding.textAiStatus != null) {
                if (status.length() > 0) {
                    binding.textAiStatus.setText(status.toString());
                    binding.textAiStatus.setVisibility(android.view.View.VISIBLE);
                } else {
                    binding.textAiStatus.setVisibility(android.view.View.GONE);
                }
            }

            updateStatusUi(info);

            binding.buttonEditItem.setText(getActionLabel(info));
            binding.buttonEditItem.setOnClickListener(v -> {
                if (onEditItemListener != null) {
                    onEditItemListener.onEditItem(info);
                }
            });

            setupTimesChips(info);

            nameWatcher = simpleWatcher(text -> info.name = text);
            dosageWatcher = simpleWatcher(text -> info.dosage = text);
            totalQuantityWatcher = simpleWatcher(text -> {
                try {
                    info.totalQuantity = Integer.parseInt(text);
                } catch (NumberFormatException e) {
                    info.totalQuantity = 0;
                }
            });
            notesWatcher = simpleWatcher(text -> info.notes = text);

            binding.inputName.addTextChangedListener(nameWatcher);
            binding.inputDosage.addTextChangedListener(dosageWatcher);
            binding.inputTotalQuantity.addTextChangedListener(totalQuantityWatcher);
            binding.inputNotes.addTextChangedListener(notesWatcher);
        }

        private void setupTimesChips(ParsedMedicationInfo info) {
            binding.chipGroupTimes.removeAllViews();
            if (info.times != null) {
                for (int i = 0; i < info.times.size(); i++) {
                    String time = info.times.get(i);
                    com.google.android.material.chip.Chip chip = new com.google.android.material.chip.Chip(
                            binding.getRoot().getContext());
                    chip.setText(time);
                    chip.setCloseIconVisible(true);
                    final int index = i;
                    chip.setOnCloseIconClickListener(v -> {
                        info.times.remove(index);
                        setupTimesChips(info);
                    });
                    binding.chipGroupTimes.addView(chip);
                }
            }

            com.google.android.material.chip.Chip addChip = new com.google.android.material.chip.Chip(
                    binding.getRoot().getContext());
            addChip.setText("+ Thêm giờ");
            addChip.setChipBackgroundColorResource(android.R.color.transparent);
            addChip.setChipStrokeWidth(1f);
            addChip.setChipStrokeColorResource(com.google.android.material.R.color.material_dynamic_neutral50);
            addChip.setOnClickListener(v -> showAddTimeDialog(info));
            binding.chipGroupTimes.addView(addChip);
        }

        private void showAddTimeDialog(ParsedMedicationInfo info) {
            new android.app.TimePickerDialog(
                    binding.getRoot().getContext(),
                    (view, hourOfDay, minute) -> {
                        String newTime = String.format(java.util.Locale.getDefault(), "%02d:%02d", hourOfDay, minute);
                        if (info.times == null) {
                            info.times = new java.util.ArrayList<>();
                        }
                        info.times.add(newTime);
                        java.util.Collections.sort(info.times);
                        setupTimesChips(info);
                    },
                    8, 0, true).show();
        }

        private void updateStatusUi(ParsedMedicationInfo info) {
            String badgeText;
            int badgeColor;
            if (info.needsReview) {
                badgeText = binding.getRoot().getContext().getString(vn.medisense.app.R.string.ocr_status_review);
                badgeColor = androidx.core.content.ContextCompat.getColor(
                        binding.getRoot().getContext(), vn.medisense.app.R.color.status_warning);
            } else if (info.missingFields != null && !info.missingFields.isEmpty()) {
                badgeText = binding.getRoot().getContext().getString(vn.medisense.app.R.string.ocr_status_missing);
                badgeColor = androidx.core.content.ContextCompat.getColor(
                        binding.getRoot().getContext(), vn.medisense.app.R.color.status_error);
            } else {
                badgeText = binding.getRoot().getContext().getString(vn.medisense.app.R.string.ocr_status_valid);
                badgeColor = androidx.core.content.ContextCompat.getColor(
                        binding.getRoot().getContext(), vn.medisense.app.R.color.status_success);
            }
            binding.textStatusBadge.setText(badgeText);
            binding.textStatusBadge.setBackgroundTintList(android.content.res.ColorStateList.valueOf(badgeColor));

            String confidenceText = binding.getRoot().getContext().getString(
                    vn.medisense.app.R.string.ai_confidence_label) + ": " + formatConfidence(info.confidence);
            binding.textConfidence.setText(confidenceText);

            String timeSummary = "-";
            if (info.times != null && !info.times.isEmpty()) {
                timeSummary = android.text.TextUtils.join(", ", info.times);
            }
            String summary = "Tần suất: " + Math.max(1, info.frequency)
                    + " | Số ngày: " + Math.max(1, info.durationDays)
                    + " | Giờ: " + timeSummary;
            binding.textSummary.setText(summary);
        }

        private String getActionLabel(ParsedMedicationInfo info) {
            if (info.needsReview || (info.missingFields != null && !info.missingFields.isEmpty())) {
                return binding.getRoot().getContext().getString(vn.medisense.app.R.string.ocr_edit_item);
            }
            return binding.getRoot().getContext().getString(vn.medisense.app.R.string.ocr_check_item);
        }

        private String formatConfidence(float confidence) {
            if (confidence <= 0f) return "-";
            int pct = Math.round(confidence * 100f);
            return pct + "%";
        }

        private TextWatcher simpleWatcher(ValueConsumer consumer) {
            return new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    consumer.accept(s.toString().trim());
                }

                @Override
                public void afterTextChanged(Editable s) {
                }
            };
        }

        boolean validate() {
            boolean valid = true;
            String name = binding.inputName.getText() != null ? binding.inputName.getText().toString().trim() : "";
            String dosage = binding.inputDosage.getText() != null ? binding.inputDosage.getText().toString().trim() : "";

            if (name.isEmpty()) {
                binding.inputName.setError("Ten thuoc khong duoc trong");
                valid = false;
            } else {
                binding.inputName.setError(null);
            }

            if (dosage.isEmpty()) {
                binding.inputDosage.setError("Lieu luong khong duoc trong");
                valid = false;
            } else {
                binding.inputDosage.setError(null);
            }

            if (binding.chipGroupTimes.getChildCount() <= 1) {
                valid = false;
            }

            return valid;
        }

        interface ValueConsumer {
            void accept(String value);
        }
    }
}
