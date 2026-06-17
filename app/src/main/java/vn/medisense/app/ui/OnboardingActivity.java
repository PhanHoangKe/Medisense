package vn.medisense.app.ui;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

import vn.medisense.app.MainActivity;
import vn.medisense.app.R;

public class OnboardingActivity extends AppCompatActivity {

    private ViewPager2 viewPager;
    private TabLayout tabLayout;
    private Button btnNext, btnSkip;
    private TextView tvLogin;

    private final int[] layouts = {
            R.layout.onboarding_slide1,
            R.layout.onboarding_slide2,
            R.layout.onboarding_slide3
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Kiểm tra xem đã onboarding chưa
        SharedPreferences prefs = getSharedPreferences("MediSensePrefs", MODE_PRIVATE);
        boolean onboarded = prefs.getBoolean("onboarded", false);
        if (onboarded) {
            startMainActivity();
            return;
        }

        setContentView(R.layout.activity_onboarding);

        viewPager = findViewById(R.id.viewPager);
        tabLayout = findViewById(R.id.tabLayout);
        btnNext = findViewById(R.id.btnNext);
        btnSkip = findViewById(R.id.btnSkip);
        tvLogin = findViewById(R.id.tvLogin);

        OnboardingAdapter adapter = new OnboardingAdapter();
        viewPager.setAdapter(adapter);

        new TabLayoutMediator(tabLayout, viewPager, (tab, position) -> {}).attach();

        btnNext.setOnClickListener(v -> {
            int current = viewPager.getCurrentItem();
            if (current < layouts.length - 1) {
                viewPager.setCurrentItem(current + 1);
            } else {
                finishOnboarding();
            }
        });

        btnSkip.setOnClickListener(v -> finishOnboarding());

        tvLogin.setOnClickListener(v -> {
        // Tùy chọn: Chuyển đến đăng nhập để đồng bộ tính năng
            startActivity(new Intent(this, LoginActivity.class));
        });

        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                if (position == layouts.length - 1) {
                    btnNext.setText("Bắt đầu");
                } else {
                    btnNext.setText("Tiếp theo");
                }
            }
        });
    }

    private void finishOnboarding() {
        SharedPreferences prefs = getSharedPreferences("MediSensePrefs", MODE_PRIVATE);
        prefs.edit().putBoolean("onboarded", true).apply();
        startMainActivity();
    }

    private void startMainActivity() {
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }

    private class OnboardingAdapter extends androidx.recyclerview.widget.RecyclerView.Adapter<OnboardingAdapter.ViewHolder> {

        @Override
        public ViewHolder onCreateViewHolder(android.view.ViewGroup parent, int viewType) {
            android.view.View view = getLayoutInflater().inflate(layouts[viewType], parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            // Các view là tĩnh trong layouts
        }

        @Override
        public int getItemCount() {
            return layouts.length;
        }

        @Override
        public int getItemViewType(int position) {
            return position;
        }

        class ViewHolder extends androidx.recyclerview.widget.RecyclerView.ViewHolder {
            ViewHolder(android.view.View itemView) {
                super(itemView);
            }
        }
    }
}
