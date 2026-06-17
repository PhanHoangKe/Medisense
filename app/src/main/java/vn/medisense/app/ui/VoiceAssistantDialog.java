package vn.medisense.app.ui;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.Window;

import androidx.annotation.NonNull;

import vn.medisense.app.databinding.DialogVoiceAssistantBinding;

/**
 * VoiceAssistantDialog - Dialog hiển thị trạng thái trợ lý giọng nói
 */
public class VoiceAssistantDialog extends Dialog {
    
    private DialogVoiceAssistantBinding binding;
    private VoiceDialogCallback callback;

    public VoiceAssistantDialog(@NonNull Context context, VoiceDialogCallback callback) {
        super(context);
        this.callback = callback;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        
        binding = DialogVoiceAssistantBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        
        // Cấu hình dialog
        if (getWindow() != null) {
            getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }
        
        setCancelable(true);
        
        // Thiết lập buttons
        binding.buttonClose.setOnClickListener(v -> {
            if (callback != null) {
                callback.onCancel();
            }
            dismiss();
        });

        binding.iconMicrophone.setOnClickListener(v -> {
            if (callback != null) {
                callback.onMicClick();
            }
        });

        // Thiết lập text input actions
        binding.layoutTextInput.setEndIconOnClickListener(v -> submitTextQuery());
        binding.editTextInputQuery.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEND) {
                submitTextQuery();
                return true;
            }
            return false;
        });
        
        // Bắt đầu idle animation
        binding.waveAnimation.startIdleAnimation();
    }

    private void submitTextQuery() {
        if (binding == null) return;
        android.text.Editable editable = binding.editTextInputQuery.getText();
        if (editable != null) {
            String query = editable.toString().trim();
            if (!query.isEmpty()) {
                // Xóa sạch text input
                binding.editTextInputQuery.setText("");
                
                // Ẩn keyboard
                android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) 
                        getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.hideSoftInputFromWindow(binding.editTextInputQuery.getWindowToken(), 0);
                }
                
                // Invoke callback
                if (callback != null) {
                    callback.onTextSubmit(query);
                }
            }
        }
    }

    /**
     * Hiển thị trạng thái đang lắng nghe
     */
    public void showListening() {
        if (binding != null) {
            binding.textStatus.setText("Đang lắng nghe...");
            binding.textRecognized.setText("");
            binding.iconMicrophone.setImageResource(vn.medisense.app.R.drawable.ic_mic);
            if (binding.textDisclaimer != null) {
                binding.textDisclaimer.setVisibility(android.view.View.GONE);
            }
        }
    }

    /**
     * Hiển thị văn bản đang nhận dạng
     */
    public void showPartialResult(String text) {
        if (binding != null) {
            binding.textRecognized.setText(text);
        }
    }

    /**
     * Hiển thị văn bản đã nhận dạng
     */
    public void showRecognizedText(String text) {
        if (binding != null) {
            binding.textStatus.setText("Đã nhận dạng");
            binding.textRecognized.setText(text);
            if (binding.textDisclaimer != null) {
                binding.textDisclaimer.setVisibility(android.view.View.GONE);
            }
        }
    }

    /**
     * Hiển thị trạng thái đang xử lý
     */
    public void showProcessing() {
        if (binding != null) {
            binding.textStatus.setText("Đang xử lý...");
            binding.waveAnimation.reset();
            if (binding.textDisclaimer != null) {
                binding.textDisclaimer.setVisibility(android.view.View.GONE);
            }
        }
    }

    /**
     * Hiển thị phản hồi
     */
    public void showResponse(String response) {
        if (binding != null) {
            binding.textStatus.setText("Trợ lý");
            binding.textRecognized.setText(response);
            binding.iconMicrophone.setImageResource(vn.medisense.app.R.drawable.ic_mic);
            if (binding.textDisclaimer != null) {
                binding.textDisclaimer.setVisibility(android.view.View.VISIBLE);
            }
        }
    }

    /**
     * Hiển thị lỗi
     */
    public void showError(String error) {
        if (binding != null) {
            binding.textStatus.setText("Lỗi");
            binding.textRecognized.setText(error);
            binding.iconMicrophone.setImageResource(vn.medisense.app.R.drawable.ic_mic);
            if (binding.textDisclaimer != null) {
                binding.textDisclaimer.setVisibility(android.view.View.GONE);
            }
        }
    }

    /**
     * Cập nhật volume cho wave animation
     */
    public void updateVolume(float volume) {
        if (binding != null) {
            binding.waveAnimation.updateVolume(volume);
        }
    }

    /**
     * Callback interface
     */
    public interface VoiceDialogCallback {
        void onCancel();
        void onTextSubmit(String text);
        void onMicClick();
    }
}
