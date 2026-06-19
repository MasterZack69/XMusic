package com.xapps.media.xmusic.models;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.view.ViewCompat;
import androidx.dynamicanimation.animation.DynamicAnimation;
import androidx.dynamicanimation.animation.FloatValueHolder;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;
import com.google.android.material.shape.MaterialShapeDrawable;

public class ExpressiveBottomSheetBehavior<V extends View> extends CustomBottomSheetBehavior<V> {

    private int floatingBottomMargin;
    private int floatingSideMargin;

    private float cornerRadiusTopLeft;
    private float cornerRadiusTopRight;
    private float cornerRadiusBottomLeft;
    private float cornerRadiusBottomRight;

    private boolean isInitialized = false;

    public ExpressiveBottomSheetBehavior() {
        super();
    }

    public ExpressiveBottomSheetBehavior(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void setFloatingMargins(int sideMargin, int bottomMargin) {
        this.floatingSideMargin = sideMargin;
        this.floatingBottomMargin = bottomMargin;
    }

    public void setFloatingCornerRadii(float tl, float tr, float bl, float br) {
        this.cornerRadiusTopLeft = tl;
        this.cornerRadiusTopRight = tr;
        this.cornerRadiusBottomLeft = bl;
        this.cornerRadiusBottomRight = br;
    }

    @Override
public boolean onLayoutChild(@NonNull CoordinatorLayout parent, @NonNull V child, int layoutDirection) {
    boolean handled = super.onLayoutChild(parent, child, layoutDirection);
    
    if (state == STATE_COLLAPSED && floatingBottomMargin > 0) {
        collapsedOffset -= floatingBottomMargin;
        ViewCompat.offsetTopAndBottom(child, -floatingBottomMargin);
    }
    
    if (!isInitialized) {
        setupExpressiveCallbacks(child);
        isInitialized = true;
    }
    return handled;
}


    private void setupExpressiveCallbacks(V child) {
        addBottomSheetCallback(new BottomSheetCallback() {
            @Override
            public void onStateChanged(@NonNull View bottomSheet, int newState) {
            }

            @Override
            public void onSlide(@NonNull View bottomSheet, float slideOffset) {
                if (slideOffset >= 0f && slideOffset <= 1f) {
                    applyExponentialMorph(bottomSheet, slideOffset);
                }
            }
        });
    }

    private void applyExponentialMorph(View child, float slideOffset) {
        float invertedOffset = 1.0f - slideOffset;
        float exponentialFactor = (float) Math.pow(invertedOffset, 3.0);

        MaterialShapeDrawable materialShapeDrawable = getMaterialShapeDrawable();
        if (materialShapeDrawable != null) {
            float currentTl = cornerRadiusTopLeft * exponentialFactor;
            float currentTr = cornerRadiusTopRight * exponentialFactor;
            float currentBl = cornerRadiusBottomLeft * exponentialFactor;
            float currentBr = cornerRadiusBottomRight * exponentialFactor;

            materialShapeDrawable.setShapeAppearanceModel(
                materialShapeDrawable.getShapeAppearanceModel().toBuilder()
                    .setTopLeftCornerSize(currentTl)
                    .setTopRightCornerSize(currentTr)
                    .setBottomLeftCornerSize(currentBl)
                    .setBottomRightCornerSize(currentBr)
                    .build()
            );
        }

        ViewGroup.MarginLayoutParams layoutParams = (ViewGroup.MarginLayoutParams) child.getLayoutParams();
        layoutParams.leftMargin = (int) (floatingSideMargin * exponentialFactor);
        layoutParams.rightMargin = (int) (floatingSideMargin * exponentialFactor);
        layoutParams.bottomMargin = (int) (floatingBottomMargin * exponentialFactor);
        child.setLayoutParams(layoutParams);
    }

    public void showWithSpring(View child) {
        settleWithSpring(child, STATE_COLLAPSED, collapsedOffset);
    }

    public void hideWithSpring(View child) {
        settleWithSpring(child, STATE_HIDDEN, parentHeight);
    }
    
    

    private void settleWithSpring(View child, int targetState, int targetTop) {
        FloatValueHolder floatValueHolder = new FloatValueHolder(child.getTop());
        SpringAnimation springAnimation = new SpringAnimation(floatValueHolder);

        SpringForce springForce = new SpringForce();
        springForce.setFinalPosition(targetTop);
        springForce.setDampingRatio(SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY);
        springForce.setStiffness(SpringForce.STIFFNESS_LOW);

        springAnimation.setSpring(springForce);

        springAnimation.addUpdateListener((animation, value, velocity) -> {
            int currentTop = child.getTop();
            int dy = Math.round(value) - currentTop;
            ViewCompat.offsetTopAndBottom(child, dy);
            dispatchOnSlide(child.getTop());
        });

        springAnimation.addEndListener((animation, canceled, value, velocity) -> {
            setStateInternal(targetState);
        });

        springAnimation.start();
    }
}
