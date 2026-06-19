package com.xapps.media.xmusic.widget;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Build;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import androidx.activity.BackEventCompat;
import androidx.activity.ComponentActivity;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.dynamicanimation.animation.FloatValueHolder;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;
import com.google.android.material.shape.MaterialShapeDrawable;
import com.xapps.media.xmusic.models.ViewDragHelper;

public class ExpressiveSliderLayout extends FrameLayout {

    public interface SliderCallback {
        void onStateChanged(int state);
        void onSlide(float slideOffset);
    }

    public static final int STATE_COLLAPSED = 1;
    public static final int STATE_EXPANDED = 2;
    public static final int STATE_DRAGGING = 3;
    public static final int STATE_SETTLING = 4;
    public static final int STATE_HIDDEN = 5;

    private static final int DISMISS_THRESHOLD = 200;

    private ViewDragHelper dragHelper;
    private View sheetView;
    private SliderCallback sliderCallback;
    private MaterialShapeDrawable internalBackground;
    private SpringAnimation settleSpringAnim;
    
    private SpringAnimation jumpBoostAnim;
    private int currentJumpBoost = 0;
    private boolean hasTriggeredBoost = false;
    private int accumulatedFingerDrag = 0;

    private int collapsedTop;
    private int expandedTop;
    private int hiddenTop;
    private int currentState = STATE_HIDDEN;
    private int peekHeight = 250;
    private int systemBottomInset = 0;

    private int floatingBottomMargin = 0;
    private int floatingSideMargin = 0;
    private float cornerRadiusTopLeft = 0f;
    private float cornerRadiusTopRight = 0f;
    private float cornerRadiusBottomLeft = 0f;
    private float cornerRadiusBottomRight = 0f;
    private int sheetBackgroundColor = Color.parseColor("#1C1B1F");

