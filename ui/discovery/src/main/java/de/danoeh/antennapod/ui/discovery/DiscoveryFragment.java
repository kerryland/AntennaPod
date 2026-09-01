package de.danoeh.antennapod.ui.discovery;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.GridView;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputLayout;
import de.danoeh.antennapod.storage.database.DBReader;
import de.danoeh.antennapod.event.DiscoveryDefaultUpdateEvent;
import de.danoeh.antennapod.net.discovery.ItunesCategoryLoader;
import de.danoeh.antennapod.net.discovery.ItunesTopListLoader;
import de.danoeh.antennapod.net.discovery.PodcastGenre;
import de.danoeh.antennapod.net.discovery.PodcastSearchResult;
import de.danoeh.antennapod.ui.appstartintent.OnlineFeedviewActivityStarter;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import org.greenrobot.eventbus.EventBus;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Searches for podcasts by navigating the genre hierarchy (Apple Podcasts genres).
 */
public class DiscoveryFragment extends Fragment implements Toolbar.OnMenuItemClickListener {
    public static final String TAG = "DiscoveryFragment";
    private static final int NUM_OF_TOP_PODCASTS = 25;
    private SharedPreferences prefs;

    /**
     * Adapter responsible with the search results.
     */
    private OnlineSearchAdapter adapter;
    private GridView gridView;
    private ProgressBar progressBar;
    private TextView txtvError;
    private Button butRetry;
    private TextView txtvEmpty;

    /**
     * List of podcasts retreived from the search.
     */
    private List<PodcastSearchResult> searchResults;
    private Disposable disposable;
    private String countryCode = "US";
    private boolean hidden;
    private MaterialToolbar toolbar;

    private List<PodcastGenre> rootGenres;
    private final Deque<PodcastGenre> genreStack = new ArrayDeque<>();
    private PodcastGenre podcastGenre;

    public DiscoveryFragment() {
        // Required empty public constructor
    }

    /**
     * Replace adapter data with provided search results from SearchTask.
     *
     * @param result List of Podcast objects containing search results
     */
    private void updateData(List<PodcastSearchResult> result) {
        this.searchResults = result;
        adapter.clear();
        if (result != null && result.size() > 0) {
            gridView.setVisibility(View.VISIBLE);
            txtvEmpty.setVisibility(View.GONE);
            for (PodcastSearchResult p : result) {
                adapter.add(p);
            }
            adapter.notifyDataSetInvalidated();
        } else {
            gridView.setVisibility(View.GONE);
            txtvEmpty.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getActivity().getSharedPreferences(ItunesTopListLoader.PREFS, Context.MODE_PRIVATE);
        countryCode = prefs.getString(ItunesTopListLoader.PREF_KEY_COUNTRY_CODE, Locale.getDefault().getCountry());
        hidden = prefs.getBoolean(ItunesTopListLoader.PREF_KEY_HIDDEN_DISCOVERY_COUNTRY, false);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View root = inflater.inflate(R.layout.fragment_online_search, container, false);
        gridView = root.findViewById(R.id.gridView);
        adapter = new OnlineSearchAdapter(getActivity(), new ArrayList<>());
        gridView.setAdapter(adapter);

        toolbar = root.findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> {
            if (podcastGenre != null) {
                podcastGenre = null;
                renderGenreView();
            } else if (!genreStack.isEmpty()) {
                genreStack.pop();
                renderGenreView();
            } else {
                getParentFragmentManager().popBackStack();
            }
        });
        toolbar.inflateMenu(R.menu.countries_menu);
        MenuItem discoverHideItem = toolbar.getMenu().findItem(R.id.discover_hide_item);
        discoverHideItem.setChecked(hidden);
        toolbar.setOnMenuItemClickListener(this);

        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(),
                new OnBackPressedCallback(true) {
                    @Override
                    public void handleOnBackPressed() {
                        if (podcastGenre != null) {
                            podcastGenre = null;
                            renderGenreView();
                        } else if (!genreStack.isEmpty()) {
                            genreStack.pop();
                            renderGenreView();
                        } else {
                            setEnabled(false);
                            requireActivity().getOnBackPressedDispatcher().onBackPressed();
                        }
                    }
                });

