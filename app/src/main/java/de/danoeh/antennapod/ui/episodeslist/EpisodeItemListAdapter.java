package de.danoeh.antennapod.ui.episodeslist;

import android.app.Activity;
import android.view.Gravity;
import android.view.InputDevice;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.view.menu.MenuBuilder;
import androidx.appcompat.widget.PopupMenu;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.RecyclerView;

import de.danoeh.antennapod.ui.SelectableAdapter;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import de.danoeh.antennapod.R;
import de.danoeh.antennapod.activity.MainActivity;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.ui.screen.episode.ItemPagerFragment;

/**
 * List adapter for the list of new episodes.
 */
public class EpisodeItemListAdapter extends SelectableAdapter<EpisodeItemViewHolder> {

    private final WeakReference<FragmentActivity> mainActivityRef;
    private List<FeedItem> episodes = new ArrayList<>();
    private FeedItem longPressedItem;
    int longPressedPosition = 0; // used to init actionMode
    private int dummyViews = 0;
    private MenuItem.OnMenuItemClickListener contextMenuClickListener;

    public EpisodeItemListAdapter(FragmentActivity mainActivity) {
        super(mainActivity);
        this.mainActivityRef = new WeakReference<>(mainActivity);
        setHasStableIds(true);
    }

    public void setDummyViews(int dummyViews) {
        this.dummyViews = dummyViews;
        notifyDataSetChanged();
    }

    public void updateItems(List<FeedItem> items) {
        episodes = items;
        notifyDataSetChanged();
        onSelectedItemsUpdated();
    }

    @Override
    public final int getItemViewType(int position) {
        return R.id.view_type_episode_item;
    }

