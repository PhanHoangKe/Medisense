package vn.medisense.app.ui;

import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import vn.medisense.app.R;

public class PillImageAdapter extends RecyclerView.Adapter<PillImageAdapter.ViewHolder> {

    private final List<PillImageItem> items;
    private final SimpleDateFormat formatter = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());

    public PillImageAdapter(List<PillImageItem> items) {
        this.items = items;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(parent);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        PillImageItem item = items.get(position);
        Glide.with(holder.imageView.getContext())
                .load(item.url)
                .placeholder(android.R.drawable.ic_menu_report_image)
                .into(holder.imageView);
        holder.timeView.setText(formatter.format(new Date(item.timestamp)));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final ImageView imageView;
        final TextView timeView;

        ViewHolder(@NonNull ViewGroup parent) {
            super(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_pill_image, parent, false));
            imageView = itemView.findViewById(R.id.imagePill);
            timeView = itemView.findViewById(R.id.textPillTime);
        }
    }
}
