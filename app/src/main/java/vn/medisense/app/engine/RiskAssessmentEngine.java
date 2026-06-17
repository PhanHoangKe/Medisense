package vn.medisense.app.engine;

import android.content.Context;

import vn.medisense.app.database.AppDatabase;
import vn.medisense.app.database.MedicationDao;
import vn.medisense.app.database.VitalSign;
import vn.medisense.app.database.VitalSignDao;
import vn.medisense.app.models.RiskReport;

import java.util.List;

public class RiskAssessmentEngine {

    private final MedicationDao medicationDao;
    private final VitalSignDao vitalSignDao;

    public RiskAssessmentEngine(Context context) {
        AppDatabase db = AppDatabase.getInstance(context);
        this.medicationDao = db.medicationDao();
        this.vitalSignDao = db.vitalSignDao();
    }

    /**
     * Tính toán rủi ro sức khỏe hiện tại của người dùng.
     * Dùng query status mới (TAKEN/MISSED) thay vì isTaken boolean.
     */
    public RiskReport assessRiskSync() {
        long now = System.currentTimeMillis();
        long sevenDaysAgo = now - (7L * 24 * 60 * 60 * 1000);

        // 1. Tính adherence dùng status mới
        //    adherenceRate = TAKEN / (TAKEN + MISSED + SKIPPED)
        int takenCount   = medicationDao.getTakenCountSync(sevenDaysAgo, now);
        int missedCount  = medicationDao.getMissedCountSync(sevenDaysAgo, now);
        int skippedCount = medicationDao.getSkippedCountSync(sevenDaysAgo, now);
        int denominator  = takenCount + missedCount + skippedCount;

        double adherenceRate = denominator > 0
                ? (double) takenCount / denominator
                : 1.0;
        double missedRate = 1.0 - adherenceRate;

        // 2. Calculate Vital Sign Volatility (Heart Rate)
        List<VitalSign> heartRates = vitalSignDao.getVitalSignsByTypeAndTimeRange("heart_rate", sevenDaysAgo, now);
        double hrVolatility = calculateVolatility(heartRates);

        // 3. Rule-based assessment
        RiskReport.RiskLevel level = RiskReport.RiskLevel.LOW;
        String advice = "Sức khỏe của bạn đang ổn định. Hãy tiếp tục duy trì!";
        
        boolean isHighAdherenceRisk = missedRate > 0.20; // Bo thuoc > 20%
        boolean isHighVitalRisk = hrVolatility > 0.15;   // Bien thien nhip tim > 15%

        if (isHighAdherenceRisk && isHighVitalRisk) {
            level = RiskReport.RiskLevel.HIGH;
            advice = "Nguy cơ RẤT CAO: Bạn đã bỏ uống thuốc nhiều lần và nhịp tim có dấu hiệu bất ổn. Vui lòng liên hệ bác sĩ ngay!";
        } else if (isHighAdherenceRisk || isHighVitalRisk) {
            level = RiskReport.RiskLevel.MEDIUM;
            if (isHighAdherenceRisk) {
                advice = "Cần chú ý: Tỉ lệ bỏ thuốc của bạn khá cao (" + (int)(missedRate * 100) + "%). Hãy đặt báo thức nhắc nhở.";
            } else {
                advice = "Cần chú ý: Nhịp tim của bạn có sự biến thiên lớn. Hãy theo dõi thêm và nghỉ ngơi hợp lý.";
            }
        }

        String details = "Tuân thủ thuốc: " + (int)(adherenceRate * 100) + "%\n" +
                         "Biến thiên nhịp tim: " + (int)(hrVolatility * 100) + "%";

        return new RiskReport(level, advice, adherenceRate, details);
    }

    /**
     * Tinh toan do bien thien (Coefficient of Variation) cua cac chi so
     */
    private double calculateVolatility(List<VitalSign> vitals) {
        if (vitals == null || vitals.size() < 2) {
            return 0.0;
        }

        double sum = 0;
        for (VitalSign v : vitals) {
            sum += v.value;
        }
        double mean = sum / vitals.size();

        double varianceSum = 0;
        for (VitalSign v : vitals) {
            varianceSum += Math.pow(v.value - mean, 2);
        }
        double stdDev = Math.sqrt(varianceSum / vitals.size());

        // Coefficient of Variation (CV) = Standard Deviation / Mean
        if (mean == 0) return 0;
        return stdDev / mean;
    }
}
