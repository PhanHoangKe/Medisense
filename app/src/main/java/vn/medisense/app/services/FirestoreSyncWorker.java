package vn.medisense.app.services;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreSettings;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import android.net.Uri;
import java.io.File;

import java.util.HashMap;
import java.util.Map;

public class FirestoreSyncWorker extends Worker {

    public FirestoreSyncWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        String syncType = getInputData().getString("syncType");
        if (syncType == null) syncType = "vital";
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        try {
            if ("vital".equals(syncType)) {
                String patientId = getInputData().getString("patientId");
                String type = getInputData().getString("type");
                float value1 = getInputData().getFloat("value1", 0f);
                float value2 = getInputData().getFloat("value2", 0f);
                long timestamp = getInputData().getLong("timestamp", 0L);
                String note = getInputData().getString("note");

                if (patientId == null || type == null || timestamp == 0L) {
                    return Result.failure();
                }

                Map<String, Object> vitalData = new HashMap<>();
                vitalData.put("patientId", patientId);
                vitalData.put("type", type);
                vitalData.put("value", value1);
                vitalData.put("value2", value2);
                vitalData.put("timestamp", timestamp);
                vitalData.put("note", note);

                com.google.android.gms.tasks.Tasks.await(
                    db.collection("VitalSigns").add(vitalData)
                );
            } else if ("image".equals(syncType)) {
                String medId = getInputData().getString("medId");
                String filePath = getInputData().getString("filePath");
                if (medId == null || filePath == null) {
                    return Result.failure();
                }
                // tải tập tin lên Firebase Storage
                com.google.firebase.storage.FirebaseStorage storage = com.google.firebase.storage.FirebaseStorage.getInstance();
                com.google.firebase.storage.StorageReference ref = storage.getReference()
                        .child("med_images/" + new File(filePath).getName());
                
                Uri downloadUri = com.google.android.gms.tasks.Tasks.await(
                    ref.putFile(Uri.fromFile(new File(filePath)))
                        .continueWithTask(task -> {
                            if (!task.isSuccessful()) {
                                throw task.getException();
                            }
                            return ref.getDownloadUrl();
                        })
                );

                String downloadUrl = downloadUri.toString();
                Map<String, Object> imageData = new HashMap<>();
                imageData.put("medId", medId);
                imageData.put("url", downloadUrl);
                imageData.put("timestamp", System.currentTimeMillis());
                
                com.google.android.gms.tasks.Tasks.await(
                    db.collection("PillImages").add(imageData)
                );
            }
            return Result.success();
        } catch (Exception e) {
            Log.e("FirestoreSyncWorker", "Lỗi đồng bộ", e);
            return Result.retry();
        }
    }
}