    @NonNull
    @Override
    public final EpisodeItemViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new EpisodeItemViewHolder(mainActivityRef.get(), parent);
    }

    @Override
    public final void onBindViewHolder(EpisodeItemViewHolder holder, int pos) {
        if (pos >= episodes.size()) {
            beforeBindViewHolder(holder, pos);
            holder.bindDummy();
            afterBindViewHolder(holder, pos);
            holder.hideSeparatorIfNecessary();
            return;
        }

        // Reset state of recycled views
        holder.coverHolder.setVisibility(View.VISIBLE);
        holder.dragHandle.setVisibility(View.GONE);

        beforeBindViewHolder(holder, pos);

        FeedItem item = episodes.get(pos);
        holder.bind(item);

        holder.itemView.setOnClickListener(v -> {
            if (!inActionMode()) {
                if (mainActivityRef.get() instanceof MainActivity) {
                    ((MainActivity) mainActivityRef.get())
                            .loadChildFragment(ItemPagerFragment.newInstance(episodes, item));
                } else {
                    ItemPagerFragment fragment = ItemPagerFragment.newInstance(episodes, item);
                    mainActivityRef.get().getSupportFragmentManager()
                            .beginTransaction()
                            .replace(R.id.fragmentContainer, fragment, "Items")
                            .addToBackStack("Items")
                            .commitAllowingStateLoss();
                }
            } else {
                toggleSelection(holder.getBindingAdapterPosition());
            }
        });
        holder.itemView.setOnLongClickListener(v -> {
            longPressedItem = item;
            longPressedPosition = holder.getBindingAdapterPosition();
            showContextMenu(v);
            return true;
        });
        holder.itemView.setOnTouchListener((v, e) -> {
            if (e.isFromSource(InputDevice.SOURCE_MOUSE)
                    && e.getButtonState() == MotionEvent.BUTTON_SECONDARY) {
                longPressedItem = item;
                longPressedPosition = holder.getBindingAdapterPosition();
                showContextMenu(v);
                return true;
            }
            return false;
        });

        holder.itemView.setSelected(false);
        if (inActionMode()) {
            holder.itemView.setActivated(false);
            holder.secondaryActionButton.setOnClickListener(
                    v -> toggleSelection(holder.getBindingAdapterPosition()));
            if (isSelected(pos)) {
                holder.itemView.setSelected(true);
            }
        }

        afterBindViewHolder(holder, pos);
        holder.hideSeparatorIfNecessary();
    }

    protected void beforeBindViewHolder(EpisodeItemViewHolder holder, int pos) {
    }

    protected void afterBindViewHolder(EpisodeItemViewHolder holder, int pos) {
    }

    @Override
    public void onViewRecycled(@NonNull EpisodeItemViewHolder holder) {
        super.onViewRecycled(holder);
        // Set all listeners to null. This is required to prevent leaking fragments that have set a listener.
        // Activity -> recycledViewPool -> EpisodeItemViewHolder -> Listener -> Fragment (can not be garbage collected)
        holder.itemView.setOnClickListener(null);
        holder.itemView.setOnLongClickListener(null);
        holder.itemView.setOnTouchListener(null);
        holder.secondaryActionButton.setOnClickListener(null);
        holder.dragHandle.setOnTouchListener(null);
        holder.coverHolder.setOnTouchListener(null);
    }

    /**
     * {@link #notifyItemChanged(int)} is final, so we can not override.
     * Calling {@link #notifyItemChanged(int)} may bind the item to a new ViewHolder and execute a transition.
     * This causes flickering and breaks the download animation that stores the old progress in the View.
     * Instead, we tell the adapter to use partial binding by calling {@link #notifyItemChanged(int, Object)}.
     * We actually ignore the payload and always do a full bind but calling the partial bind method ensures
     * that ViewHolders are always re-used.
     *
     * @param position Position of the item that has changed
     */
    public void notifyItemChangedCompat(int position) {
        notifyItemChanged(position, "foo");
    }

    @Nullable
    public FeedItem getLongPressedItem() {
        return longPressedItem;
    }

    @Override
    public long getItemId(int position) {
        if (position >= episodes.size()) {
            return RecyclerView.NO_ID; // Dummy views
        }
        FeedItem item = episodes.get(position);
        return item != null ? item.getId() : RecyclerView.NO_POSITION;
    }

    @Override
    public int getItemCount() {
        return dummyViews + episodes.size();
    }

    protected FeedItem getItem(int index) {
        return episodes.get(index);
    }

    protected Activity getActivity() {
        return mainActivityRef.get();
    }

    public void setContextMenuClickListener(MenuItem.OnMenuItemClickListener contextMenuClickListener) {
        this.contextMenuClickListener = contextMenuClickListener;
    }

    public void showContextMenu(View anchor) {
        if (inActionMode() || longPressedItem == null) {
            return;
        }
        ViewGroup content = (ViewGroup) getActivity().findViewById(android.R.id.content);
        View popupAnchor = anchor;
        if (content != null) {
            popupAnchor = new View(getActivity());
            FrameLayout.LayoutParams anchorParams = new FrameLayout.LayoutParams(1, 1);
            anchorParams.gravity = Gravity.TOP | Gravity.END;
            content.addView(popupAnchor, anchorParams);
        }

        PopupMenu popupMenu = new PopupMenu(getActivity(), popupAnchor);
        Menu menu = popupMenu.getMenu();
        onInflateContextMenu(menu);
        getActivity().getMenuInflater().inflate(R.menu.feeditemlist_context, menu);
        FeedItemMenuHandler.onPrepareMenu(getActivity(), menu,
                Collections.singletonList(longPressedItem), R.id.skip_episode_item);
        onPrepareContextMenu(menu);
        popupMenu.setForceShowIcon(true);
        if (menu instanceof MenuBuilder) {
            ((MenuBuilder) menu).setOptionalIconsVisible(true);
        }
        if (contextMenuClickListener != null) {
            popupMenu.setOnMenuItemClickListener(contextMenuClickListener::onMenuItemClick);
        }

        if (content != null) {
            // Position the popup vertically centered on the right edge of the screen.
            int visibleItems = 0;
            for (int i = 0; i < menu.size(); i++) {
                if (menu.getItem(i).isVisible()) {
                    visibleItems++;
                }
            }
            float density = getActivity().getResources().getDisplayMetrics().density;
            int menuHeight = visibleItems * Math.round(48f * density);
            int verticalCenter = getActivity().getResources().getDisplayMetrics().heightPixels / 2;
            FrameLayout.LayoutParams anchorParams =
                    (FrameLayout.LayoutParams) popupAnchor.getLayoutParams();
            anchorParams.topMargin = Math.max(0, verticalCenter - menuHeight / 2);
            popupAnchor.setLayoutParams(anchorParams);

            ViewGroup container = content;
            View dismissAnchor = popupAnchor;
            popupMenu.setOnDismissListener(m -> container.removeView(dismissAnchor));

            // Show once the anchor view has been laid out so the position is correct.
            dismissAnchor.getViewTreeObserver().addOnGlobalLayoutListener(
                    new ViewTreeObserver.OnGlobalLayoutListener() {
                        private boolean shown = false;

                        @Override
                        public void onGlobalLayout() {
                            if (shown) {
                                return;
                            }
                            shown = true;
                            dismissAnchor.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                            popupMenu.show();
                        }
                    });
        } else {
            popupMenu.show();
        }
    }

    protected void onInflateContextMenu(Menu menu) {
    }

    protected void onPrepareContextMenu(Menu menu) {
    }

    public boolean onContextItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.multi_select) {
            startSelectMode(longPressedPosition);
            return true;
        }
        return false;
    }

    public List<FeedItem> getSelectedItems() {
        List<FeedItem> items = new ArrayList<>();
        for (int i = 0; i < getItemCount(); i++) {
            if (isSelected(i)) {
                items.add(getItem(i));
            }
        }
        return items;
    }

    public List<FeedItem> getSelectedFeedItemsInOrder() {
        List<FeedItem> items = new ArrayList<>();
        for (Integer pos : super.getSelectedItemsInOrder()) {
            items.add(getItem(pos));
        }
        return items;
    }
}
