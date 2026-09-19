package de.danoeh.antennapod.storage.importexport;

import android.content.Context;
import androidx.test.core.app.ApplicationProvider;
import de.danoeh.antennapod.event.FeedUpdateRunningEvent;
import de.danoeh.antennapod.event.MessageEvent;
import de.danoeh.antennapod.storage.preferences.UserPreferences;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class AutomaticDatabaseExportWorkerTest {

    private Context context;
    private MessageEventSubscriber subscriber;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        UserPreferences.init(context);
        UserPreferences.setAutomaticExportFolder(null);
        UserPreferences.setLastBackupTime(0);
        subscriber = new MessageEventSubscriber();
        EventBus.getDefault().register(subscriber);
    }

    @Test
    public void testNoBackupAttemptedWhenFolderUnset() {
        AutomaticDatabaseExportWorker.runIfNeeded(context);
        assertTrue("No prompt should be shown when no export folder is configured",
                subscriber.events.isEmpty());
    }

    @Test
    public void testNoBackupAttemptedWhenBackupIsRecent() {
        UserPreferences.setAutomaticExportFolder("content://test/folder");
        UserPreferences.setLastBackupTime(System.currentTimeMillis());

        AutomaticDatabaseExportWorker.runIfNeeded(context);

        assertTrue("No prompt should be shown when the backup is recent",
                subscriber.events.isEmpty());
    }

    @Test
    public void testBackupAttemptedWhenNoRecentBackup() {
        UserPreferences.setAutomaticExportFolder("content://com.android.externalstorage.documents/tree/primary%3ADownload");

        AutomaticDatabaseExportWorker.runIfNeeded(context);

        assertTrue("A backup attempt should be made when the folder is due",
                !subscriber.events.isEmpty());
    }

    @Test
    public void testFeedUpdateFinishedStateTriggersBackup() {
        AutomaticDatabaseExportWorker.init(context);
        UserPreferences.setAutomaticExportFolder("content://com.android.externalstorage.documents/tree/primary%3ADownload");

        EventBus.getDefault().post(new FeedUpdateRunningEvent(FeedUpdateRunningEvent.State.FINISHED));

        assertTrue("A backup attempt should be made when a feed update finishes",
                !subscriber.events.isEmpty());
    }

    public static class MessageEventSubscriber {
        final List<MessageEvent> events = new ArrayList<>();

        @Subscribe
        public void onMessageEvent(MessageEvent event) {
            events.add(event);
        }
    }
}
