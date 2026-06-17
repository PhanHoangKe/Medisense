package vn.medisense.app.utils;

import android.content.Context;
import android.util.Log;

import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import vn.medisense.app.database.VitalSign;
import vn.medisense.app.services.FirestoreSyncWorker;

public class FirestoreSyncHelper {
        private static final String TAG = "FirestoreSyncHelper";

    public static void syncVitalSign(Context context, VitalSign vitalSign) {
                FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
                if (user == null) {
                        Log.w(TAG, "Chưa xác thực người dùng. Bỏ qua đồng bộ chỉ số sinh tồn (VitalSign).");
                        return;
                }

        Data inputData = new Data.Builder()
                                .putString("patientId", user.getUid())
                .putString("type", vitalSign.type)
                .putFloat("value1", vitalSign.value)
                .putFloat("value2", vitalSign.value2)
                .putLong("timestamp", vitalSign.timestamp)
                .putString("note", vitalSign.note)
                .build();

        // Ràng buộc: Có mạng mới thực sự push lên, còn không thì chờ
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();

        OneTimeWorkRequest syncRequest = new OneTimeWorkRequest.Builder(FirestoreSyncWorker.class)
                .setConstraints(constraints)
                .setInputData(inputData)
                .build();

        WorkManager.getInstance(context).enqueue(syncRequest);
    }
}
