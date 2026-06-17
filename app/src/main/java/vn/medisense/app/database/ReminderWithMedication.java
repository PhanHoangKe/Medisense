package vn.medisense.app.database;

import androidx.room.Embedded;
import androidx.room.Relation;

public class ReminderWithMedication {
    @Embedded
    public Reminder reminder;

    @Relation(parentColumn = "medicationId", entityColumn = "id")
    public Medication medication;
}

