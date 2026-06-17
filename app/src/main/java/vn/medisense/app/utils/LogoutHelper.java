package vn.medisense.app.utils;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import com.google.firebase.auth.FirebaseAuth;

import vn.medisense.app.database.AppDatabase;
import vn.medisense.app.services.CaregiverSyncService;
import vn.medisense.app.services.MedicalIdService;
import vn.medisense.app.ui.SplashActivity;

public class LogoutHelper {

    public static void logout(Context context) {
        // Sign out from Firebase Auth
        FirebaseAuth.getInstance().signOut();

        // Stop CaregiverSyncService if running
        try {
            Intent caregiverIntent = new Intent(context, CaregiverSyncService.class);
            context.stopService(caregiverIntent);
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Stop MedicalIdService if running
        try {
            Intent medicalIdIntent = new Intent(context, MedicalIdService.class);
            context.stopService(medicalIdIntent);
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Cancel all notifications
        NotificationUtils.cancelAll(context);

        // Close AppDatabase instance
        AppDatabase.closeInstance();

        // Clear sensitive SharedPreferences (keep onboarded flag)
        SharedPreferences prefs = context.getSharedPreferences("MediSensePrefs", Context.MODE_PRIVATE);
        boolean onboarded = prefs.getBoolean("onboarded", false);
        SharedPreferences.Editor editor = prefs.edit();
        editor.clear();
        editor.putBoolean("onboarded", onboarded);
        editor.apply();

        // Clear ProfileManager preferences
        SharedPreferences profilePrefs = context.getSharedPreferences("MediSenseProfile", Context.MODE_PRIVATE);
        profilePrefs.edit().clear().apply();

        // Navigate to SplashActivity
        Intent intent = new Intent(context, SplashActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        context.startActivity(intent);
    }
}