        gridView.setOnItemClickListener((parent, view1, position, id) -> {
            if (podcastGenre != null) {
                PodcastSearchResult podcast = searchResults.get(position);
                if (podcast.feedUrl == null) {
                    return;
                }
                // Show information about the podcast when the list item is clicked
                startActivity(new OnlineFeedviewActivityStarter(getContext(), podcast.feedUrl).getIntent());
            } else {
                onGenreClick(position);
            }
        });

        progressBar = root.findViewById(R.id.progressBar);
        txtvError = root.findViewById(R.id.txtvError);
        butRetry = root.findViewById(R.id.butRetry);
        txtvEmpty = root.findViewById(android.R.id.empty);

        loadGenres();
        return root;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (disposable != null) {
            disposable.dispose();
        }
        adapter = null;
    }

    private void showOnlyProgressBar() {
        gridView.setVisibility(View.GONE);
        txtvError.setVisibility(View.GONE);
        butRetry.setVisibility(View.GONE);
        txtvEmpty.setVisibility(View.GONE);
        progressBar.setVisibility(View.VISIBLE);
    }

    private void loadGenres() {
        if (disposable != null) {
            disposable.dispose();
        }
        showOnlyProgressBar();
        disposable = Observable.fromCallable(new ItunesCategoryLoader()::loadGenres)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(tree -> {
                    rootGenres = tree;
                    renderGenreView();
                }, error -> {
                    Log.e(TAG, Log.getStackTraceString(error));
                    progressBar.setVisibility(View.GONE);
                    txtvError.setText(error.toString());
                    txtvError.setVisibility(View.VISIBLE);
                    butRetry.setOnClickListener(v -> loadGenres());
                    butRetry.setVisibility(View.VISIBLE);
                });
    }

    private void renderGenreView() {
        podcastGenre = null;
        progressBar.setVisibility(View.GONE);
        txtvError.setVisibility(View.GONE);
        butRetry.setVisibility(View.GONE);
        txtvEmpty.setVisibility(View.GONE);
        if (hidden) {
            gridView.setVisibility(View.GONE);
            txtvError.setVisibility(View.VISIBLE);
            txtvError.setText(getResources().getString(R.string.discover_is_hidden));
            return;
        }
        PodcastGenre current = genreStack.peek();
        List<String> genreMenuNames = new ArrayList<>();
        List<PodcastGenre> items;
        if (current == null) {
            items = rootGenres;
            toolbar.setTitle(getString(R.string.discover));
        } else {
            toolbar.setTitle(current.name);
            genreMenuNames.add(getString(R.string.view_all_podcasts, current.name));
            items = current.children;
        }
        for (PodcastGenre genre : items) {
            genreMenuNames.add(genre.name);
        }
        int headerPosition = current == null ? -1 : 0;
        GenreAdapter genreAdapter =
                new GenreAdapter(getContext(), R.layout.item_genre, R.id.txtvGenre, genreMenuNames, headerPosition);
        gridView.setAdapter(genreAdapter);
        gridView.setVisibility(View.VISIBLE);
    }

    private void onGenreClick(int position) {
        PodcastGenre current = genreStack.peek();
        PodcastGenre clicked;
        if (current == null) {
            clicked = rootGenres.get(position);
        } else if (position == 0) {
            loadPodcastsFor(current);
            return;
        } else {
            clicked = current.children.get(position - 1);
        }
        if (clicked.children.isEmpty()) {
            loadPodcastsFor(clicked);
        } else {
            // Navigate down to view child genres
            genreStack.push(clicked);
            renderGenreView();
        }
    }

    private void loadPodcastsFor(PodcastGenre genre) {
        if (disposable != null) {
            disposable.dispose();
        }
        podcastGenre = genre;
        toolbar.setTitle(genre.name);
        showOnlyProgressBar();
        ItunesTopListLoader loader = new ItunesTopListLoader(getContext());
        disposable = Observable.fromCallable(() ->
                        loader.loadToplist(countryCode, String.valueOf(genre.id),
                                NUM_OF_TOP_PODCASTS, DBReader.getFeedList()))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(result -> {
                    searchResults = result;
                    progressBar.setVisibility(View.GONE);
                    adapter = new OnlineSearchAdapter(getActivity(), new ArrayList<>());
                    gridView.setAdapter(adapter);
                    updateData(searchResults);
                    txtvEmpty.setText(getString(R.string.search_status_no_results));
                }, error -> {
                    Log.e(TAG, Log.getStackTraceString(error));
                    progressBar.setVisibility(View.GONE);
                    txtvError.setText(error.toString());
                    txtvError.setVisibility(View.VISIBLE);
                    butRetry.setOnClickListener(v -> loadPodcastsFor(genre));
                    butRetry.setVisibility(View.VISIBLE);
                });
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
        final int itemId = item.getItemId();
        if (itemId == R.id.discover_hide_item) {
            item.setChecked(!item.isChecked());
            hidden = item.isChecked();
            prefs.edit().putBoolean(ItunesTopListLoader.PREF_KEY_HIDDEN_DISCOVERY_COUNTRY, hidden).apply();

            EventBus.getDefault().post(new DiscoveryDefaultUpdateEvent());
            podcastGenre = null;
            renderGenreView();
            return true;
        } else if (itemId == R.id.discover_countries_item) {

            LayoutInflater inflater = getLayoutInflater();
            View selectCountryDialogView = inflater.inflate(R.layout.select_country_dialog, null);
            MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(getContext());
            builder.setView(selectCountryDialogView);

            List<String> countryCodeArray = new ArrayList<>(Arrays.asList(Locale.getISOCountries()));
            Map<String, String> countryCodeNames = new HashMap<>();
            Map<String, String> countryNameCodes = new HashMap<>();
            for (String code : countryCodeArray) {
                Locale locale = new Locale("", code);
                String countryName = locale.getDisplayCountry();
                countryCodeNames.put(code, countryName);
                countryNameCodes.put(countryName, code);
            }

            List<String> countryNamesSort = new ArrayList<>(countryCodeNames.values());
            Collections.sort(countryNamesSort);

            ArrayAdapter<String> dataAdapter =
                    new ArrayAdapter<>(this.getContext(), android.R.layout.simple_list_item_1, countryNamesSort);
            TextInputLayout textInput = selectCountryDialogView.findViewById(R.id.country_text_input);
            MaterialAutoCompleteTextView editText = (MaterialAutoCompleteTextView) textInput.getEditText();
            editText.setAdapter(dataAdapter);
            editText.setText(countryCodeNames.get(countryCode));
            editText.setOnClickListener(view -> {
                if (editText.getText().length() != 0) {
                    editText.setText("");
                    editText.postDelayed(editText::showDropDown, 100);
                }
            });
            editText.setOnFocusChangeListener((v, hasFocus) -> {
                if (hasFocus) {
                    editText.setText("");
                    editText.postDelayed(editText::showDropDown, 100);
                }
            });

            builder.setPositiveButton(android.R.string.ok, (dialogInterface, i) -> {
                String countryName = editText.getText().toString();
                if (countryNameCodes.containsKey(countryName)) {
                    countryCode = countryNameCodes.get(countryName);
                    MenuItem discoverHideItem = toolbar.getMenu().findItem(R.id.discover_hide_item);
                    discoverHideItem.setChecked(false);
                    hidden = false;
                }

                prefs.edit().putBoolean(ItunesTopListLoader.PREF_KEY_HIDDEN_DISCOVERY_COUNTRY, hidden).apply();
                prefs.edit().putString(ItunesTopListLoader.PREF_KEY_COUNTRY_CODE, countryCode).apply();

                EventBus.getDefault().post(new DiscoveryDefaultUpdateEvent());
                if (podcastGenre != null) {
                    loadPodcastsFor(podcastGenre);
                } else {
                    renderGenreView();
                }
            });
            builder.setNegativeButton(R.string.cancel_label, null);
            builder.show();
            return true;
        }
        return false;
    }
}
