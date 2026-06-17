package vn.medisense.app.utils;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.media.ToneGenerator;
import android.net.Uri;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.util.Log;

import androidx.core.content.ContextCompat;

public class AudioResourceHelper {
    private static final String TAG = "AudioResourceHelper";
    private static MediaPlayer mediaPlayer;
    private static Vibrator vibrator;

    public static void startAlarmSound(Context context) {
        stopAlarmSound(); // Đảm bảo không có âm thanh chồng chéo

        try {
            Uri alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
            if (alarmUri == null) {
                alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            }

            mediaPlayer = new MediaPlayer();
            mediaPlayer.setDataSource(context, alarmUri);

            // Khối quan trọng để bỏ qua âm lượng "media" và phát to qua luồng báo thức
            AudioAttributes attributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build();
            mediaPlayer.setAudioAttributes(attributes);

            // Lặp âm thanh cho đến khi người dùng hành động
            mediaPlayer.setLooping(true);
            mediaPlayer.prepare();
            mediaPlayer.start();
            Log.d(TAG, "Started critical ALARM ringing.");

            startVibration(context);
        } catch (Exception e) {
            Log.e(TAG, "Failed to start alarm sound: " + e.getMessage());
        }
    }

    public static void stopAlarmSound() {
        if (mediaPlayer != null) {
            try {
                if (mediaPlayer.isPlaying()) {
                    mediaPlayer.stop();
                }
                mediaPlayer.release();
                Log.d(TAG, "Stopped critical ALARM ringing.");
            } catch (Exception e) {
                Log.e(TAG, "Failed to stop alarm sound: " + e.getMessage());
            } finally {
                mediaPlayer = null;
            }
        }
        stopVibration();
    }

    private static void startVibration(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            VibratorManager vibratorManager = (VibratorManager) context
                    .getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
            if (vibratorManager != null) {
                vibrator = vibratorManager.getDefaultVibrator();
            }
        } else {
            vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        }

        if (vibrator != null && vibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // Vibrate for 1s, pause for 1s, repeat indefinitely
                long[] pattern = { 0, 1000, 1000 };
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, 0));
            } else {
                long[] pattern = { 0, 1000, 1000 };
                vibrator.vibrate(pattern, 0);
            }
        }
    }

    private static void stopVibration() {
        if (vibrator != null) {
            vibrator.cancel();
            vibrator = null;
        }
    }

    /**
     * Play a short, loud beep for nagging notifications.
     */
    public static void playShortBeep(Context context) {
        try {
            ToneGenerator toneG = new ToneGenerator(AudioManager.STREAM_ALARM, 100);
            toneG.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 500); 
            // 500ms tone duration

            // Thêm rung ngắn
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                VibratorManager vibratorManager = (VibratorManager) context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
                if (vibratorManager != null) {
                    Vibrator v = vibratorManager.getDefaultVibrator();
                    v.vibrate(VibrationEffect.createOneShot(300, VibrationEffect.DEFAULT_AMPLITUDE));
                }
            } else {
                Vibrator v = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
                if (v != null) {
                    v.vibrate(300);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to play short beep: " + e.getMessage());
        }
    }
}
