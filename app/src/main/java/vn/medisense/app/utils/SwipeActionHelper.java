package vn.medisense.app.utils;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import vn.medisense.app.database.ReminderWithMedication;
import vn.medisense.app.ui.ReminderAdapter;

public class SwipeActionHelper {

    public interface SwipeActionListener {
        void onSwipeRight(ReminderWithMedication item, int position);
        void onSwipeLeft(ReminderWithMedication item, int position);
    }

    public static void attach(Context context, RecyclerView recyclerView, ReminderAdapter adapter, SwipeActionListener listener) {
        ItemTouchHelper itemTouchHelper = new ItemTouchHelper(
                new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT) {

                    @Override
                    public boolean onMove(
                            @NonNull RecyclerView recyclerView,
                            @NonNull RecyclerView.ViewHolder viewHolder,
                            @NonNull RecyclerView.ViewHolder target) {
                        return false;
                    }

                    @Override
                    public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                        int position = viewHolder.getAdapterPosition();
                        ReminderWithMedication item = adapter.getCurrentList().get(position);

                        if (direction == ItemTouchHelper.RIGHT) {
                            // Vuốt sang phải: Đánh dấu đã uống
                            listener.onSwipeRight(item, position);
                        } else if (direction == ItemTouchHelper.LEFT) {
                            // Vuốt sang trái: Chỉnh sửa / Menu ngữ cảnh
                            listener.onSwipeLeft(item, position);
                        }
                    }

                    @Override
                    public void onChildDraw(@NonNull Canvas c,
                                            @NonNull RecyclerView recyclerView,
                                            @NonNull RecyclerView.ViewHolder viewHolder,
                                            float dX, float dY, int actionState, boolean isCurrentlyActive) {
                        if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE) {
                            View itemView = viewHolder.itemView;
                            Paint p = new Paint();
                            if (dX > 0) {
                                // Nền vuốt sang phải (Xanh lá)
                                p.setColor(ContextCompat.getColor(context, android.R.color.holo_green_light));
                                c.drawRect((float) itemView.getLeft(), (float) itemView.getTop(),
                                        itemView.getLeft() + dX, (float) itemView.getBottom(), p);
                            } else if (dX < 0) {
                                // Nền vuốt sang trái (Cam)
                                p.setColor(ContextCompat.getColor(context, android.R.color.holo_orange_light));
                                c.drawRect((float) itemView.getRight() + dX, (float) itemView.getTop(),
                                        (float) itemView.getRight(), (float) itemView.getBottom(), p);
                            }
                        }
                        super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);
                    }
                });
        
        itemTouchHelper.attachToRecyclerView(recyclerView);
    }
}
