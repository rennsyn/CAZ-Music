package com.zulaika.musicplayer.listener;

import com.zulaika.musicplayer.model.Music;

import java.util.List;

public interface MusicSelectListener {
    void playQueue(List<Music> musicList, boolean shuffle);

    void addToQueue(List<Music> musicList);

    void refreshMediaLibrary();
}