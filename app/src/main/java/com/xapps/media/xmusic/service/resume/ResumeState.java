package com.xapps.media.xmusic.service.resume;

import java.io.Serializable;
import java.util.ArrayList;

public class ResumeState implements Serializable {
    private static final long serialVersionUID = 1L;

    public int version = 1;
    public boolean shuffle;
    public int repeatMode;
    public int currentIndex;
    public long positionMs;
    public float speed = 1.0f;
    public ArrayList<ResumeSong> songs = new ArrayList<>();

    public ResumeState() {
    }

    public ResumeState(
            boolean shuffle,
            int repeatMode,
            int currentIndex,
            long positionMs,
            float speed,
            ArrayList<ResumeSong> songs
    ) {
        this.shuffle = shuffle;
        this.repeatMode = repeatMode;
        this.currentIndex = currentIndex;
        this.positionMs = positionMs;
        this.speed = speed;
        this.songs = songs;
    }
}