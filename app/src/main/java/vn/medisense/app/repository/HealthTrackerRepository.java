package vn.medisense.app.repository;

import android.content.Context;

import java.util.List;

import vn.medisense.app.database.AppDatabase;
import vn.medisense.app.database.MeasurementTask;
import vn.medisense.app.database.MeasurementTaskDao;
import vn.medisense.app.database.Reminder;
import vn.medisense.app.database.Medication;
import vn.medisense.app.database.VitalSign;
import vn.medisense.app.database.VitalSignDao;
import vn.medisense.app.engine.RiskAssessmentEngine;
import vn.medisense.app.models.RiskReport;

public class HealthTrackerRepository {
    private final VitalSignDao vitalSignDao;
    private final MeasurementTaskDao measurementTaskDao;
    private final RiskAssessmentEngine riskEngine;
    private final AppDatabase db;

    public HealthTrackerRepository(Context context) {
        db = AppDatabase.getInstance(context);
        vitalSignDao = db.vitalSignDao();
        measurementTaskDao = db.measurementTaskDao();
        riskEngine = new RiskAssessmentEngine(context);
    }

    public androidx.lifecycle.LiveData<List<MeasurementTask>> getAllTasks() {
        return measurementTaskDao.getAllTasks();
    }

    public long insertMeasurementTask(MeasurementTask task) {
        return measurementTaskDao.insertTask(task);
    }

    public void deleteMeasurementTask(MeasurementTask task) {
        measurementTaskDao.deleteTask(task);
    }

    public void insertVitalSign(VitalSign vitalSign) {
        vitalSignDao.insertVitalSign(vitalSign);
    }

    public VitalSign getLatestVitalSign(String type) {
        return vitalSignDao.getLatestVitalSign(type);
    }

    public List<VitalSign> getVitalSignsByTypeAndTimeRange(String type, long startTime, long endTime) {
        return vitalSignDao.getVitalSignsByTypeAndTimeRange(type, startTime, endTime);
    }

    public List<VitalSign> getAllVitalSignsInRange(long startTime, long endTime) {
        return vitalSignDao.getAllVitalSignsInRange(startTime, endTime);
    }

    public List<Reminder> getRemindersBetweenSync(long startTime, long endTime) {
        return db.medicationDao().getRemindersBetweenSync(startTime, endTime);
    }

    public List<Medication> getAllMedicationsSync() {
        return db.medicationDao().getAllMedicationsSync();
    }

    public RiskReport assessRiskSync() {
        return riskEngine.assessRiskSync();
    }
}
