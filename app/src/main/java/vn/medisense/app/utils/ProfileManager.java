package vn.medisense.app.utils;

import android.content.Context;
import android.content.SharedPreferences;

public class ProfileManager {

    private static final String PREF_NAME = "MediSenseProfile";
    private static final String KEY_BLOOD_TYPE = "blood_type";
    private static final String KEY_ALLERGIES = "allergies";
    private static final String KEY_MEDICATIONS = "medications";
    private static final String KEY_ICE_CONTACT = "ice_contact";
    private static final String KEY_SOS_ENABLED = "sos_enabled";

    // TODO: Use EncryptedSharedPreferences in production to protect sensitive medical data
    // Dependency: androidx.security:security-crypto:1.1.0-alpha06
    private final SharedPreferences prefs;

    public ProfileManager(Context context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public String getBloodType() {
        return prefs.getString(KEY_BLOOD_TYPE, "");
    }

    public void setBloodType(String bloodType) {
        prefs.edit().putString(KEY_BLOOD_TYPE, bloodType).apply();
    }

    public String getAllergies() {
        return prefs.getString(KEY_ALLERGIES, "");
    }

    public void setAllergies(String allergies) {
        prefs.edit().putString(KEY_ALLERGIES, allergies).apply();
    }

    public String getMedications() {
        return prefs.getString(KEY_MEDICATIONS, "");
    }

    public void setMedications(String medications) {
        prefs.edit().putString(KEY_MEDICATIONS, medications).apply();
    }

    public String getIceContact() {
        return prefs.getString(KEY_ICE_CONTACT, "");
    }

    public void setIceContact(String iceContact) {
        prefs.edit().putString(KEY_ICE_CONTACT, iceContact).apply();
    }

    public boolean isSosEnabled() {
        return prefs.getBoolean(KEY_SOS_ENABLED, false);
    }

    public void setSosEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_SOS_ENABLED, enabled).apply();
    }
}
