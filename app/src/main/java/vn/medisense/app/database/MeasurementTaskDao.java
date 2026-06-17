package vn.medisense.app.database;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface MeasurementTaskDao {
    @Insert
    long insertTask(MeasurementTask task);

    @Update
    void updateTask(MeasurementTask task);

    @Delete
    void deleteTask(MeasurementTask task);

    @Query("SELECT * FROM measurement_tasks WHERE id = :taskId")
    MeasurementTask getTaskById(int taskId);

    @Query("SELECT * FROM measurement_tasks ORDER BY timeOfDay ASC")
    LiveData<List<MeasurementTask>> getAllTasks();

    @Query("SELECT * FROM measurement_tasks WHERE isActive = 1 ORDER BY timeOfDay ASC")
    List<MeasurementTask> getActiveTasksSync();
}
