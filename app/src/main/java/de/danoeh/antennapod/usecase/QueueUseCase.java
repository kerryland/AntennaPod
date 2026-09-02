package de.danoeh.antennapod.usecase;

import android.app.Activity;
import android.content.Context;
import android.util.Log;

import androidx.annotation.PluralsRes;
import androidx.annotation.Nullable;

import org.greenrobot.eventbus.EventBus;

import java.util.List;

import de.danoeh.antennapod.R;
import de.danoeh.antennapod.event.MessageEvent;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.playback.service.PlaybackController;
import de.danoeh.antennapod.playback.service.PlaybackStatus;
import de.danoeh.antennapod.storage.database.DBReader;
import de.danoeh.antennapod.storage.database.DBWriter;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.schedulers.Schedulers;

public class QueueUseCase {
    private static final String TAG = "QueueUseCase";
    private static QueueUseCase instance;

    public static QueueUseCase getInstance() {
        return instance;
    }

    public static void setInstance(@Nullable QueueUseCase instance) {
        QueueUseCase.instance = instance;
    }

    public void moveToPlayNext(Context context, List<FeedItem> items, @Nullable CurrentPositionCallback uiUpdateCallback) {
        Observable.fromCallable(() -> DBReader.getQueue())
                .subscribeOn(Schedulers.computation())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(queueItems -> {
                    findCurrentlyPlayingPosition(context, queueItems, position -> {
                        position = position + 1;
                        Log.d(TAG, "Moving items to play next position: " + position);
                        DBWriter.moveQueueItemsToPosition(position, items);
                        showMessage(R.plurals.move_to_play_next_message, items.size(), context);
                        if (uiUpdateCallback != null) {
                            uiUpdateCallback.onCurrentPosition(position);
                        }
                    });
                });
    }

    public interface CurrentPositionCallback {
        void onCurrentPosition(int position);
    }

    private static void findCurrentlyPlayingPosition(Context context, List<FeedItem> queue, final CurrentPositionCallback callback) {
        PlaybackController.bindToMedia3Service(context, controller -> {
            for (int element = 0; element < queue.size(); element++) {
                FeedItem feedItem = queue.get(element);
                if (PlaybackStatus.isPlaying(feedItem.getMedia())) {
                    callback.onCurrentPosition(element);
                    return;
                }
            }

            Log.d(TAG, "Nothing currently playing");
            callback.onCurrentPosition(-1);
        });
    }

    private static void showMessage(@PluralsRes int msgId, int numItems, Context context) {
        if (numItems == 1 || !(context instanceof Activity)) {
            return;
        }
        Activity activity = (Activity) context;
        activity.runOnUiThread(() -> {
            String text = activity.getResources().getQuantityString(msgId, numItems, numItems);
            EventBus.getDefault().post(new MessageEvent(text));
        });
    }
}
