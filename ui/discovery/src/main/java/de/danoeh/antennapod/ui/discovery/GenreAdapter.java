package de.danoeh.antennapod.ui.discovery;

import android.content.Context;
import android.graphics.Typeface;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;

/**
 * Displays genre names in the browse grid, add a
 * "view all podcasts in this genre" header if there are subgenres.
 */
public class GenreAdapter extends ArrayAdapter<String> {
    private final int headerPosition;

    public GenreAdapter(Context context, int resource, int textViewResourceId, List<String> objects,
                        int headerPosition) {
        super(context, resource, textViewResourceId, objects);
        this.headerPosition = headerPosition;
    }

    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        View view = super.getView(position, convertView, parent);
        TextView textView = view.findViewById(R.id.txtvGenre);
        if (position == headerPosition) {
            textView.setTypeface(textView.getTypeface(), Typeface.BOLD);
        } else {
            textView.setTypeface(Typeface.DEFAULT);
        }
        return view;
    }
}
