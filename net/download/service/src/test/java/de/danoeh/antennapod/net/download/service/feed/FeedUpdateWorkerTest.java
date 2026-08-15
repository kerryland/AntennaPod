package de.danoeh.antennapod.net.download.service.feed;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.work.ListenableWorker;
import androidx.work.WorkerParameters;

import de.danoeh.antennapod.net.common.NetworkUtils;
import de.danoeh.antennapod.net.download.serviceinterface.AutoDownloadManager;
import de.danoeh.antennapod.net.download.serviceinterface.DownloadServiceInterface;
import de.danoeh.antennapod.net.download.serviceinterface.DownloadServiceInterfaceStub;
import de.danoeh.antennapod.net.sync.serviceinterface.SynchronizationQueue;
import de.danoeh.antennapod.net.sync.serviceinterface.SynchronizationQueueStub;
import de.danoeh.antennapod.storage.database.PodDBAdapter;
import de.danoeh.antennapod.storage.preferences.UserPreferences;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.shadows.ShadowConnectivityManager;
import org.robolectric.shadows.ShadowNetworkInfo;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.robolectric.Shadows.shadowOf;

import java.util.concurrent.Future;

@RunWith(RobolectricTestRunner.class)
public class FeedUpdateWorkerTest {

    private Context context;

    @Before
    public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();

        AutoDownloadManager.setInstance(new AutoDownloadManager() {
            @Override
            public Future<?> autodownloadUndownloadedItems(Context context) {
                return null;
            }

            @Override
            public void performAutoCleanup(Context context) {

            }

        });

        DownloadServiceInterface.setImpl(new DownloadServiceInterfaceStub());
        SynchronizationQueue.setInstance(new SynchronizationQueueStub());

        ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        ShadowConnectivityManager shadowConnectivityManager = shadowOf(connectivityManager);

        // Set up network info
        NetworkInfo networkInfo = ShadowNetworkInfo.newInstance(
                NetworkInfo.DetailedState.CONNECTED,
                ConnectivityManager.TYPE_WIFI,
                0,
                true,
                true
        );
        shadowConnectivityManager.setActiveNetworkInfo(networkInfo);

        NetworkUtils.init(context);

        UserPreferences.init(context);
        PodDBAdapter.init(context);
        PodDBAdapter.deleteDatabase();
    }

    @Test
    public void testDoWorkNoFeeds() {
        WorkerParameters mockParams = mock(WorkerParameters.class);
        when(mockParams.getInputData()).thenReturn(androidx.work.Data.EMPTY);

        FeedUpdateWorker worker = new FeedUpdateWorker(context, mockParams);
        //--------------------------------------------------------
        ListenableWorker.Result result = worker.doWork();
        //--------------------------------------------------------
        assertEquals(ListenableWorker.Result.success(), result);
    }

    @Test
    public void testDoWorkWithLocalFeed() {
        de.danoeh.antennapod.model.feed.Feed feed = new de.danoeh.antennapod.model.feed.Feed("folder://test", null, "Local Feed");
        feed.setItems(new java.util.ArrayList<>());
        PodDBAdapter adapter = PodDBAdapter.getInstance();
        adapter.open();
        adapter.setCompleteFeed(feed);
        adapter.close();

        WorkerParameters mockParams = mock(WorkerParameters.class);
        when(mockParams.getInputData()).thenReturn(androidx.work.Data.EMPTY);

        FeedUpdateWorker worker = new FeedUpdateWorker(context, mockParams);
        //--------------------------------------------------------
        ListenableWorker.Result result = worker.doWork();
        //--------------------------------------------------------

        assertEquals(ListenableWorker.Result.success(), result);
    }
}
