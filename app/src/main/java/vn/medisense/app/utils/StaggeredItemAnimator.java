package vn.medisense.app.utils;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

/**
 * StaggeredItemAnimator - Custom RecyclerView ItemAnimator for MediSense 2026
 * Provides staggered fade-in, scale-in, and spring animations for list items
 */
public class StaggeredItemAnimator extends DefaultItemAnimator {

    private static final long STAGGER_DELAY = 50L;
    private int pendingAdditions = 0;

    @Override
    public boolean animateAdd(RecyclerView.ViewHolder holder) {
        // Reset trạng thái trước hoạt ảnh
        holder.itemView.setAlpha(0f);
        holder.itemView.setScaleX(0.8f);
        holder.itemView.setScaleY(0.8f);
        holder.itemView.setTranslationY(30f);
        
        pendingAdditions++;
        
        // Tính toán độ trễ phân tầng dựa trên vị trí
        long delay = pendingAdditions * STAGGER_DELAY;
        
        // Combined scale + fade + translate animation
        AnimatorSet animatorSet = new AnimatorSet();
        
        ObjectAnimator alphaAnim = ObjectAnimator.ofFloat(holder.itemView, "alpha", 0f, 1f);
        alphaAnim.setDuration(300);
        
        ObjectAnimator scaleXAnim = ObjectAnimator.ofFloat(holder.itemView, "scaleX", 0.8f, 1f);
        scaleXAnim.setDuration(300);
        scaleXAnim.setInterpolator(new OvershootInterpolator(1.5f));
        
        ObjectAnimator scaleYAnim = ObjectAnimator.ofFloat(holder.itemView, "scaleY", 0.8f, 1f);
        scaleYAnim.setDuration(300);
        scaleYAnim.setInterpolator(new OvershootInterpolator(1.5f));
        
        ObjectAnimator translateYAnim = ObjectAnimator.ofFloat(holder.itemView, "translationY", 30f, 0f);
        translateYAnim.setDuration(300);
        translateYAnim.setInterpolator(new DecelerateInterpolator());
        
        animatorSet.playTogether(alphaAnim, scaleXAnim, scaleYAnim, translateYAnim);
        animatorSet.setStartDelay(delay);
        animatorSet.setDuration(300);
        
        animatorSet.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animator) {
                dispatchAddStarting(holder);
            }
            
            @Override
            public void onAnimationEnd(Animator animator) {
                pendingAdditions--;
                dispatchAddFinished(holder);
            }
            
