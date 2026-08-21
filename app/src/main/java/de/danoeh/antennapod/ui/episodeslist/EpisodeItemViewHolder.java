package de.danoeh.antennapod.ui.episodeslist;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.text.Layout;
import android.text.format.Formatter;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.cardview.widget.CardView;
import androidx.core.graphics.ColorUtils;
import androidx.recyclerview.widget.RecyclerView;

import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.StateListDrawable;

import de.danoeh.antennapod.R;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.parser.feed.util.DateUtils;
import de.danoeh.antennapod.ui.CoverLoader;
import de.danoeh.antennapod.actionbutton.ItemActionButton;
import de.danoeh.antennapod.playback.service.PlaybackStatus;
import de.danoeh.antennapod.event.playback.PlaybackPositionEvent;
import de.danoeh.antennapod.ui.common.DateFormatter;
import de.danoeh.antennapod.ui.common.ThemeUtils;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.model.playback.MediaType;
import de.danoeh.antennapod.net.download.serviceinterface.DownloadServiceInterface;
import de.danoeh.antennapod.storage.preferences.UserPreferences;
import de.danoeh.antennapod.ui.common.Converter;
import de.danoeh.antennapod.net.common.NetworkUtils;
import de.danoeh.antennapod.model.playback.Playable;
import de.danoeh.antennapod.ui.common.CircularProgressBar;
import de.danoeh.antennapod.ui.episodes.ImageResourceUtils;

import java.util.Date;

/**
 * Holds the view which shows FeedItems.
 */
public class EpisodeItemViewHolder extends RecyclerView.ViewHolder {
    private static final String TAG = "EpisodeItemViewHolder";

    public final View container;
    public final ImageView dragHandle;
    public final TextView placeholder;
    public final ImageView cover;
    private final TextView title;
    private final TextView pubDate;
    private final TextView position;
    private final TextView duration;
    private final TextView feedTitle;
    private final TextView size;
    public final ImageView isInbox;
    public final ImageView isInQueue;
    private final ImageView isPermanent;
    private final ImageView isVideo;
    public final ImageView isFavorite;
    private final ProgressBar progressBar;
    public final View secondaryActionButton;
    public final ImageView secondaryActionIcon;
    private final CircularProgressBar secondaryActionProgress;
    private final TextView separatorIcons;
    private final View leftPadding;
    public final CardView coverHolder;

    private final Activity activity;
    private FeedItem item;

