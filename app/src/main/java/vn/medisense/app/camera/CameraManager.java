package vn.medisense.app.camera;

import android.content.Context;
import android.net.Uri;
import androidx.annotation.NonNull;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;
import com.google.common.util.concurrent.ListenableFuture;

import java.io.File;
import java.util.concurrent.ExecutionException;

/**
 * Quản lý camera và chức năng chụp ảnh
 * Sử dụng CameraX API để xử lý camera lifecycle
 */
public class CameraManager {
    private final Context context;
    private ImageCapture imageCapture;
    private ProcessCameraProvider cameraProvider;

    /**
     * Interface callback để thông báo kết quả chụp ảnh
     */
    public interface CaptureCallback {
        void onCaptureSuccess(File imageFile);
        void onCaptureError(String errorMessage);
    }

    /**
     * Interface callback để thông báo kết quả khởi tạo camera
     */
    public interface CameraInitCallback {
        void onCameraInitSuccess();
        void onCameraInitError(String errorMessage);
    }

    public CameraManager(Context context) {
        this.context = context;
    }

    /**
     * Khởi tạo và bắt đầu camera
     * 
     * @param lifecycleOwner Lifecycle owner (thường là Activity)
     * @param previewView View để hiển thị preview camera
     * @param callback Callback thông báo kết quả
     */
    public void startCamera(
            @NonNull LifecycleOwner lifecycleOwner,
            @NonNull PreviewView previewView,
            @NonNull CameraInitCallback callback) {
        
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = 
            ProcessCameraProvider.getInstance(context);

        cameraProviderFuture.addListener(() -> {
            try {
                cameraProvider = cameraProviderFuture.get();
                bindCameraUseCases(lifecycleOwner, previewView);
                callback.onCameraInitSuccess();
            } catch (ExecutionException | InterruptedException e) {
                callback.onCameraInitError("Lỗi khởi tạo camera: " + e.getMessage());
            }
        }, ContextCompat.getMainExecutor(context));
    }

    /**
     * Bind các use case của camera (Preview và ImageCapture)
     */
    private void bindCameraUseCases(
            @NonNull LifecycleOwner lifecycleOwner,
            @NonNull PreviewView previewView) {
        
        // Tạo Preview use case
        Preview preview = new Preview.Builder().build();
        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        // Tạo ImageCapture use case
        imageCapture = new ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build();

        // Chọn camera sau
        CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;

        // Unbind tất cả trước khi bind lại
        if (cameraProvider != null) {
            cameraProvider.unbindAll();
            cameraProvider.bindToLifecycle(
                lifecycleOwner, 
                cameraSelector, 
                preview, 
                imageCapture
            );
        }
    }

    /**
     * Chụp ảnh và lưu vào file tạm
     * 
     * @param outputFile File để lưu ảnh
     * @param callback Callback thông báo kết quả
     */
    public void capturePhoto(@NonNull File outputFile, @NonNull CaptureCallback callback) {
        if (imageCapture == null) {
            callback.onCaptureError("Camera chưa được khởi tạo");
            return;
        }

        ImageCapture.OutputFileOptions outputOptions = 
            new ImageCapture.OutputFileOptions.Builder(outputFile).build();

        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(context),
            new ImageCapture.OnImageSavedCallback() {
                @Override
                public void onImageSaved(@NonNull ImageCapture.OutputFileResults results) {
                    callback.onCaptureSuccess(outputFile);
                }

                @Override
                public void onError(@NonNull ImageCaptureException exception) {
                    callback.onCaptureError("Lỗi chụp ảnh: " + exception.getMessage());
                }
            }
        );
    }

    /**
     * Giải phóng tài nguyên camera
     */
    public void shutdown() {
        if (cameraProvider != null) {
            cameraProvider.unbindAll();
        }
    }
}
