package vn.medisense.app.ui;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import vn.medisense.app.R;

/**
 * Activity chứa HealthTrackerFragment
 */
public class HealthTrackerActivity extends AppCompatActivity {
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_health_tracker);
        
        // Set up toolbar
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Theo dõi Sức khỏe");
        }
        
        // Load fragment
        if (savedInstanceState == null) {
            getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragmentContainer, new HealthTrackerFragment())
                .commit();
        }
    }
    
    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}
