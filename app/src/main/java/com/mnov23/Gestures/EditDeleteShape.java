package com.mnov23.Gestures;

import android.database.Cursor;
import android.os.Bundle;
// decrepit. like an old man crapping his pants. upon being forced to read an anti-communist manifesto against his wishes
//import android.support.v4.app.Fragment;
//import android.support.v4.app.LoaderManager;
//import android.support.v4.content.CursorLoader;
//import android.support.v4.content.Loader;
import androidx.fragment.app.Fragment;
import androidx.loader.app.LoaderManager;
import androidx.loader.content.CursorLoader;
import androidx.loader.content.Loader;

// cont.
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;

import com.mnov23.Gestures.provider.SchemeShapes;

/**
 * A simple {@link Fragment} subclass.
 */
public class EditDeleteShape extends Fragment implements LoaderManager.LoaderCallbacks<Cursor> {
    LayoutInflater myInflater = null;
    ListView shapesListView;        // renamed from lvList
    ItemCursorAdapter shapesListViewAdapter;  // renamed from listViewAdapter

    public EditDeleteShape() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getLoaderManager().initLoader(0, null, this);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        myInflater = inflater;
        View v = inflater.inflate(R.layout.fragment_edit_delete_shape, container, false);

        shapesListView = (ListView) v.findViewById(R.id.list_edit_delete_shapes);
        shapesListView.addHeaderView(myInflater.inflate(R.layout.edit_delete_shape_list_header, null));

        shapesListViewAdapter = new ItemCursorAdapter(getContext(), null, getActivity().getSupportFragmentManager()); // null is an empty cursor
        shapesListView.setAdapter(shapesListViewAdapter);

        return v;
    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        CursorLoader cursorLoader = new CursorLoader(getActivity(),
            SchemeShapes.Shape.CONTENT_URI,
            //VersionContract.Version.buildUri(2),
            SchemeShapes.Shape.PROJECTION,
            null,
            null,
            null
        );
        return cursorLoader;
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor cursor) {
        shapesListViewAdapter.swapCursor(cursor); // swap to refresh the current cursor.
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
        super.onResume();
    }
}
