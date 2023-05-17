package com.zulaika.musicplayer.listener;

import androidx.lifecycle.MutableLiveData;

public interface SleepTimerSetListener {
    void setTimer(int minutes);

    void cancelTimer();

    MutableLiveData<Long> getTick();
}
