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

## Feed Updates

```
OpmlImportActivity.doImport
	FeedDatabaseWriter.updateFeed(Feed) 

AddFeedFragment.addLocalFolder
	FeedDatabaseWriter.updateFeed(Feed) 

FeedSettingsPrefererenceFragment.addLocalFolderResult
	FeedDatabaseWriter.updateFeed(Feed) 

FeedUpdateWorker.refreshFeeds()
    FeedUpdateWorker.refreshFeed(feed)
    	FeedDatabaseWriter.updateFeed(Feed) 
    LocalFeedUpdater.UpdateFeed
        LocalFeedUpdater.tryUpdateFeed
            FeedDatabaseWriter.updateFeed(Feed)
    populateInboxOrQueue

SyncService.syncSubscriptions
	FeedDatabaseWriter.updateFeed(Feed) 
```

## Queue updates

When something is removed from the queue we need to repopulate it to maintain maxEpisodes (ideally). Could just wait for refresh

See `DBWriter.removeQueueItemSynchronous`

## Inbox Updates

When something is removed from the queue we need to repopulate it to maintain maxEpisodes (ideally). Could just wait for refresh







## Downloads

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


​        
## UI

MainActivity
- HomeFragment
- QueueFragment -- queue
- InboxFragment
- AllEpisodesFragment
- CompletedDownloadsFragment
- PlaybackHistoryFragment
- EpisodesListFragment -- inbox
- FeedItemListFragment -- list episodes in podcast feed

FeedItemMenuHandler

HomeFragment loads:
- QueueSection
- InboxSection
- EpisodesSurpriseSection
- SubscriptionsSection
- DownloadsSection

AudioPlayerFragment
HomeSection
ItemPagerFragment
CompletedDownloadsFragment
SearchFragment

PlayerWidget extends AppWidgetProvider


## Tests
.\gradlew :app:testPlayDebugUnitTest
