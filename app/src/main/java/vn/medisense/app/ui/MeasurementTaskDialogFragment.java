package vn.medisense.app.ui;

import android.app.Dialog;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import vn.medisense.app.database.MeasurementTask;

public class MeasurementTaskDialogFragment extends DialogFragment {

    private OnTaskAddedListener listener;

    public interface OnTaskAddedListener {
        void onTaskAdded(MeasurementTask task);
    }

    public static MeasurementTaskDialogFragment newInstance() {
        return new MeasurementTaskDialogFragment();
    }

    public void setListener(OnTaskAddedListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        String[] types = {"blood_pressure", "heart_rate", "blood_sugar"};
        String[] labels = {"Huyết áp", "Nhịp tim", "Đường huyết"};
        final int[] selectedIndex = {0};

        return new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Thêm lịch nhắc đo")
                .setSingleChoiceItems(labels, 0, (d, which) -> selectedIndex[0] = which)
                .setPositiveButton("Thêm", (d, which) -> {
                    // Mở TimePicker sau khi chọn xong loại chỉ số
                    android.app.TimePickerDialog timePicker = new android.app.TimePickerDialog(
                            requireContext(),
                            (tp, hour, minute) -> {
                                long timeOfDay = (hour * 60L + minute) * 60L * 1000L;
                                int si = selectedIndex[0];
                                MeasurementTask task = new MeasurementTask(
                                        types[si],
                                        "Đo " + labels[si] + " " + String.format(java.util.Locale.getDefault(), "%02d:%02d", hour, minute),
                                        timeOfDay);
                                if (listener != null) {
                                    listener.onTaskAdded(task);
                                }
                            }, 8, 0, true);
                    timePicker.show();
                })
                .setNegativeButton("Hủy", null)
                .create();
    }
}
