package de.danoeh.antennapod.net.download.service.episode;

import android.content.Context;

import androidx.test.platform.app.InstrumentationRegistry;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.model.feed.FeedPreferences;
import de.danoeh.antennapod.model.feed.VolumeAdaptionSetting;
import de.danoeh.antennapod.net.sync.serviceinterface.SynchronizationQueue;
import de.danoeh.antennapod.net.sync.serviceinterface.SynchronizationQueueStub;
import de.danoeh.antennapod.storage.database.DBReader;
import de.danoeh.antennapod.storage.database.DBWriter;
import de.danoeh.antennapod.storage.database.PodDBAdapter;
import de.danoeh.antennapod.storage.preferences.PlaybackPreferences;
import de.danoeh.antennapod.storage.preferences.SynchronizationSettings;
import de.danoeh.antennapod.storage.preferences.UserPreferences;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class MediaDownloadedHandlerTest {
    private static final int NUM_ITEMS = 5;

    private Context context;
    private File destFolder;
    private final List<FeedItem> items = new ArrayList<>();
    private final List<File> files = new ArrayList<>();

    @Before
    public void setUp() throws IOException {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        destFolder = new File(context.getCacheDir(), "MediaDownloadedHandlerTest");
        assertTrue(destFolder.mkdir());

        PodDBAdapter.init(context);
        PodDBAdapter.deleteDatabase();
        PodDBAdapter adapter = PodDBAdapter.getInstance();
        adapter.open();
        adapter.close();

        UserPreferences.init(context);
        PlaybackPreferences.init(context);
        SynchronizationSettings.init(context);
        SynchronizationQueue.setInstance(new SynchronizationQueueStub());

        Feed feed = new Feed("url", null, "title");
        feed.setItems(items);
        feed.setPreferences(new FeedPreferences(0, FeedPreferences.AutoDownloadSetting.GLOBAL,
                FeedPreferences.AutoDeleteAction.GLOBAL, VolumeAdaptionSetting.OFF,
                FeedPreferences.NewEpisodesAction.GLOBAL, null, null));
        for (int i = 0; i < NUM_ITEMS; i++) {
            FeedItem item = new FeedItem(0, "title", "id" + i, "link", new Date(NUM_ITEMS - i),
                    FeedItem.UNPLAYED, feed);
            File file = new File(destFolder, "file " + i);
            assertTrue(file.createNewFile());
            files.add(file);
            item.setMedia(new FeedMedia(0, item, 1, 0, 1L, "m",
                    file.getAbsolutePath(), "url", System.currentTimeMillis(), null, 0, 0));
            items.add(item);
        }
        adapter.open();
        adapter.setCompleteFeed(feed);
        adapter.close();
    }

    @After
    public void tearDown() {
        for (File file : destFolder.listFiles()) {
            assertTrue(file.delete());
        }
        assertTrue(destFolder.delete());
        DBWriter.tearDownTests();
        PodDBAdapter.tearDownTests();
    }

    @Test
    public void testDeleteEpisodesOverLimitKeepsNewest() {
        FeedItem downloadedItem = DBReader.getFeedItem(items.get(0).getId());
        downloadedItem.getFeed().getPreferences().setEpisodeLimit(2);

        MediaDownloadedHandler.deleteEpisodesOverLimit(context, downloadedItem);

        assertFilesExist(true, true, false, false, false);
    }

    @Test
    public void testDeleteEpisodesOverLimitSkipsProtectedEpisodes() throws Exception {
        DBWriter.addFavoriteItems(Collections.singletonList(items.get(3))).get();
        PlaybackPreferences.writeMediaPlaying(DBReader.getFeedItem(items.get(2).getId()).getMedia());
        FeedItem downloadedItem = DBReader.getFeedItem(items.get(4).getId());
        downloadedItem.getFeed().getPreferences().setEpisodeLimit(4);

        MediaDownloadedHandler.deleteEpisodesOverLimit(context, downloadedItem);

        assertFilesExist(true, false, true, true, true);
    }

    private void assertFilesExist(boolean... expected) {
        for (int i = 0; i < expected.length; i++) {
            assertEquals("file " + i, expected[i], files.get(i).exists());
        }
    }
}
