package io.github.oyuha12.hyundaimotors.data;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

/** Temporary source driven by the on-screen toggle. */
public final class FakeDrivingStateSource implements DrivingStateSource {
    private final MutableLiveData<Boolean> driving = new MutableLiveData<>(false);

    @Override
    public LiveData<Boolean> isDriving() {
        return driving;
    }

    public void toggle() {
        driving.setValue(!Boolean.TRUE.equals(driving.getValue()));
    }
}
