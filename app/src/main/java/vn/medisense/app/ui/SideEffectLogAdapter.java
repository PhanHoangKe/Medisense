package vn.medisense.app.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import vn.medisense.app.R;
import vn.medisense.app.database.SideEffectLog;

/**
 * Adapter hiển thị lịch sử tác dụng phụ
 */
public class SideEffectLogAdapter extends RecyclerView.Adapter<SideEffectLogAdapter.ViewHolder> {
    
    private List<SideEffectLog> logs = new ArrayList<>();
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());

    public void setLogs(List<SideEffectLog> logs) {
        this.logs = logs;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_side_effect_log, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        SideEffectLog log = logs.get(position);
        
        // Hiển thị ngày giờ
        holder.tvLogDate.setText(dateFormat.format(new Date(log.timestamp)));
        
        // Hiển thị mood rating với emoji
        String moodEmoji = getMoodEmoji(log.moodRating);
        holder.tvMoodRating.setText(String.format("Tâm trạng: %s (%d/5)", moodEmoji, log.moodRating));
        
        // Hiển thị triệu chứng
        if (log.symptoms != null && !log.symptoms.trim().isEmpty()) {
            holder.tvSymptoms.setText(log.symptoms);
        } else {
            holder.tvSymptoms.setText("Không có triệu chứng");
        }
        
        // Hiển thị ghi chú (nếu có)
        if (log.note != null && !log.note.trim().isEmpty()) {
            holder.tvNote.setVisibility(View.VISIBLE);
            holder.tvNote.setText("Ghi chú: " + log.note);
        } else {
            holder.tvNote.setVisibility(View.GONE);
        }
    }

    @Override
    public int getItemCount() {
        return logs.size();
    }

    /**
     * Lấy emoji tương ứng với moodRating
     */
    private String getMoodEmoji(int rating) {
        switch (rating) {
            case 1: return "😫"; // Very Bad
            case 2: return "😞"; // Bad
            case 3: return "😐"; // Neutral
            case 4: return "🙂"; // Good
            case 5: return "😊"; // Very Good
            default: return "😐";
        }
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvLogDate;
        TextView tvMoodRating;
        TextView tvSymptoms;
        TextView tvNote;

        ViewHolder(View itemView) {
            super(itemView);
            tvLogDate = itemView.findViewById(R.id.tvLogDate);
            tvMoodRating = itemView.findViewById(R.id.tvMoodRating);
            tvSymptoms = itemView.findViewById(R.id.tvSymptoms);
            tvNote = itemView.findViewById(R.id.tvNote);
        }
    }
}
