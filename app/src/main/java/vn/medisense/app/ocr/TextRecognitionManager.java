package vn.medisense.app.ocr;

import android.content.Context;
import android.net.Uri;
import androidx.annotation.NonNull;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.io.File;
import java.io.IOException;

/**
 * Quản lý việc nhận dạng văn bản từ ảnh sử dụng ML Kit
 */
public class TextRecognitionManager {
    private final Context context;
    private final TextRecognizer textRecognizer;

    /**
     * Interface callback để trả kết quả nhận dạng văn bản
     */
    public interface RecognitionCallback {
        void onRecognitionSuccess(@NonNull String extractedText);
        void onRecognitionError(@NonNull String errorMessage);
    }

    public TextRecognitionManager(Context context) {
        this.context = context;
        this.textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
    }

    /**
     * Nhận dạng văn bản từ file ảnh
     * 
     * @param imageFile File ảnh cần nhận dạng
     * @param callback Callback trả kết quả
     */
    public void recognizeTextFromFile(@NonNull File imageFile, @NonNull RecognitionCallback callback) {
        try {
            InputImage image = InputImage.fromFilePath(context, Uri.fromFile(imageFile));
            processImage(image, callback);
        } catch (IOException e) {
            callback.onRecognitionError("Lỗi đọc file ảnh: " + e.getMessage());
        }
    }

    /**
     * Nhận dạng văn bản từ URI ảnh
     * 
     * @param imageUri URI của ảnh (từ gallery)
     * @param callback Callback trả kết quả
     */
    public void recognizeTextFromUri(@NonNull Uri imageUri, @NonNull RecognitionCallback callback) {
        try {
            InputImage image = InputImage.fromFilePath(context, imageUri);
            processImage(image, callback);
        } catch (IOException e) {
            callback.onRecognitionError("Lỗi đọc ảnh từ URI: " + e.getMessage());
        }
    }

    /**
     * Xử lý ảnh và nhận dạng văn bản
     */
    private void processImage(@NonNull InputImage image, @NonNull RecognitionCallback callback) {
        textRecognizer.process(image)
            .addOnSuccessListener(visionText -> {
                String extractedText = visionText.getText();
                if (extractedText != null && !extractedText.trim().isEmpty()) {
                    callback.onRecognitionSuccess(extractedText);
                } else {
                    callback.onRecognitionError("Không tìm thấy văn bản trong ảnh");
                }
            })
            .addOnFailureListener(e -> {
                callback.onRecognitionError("Lỗi nhận dạng văn bản: " + e.getMessage());
            });
    }

    /**
     * Giải phóng tài nguyên
     */
    public void close() {
        if (textRecognizer != null) {
            textRecognizer.close();
        }
    }
}
