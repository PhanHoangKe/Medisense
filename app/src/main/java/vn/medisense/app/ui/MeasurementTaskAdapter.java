package vn.medisense.app.ui;

import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import vn.medisense.app.database.MeasurementTask;

public class MeasurementTaskAdapter extends RecyclerView.Adapter<MeasurementTaskAdapter.ViewHolder> {

    private final List<MeasurementTask> tasks;
    private final OnTaskDeleteListener deleteListener;

    public interface OnTaskDeleteListener {
        void onDelete(MeasurementTask task);
    }

    public MeasurementTaskAdapter(List<MeasurementTask> tasks, OnTaskDeleteListener deleteListener) {
        this.tasks = tasks;
        this.deleteListener = deleteListener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        android.content.Context context = parent.getContext();
        android.util.TypedValue tvOnSurface = new android.util.TypedValue();
        android.util.TypedValue tvOnSurfaceVariant = new android.util.TypedValue();
        context.getTheme().resolveAttribute(com.google.android.material.R.attr.colorOnSurface, tvOnSurface, true);
        context.getTheme().resolveAttribute(com.google.android.material.R.attr.colorOnSurfaceVariant, tvOnSurfaceVariant, true);
        int colorOnSurface = tvOnSurface.data;
        int colorOnSurfaceVariant = tvOnSurfaceVariant.data;

        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, 12, 0, 12);

        // Left alarm icon
        android.widget.ImageView iv = new android.widget.ImageView(context);
        iv.setImageResource(vn.medisense.app.R.drawable.ic_alarm);
        iv.setColorFilter(colorOnSurfaceVariant);
        int iconSize = (int) android.util.TypedValue.applyDimension(
                android.util.TypedValue.COMPLEX_UNIT_DIP, 18, context.getResources().getDisplayMetrics());
        LinearLayout.LayoutParams ivLp = new LinearLayout.LayoutParams(iconSize, iconSize);
        ivLp.rightMargin = (int) android.util.TypedValue.applyDimension(
                android.util.TypedValue.COMPLEX_UNIT_DIP, 12, context.getResources().getDisplayMetrics());
        iv.setLayoutParams(ivLp);
        row.addView(iv);

        TextView tv = new TextView(context);
        tv.setId(android.R.id.text1);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        tv.setLayoutParams(lp);
        tv.setTextSize(14f);
        tv.setTextColor(colorOnSurface);
        try {
            tv.setTypeface(androidx.core.content.res.ResourcesCompat.getFont(context, vn.medisense.app.R.font.inter));
        } catch (Exception e) {
            // Fallback đến default sans-serif
        }
        row.addView(tv);

        ImageButton btn = new ImageButton(context);
        btn.setId(android.R.id.button1);
        btn.setImageResource(vn.medisense.app.R.drawable.ic_delete);
        btn.setColorFilter(colorOnSurfaceVariant);
        btn.setBackground(null);
        row.addView(btn);

        row.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        return new ViewHolder(row);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        MeasurementTask task = tasks.get(position);
        long hours = task.timeOfDay / (60 * 60 * 1000L);
        long mins = (task.timeOfDay % (60 * 60 * 1000L)) / (60 * 1000L);
        String label = task.title + String.format(" (%02d:%02d)", hours, mins);

        holder.tv.setText(label);
        holder.btn.setOnClickListener(v -> {
            if (deleteListener != null) {
                deleteListener.onDelete(task);
            }
        });
    }

    @Override
    public int getItemCount() {
        return tasks.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tv;
        ImageButton btn;

        ViewHolder(@NonNull LinearLayout view) {
            super(view);
            tv = view.findViewById(android.R.id.text1);
            btn = view.findViewById(android.R.id.button1);
        }
    }
}
