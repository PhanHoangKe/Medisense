package vn.medisense.app.database;

import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * Entity for the Side Effect Journal, storing how a user felt after taking a medication.
 */
@Entity(tableName = "side_effect_logs",
        foreignKeys = @ForeignKey(entity = Medication.class,
                parentColumns = "id",
                childColumns = "medicationId",
                onDelete = ForeignKey.CASCADE),
        indices = {@Index("medicationId")})
public class SideEffectLog {

    @PrimaryKey(autoGenerate = true)
    public int logId;

    public int medicationId; // Foreign key to Medication

    public long timestamp; // Khi log này được ghi lại

    public int moodRating; // 1 (Very Bad) to 5 (Very Good)

    public String symptoms; // Comma-separated list of symptoms (e.g., "Buồn nôn,Chóng mặt")

    public String note; // Free-text note
}
