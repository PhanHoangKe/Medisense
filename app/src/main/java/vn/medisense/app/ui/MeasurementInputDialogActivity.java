package vn.medisense.app.ui;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import vn.medisense.app.R;
import vn.medisense.app.database.AppDatabase;
import vn.medisense.app.database.VitalSign;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import vn.medisense.app.utils.AppExecutors;

public class MeasurementInputDialogActivity extends AppCompatActivity {

    public static final String ACTION_VITAL_SIGN_ADDED = "vn.medisense.app.VITAL_SIGN_ADDED";

    private String type;
    private int taskId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_measurement_input_dialog);

        // Bật màn hình và hiển thị trên màn hình khóa
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        } else {
            getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                    | android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        }

        Intent intent = getIntent();
        type = intent.getStringExtra("type");
        String title = intent.getStringExtra("title");
        taskId = intent.getIntExtra("taskId", -1);

        if (type == null) {
            Log.e("MeasurementInput", "Type là null, đang đóng.");
            finish();
            return;
        }

        TextView dialogTitle = findViewById(R.id.dialogTitle);
        dialogTitle.setText(title != null ? title : "Nhập chỉ số");

        setupNumberPickers();

        MaterialButton btnCancel = findViewById(R.id.btnCancel);
        MaterialButton btnSave = findViewById(R.id.btnSave);

        btnCancel.setOnClickListener(v -> finish());
        btnSave.setOnClickListener(v -> saveVitalSign());
    }

    private void setupNumberPickers() {
        LinearLayout bloodPressureSection = findViewById(R.id.bloodPressureSection);
        LinearLayout heartRateSection = findViewById(R.id.heartRateSection);
        LinearLayout bloodSugarSection = findViewById(R.id.bloodSugarSection);
        LinearLayout weightSection = findViewById(R.id.weightSection);

        if (type.equals("blood_pressure")) {
            bloodPressureSection.setVisibility(View.VISIBLE);
            NumberPicker systolicPicker = findViewById(R.id.pickerSystolic);
            NumberPicker diastolicPicker = findViewById(R.id.pickerDiastolic);
            
            systolicPicker.setMinValue(90);
            systolicPicker.setMaxValue(200);
            systolicPicker.setValue(120);
            
            diastolicPicker.setMinValue(60);
            diastolicPicker.setMaxValue(120);
            diastolicPicker.setValue(80);
        } else if (type.equals("heart_rate")) {
            heartRateSection.setVisibility(View.VISIBLE);
            NumberPicker heartRatePicker = findViewById(R.id.pickerHeartRate);
            heartRatePicker.setMinValue(40);
            heartRatePicker.setMaxValue(200);
            heartRatePicker.setValue(75);
        } else if (type.equals("blood_sugar")) {
            bloodSugarSection.setVisibility(View.VISIBLE);
            NumberPicker bloodSugarPicker = findViewById(R.id.pickerBloodSugar);
            bloodSugarPicker.setMinValue(50);
            bloodSugarPicker.setMaxValue(400);
            bloodSugarPicker.setValue(100);
        } else if (type.equals("weight")) {
            weightSection.setVisibility(View.VISIBLE);
            NumberPicker weightPicker = findViewById(R.id.pickerWeight);
            weightPicker.setMinValue(30);
            weightPicker.setMaxValue(200);
            weightPicker.setValue(65);
        }
    }

    private void saveVitalSign() {
        float value1 = 0, value2 = 0;
        String note = "";

        if (type.equals("blood_pressure")) {
            NumberPicker systolicPicker = findViewById(R.id.pickerSystolic);
            NumberPicker diastolicPicker = findViewById(R.id.pickerDiastolic);
            value1 = systolicPicker.getValue();
            value2 = diastolicPicker.getValue();
        } else if (type.equals("heart_rate")) {
            NumberPicker heartRatePicker = findViewById(R.id.pickerHeartRate);
            value1 = heartRatePicker.getValue();
        } else if (type.equals("blood_sugar")) {
            NumberPicker bloodSugarPicker = findViewById(R.id.pickerBloodSugar);
            value1 = bloodSugarPicker.getValue();
        } else if (type.equals("weight")) {
            NumberPicker weightPicker = findViewById(R.id.pickerWeight);
            value1 = weightPicker.getValue();
        }

        TextInputEditText inputNote = findViewById(R.id.inputNote);
        if (inputNote != null && inputNote.getText() != null) {
            note = inputNote.getText().toString().trim();
        }

        VitalSign vitalSign = new VitalSign(
                type,
                value1,
                value2,
                System.currentTimeMillis(),
                note
        );

        AppExecutors.getInstance().diskIO().execute(() -> {
            AppDatabase.getInstance(getApplicationContext()).vitalSignDao().insertVitalSign(vitalSign);
            vn.medisense.app.utils.FirestoreSyncHelper.syncVitalSign(getApplicationContext(), vitalSign);
            AppExecutors.getInstance().mainThread().execute(() -> {
                Toast.makeText(this, "Đã lưu chỉ số", Toast.LENGTH_SHORT).show();
                // Gửi broadcast để cập nhật các fragment đang mở (ví dụ: HealthTrackerFragment)
                sendBroadcast(new Intent(ACTION_VITAL_SIGN_ADDED));
                finish();
            });
        });
    }
}
