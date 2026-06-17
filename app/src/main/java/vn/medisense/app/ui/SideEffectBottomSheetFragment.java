package vn.medisense.app.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import vn.medisense.app.database.AppDatabase;
import vn.medisense.app.database.SideEffectLog;
import vn.medisense.app.databinding.BottomSheetSideEffectBinding;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.chip.Chip;

import java.util.ArrayList;
import java.util.List;
import vn.medisense.app.utils.AppExecutors;

public class SideEffectBottomSheetFragment extends BottomSheetDialogFragment {

    private BottomSheetSideEffectBinding binding;
    private int medicationId = -1;
    private String medicationName = "";

    public static SideEffectBottomSheetFragment newInstance(int medId, String medName) {
        SideEffectBottomSheetFragment fragment = new SideEffectBottomSheetFragment();
        Bundle args = new Bundle();
        args.putInt("MEDICATION_ID", medId);
        args.putString("MEDICATION_NAME", medName);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            medicationId = getArguments().getInt("MEDICATION_ID", -1);
            medicationName = getArguments().getString("MEDICATION_NAME", "");
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = BottomSheetSideEffectBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        binding.tvMedicationName.setText("Thuốc: " + medicationName);

        // Xử lý kiểu lựa chọn - đảm bảo chỉ một tâm trạng được chọn
        binding.rgMood.setOnCheckedChangeListener((group, checkedId) -> {
            // Bỏ chọn trước đó
            for (int i = 0; i < group.getChildCount(); i++) {
                group.getChildAt(i).setAlpha(0.3f);
            }
            // Highlight selected
            view.findViewById(checkedId).setAlpha(1.0f);
        });

        // Khởi tạo state
        binding.rgMood.check(binding.rbNeutral.getId());

        binding.btnSaveLog.setOnClickListener(v -> saveSideEffectLog());
    }

    private void saveSideEffectLog() {
        if (medicationId == -1) {
            Toast.makeText(getContext(), "Lỗi: Không tìm thấy thuốc", Toast.LENGTH_SHORT).show();
            dismiss();
            return;
        }

        int moodRating = 3; // Default Neutral
        int checkedId = binding.rgMood.getCheckedRadioButtonId();

        if (checkedId == binding.rbVeryBad.getId()) moodRating = 1;
        else if (checkedId == binding.rbBad.getId()) moodRating = 2;
        else if (checkedId == binding.rbNeutral.getId()) moodRating = 3;
        else if (checkedId == binding.rbGood.getId()) moodRating = 4;
        else if (checkedId == binding.rbVeryGood.getId()) moodRating = 5;

        // Get selected symptoms
        List<String> symptomsList = new ArrayList<>();
        for (int i = 0; i < binding.cgSymptoms.getChildCount(); i++) {
            Chip chip = (Chip) binding.cgSymptoms.getChildAt(i);
            if (chip.isChecked()) {
                symptomsList.add(chip.getText().toString());
            }
        }
        String symptoms = String.join(", ", symptomsList);

        String note = binding.editNote.getText() != null ? binding.editNote.getText().toString().trim() : "";

        // Lưu vào DB ở background
        SideEffectLog log = new SideEffectLog();
        log.medicationId = medicationId;
        log.timestamp = System.currentTimeMillis();
        log.moodRating = moodRating;
        log.symptoms = symptoms;
        log.note = note;

        AppExecutors.getInstance().diskIO().execute(() -> {
            AppDatabase.getInstance(getContext()).sideEffectLogDao().insert(log);
            
            AppExecutors.getInstance().mainThread().execute(() -> {
                if (getActivity() != null && getContext() != null) {
                    Toast.makeText(getContext(), "Cảm ơn bạn đã cập nhật tình trạng!", Toast.LENGTH_SHORT).show();
                    
                    // Notify parent activity để reload side effects
                    getParentFragmentManager().setFragmentResult("side_effect_saved", new android.os.Bundle());
                    
                    dismiss();
                }
            });
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
