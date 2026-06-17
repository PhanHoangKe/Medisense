package vn.medisense.app.utils;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.app.ActivityOptions;
import android.content.Intent;
import android.os.Build;
import android.transition.Explode;
import android.transition.Fade;
import android.transition.Slide;
import android.transition.Transition;
import android.transition.TransitionManager;
import android.view.Gravity;
import android.view.View;
import android.view.ViewAnimationUtils;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.recyclerview.widget.RecyclerView;

import vn.medisense.app.R;

/**
 * AnimationUtils - Comprehensive animation utilities for MediSense 2026
 * Handles shared element transitions, custom animations, and motion effects
 */
public class AnimationUtils {

    // Animation durations
    public static final int DURATION_SHORT = 200;
    public static final int DURATION_NORMAL = 300;
    public static final int DURATION_LONG = 400;
    public static final int DURATION_STAGGERED = 50;

    /**
     * Start activity with shared element transition
     * Usage: AnimationUtils.startWithSharedElement(activity, intent, view, "transitionName");
     */
    public static void startWithSharedElement(@NonNull Activity activity, 
                                               @NonNull Intent intent,
                                               @NonNull View sharedView,
                                               @NonNull String transitionName) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            sharedView.setTransitionName(transitionName);
            ActivityOptions options = ActivityOptions.makeSceneTransitionAnimation(
                    activity,
                    sharedView,
                    transitionName
            );
            activity.startActivity(intent, options.toBundle());
        } else {
            activity.startActivity(intent);
        }
    }

    /**
     * Apply slide transition to activity
     */
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public static void applySlideTransition(@NonNull Activity activity, int slideEdge) {
        Slide slide = new Slide(slideEdge);
        slide.setDuration(DURATION_NORMAL);
        slide.setInterpolator(new DecelerateInterpolator());
        activity.getWindow().setEnterTransition(slide);
        activity.getWindow().setExitTransition(slide);
    }

    /**
     * Apply fade transition
     */
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public static void applyFadeTransition(@NonNull Activity activity) {
        Fade fade = new Fade();
        fade.setDuration(DURATION_NORMAL);
        activity.getWindow().setEnterTransition(fade);
        activity.getWindow().setExitTransition(fade);
    }

    /**
     * Apply explode transition
     */
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public static void applyExplodeTransition(@NonNull Activity activity) {
        Explode explode = new Explode();
        explode.setDuration(DURATION_NORMAL);
        explode.setInterpolator(new DecelerateInterpolator());
        activity.getWindow().setEnterTransition(explode);
    }

    /**
     * Card spring animation on tap
     */
    public static void cardSpringAnimation(@NonNull View cardView) {
        AnimatorSet animatorSet = new AnimatorSet();

        ObjectAnimator scaleXDown = ObjectAnimator.ofFloat(cardView, "scaleX", 1.0f, 0.95f);
        ObjectAnimator scaleYDown = ObjectAnimator.ofFloat(cardView, "scaleY", 1.0f, 0.95f);
        scaleXDown.setDuration(100);
        scaleYDown.setDuration(100);

        ObjectAnimator scaleXUp = ObjectAnimator.ofFloat(cardView, "scaleX", 0.95f, 1.0f);
        ObjectAnimator scaleYUp = ObjectAnimator.ofFloat(cardView, "scaleY", 0.95f, 1.0f);
        scaleXUp.setDuration(300);
        scaleYUp.setDuration(300);
        scaleXUp.setInterpolator(new OvershootInterpolator());
        scaleYUp.setInterpolator(new OvershootInterpolator());

        animatorSet.play(scaleXDown).with(scaleYDown);
        animatorSet.play(scaleXUp).with(scaleYUp).after(scaleXDown);
        animatorSet.start();
    }

    /**
     * Button pulse animation
     */
    public static void buttonPulseAnimation(@NonNull View button) {
        AnimatorSet animatorSet = new AnimatorSet();

        ObjectAnimator scaleXDown = ObjectAnimator.ofFloat(button, "scaleX", 1.0f, 0.98f);
        ObjectAnimator scaleYDown = ObjectAnimator.ofFloat(button, "scaleY", 1.0f, 0.98f);
        scaleXDown.setDuration(150);
        scaleYDown.setDuration(150);

        ObjectAnimator scaleXUp = ObjectAnimator.ofFloat(button, "scaleX", 0.98f, 1.0f);
        ObjectAnimator scaleYUp = ObjectAnimator.ofFloat(button, "scaleY", 0.98f, 1.0f);
        scaleXUp.setDuration(200);
        scaleYUp.setDuration(200);
        scaleXUp.setInterpolator(new OvershootInterpolator());
        scaleYUp.setInterpolator(new OvershootInterpolator());

        animatorSet.play(scaleXDown).with(scaleYDown);
        animatorSet.play(scaleXUp).with(scaleYUp).after(scaleXDown);
        animatorSet.start();
    }

    /**
     * FAB rotation animation (for menu expand)
     */
    public static void fabRotationAnimation(@NonNull View fab, boolean expand) {
        ObjectAnimator rotation = ObjectAnimator.ofFloat(fab, "rotation",
                expand ? 0f : 45f, expand ? 45f : 0f);
        rotation.setDuration(DURATION_NORMAL);
        rotation.setInterpolator(new DecelerateInterpolator());
        rotation.start();
    }

    /**
     * Staggered fade-in animation for view list
     */
    public static void staggeredFadeIn(@NonNull ViewGroup container, long staggerDelay) {
        for (int i = 0; i < container.getChildCount(); i++) {
            View child = container.getChildAt(i);
            child.setAlpha(0f);
            child.setTranslationY(30f);

            child.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(DURATION_NORMAL)
                    .setStartDelay(i * staggerDelay)
                    .setInterpolator(new DecelerateInterpolator())
                    .start();
        }
    }

    /**
     * Circular reveal animation
     */
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public static void circularReveal(@NonNull View view, int centerX, int centerY) {
        int radius = Math.max(view.getWidth(), view.getHeight());
        Animator animator = ViewAnimationUtils.createCircularReveal(view, centerX, centerY, 0, radius);
        animator.setDuration(DURATION_LONG);
        animator.setInterpolator(new DecelerateInterpolator());
        view.setVisibility(View.VISIBLE);
        animator.start();
    }

    /**
     * Circular hide animation
     */
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public static void circularHide(@NonNull View view, int centerX, int centerY) {
        int radius = Math.max(view.getWidth(), view.getHeight());
        Animator animator = ViewAnimationUtils.createCircularReveal(view, centerX, centerY, radius, 0);
        animator.setDuration(DURATION_LONG);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                view.setVisibility(View.INVISIBLE);
            }
        });
        animator.start();
    }

    /**
     * Slide in animation
     */
    public static void slideIn(@NonNull View view, int direction) {
        Animation animation = android.view.animation.AnimationUtils.loadAnimation(view.getContext(),
                direction == Gravity.LEFT ? R.anim.slide_in_left : R.anim.slide_in_right);
        view.startAnimation(animation);
    }

    /**
     * Slide out animation
     */
    public static void slideOut(@NonNull View view, int direction) {
        Animation animation = android.view.animation.AnimationUtils.loadAnimation(view.getContext(),
                direction == Gravity.LEFT ? R.anim.slide_out_left : R.anim.slide_out_right);
        animation.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {}

            @Override
            public void onAnimationEnd(Animation animation) {
                view.setVisibility(View.GONE);
            }

            @Override
            public void onAnimationRepeat(Animation animation) {}
        });
        view.startAnimation(animation);
    }

    /**
     * Fade in animation
     */
    public static void fadeIn(@NonNull View view) {
        Animation animation = android.view.animation.AnimationUtils.loadAnimation(view.getContext(), R.anim.fade_in);
        view.setVisibility(View.VISIBLE);
        view.startAnimation(animation);
    }

    /**
     * Fade out animation
     */
    public static void fadeOut(@NonNull View view) {
        Animation animation = android.view.animation.AnimationUtils.loadAnimation(view.getContext(), R.anim.fade_out);
        animation.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {}

            @Override
            public void onAnimationEnd(Animation animation) {
                view.setVisibility(View.GONE);
            }

            @Override
            public void onAnimationRepeat(Animation animation) {}
        });
        view.startAnimation(animation);
    }

    /**
     * Apply item animator to RecyclerView
     */
    public static void applyStaggeredItemAnimator(@NonNull RecyclerView recyclerView) {
        recyclerView.setItemAnimator(new StaggeredItemAnimator());
    }

    /**
     * Shimmer effect setup (placeholder for ShimmerFrameLayout integration)
     */
    public static void showShimmer(@NonNull View shimmerView) {
        shimmerView.setVisibility(View.VISIBLE);
        Animation animation = android.view.animation.AnimationUtils.loadAnimation(shimmerView.getContext(), R.anim.shimmer_translate);
        shimmerView.startAnimation(animation);
    }

    public static void hideShimmer(@NonNull View shimmerView) {
        shimmerView.clearAnimation();
        shimmerView.setVisibility(View.GONE);
    }

}
