package com.xapps.media.xmusic.service.resume;

import java.io.Serializable;

public class ResumeSong implements Serializable {
    private static final long serialVersionUID = 1L;

    public String title;
    public String artist;
    public String path;
    public String artworkUri;

    public ResumeSong() {
    }

    public ResumeSong(String title, String artist, String path, String artworkUri) {
        this.title = title;
        this.artist = artist;
        this.path = path;
        this.artworkUri = artworkUri;
    }
}