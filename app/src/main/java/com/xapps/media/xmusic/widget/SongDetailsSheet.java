package com.xapps.media.xmusic.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;

import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.motion.widget.MotionLayout;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.viewbinding.ViewBinding;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.Target;
import com.xapps.media.xmusic.activity.MainActivity;
import com.xapps.media.xmusic.databinding.LayoutSongDetailsSheetBinding;
import com.xapps.media.xmusic.R;
import com.xapps.media.xmusic.models.BottomSheetBehavior;
import com.xapps.media.xmusic.models.Song;
import com.xapps.media.xmusic.utils.Log;

public class SongDetailsSheet extends CoordinatorLayout {

    private LayoutSongDetailsSheetBinding binding;

    private BottomSheetBehavior<MotionLayout> behavior;
    private View v;
    private MainActivity activity;

    public SongDetailsSheet(@NonNull Context context) {
        super(context);
        init(context);
    }

    public SongDetailsSheet(
            @NonNull Context context,
            @Nullable AttributeSet attrs
    ) {
        super(context, attrs);
        init(context);
    }

    public SongDetailsSheet(
            @NonNull Context context,
            @Nullable AttributeSet attrs,
            int defStyleAttr
    ) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        activity = (MainActivity) context;
    }

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();

        post(() -> {
            try {
                behavior = BottomSheetBehavior.from(
                       (MotionLayout) findViewById(R.id.songDetailsSheet)
                );
                behavior.setSkipCollapsed(false);
                behavior.setHideable(true);
                behavior.setHideFriction(0.1f);
                behavior.setState(
                    BottomSheetBehavior.STATE_HIDDEN
                );
                
                findViewById(R.id.songDetailsSheet).setVisibility(View.VISIBLE);
                
                activity.findViewById(R.id.songDetailsSheetContainer).setOnClickListener(v -> {
                    behavior.setState(BottomSheetBehavior.STATE_HIDDEN);
                    activity.findViewById(R.id.songDetailsSheetContainer).setClickable(false);
                });
                
                activity.findViewById(R.id.songDetailsSheetContainer).setClickable(false);

                behavior.addBottomSheetCallback(
                        new BottomSheetBehavior.BottomSheetCallback() {

                            @Override
                            public void onStateChanged(@NonNull android.view.View bottomSheet, int newState) {
                                if (newState == BottomSheetBehavior.STATE_HIDDEN) {
                                    activity.musicListFragment.freeze(false);
                                    activity.HidePlayer(false);
                                    activity.findViewById(R.id.songDetailsSheetContainer).setClickable(false);
                                }
                            }

                            @Override
                            public void onSlide(@NonNull android.view.View bottomSheet, float slideOffset) {
                                ((MotionLayout) findViewById(R.id.songDetailsSheet)).setProgress(Math.max(0f, slideOffset));
                                activity.getBinding().Scrim.setAlpha(Math.max(0f, (slideOffset+1f)/2f)*1f);
                                findViewById(R.id.small_titles_container).setAlpha(1f-slideOffset*2);
                            }
                        }
                );
            } catch (Exception ignored) {
                ignored.printStackTrace();
            }
        });
    }

    public void expand() {
        if (behavior != null) {
            behavior.setState(
                    BottomSheetBehavior.STATE_EXPANDED
            );
        }
    }

    public void showSheet(Song song) {
        if (behavior != null) {
            behavior.setPeekHeight(findViewById(R.id.collapsedContent).getHeight() + findViewById(R.id.songArtwork).getHeight()*2);
            Glide.with(getContext()).load(song.getArtworkUri() == null? R.drawable.placeholder : song.getArtworkUri()).override(Target.SIZE_ORIGINAL).centerCrop().into((ImageView) findViewById(R.id.songArtwork));
            ((TextView) findViewById(R.id.songArtist)).setText(song.artist);
            ((TextView) findViewById(R.id.songTitle)).setText(song.title);
            ((StatusBarScrimView) findViewById(R.id.bottom_scrim)).setSolidPercentage(1f);
            behavior.setState(
                    BottomSheetBehavior.STATE_COLLAPSED
            );
            activity.HidePlayer(true);
            activity.findViewById(R.id.songDetailsSheetContainer).setClickable(true);
        }
        
    }

    public void hide() {
        if (behavior != null) {
            behavior.setState(
                    BottomSheetBehavior.STATE_HIDDEN
            );
        }
    }

    public MotionLayout getMotionLayout() {
        return ((MotionLayout) findViewById(R.id.songDetailsSheet));
    }

    public LayoutSongDetailsSheetBinding getBinding() {
        return binding;
    }
}