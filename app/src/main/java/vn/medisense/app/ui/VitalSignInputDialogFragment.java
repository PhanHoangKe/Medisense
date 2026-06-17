package vn.medisense.app.ui;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.NumberPicker;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;

import vn.medisense.app.R;

public class VitalSignInputDialogFragment extends DialogFragment {

    private String title;
    private String type;
    private OnVitalSignSavedListener listener;

    public interface OnVitalSignSavedListener {
        void onSaved(String type, float value1, float value2, String note);
    }

    public static VitalSignInputDialogFragment newInstance(String title, String type) {
        VitalSignInputDialogFragment fragment = new VitalSignInputDialogFragment();
        Bundle args = new Bundle();
        args.putString("title", title);
        args.putString("type", type);
        fragment.setArguments(args);
        return fragment;
    }

    public void setListener(OnVitalSignSavedListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        if (getArguments() != null) {
            title = getArguments().getString("title");
            type = getArguments().getString("type");
        }

        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_vital_sign_input, null);
        android.widget.TextView dialogTitle = dialogView.findViewById(R.id.dialogTitle);
        dialogTitle.setText("Nhập " + title);

        LinearLayout bloodPressureSection = dialogView.findViewById(R.id.bloodPressureSection);
        LinearLayout heartRateSection = dialogView.findViewById(R.id.heartRateSection);
        LinearLayout bloodSugarSection = dialogView.findViewById(R.id.bloodSugarSection);
        LinearLayout weightSection = dialogView.findViewById(R.id.weightSection); // Thêm weight section

        if (type.equals("blood_pressure")) {
            bloodPressureSection.setVisibility(View.VISIBLE);
            NumberPicker systolicPicker = dialogView.findViewById(R.id.pickerSystolic);
            NumberPicker diastolicPicker = dialogView.findViewById(R.id.pickerDiastolic);
            systolicPicker.setMinValue(90);
            systolicPicker.setMaxValue(200);
            systolicPicker.setValue(120);
            diastolicPicker.setMinValue(60);
            diastolicPicker.setMaxValue(120);
            diastolicPicker.setValue(80);
        } else if (type.equals("heart_rate")) {
            heartRateSection.setVisibility(View.VISIBLE);
            NumberPicker heartRatePicker = dialogView.findViewById(R.id.pickerHeartRate);
            heartRatePicker.setMinValue(40);
            heartRatePicker.setMaxValue(200);
            heartRatePicker.setValue(75);
        } else if (type.equals("blood_sugar")) {
            bloodSugarSection.setVisibility(View.VISIBLE);
            NumberPicker bloodSugarPicker = dialogView.findViewById(R.id.pickerBloodSugar);
            bloodSugarPicker.setMinValue(50);
            bloodSugarPicker.setMaxValue(400);
            bloodSugarPicker.setValue(100);
        } else if (type.equals("weight")) {
            // Thêm xử lý cho cân nặng
            weightSection.setVisibility(View.VISIBLE);
            NumberPicker weightPicker = dialogView.findViewById(R.id.pickerWeight);
            weightPicker.setMinValue(30);
            weightPicker.setMaxValue(200);
            weightPicker.setValue(65);
        }

        Dialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setView(dialogView)
                .create();

        dialogView.findViewById(R.id.btnSave).setOnClickListener(v -> {
            float value1 = 0, value2 = 0;
            String note = "";

            if (type.equals("blood_pressure")) {
                NumberPicker systolicPicker = dialogView.findViewById(R.id.pickerSystolic);
                NumberPicker diastolicPicker = dialogView.findViewById(R.id.pickerDiastolic);
                value1 = systolicPicker.getValue();
                value2 = diastolicPicker.getValue();
            } else if (type.equals("heart_rate")) {
                NumberPicker heartRatePicker = dialogView.findViewById(R.id.pickerHeartRate);
                value1 = heartRatePicker.getValue();
            } else if (type.equals("blood_sugar")) {
                NumberPicker bloodSugarPicker = dialogView.findViewById(R.id.pickerBloodSugar);
                value1 = bloodSugarPicker.getValue();
            } else if (type.equals("weight")) {
                // Thêm xử lý lưu cân nặng
                NumberPicker weightPicker = dialogView.findViewById(R.id.pickerWeight);
                value1 = weightPicker.getValue();
            }

            TextInputEditText noteInput = dialogView.findViewById(R.id.inputNote);
            if (noteInput != null && noteInput.getText() != null) {
                note = noteInput.getText().toString();
            }

            if (listener != null) {
                listener.onSaved(type, value1, value2, note);
            }
            dismiss();
        });

        dialogView.findViewById(R.id.btnCancel).setOnClickListener(v -> dismiss());

        return dialog;
    }
}