    public EpisodeItemViewHolder(Activity activity, ViewGroup parent) {
        super(LayoutInflater.from(activity).inflate(R.layout.feeditemlist_item, parent, false));
        this.activity = activity;
        container = itemView.findViewById(R.id.container);
        dragHandle = itemView.findViewById(R.id.drag_handle);
        placeholder = itemView.findViewById(R.id.txtvPlaceholder);
        cover = itemView.findViewById(R.id.imgvCover);
        title = itemView.findViewById(R.id.txtvTitle);
        title.setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_FULL);
        feedTitle = itemView.findViewById(R.id.feedTitle);
        pubDate = itemView.findViewById(R.id.txtvPubDate);
        position = itemView.findViewById(R.id.txtvPosition);
        duration = itemView.findViewById(R.id.txtvDuration);
        progressBar = itemView.findViewById(R.id.progressBar);
        isInQueue = itemView.findViewById(R.id.ivInPlaylist);
        isPermanent = itemView.findViewById(R.id.isPermanent);
        isVideo = itemView.findViewById(R.id.ivIsVideo);
        isInbox = itemView.findViewById(R.id.statusInbox);
        isFavorite = itemView.findViewById(R.id.isFavorite);
        size = itemView.findViewById(R.id.size);
        separatorIcons = itemView.findViewById(R.id.separatorIcons);
        secondaryActionProgress = itemView.findViewById(R.id.secondaryActionProgress);
        secondaryActionButton = itemView.findViewById(R.id.secondaryActionButton);
        secondaryActionIcon = itemView.findViewById(R.id.secondaryActionIcon);
        coverHolder = itemView.findViewById(R.id.coverHolder);
        leftPadding = itemView.findViewById(R.id.left_padding);
        itemView.setTag(this);
    }

    public void bind(FeedItem item) {
        this.item = item;
        placeholder.setText(item.getFeed().getTitle());
        title.setText(item.getTitle());
        feedTitle.setText(item.getFeed().getTitle());
        if (item.isPlayed()) {
            leftPadding.setContentDescription(item.getTitle() + ". " + activity.getString(R.string.is_played));
        } else {
            leftPadding.setContentDescription(item.getTitle());
        }
        pubDate.setText(DateFormatter.formatAbbrev(activity, item.getPubDate()));
        pubDate.setContentDescription(DateFormatter.formatForAccessibility(item.getPubDate()));
        isInbox.setVisibility(item.isNew() ? View.VISIBLE : View.GONE);
        isFavorite.setVisibility(item.isTagged(FeedItem.TAG_FAVORITE) ? View.VISIBLE : View.GONE);
        isInQueue.setVisibility(item.isTagged(FeedItem.TAG_QUEUE) ? View.VISIBLE : View.GONE);
        isPermanent.setVisibility(item.isTagged(FeedItem.TAG_QUEUE_PERMANENT) ? View.VISIBLE : View.GONE);

        container.setAlpha(item.isPlayed() ? 0.5f : 1.0f);
        setFreshBackground();

        ItemActionButton actionButton = ItemActionButton.forItem(item);
        actionButton.configure(secondaryActionButton, secondaryActionIcon, activity);
        secondaryActionButton.setFocusable(false);

        if (item.getMedia() != null) {
            bind(item.getMedia());
        } else {
            secondaryActionProgress.setPercentage(0, item);
            secondaryActionProgress.setIndeterminate(false);
            isVideo.setVisibility(View.GONE);
            isPermanent.setVisibility(View.GONE);
            progressBar.setVisibility(View.GONE);
            duration.setVisibility(View.GONE);
            position.setVisibility(View.GONE);
            itemView.setActivated(false);
        }

        if (coverHolder.getVisibility() == View.VISIBLE) {
            new CoverLoader()
                    .withUri(ImageResourceUtils.getEpisodeListImageLocation(item))
                    .withFallbackUri(item.getFeed().getImageUrl())
                    .withPlaceholderView(placeholder)
                    .withCoverView(cover)
                    .load();
        }
    }

    private void bind(FeedMedia media) {
        isVideo.setVisibility(media.getMediaType() == MediaType.VIDEO ? View.VISIBLE : View.GONE);
        duration.setVisibility(media.getDuration() > 0 ? View.VISIBLE : View.GONE);

        itemView.setActivated(PlaybackStatus.isPlaying(media));

        if (DownloadServiceInterface.get().isDownloadingEpisode(media.getDownloadUrl())) {
            float percent = 0.01f * DownloadServiceInterface.get().getProgress(media.getDownloadUrl());
            secondaryActionProgress.setPercentage(Math.max(percent, 0.01f), item);
            secondaryActionProgress.setIndeterminate(
                    DownloadServiceInterface.get().isEpisodeQueued(media.getDownloadUrl()));
        } else if (media.isDownloaded()) {
            secondaryActionProgress.setPercentage(1, item); // Do not animate 100% -> 0%
            secondaryActionProgress.setIndeterminate(false);
        } else {
            secondaryActionProgress.setPercentage(0, item); // Animate X% -> 0%
            secondaryActionProgress.setIndeterminate(false);
        }

        duration.setText(Converter.getDurationStringLong(media.getDuration()));
        duration.setContentDescription(activity.getString(R.string.chapter_duration,
                Converter.getDurationStringLocalized(activity, media.getDuration())));
        if (PlaybackStatus.isPlaying(item.getMedia()) || item.isInProgress()) {
            int progress = (int) (100.0 * media.getPosition() / media.getDuration());
            int remainingTime = Math.max(media.getDuration() - media.getPosition(), 0);
            progressBar.setProgress(progress);
            position.setText(Converter.getDurationStringLong(media.getPosition()));
            position.setContentDescription(activity.getString(R.string.position,
                    Converter.getDurationStringLocalized(activity, media.getPosition())));
            progressBar.setVisibility(View.VISIBLE);
            position.setVisibility(View.VISIBLE);
            if (UserPreferences.shouldShowRemainingTime()) {
                duration.setText(((remainingTime > 0) ? "-" : "") + Converter.getDurationStringLong(remainingTime));
                duration.setContentDescription(activity.getString(R.string.chapter_duration,
                        Converter.getDurationStringLocalized(activity, (media.getDuration() - media.getPosition()))));
            }
        } else {
            progressBar.setVisibility(View.GONE);
            position.setVisibility(View.GONE);
        }

        if (media.getSize() > 0) {
            size.setText(Formatter.formatShortFileSize(activity, media.getSize()));
        } else if (NetworkUtils.isEpisodeHeadDownloadAllowed() && !media.checkedOnSizeButUnknown()) {
            size.setText("");
            MediaSizeLoader.getFeedMediaSizeObservable(media).subscribe(
                    sizeValue -> {
                        if (sizeValue > 0) {
                            size.setText(Formatter.formatShortFileSize(activity, sizeValue));
                        } else {
                            size.setText("");
                        }
                    }, error -> {
                        size.setText("");
                        Log.e(TAG, Log.getStackTraceString(error));
                    });
        } else {
            size.setText("");
        }
    }

    public void bindDummy() {
        item = new FeedItem();
        item.setFeed(new Feed("", ""));
        container.setAlpha(0.1f);
        secondaryActionIcon.setImageDrawable(null);
        isInbox.setVisibility(View.VISIBLE);
        isVideo.setVisibility(View.GONE);
        isFavorite.setVisibility(View.GONE);
        isInQueue.setVisibility(View.GONE);
        isPermanent.setVisibility(View.GONE);
        title.setText("███████");
        feedTitle.setText("");
        pubDate.setText("████");
        duration.setText("████");
        secondaryActionProgress.setPercentage(0, null);
        secondaryActionProgress.setIndeterminate(false);
        progressBar.setVisibility(View.GONE);
        position.setVisibility(View.GONE);
        dragHandle.setVisibility(View.GONE);
        size.setText("");
        itemView.setActivated(false);
        placeholder.setText("");
        if (coverHolder.getVisibility() == View.VISIBLE) {
            new CoverLoader()
                    .withResource(R.color.medium_gray)
                    .withPlaceholderView(placeholder)
                    .withCoverView(cover)
                    .load();
        }
    }

    /**
     * Highlights items that were added to the inbox or queue today (or yesterday, in a lighter
     * shade). The background is built programmatically so no drawable resources are duplicated.
     */
    private void setFreshBackground() {
        Date added = item.getAddedToInboxOrQueue();
        if (added == null) {
            container.setBackgroundResource(R.drawable.bg_episode_list_item);
            return;
        }
        int color = ThemeUtils.getColorFromAttr(activity, R.attr.colorBackgroundFloating);
        color = ColorUtils.blendARGB(color, Color.WHITE, 0.8f);

        if (DateUtils.isToday(added)) {
            container.setBackground(buildFreshBackground(ColorUtils.setAlphaComponent(color, 20)));
        } else if (DateUtils.isYesterday(added)) {
            container.setBackground(buildFreshBackground(ColorUtils.setAlphaComponent(color, 10)));
        } else {
            container.setBackgroundResource(R.drawable.bg_episode_list_item);
        }
    }

    private Drawable buildFreshBackground(int color) {
        float density = activity.getResources().getDisplayMetrics().density;
        int cornerRadius = (int) (12 * density);
        int insetH = (int) (4 * density);
        int insetV = (int) (2 * density);

        GradientDrawable normalShape = new GradientDrawable();
        normalShape.setCornerRadius(cornerRadius);
        normalShape.setColor(color);

        GradientDrawable selectedShape = new GradientDrawable();
        selectedShape.setCornerRadius(cornerRadius);
        selectedShape.setColor(ThemeUtils.getColorFromAttr(activity, R.attr.colorSecondaryContainer));

        StateListDrawable content = new StateListDrawable();
        content.addState(new int[]{android.R.attr.state_activated}, selectedShape);
        content.addState(new int[]{android.R.attr.state_selected}, selectedShape);
        content.addState(new int[]{}, normalShape);

        GradientDrawable mask = new GradientDrawable();
        mask.setCornerRadius(cornerRadius);
        mask.setColor(0xFF000000);

        RippleDrawable ripple = new RippleDrawable(
                ColorStateList.valueOf(ThemeUtils.getColorFromAttr(activity, R.attr.colorControlHighlight)),
                content, mask);
        return new InsetDrawable(ripple, insetH, insetV, insetH, insetV);
    }

    private void updateDuration(PlaybackPositionEvent event) {
        if (getFeedItem().getMedia() != null) {
            getFeedItem().getMedia().setPosition(event.getPosition());
            getFeedItem().getMedia().setDuration(event.getDuration());
        }
        int currentPosition = event.getPosition();
        int timeDuration = event.getDuration();
        int remainingTime = Math.max(timeDuration - currentPosition, 0);
        Log.d(TAG, "currentPosition " + Converter.getDurationStringLong(currentPosition));
        if (currentPosition == Playable.INVALID_TIME || timeDuration == Playable.INVALID_TIME) {
            Log.w(TAG, "Could not react to position observer update because of invalid time");
            return;
        }
        if (UserPreferences.shouldShowRemainingTime()) {
            duration.setText(((remainingTime > 0) ? "-" : "") + Converter.getDurationStringLong(remainingTime));
        } else {
            duration.setText(Converter.getDurationStringLong(timeDuration));
        }
    }

    public FeedItem getFeedItem() {
        return item;
    }

    public boolean isPlayingItem() {
        return item.getMedia() != null && PlaybackStatus.isPlaying(item.getMedia());
    }

    public void notifyPlaybackPositionUpdated(PlaybackPositionEvent event) {
        progressBar.setProgress((int) (100.0 * event.getPosition() / event.getDuration()));
        position.setText(Converter.getDurationStringLong(event.getPosition()));
        updateDuration(event);
        duration.setVisibility(View.VISIBLE); // Even if the duration was previously unknown, it is now known
    }

    /**
     * Hides the separator dot between icons and text if there are no icons.
     */
    public void hideSeparatorIfNecessary() {
        boolean hasIcons = isInbox.getVisibility() == View.VISIBLE
                || isInQueue.getVisibility() == View.VISIBLE
                || isPermanent.getVisibility() == View.VISIBLE
                || isVideo.getVisibility() == View.VISIBLE
                || isFavorite.getVisibility() == View.VISIBLE
                || isInbox.getVisibility() == View.VISIBLE;
        separatorIcons.setVisibility(hasIcons ? View.VISIBLE : View.GONE);
    }
}
