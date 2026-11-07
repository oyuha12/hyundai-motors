package io.github.oyuha12.hyundaimotors.data;

import androidx.lifecycle.LiveData;

/**
 * Whether the UI must apply driving restrictions.
 *
 * <p>The screens depend only on this interface, so the real implementation can replace
 * {@link FakeDrivingStateSource} without UI changes. It should be backed by
 * CarUxRestrictionsManager, as the Pleos driving restriction guide requires.
 */
public interface DrivingStateSource {
    LiveData<Boolean> isDriving();
}
