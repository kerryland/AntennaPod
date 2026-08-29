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

MediaLibrarySessionCallback is the integration with media3
that delivers episodes to play

## Feed Updates
FeedUpdateWorker
    FeedUpdateManagerImpl.runOnce implicitly calls FeedUpdateWorker.doWork and 
    DestinationSelector.populateInboxOrQueue are good places to start looking
    DestinationSelectorTest

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

See `DBWriter.removeQueueItemSynchronous`

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

## UI

MainActivity
- HomeFragment
- QueueFragment -- queue
- InboxFragment
- AllEpisodesFragment
- CompletedDownloadsFragment
- PlaybackHistoryFragment
- EpisodesListFragment -- base class. extended by InboxFragment etc (but not QueueFragment)
- FeedItemListFragment -- list episodes in podcast feed

### Menus
FeedItemMenuHandler
EpisodeMultiSelectActionHandler


menu XMLs live in app/src/main/res/menu/ unless noted.

Context (long-press) menus

feeditemlist_context.xml — EpisodeItemListAdapter (Episodes/Inbox/Feed item list/Completed downloads/Search), HorizontalItemListAdapter
queue_context.xml — QueueRecyclerAdapter (appended to the generic after my reorder)
nav_feed_context.xml — NavDrawerFragment, HorizontalFeedListAdapter
nav_folder_context.xml — NavDrawerFragment, SubscriptionTagAdapter
opml_selection_options.xml — OpmlImportActivity
Multi-select / action-bar (CAB) menu

multi_select_options.xml — SelectableAdapter
Toolbar / options menus

episodes.xml — AllEpisodesFragment
inbox.xml — InboxFragment
queue.xml — QueueFragment
subscriptions.xml — SubscriptionFragment
favorites.xml — FavoritesFragment
home.xml — HomeFragment
downloads_completed.xml — CompletedDownloadsFragment
download_log.xml — DownloadLogFragment
feedlist.xml — FeedItemlistFragment
feedinfo.xml — FeedInfoFragment
feeditem_options.xml — ItemPagerFragment
mediaplayer.xml — AudioPlayerFragment, Media3VideoPlayerActivity, VideoplayerActivity
playback_history.xml — PlaybackHistoryFragment
search.xml — SearchFragment
transcript.xml — TranscriptDialogFragment
cast_button.xml — playback:cast/src/play/ → CastEnabledActivity
countries_menu.xml, online_search.xml — ui:discovery → DiscoveryFragment, OnlineSearchFragment
bug_report_options.xml — ui:preferences → BugReportFragment
statistics.xml — ui:statistics → StatisticsFragment

Swipe action    
- swipe_actions.xml
- SwipeActions.java

# UI Fragments
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
SubscriptionFragment
- fragment_subscriptions.xml
- subscription_grid_item.xml


PlayerWidget extends AppWidgetProvider

# Database
DBPodAdapter -- create database
DBUpgrader -- upgrade database schema. Based on 'oldVersion' and PodDBAdapter.VERSION 
DBWriter -- write to database

## FeedItem
read (state):
   NEW = -1; // i.e. in the inbox
   UNPLAYED = 0;
   PLAYED = 1;

# Sorting
What controls the order we see things in AntennaPod?
## On Screen Sorting
"Episode Lists" (Inbox etc, but not Queue) are sorted based on the following:

| Location                                | Setting                                                         | Meaning                                            |
|-----------------------------------------|-----------------------------------------------------------------|----------------------------------------------------|
| Global. Episode lists                   | Default sort order (UserPreferences.getPrefGlobalSortedOrder()) | Sort order for display when nothing else specified |
| Each 'List' screen menu, except 'Queue' | 'Sort' menu item  (Feed.getSortOrder)                           | Specific sort order for list                       |

Available Global Sort Orders:
- Episode title (asc + desc)
- Duration (asc + desc)
- Date (asc + desc)
- Priority (asc) and Date (asc or desc depending on "Episode Download Order"). See below

## Download/Inbox addition order (Priority)
Episodes are added to the inbox (or automatically downloaded) based
on the podcast-specific settings:

| Setting                | Values                     | Meaning                              |
|------------------------|----------------------------|--------------------------------------|
| Priority               | 1 (high) to 5 (low)        | How important this podcast is to you |
| Episode Download Order | Newest First, Oldest First | Do you want old or new episodes?     |

## Queue Order
Queue order is determined by the order in which items are added to the queue .
Items added automatically use the default "Enqueue Location":

_Global Setting. Playback. Queue. Enqueue Location_
- Back, Front, After current episode, Random, Priority

The Inbox menu also allows adhoc selection of each of these options


## Tests
.\gradlew :app:testPlayDebugUnitTest