            @Override
            public void onAnimationCancel(Animator animation) {
                pendingAdditions--;
                holder.itemView.setAlpha(1f);
                holder.itemView.setScaleX(1f);
                holder.itemView.setScaleY(1f);
                holder.itemView.setTranslationY(0f);
            }
        });
        
        animatorSet.start();
        return true;
    }

    @Override
    public boolean animateRemove(RecyclerView.ViewHolder holder) {
        // Trượt sang trái với mờ dần
        ObjectAnimator translateX = ObjectAnimator.ofFloat(holder.itemView, "translationX", 0f, -holder.itemView.getWidth());
        ObjectAnimator alpha = ObjectAnimator.ofFloat(holder.itemView, "alpha", 1f, 0f);
        
        AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.playTogether(translateX, alpha);
        animatorSet.setDuration(300);
        animatorSet.setInterpolator(new DecelerateInterpolator(1.5f));
        
        animatorSet.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animator) {
                dispatchRemoveStarting(holder);
            }
            
            @Override
            public void onAnimationEnd(Animator animator) {
                holder.itemView.setTranslationX(0f);
                dispatchRemoveFinished(holder);
            }
        });
        
        animatorSet.start();
        return true;
    }

    @Override
    public boolean animateMove(RecyclerView.ViewHolder holder, int fromX, int fromY, int toX, int toY) {
        if (fromX == toX && fromY == toY) {
            dispatchMoveFinished(holder);
            return false;
        }
        
        holder.itemView.setTranslationX(fromX);
        holder.itemView.setTranslationY(fromY);
        
        AnimatorSet animatorSet = new AnimatorSet();
        ObjectAnimator translateX = ObjectAnimator.ofFloat(holder.itemView, "translationX", fromX, toX);
        ObjectAnimator translateY = ObjectAnimator.ofFloat(holder.itemView, "translationY", fromY, toY);
        
        animatorSet.playTogether(translateX, translateY);
        animatorSet.setDuration(200);
        animatorSet.setInterpolator(new DecelerateInterpolator());
        
        animatorSet.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animator) {
                dispatchMoveStarting(holder);
            }
            
            @Override
            public void onAnimationEnd(Animator animator) {
                holder.itemView.setTranslationX(0f);
                holder.itemView.setTranslationY(0f);
                dispatchMoveFinished(holder);
            }
        });
        
        animatorSet.start();
        return true;
    }

    @Override
    public boolean animateChange(RecyclerView.ViewHolder oldHolder, RecyclerView.ViewHolder newHolder, int fromX, int fromY, int toX, int toY) {
        if (oldHolder == newHolder) {
            // Cùng một view holder - hoạt ảnh thay đổi giới hạn
            return animateMove(oldHolder, fromX, fromY, toX, toY);
        }
        
        // Mờ dần cái cũ, hiện dần cái mới
        ObjectAnimator oldAlpha = ObjectAnimator.ofFloat(oldHolder.itemView, "alpha", 1f, 0f);
        oldAlpha.setDuration(150);
        
        ObjectAnimator newAlpha = ObjectAnimator.ofFloat(newHolder.itemView, "alpha", 0f, 1f);
        newAlpha.setDuration(150);
        
        AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.playSequentially(oldAlpha, newAlpha);
        
        animatorSet.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animator) {
                dispatchChangeStarting(oldHolder, true);
                dispatchChangeStarting(newHolder, false);
            }
            
            @Override
            public void onAnimationEnd(Animator animator) {
                oldHolder.itemView.setAlpha(1f);
                dispatchChangeFinished(oldHolder, true);
                dispatchChangeFinished(newHolder, false);
            }
        });
        
        animatorSet.start();
        return true;
    }

    @Override
    public void runPendingAnimations() {
        super.runPendingAnimations();
    }

    @Override
    public void endAnimation(RecyclerView.ViewHolder item) {
        super.endAnimation(item);
    }

    @Override
    public void endAnimations() {
        super.endAnimations();
        pendingAdditions = 0;
    }

    @Override
    public boolean isRunning() {
        return super.isRunning();
    }

    /**
     * Simple AnimatorSet wrapper for chaining
     */
    private static class AnimatorSet {
        private final List<Animator> animators = new ArrayList<>();
        private long startDelay = 0;
        private long duration = 300;
        private final List<Animator.AnimatorListener> listeners = new ArrayList<>();

        public void playTogether(ObjectAnimator... animators) {
            for (ObjectAnimator animator : animators) {
                this.animators.add(animator);
            }
        }

        public void playSequentially(ObjectAnimator... animators) {
            long currentDelay = 0;
            for (ObjectAnimator animator : animators) {
                animator.setStartDelay(currentDelay);
                currentDelay += animator.getDuration();
                this.animators.add(animator);
            }
        }

        public void setStartDelay(long delay) {
            this.startDelay = delay;
        }

        public void setDuration(long duration) {
            this.duration = duration;
        }

        public void setInterpolator(android.view.animation.Interpolator interpolator) {
            for (Animator animator : animators) {
                if (animator instanceof ObjectAnimator) {
                    ((ObjectAnimator) animator).setInterpolator(interpolator);
                }
            }
        }

        public void addListener(Animator.AnimatorListener listener) {
            listeners.add(listener);
        }

        public void start() {
            for (Animator animator : animators) {
                animator.setStartDelay(startDelay + animator.getStartDelay());
                if (duration > 0) {
                    animator.setDuration(duration);
                }
                
                for (Animator.AnimatorListener listener : listeners) {
                    animator.addListener(listener);
                }
                
                animator.start();
            }
        }
    }
}
