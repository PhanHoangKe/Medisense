package vn.medisense.app.ui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import vn.medisense.app.R;
import vn.medisense.app.database.Reminder;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * CaregiverLogAdapter — Hiển thị log uống thuốc cho người chăm sóc.
 * Hỗ trợ 5 trạng thái: TAKEN / MISSED / SKIPPED / SNOOZED / PENDING.
 * Tương thích ngược với dữ liệu cũ chỉ có isTaken boolean.
 */
public class CaregiverLogAdapter extends RecyclerView.Adapter<CaregiverLogAdapter.LogViewHolder> {

    private List<LogData> logs = new ArrayList<>();

    public void setLogs(List<LogData> newLogs) {
        this.logs = newLogs != null ? newLogs : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public LogViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_caregiver_log, parent, false);
        return new LogViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull LogViewHolder holder, int position) {
        LogData log = logs.get(position);
        Context ctx = holder.itemView.getContext();

        // ── Tên thuốc ────────────────────────────────────────────────────────
        holder.tvName.setText(log.name != null ? log.name : "Thuốc không xác định");

        // ── Thời gian ────────────────────────────────────────────────────────
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm - dd/MM/yyyy", Locale.getDefault());
        String timeStr = log.time > 0 ? sdf.format(new Date(log.time)) : "N/A";
        holder.tvTime.setText("Cập nhật lúc: " + timeStr);

        // ── Trạng thái (resolve từ status hoặc fallback isTaken) ─────────────
        String resolvedStatus = log.resolvedStatus();

        switch (resolvedStatus) {
            case Reminder.STATUS_TAKEN:
                holder.tvStatus.setText("✓ Đã uống");
                holder.tvStatus.setTextColor(ContextCompat.getColor(ctx, R.color.status_success));
                holder.imgStatus.setImageResource(R.drawable.ic_log_status_taken);
                holder.imgStatus.clearColorFilter();
                break;

            case Reminder.STATUS_MISSED:
                holder.tvStatus.setText("✗ Đã bỏ lỡ");
                holder.tvStatus.setTextColor(ContextCompat.getColor(ctx, R.color.status_error));
                holder.imgStatus.setImageResource(R.drawable.ic_log_status_missed);
                holder.imgStatus.clearColorFilter();
                break;

            case Reminder.STATUS_SKIPPED:
                holder.tvStatus.setText("⊘ Đã bỏ qua");
                holder.tvStatus.setTextColor(ContextCompat.getColor(ctx, R.color.status_warning));
                holder.imgStatus.setImageResource(R.drawable.ic_log_status_skipped);
                holder.imgStatus.clearColorFilter();
                break;

            case Reminder.STATUS_SNOOZED:
                holder.tvStatus.setText("⏰ Đã nhắc lại");
                holder.tvStatus.setTextColor(ContextCompat.getColor(ctx, R.color.status_info));
                holder.imgStatus.setImageResource(R.drawable.ic_log_status_snoozed);
                holder.imgStatus.clearColorFilter();
                break;

            default: // PENDING
                holder.tvStatus.setText("○ Chưa uống");
                holder.tvStatus.setTextColor(ContextCompat.getColor(ctx, R.color.medication_pending));
                holder.imgStatus.setImageResource(R.drawable.ic_log_status_pending);
                holder.imgStatus.clearColorFilter();
                break;
        }

        // ── Badge "Uống trễ" ─────────────────────────────────────────────────
        holder.tvLateBadge.setVisibility(log.isLate ? View.VISIBLE : View.GONE);

        // ── Lý do bỏ qua ─────────────────────────────────────────────────────
        if (log.skipReason != null && !log.skipReason.trim().isEmpty()) {
            holder.tvSkipReason.setText("Lý do: " + log.skipReason.trim());
            holder.tvSkipReason.setVisibility(View.VISIBLE);
        } else {
            holder.tvSkipReason.setVisibility(View.GONE);
        }

        // ── Tồn kho còn lại ──────────────────────────────────────────────────
        if (holder.tvStockRemaining != null) {
            if (log.stockRemaining >= 0) {
                holder.tvStockRemaining.setText("Tồn kho còn: " + log.stockRemaining);
                holder.tvStockRemaining.setVisibility(View.VISIBLE);
                // Cảnh báo nếu tồn kho thấp
                if (log.stockRemaining == 0) {
                    holder.tvStockRemaining.setTextColor(
                            ContextCompat.getColor(ctx, R.color.status_error));
                } else if (log.stockRemaining <= 5) {
                    holder.tvStockRemaining.setTextColor(
                            ContextCompat.getColor(ctx, R.color.status_warning));
                } else {
                    holder.tvStockRemaining.setTextColor(
                            ContextCompat.getColor(ctx, R.color.text_secondary));
                }
            } else {
                holder.tvStockRemaining.setVisibility(View.GONE);
            }
        }
    }

    @Override
    public int getItemCount() {
        return logs.size();
    }

    // ─── ViewHolder ───────────────────────────────────────────────────────────

    static class LogViewHolder extends RecyclerView.ViewHolder {
        TextView tvName, tvTime, tvStatus, tvLateBadge, tvSkipReason, tvStockRemaining;
        ImageView imgStatus;

        LogViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName           = itemView.findViewById(R.id.tvLogMedName);
            tvTime           = itemView.findViewById(R.id.tvLogTime);
            tvStatus         = itemView.findViewById(R.id.tvLogStatusText);
            tvLateBadge      = itemView.findViewById(R.id.tvLateBadge);
            tvSkipReason     = itemView.findViewById(R.id.tvSkipReason);
            imgStatus        = itemView.findViewById(R.id.imgStatus);
            // tvStockRemaining có thể null nếu layout cũ chưa có view này
            tvStockRemaining = itemView.findViewById(R.id.tvStockRemaining);
        }
    }
}
