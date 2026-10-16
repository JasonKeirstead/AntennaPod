package de.danoeh.antennapod.ui.preferences.screen;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import de.danoeh.antennapod.storage.database.DBWriter;
import de.danoeh.antennapod.storage.preferences.UserPreferences;
import de.danoeh.antennapod.ui.preferences.R;

public class AutoDownloadPreferencesFragment extends AnimatedPreferenceFragment {

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.preferences_autodownload);
        findPreference(UserPreferences.PREF_EPISODES_PER_PODCAST_LIMIT).setOnPreferenceChangeListener(
                (preference, newValue) -> {
                    DBWriter.removeEpisodesOverPodcastLimit(getContext(), Integer.parseInt((String) newValue));
                    return true;
                });
    }

    @Override
    public void onStart() {
        super.onStart();
        ((AppCompatActivity) getActivity()).getSupportActionBar().setTitle(R.string.pref_automatic_download_title);
    }
}