    private final OnBackPressedCallback backCallback = new OnBackPressedCallback(false) {
        @RequiresApi(api = Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
        @Override
        public void handleOnBackStarted(@NonNull BackEventCompat backEvent) {}

        @RequiresApi(api = Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
        @Override
        public void handleOnBackProgressed(@NonNull BackEventCompat backEvent) {
            float progress = backEvent.getProgress();
            float slideOffset = Math.max(0f, 1f - progress);
            
            int range = collapsedTop - expandedTop;
            int targetTop = collapsedTop - (int) (range * slideOffset);
            
            int dy = targetTop - sheetView.getTop();
            ViewCompat.offsetTopAndBottom(sheetView, dy);
            
            applyExponentialMorph(slideOffset);
            
            if (sliderCallback != null) {
                sliderCallback.onSlide(slideOffset);
            }
        }

        @Override
        public void handleOnBackPressed() {
            setState(STATE_COLLAPSED);
        }

        @RequiresApi(api = Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
        @Override
        public void handleOnBackCancelled() {
            setState(STATE_EXPANDED);
        }
    };

    public ExpressiveSliderLayout(@NonNull Context context) {
        super(context);
        init();
    }

    public ExpressiveSliderLayout(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        dragHelper = ViewDragHelper.create(this, 1.0f, new DragCallback());
        internalBackground = new MaterialShapeDrawable();
        internalBackground.setFillColor(ColorStateList.valueOf(sheetBackgroundColor));
        ViewCompat.setElevation(this, 100f);
        setClickable(true);

        ViewCompat.setOnApplyWindowInsetsListener(this, (v, insets) -> {
            int newBottom = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom;
            if (systemBottomInset != newBottom) {
                systemBottomInset = newBottom;
                requestLayout();
            }
            return insets;
        });

        FloatValueHolder boostHolder = new FloatValueHolder(0f);
        jumpBoostAnim = new SpringAnimation(boostHolder);
        SpringForce boostForce = new SpringForce();
        boostForce.setStiffness(SpringForce.STIFFNESS_MEDIUM);
        boostForce.setDampingRatio(SpringForce.DAMPING_RATIO_NO_BOUNCY);
        jumpBoostAnim.setSpring(boostForce);

        jumpBoostAnim.addUpdateListener((animation, value, velocity) -> {
            if (sheetView != null && dragHelper.getViewDragState() == ViewDragHelper.STATE_DRAGGING) {
                int newBoost = Math.round(value);
                int dyBoost = newBoost - currentJumpBoost;
                currentJumpBoost = newBoost;

                int currentTop = sheetView.getTop();
                if (currentTop + dyBoost > hiddenTop) {
                    dyBoost = hiddenTop - currentTop;
                }
                
                if (dyBoost != 0) {
                    ViewCompat.offsetTopAndBottom(sheetView, dyBoost);
                    float progress = calculateSlideOffset(sheetView.getTop());
                    applyExponentialMorph(progress);
                    if (sliderCallback != null) {
                        sliderCallback.onSlide(progress);
                    }
                }
            }
        });
    }

    public void setupPredictiveBack(ComponentActivity activity) {
        activity.getOnBackPressedDispatcher().addCallback(activity, backCallback);
    }

    public OnBackPressedCallback getPredictiveBackCallback() {
        return backCallback;
    }

    public void setSliderCallback(SliderCallback callback) {
        this.sliderCallback = callback;
    }

    public void setPeekHeight(int peekHeight) {
        this.peekHeight = peekHeight;
        requestLayout();
    }

    public void setSheetBackgroundColor(int color) {
        this.sheetBackgroundColor = color;
        if (internalBackground != null) {
            internalBackground.setFillColor(ColorStateList.valueOf(color));
        }
    }

    public void setFloatingMargins(int sideMargin, int bottomMargin) {
        this.floatingSideMargin = sideMargin;
        this.floatingBottomMargin = bottomMargin;
        requestLayout();
    }

    public void setFloatingCornerRadii(float tl, float tr, float bl, float br) {
        this.cornerRadiusTopLeft = tl;
        this.cornerRadiusTopRight = tr;
        this.cornerRadiusBottomLeft = bl;
        this.cornerRadiusBottomRight = br;
        
        if (currentState == STATE_COLLAPSED) {
            applyExponentialMorph(0f);
        }
    }

    public void setState(int state) {
        if (this.currentState == state) return;
        
        if (sheetView == null || !ViewCompat.isLaidOut(this) || getHeight() == 0) {
            this.currentState = state;
            return;
        }

        int targetTop;
        if (state == STATE_EXPANDED) {
            targetTop = expandedTop;
        } else if (state == STATE_COLLAPSED) {
            targetTop = collapsedTop;
        } else if (state == STATE_HIDDEN) {
            targetTop = hiddenTop;
        } else {
            return;
        }

        dispatchState(STATE_SETTLING);
        settleWithSpring(targetTop, state, 0f);
    }

    private void settleWithSpring(int targetTop, int targetState, float initialVelocity) {
        if (settleSpringAnim != null && settleSpringAnim.isRunning()) {
            settleSpringAnim.cancel();
        }

        FloatValueHolder floatValueHolder = new FloatValueHolder(sheetView.getTop());
        settleSpringAnim = new SpringAnimation(floatValueHolder);

        settleSpringAnim.setStartVelocity(initialVelocity);

        SpringForce springForce = new SpringForce();
        springForce.setFinalPosition(targetTop);
        springForce.setDampingRatio(SpringForce.DAMPING_RATIO_NO_BOUNCY);
        springForce.setStiffness(SpringForce.STIFFNESS_MEDIUM);

        settleSpringAnim.setSpring(springForce);

        settleSpringAnim.addUpdateListener((animation, value, velocity) -> {
            int currentTop = sheetView.getTop();
            int dy = Math.round(value) - currentTop;
            ViewCompat.offsetTopAndBottom(sheetView, dy);
            
            float progress = calculateSlideOffset(sheetView.getTop());
            applyExponentialMorph(progress);
            
            if (sliderCallback != null) {
                sliderCallback.onSlide(progress);
            }
        });

        settleSpringAnim.addEndListener((animation, canceled, value, velocity) -> {
            if (!canceled) {
                dispatchState(targetState);
            }
        });

        settleSpringAnim.start();
    }

    private float calculateSlideOffset(int top) {
        if (top <= collapsedTop) {
            float range = collapsedTop - expandedTop;
            return range > 0 ? (float) (collapsedTop - top) / range : 0f;
        } else {
            float hideRange = hiddenTop - collapsedTop;
            return hideRange > 0 ? ((float) (collapsedTop - top) / hideRange) : 0f;
        }
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        if (getChildCount() > 0) {
            sheetView = getChildAt(0);
            sheetView.setBackground(internalBackground);
            sheetView.setClipToOutline(true);
            sheetView.setClickable(true);
        }
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        if (sheetView != null) {
            int actualPeekHeight = peekHeight + (floatingBottomMargin == 0 ? systemBottomInset : 0);
            int actualBottomMargin = floatingBottomMargin > 0 ? floatingBottomMargin + systemBottomInset : 0;
            
            expandedTop = 0;
            hiddenTop = getHeight();
            collapsedTop = hiddenTop - actualPeekHeight - actualBottomMargin;

            int currentTop;
            if (currentState == STATE_EXPANDED) currentTop = expandedTop;
            else if (currentState == STATE_HIDDEN) currentTop = hiddenTop;
            else currentTop = collapsedTop;

            sheetView.layout(left, currentTop, right, currentTop + sheetView.getMeasuredHeight());

            if (currentState == STATE_COLLAPSED) {
                applyExponentialMorph(0f);
            }
        }
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        boolean intercepted = dragHelper.shouldInterceptTouchEvent(ev);
        if (ev.getActionMasked() == MotionEvent.ACTION_DOWN) {
            if (dragHelper.isViewUnder(sheetView, (int) ev.getX(), (int) ev.getY())) {
                if (getParent() != null) {
                    getParent().requestDisallowInterceptTouchEvent(true);
                }
            }
        }
        return intercepted;
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        dragHelper.processTouchEvent(ev);
        return true;
    }

    private void dispatchState(int state) {
        if (currentState != state) {
            currentState = state;
            if (sliderCallback != null) {
                sliderCallback.onStateChanged(state);
            }
        }
    }

    private void applyExponentialMorph(float slideOffset) {
        if (sheetView == null) return;

        float safeOffset = Math.max(0f, Math.min(1f, slideOffset));
        float invertedOffset = 1.0f - safeOffset;
        float exponentialFactor = (float) Math.pow(invertedOffset, 3.0);

        if (internalBackground != null) {
            float currentTl = cornerRadiusTopLeft * exponentialFactor;
            float currentTr = cornerRadiusTopRight * exponentialFactor;
            float currentBl = cornerRadiusBottomLeft * exponentialFactor;
            float currentBr = cornerRadiusBottomRight * exponentialFactor;

            internalBackground.setShapeAppearanceModel(
                internalBackground.getShapeAppearanceModel().toBuilder()
                    .setTopLeftCornerSize(currentTl)
                    .setTopRightCornerSize(currentTr)
                    .setBottomLeftCornerSize(currentBl)
                    .setBottomRightCornerSize(currentBr)
                    .build()
            );
        }

        int parentWidth = getWidth();
        int parentHeight = getHeight();

        int currentLeft = (int) (floatingSideMargin * exponentialFactor);
        int currentRight = parentWidth - currentLeft;
        
        int currentTop = sheetView.getTop();
        int currentBottom;

        int actualBottomMargin = floatingBottomMargin > 0 ? floatingBottomMargin + systemBottomInset : 0;

        if (currentTop > collapsedTop) {
            int actualPeekHeight = peekHeight + (floatingBottomMargin == 0 ? systemBottomInset : 0);
            currentBottom = currentTop + actualPeekHeight;
        } else {
            currentBottom = parentHeight - (int) (actualBottomMargin * exponentialFactor);
        }

        sheetView.layout(currentLeft, currentTop, currentRight, currentBottom);
    }

    private class DragCallback extends ViewDragHelper.Callback {

        @Override
        public boolean tryCaptureView(@NonNull View child, int pointerId) {
            return child == sheetView;
        }

        @Override
        public void onViewPositionChanged(@NonNull View changedView, int left, int top, int dx, int dy) {
            float progress = calculateSlideOffset(top);
            applyExponentialMorph(progress);

            if (sliderCallback != null) {
                sliderCallback.onSlide(progress);
            }
        }

        @Override
        public void onViewDragStateChanged(int state) {
            if (state == ViewDragHelper.STATE_DRAGGING) {
                dispatchState(STATE_DRAGGING);
                if (settleSpringAnim != null && settleSpringAnim.isRunning()) {
                    settleSpringAnim.cancel();
                }
            }
        }

        @Override
        public void onViewReleased(@NonNull View releasedChild, float xvel, float yvel) {
            if (jumpBoostAnim != null && jumpBoostAnim.isRunning()) {
                jumpBoostAnim.cancel();
            }
            hasTriggeredBoost = false;
            currentJumpBoost = 0;

            int targetTop;
            int targetState;

            boolean passedThreshold = accumulatedFingerDrag >= DISMISS_THRESHOLD;
            accumulatedFingerDrag = 0;

            if (yvel < -500) {
                targetTop = expandedTop;
                targetState = STATE_EXPANDED;
            } else if (yvel > 500) {
                if (releasedChild.getTop() >= collapsedTop) {
                    if (passedThreshold) {
                        targetTop = hiddenTop;
                        targetState = STATE_HIDDEN;
                    } else {
                        targetTop = collapsedTop;
                        targetState = STATE_COLLAPSED;
                    }
                } else {
                    targetTop = collapsedTop;
                    targetState = STATE_COLLAPSED;
                }
            } else {
                if (releasedChild.getTop() > collapsedTop) {
                    if (passedThreshold) {
                        targetTop = hiddenTop;
                        targetState = STATE_HIDDEN;
                    } else {
                        targetTop = collapsedTop;
                        targetState = STATE_COLLAPSED;
                    }
                } else {
                    if (releasedChild.getTop() < (collapsedTop + expandedTop) / 2) {
                        targetTop = expandedTop;
                        targetState = STATE_EXPANDED;
                    } else {
                        targetTop = collapsedTop;
                        targetState = STATE_COLLAPSED;
                    }
                }
            }

            dispatchState(STATE_SETTLING);
            float maxVelocity = 3000f;
            float clampedVelocity = Math.max(-maxVelocity, Math.min(yvel, maxVelocity));
            settleWithSpring(targetTop, targetState, clampedVelocity);
        }

        @Override
        public int clampViewPositionVertical(@NonNull View child, int top, int dy) {
            int currentTop = child.getTop();
            
            if (top > collapsedTop) {
                if (dy < 0) {
                    if (hasTriggeredBoost) {
                        hasTriggeredBoost = false;
                        jumpBoostAnim.cancel();
                        currentJumpBoost = 0;
                    }
                    accumulatedFingerDrag += dy;
                    if (accumulatedFingerDrag < 0) accumulatedFingerDrag = 0;
                    return Math.min(top, hiddenTop);
                }

                if (currentTop <= collapsedTop) {
                    accumulatedFingerDrag = top - collapsedTop;
                } else {
                    accumulatedFingerDrag += dy;
                }
                
                if (accumulatedFingerDrag < DISMISS_THRESHOLD) {
                    if (hasTriggeredBoost) {
                        hasTriggeredBoost = false;
                        jumpBoostAnim.cancel();
                        currentJumpBoost = 0;
                    }
                    
                    float progress = (float) accumulatedFingerDrag / DISMISS_THRESHOLD;
                    float friction = 0.99f * (float) Math.pow(1.0f - progress, 5.0);
                    
                    int freeDy = 0;
                    int frictionDy = dy;
                    if (currentTop <= collapsedTop) {
                        freeDy = collapsedTop - currentTop;
                        frictionDy = top - collapsedTop;
                    }
                    
                    int delta = Math.round(frictionDy * friction);
                    if (frictionDy > 0 && delta == 0) delta = 1;
                    
                    return Math.min(currentTop + freeDy + delta, hiddenTop);
                } else {
                    if (!hasTriggeredBoost) {
                        hasTriggeredBoost = true;
                        currentJumpBoost = 0;
                        jumpBoostAnim.setStartValue(0f);
                        
                        int overdrag = currentTop - collapsedTop;
                        int catchUpAmount = Math.max(0, accumulatedFingerDrag - overdrag);
                        
                        jumpBoostAnim.getSpring().setFinalPosition((float) catchUpAmount);
                        jumpBoostAnim.start();
                    }
                    
                    int freeDy = 0;
                    int normalDy = dy;
                    if (currentTop <= collapsedTop) {
                        freeDy = collapsedTop - currentTop;
                        normalDy = top - collapsedTop;
                    }
                    
                    return Math.min(currentTop + freeDy + normalDy, hiddenTop);
                }
            }
            
            accumulatedFingerDrag = 0;
            if (hasTriggeredBoost) {
                hasTriggeredBoost = false;
                jumpBoostAnim.cancel();
                currentJumpBoost = 0;
            }
            
            return Math.max(expandedTop, Math.min(top, collapsedTop));
        }
        
        @Override
        public int getViewVerticalDragRange(@NonNull View child) {
            return hiddenTop - expandedTop;
        }
    }
}
