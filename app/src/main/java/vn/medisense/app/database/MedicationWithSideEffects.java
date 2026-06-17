package vn.medisense.app.database;

import androidx.room.Embedded;
import androidx.room.Relation;

import java.util.List;

/**
 * POJO defining the one-to-many relationship between a Medication and its SideEffectLogs.
 */
public class MedicationWithSideEffects {

    @Embedded
    public Medication medication;

    @Relation(
            parentColumn = "id", // Medication's primary key
            entityColumn = "medicationId" // SideEffectLog's foreign key
    )
    public List<SideEffectLog> sideEffectLogs;
}
