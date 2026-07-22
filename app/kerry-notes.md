# Key classes:

## playback.service:
Media3PlaybackService extends MediaLibraryService extends MediaSessionService
- a foreground service that enables background playback
- encapsulates the Player and MediaSession
```java
  private ExoPlayer exoPlayer;
  private Player player;
  private MediaLibrarySession mediaSession;
```


MediaButtonReceiver extends BroadcastReceiver
PlaybackController
PlaybackService
PlaybackServiceInterface (rubbish statics)

PlaybackServiceStarter
    .start 
PlaybackStatus


PlayerWidget extends AppWidgetProvider

----------

BulkDownloader

DownloadServiceInterfaceImpl
   .download  -- puts download into the queue (BulkDownloader, multi, Automatic)
   .downloadNow (click in DownloadActionButton)
|
EpisodeDownloadWorker - a synchronous "Worker" triggered by WorkManager. 10 min max runtime
   .doWork -- with progress bars
|
EpisodeDownloadWorker.performDownload (synchronous) 
|
DefaultDownloaderFactory
|
(actual download)
        
---------------------------------------

EpisodesListFragment -- inbox
QueueFragment -- queue
FeedItemListFragment -- podcast

AudioPlayerFragment
HomeSection
ItemPagerFragment
CompletedDownloadsFragment
SearchFragment



