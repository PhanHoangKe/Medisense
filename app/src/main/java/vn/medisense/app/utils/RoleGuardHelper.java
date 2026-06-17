package vn.medisense.app.utils;

import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import vn.medisense.app.ui.LoginActivity;
import vn.medisense.app.MainActivity;

public class RoleGuardHelper {

    public interface RoleCheckCallback {
        void onRoleValid();
        void onRoleInvalid();
    }

    public static void checkUserRole(Context context, String requiredRole, RoleCheckCallback callback) {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            // Not logged in, go to LoginActivity
            Intent intent = new Intent(context, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            context.startActivity(intent);
            if (callback != null) {
                callback.onRoleInvalid();
            }
            return;
        }

        // Check role from Firestore first, don't rely only on SharedPreferences
        FirebaseFirestore.getInstance()
                .collection("users")
                .document(currentUser.getUid())
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    String role = documentSnapshot.getString("role");
                    if (role != null && role.equals(requiredRole)) {
                        // Update SharedPreferences for consistency
                        context.getSharedPreferences("MediSensePrefs", Context.MODE_PRIVATE)
                                .edit()
                                .putString("userRole", role)
                                .apply();
                        if (callback != null) {
                            callback.onRoleValid();
                        }
                    } else {
                        // Role mismatch
                        Toast.makeText(context, "Bạn không có quyền truy cập màn hình này", Toast.LENGTH_LONG).show();
                        Intent intent = new Intent(context, MainActivity.class);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        context.startActivity(intent);
                        if (callback != null) {
                            callback.onRoleInvalid();
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    // Fallback to SharedPreferences if Firestore fails
                    String role = context.getSharedPreferences("MediSensePrefs", Context.MODE_PRIVATE)
                            .getString("userRole", null);
                    if (role != null && role.equals(requiredRole)) {
                        if (callback != null) {
                            callback.onRoleValid();
                        }
                    } else {
                        Toast.makeText(context, "Bạn không có quyền truy cập màn hình này", Toast.LENGTH_LONG).show();
                        Intent intent = new Intent(context, MainActivity.class);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        context.startActivity(intent);
                        if (callback != null) {
                            callback.onRoleInvalid();
                        }
                    }
                });
    }
}
