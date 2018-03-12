package de.slgdev.schwarzes_brett.activity;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.res.ResourcesCompat;
import android.support.v4.widget.SwipeRefreshLayout;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseExpandableListAdapter;
import android.widget.ExpandableListView;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import java.util.Date;

import de.slgdev.leoapp.R;
import de.slgdev.leoapp.notification.NotificationHandler;
import de.slgdev.leoapp.sqlite.SQLiteConnectorSchwarzesBrett;
import de.slgdev.leoapp.utility.User;
import de.slgdev.leoapp.utility.Utils;
import de.slgdev.leoapp.utility.datastructure.List;
import de.slgdev.leoapp.view.ActivityStatus;
import de.slgdev.leoapp.view.LeoAppNavigationActivity;
import de.slgdev.schwarzes_brett.dialog.NewEntryDialog;
import de.slgdev.schwarzes_brett.task.DeleteEntryTask;
import de.slgdev.schwarzes_brett.task.FileDownloadTask;
import de.slgdev.schwarzes_brett.task.SyncNewsTask;
import de.slgdev.schwarzes_brett.task.UpdateViewTrackerTask;
import de.slgdev.schwarzes_brett.utility.Entry;
import de.slgdev.schwarzes_brett.utility.SchwarzesBrettUtils;

/**
 * SchwarzesBrettActivity.
 * <p>
 * Anzeige des digitalen Schwarzen-Bretts, hier wird eine ausklappbare Liste mit allen Neuigkeiten, die entweder per Webinterface oder per App hinzugefügt wurden, angezeigt.
 * Einzelne Einträge lassen sich für mehr Informationen aufklappen. Mit einem ausreichenden Permissionlevel wird ein FAB mit der Option, neue Einträge zu verfassen, angezeigt.
 *
 * @author Gianni, Kim, Moritz
 * @version 2018.0803
 * @since 0.0.1
 */
public class SchwarzesBrettActivity extends LeoAppNavigationActivity {

    private static final int MY_PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE = 42;

    private static SQLiteConnectorSchwarzesBrett sqLiteConnector;
    private static SQLiteDatabase                sqLiteDatabase;

    private List<Entry> entries;

    private String rawLocation;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Utils.getNotificationManager().cancel(NotificationHandler.ID_SCHWARZES_BRETT);
        Utils.getController().registerSchwarzesBrettActivity(this);

        receive();

        if (sqLiteConnector == null)
            sqLiteConnector = new SQLiteConnectorSchwarzesBrett(Utils.getContext());
        if (sqLiteDatabase == null)
            sqLiteDatabase = sqLiteConnector.getReadableDatabase();

        initButton();
        initExpandableListView();
        initSwipeToRefresh();
    }

    @Override
    protected int getContentView() {
        return R.layout.activity_schwarzesbrett;
    }

    @Override
    protected int getDrawerLayoutId() {
        return R.id.drawerLayout;
    }

    @Override
    protected int getNavigationId() {
        return R.id.navigationView;
    }

    @Override
    protected int getToolbarId() {
        return R.id.toolbar;
    }

    @Override
    protected int getToolbarTextId() {
        return R.string.title_news;
    }

    @Override
    protected int getNavigationHighlightId() {
        return R.id.newsboard;
    }

    @Override
    protected void onResume() {
        super.onResume();
        Utils.getNotificationManager().cancel(NotificationHandler.ID_SCHWARZES_BRETT);
        receive();
    }

    @Override
    protected void onPause() {
        super.onPause();
        receive();
    }

    @Override
    protected String getActivityTag() {
        return "SchwarzesBrettActivity";
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == MY_PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE &&
                grantResults.length > 0 &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            new FileDownloadTask().execute(rawLocation);
        }
    }

    @Override
    public void finish() {
        super.finish();

        sqLiteDatabase.close();
        sqLiteConnector.close();
        sqLiteDatabase = null;
        sqLiteConnector = null;

        Utils.getController().registerSchwarzesBrettActivity(null);
    }

    private void initSwipeToRefresh() {
        final SwipeRefreshLayout swipeLayout = findViewById(R.id.swipeRefreshLayout);
        swipeLayout.setOnRefreshListener(() -> new SyncNewsTask(swipeLayout).execute());

        swipeLayout.setColorSchemeColors(ResourcesCompat.getColor(getResources(), R.color.colorPrimary, null));
    }

    private void initButton() {
        View button = findViewById(R.id.floatingActionButton);

        if (Utils.getUserPermission() >= User.PERMISSION_LEHRER) {
            button.setVisibility(View.VISIBLE);
            button.setOnClickListener(v -> new NewEntryDialog(SchwarzesBrettActivity.this).show());
        }
    }

    private void initExpandableListView() {
        createGroupList();

        ExpandableListView expandableListView = findViewById(R.id.expandableListView);

        ExpandableListAdapter expandableListAdapter = new ExpandableListAdapter(entries);
        expandableListView.setAdapter(expandableListAdapter);

        expandableListView.setOnGroupClickListener((parent, v, groupPosition, id) -> {
            int remoteID = entries.toIndex(groupPosition).getContent().id;
            if (Utils.getUserPermission() == User.PERMISSION_LEHRER || SchwarzesBrettUtils.messageAlreadySeen(remoteID))
                return false;
            String cache = Utils.getController().getPreferences().getString("pref_key_cache_vieweditems", "");
            if (!cache.equals(""))
                cache += "-";
            Utils.getController().getPreferences()
                    .edit()
                    .putString("pref_key_cache_vieweditems", cache + "1:" + remoteID)
                    .apply();
            if (Utils.isNetworkAvailable())
                new UpdateViewTrackerTask().execute(remoteID);
            return false;
        });

        if (entries.size() == 0) {
            findViewById(R.id.noEntries).setVisibility(View.VISIBLE);
        } else {
            findViewById(R.id.noEntries).setVisibility(View.GONE);
        }
    }

    private void createGroupList() {
        entries = new List<>();

        String stufe = Utils.getUserStufe();
        Cursor cursor;
        switch (stufe) {
            case "":
            case "TEA":
                cursor = sqLiteDatabase.query(
                        SQLiteConnectorSchwarzesBrett.TABLE_EINTRAEGE,
                        new String[]{
                                SQLiteConnectorSchwarzesBrett.EINTRAEGE_ADRESSAT,
                                SQLiteConnectorSchwarzesBrett.EINTRAEGE_TITEL,
                                SQLiteConnectorSchwarzesBrett.EINTRAEGE_INHALT,
                                SQLiteConnectorSchwarzesBrett.EINTRAEGE_ERSTELLDATUM,
                                SQLiteConnectorSchwarzesBrett.EINTRAEGE_ABLAUFDATUM,
                                SQLiteConnectorSchwarzesBrett.EINTRAEGE_ANHANG,
                                SQLiteConnectorSchwarzesBrett.EINTRAEGE_VIEWS,
                                SQLiteConnectorSchwarzesBrett.EINTRAEGE_REMOTE_ID},
                        null,
                        null,
                        null,
                        null,
                        null
                );
                break;
            case "EF":
            case "Q1":
            case "Q2":
                cursor = sqLiteDatabase.query(SQLiteConnectorSchwarzesBrett.TABLE_EINTRAEGE, new String[]{SQLiteConnectorSchwarzesBrett.EINTRAEGE_ADRESSAT, SQLiteConnectorSchwarzesBrett.EINTRAEGE_TITEL, SQLiteConnectorSchwarzesBrett.EINTRAEGE_INHALT, SQLiteConnectorSchwarzesBrett.EINTRAEGE_ERSTELLDATUM, SQLiteConnectorSchwarzesBrett.EINTRAEGE_ABLAUFDATUM, SQLiteConnectorSchwarzesBrett.EINTRAEGE_ANHANG, SQLiteConnectorSchwarzesBrett.EINTRAEGE_VIEWS, SQLiteConnectorSchwarzesBrett.EINTRAEGE_REMOTE_ID}, SQLiteConnectorSchwarzesBrett.EINTRAEGE_ADRESSAT + " = '" + stufe + "' OR " + SQLiteConnectorSchwarzesBrett.EINTRAEGE_ADRESSAT + " = 'Sek II' OR " + SQLiteConnectorSchwarzesBrett.EINTRAEGE_ADRESSAT + " = 'Alle'", null, null, null, null);
                break;
            default:
                cursor = sqLiteDatabase.query(SQLiteConnectorSchwarzesBrett.TABLE_EINTRAEGE, new String[]{SQLiteConnectorSchwarzesBrett.EINTRAEGE_ADRESSAT, SQLiteConnectorSchwarzesBrett.EINTRAEGE_TITEL, SQLiteConnectorSchwarzesBrett.EINTRAEGE_INHALT, SQLiteConnectorSchwarzesBrett.EINTRAEGE_ERSTELLDATUM, SQLiteConnectorSchwarzesBrett.EINTRAEGE_ABLAUFDATUM, SQLiteConnectorSchwarzesBrett.EINTRAEGE_ANHANG, SQLiteConnectorSchwarzesBrett.EINTRAEGE_VIEWS, SQLiteConnectorSchwarzesBrett.EINTRAEGE_REMOTE_ID}, SQLiteConnectorSchwarzesBrett.EINTRAEGE_ADRESSAT + " = '" + stufe + "' OR " + SQLiteConnectorSchwarzesBrett.EINTRAEGE_ADRESSAT + " = 'Sek I' OR " + SQLiteConnectorSchwarzesBrett.EINTRAEGE_ADRESSAT + " = 'Alle'", null, null, null, null);
                break;
        }


        for (cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {

            Date erstelldatum = new Date(cursor.getLong(3));
            Date ablaufdatum  = new Date(cursor.getLong(4));

            Entry entry = new Entry(
                    cursor.getInt(7),
                    cursor.getString(0),
                    cursor.getString(1),
                    cursor.getString(2).replace("\\\\", "\n"),
                    cursor.getInt(6),
                    erstelldatum,
                    ablaufdatum,
                    cursor.getString(5)
            );

            entries.append(entry);
        }
        cursor.close();
    }

    private void receive() {
        new SyncNewsTask(null).execute();
    }

    public void refreshUI() {
        initExpandableListView();
    }

    private class ExpandableListAdapter extends BaseExpandableListAdapter {

        private final List<Entry> entries;

        ExpandableListAdapter(List<Entry> entries) {
            this.entries = entries;
        }

        @SuppressLint("SetTextI18n")
        @Override
        public View getGroupView(int groupPosition, boolean isExpanded, View convertView, ViewGroup parent) {

            convertView = getLayoutInflater().inflate(R.layout.list_item_expandable_title, null);

            TextView textView = convertView.findViewById(R.id.titleKlausur);
            textView.setText((String) getGroup(groupPosition));

            TextView textViewStufe = convertView.findViewById(R.id.textViewStufe);
            textViewStufe.setText(entries.toIndex(groupPosition).getContent().to);

            if (Utils.getUserPermission() >= User.PERMISSION_LEHRER) {

                TextView textViewViews = convertView.findViewById(R.id.textViewViews);
                textViewViews.setVisibility(View.VISIBLE);
                int views = entries.toIndex(groupPosition).getContent().views;
                String viewString = views > 999 ? "999+" : String.valueOf(views);
                textViewViews.setText(viewString);

            } else {

                RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) textViewStufe.getLayoutParams();
                params.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
                textViewStufe.setLayoutParams(params);

            }

            return convertView;
        }

        @Override
        public View getChildView(final int groupPosition, int childPosition, boolean isLastChild, View convertView, ViewGroup parent) {

            if (isLastChild) {

                convertView = getLayoutInflater().inflate(R.layout.list_item_expandable_child_alt, null);
                final TextView textViewDate = convertView.findViewById(R.id.titleKlausur);
                textViewDate.setText(entries.toIndex(groupPosition).getContent().getFormattedDates());

                final ImageView button = convertView.findViewById(R.id.delete);

                if (Utils.getUserPermission() < User.PERMISSION_LEHRER)
                    button.setVisibility(View.GONE);

                button.setOnClickListener(v -> {

                    entries.toIndex(groupPosition).remove();
                    notifyDataSetChanged();

                    Snackbar snackbar = Snackbar.make(findViewById(R.id.coordinatorLayout), R.string.entry_deleted, Snackbar.LENGTH_SHORT);

                    snackbar.setActionTextColor(ContextCompat.getColor(Utils.getContext(), R.color.colorPrimary));
                    snackbar.setAction(Utils.getContext().getString(R.string.snackbar_undo), v1 -> {});
                    snackbar.addCallback(new Snackbar.Callback() {

                        @Override
                        public void onDismissed(Snackbar snackbar, int event) {

                            if (event == DISMISS_EVENT_ACTION)
                                initExpandableListView();
                            else
                                new DeleteEntryTask()
                                        .addListener(params -> {
                                            if (Utils.getController().getSchwarzesBrettActivity() == null) {
                                                sqLiteDatabase.close();
                                                sqLiteConnector.close();
                                                sqLiteDatabase = null;
                                                sqLiteConnector = null;
                                            }
                                        })
                                        .execute(entries.toIndex(groupPosition).getContent().id);

                        }

                        @Override
                        public void onShown(Snackbar snackbar) {
                            //stub
                        }

                    });

                    snackbar.show();

                });

            } else if (childPosition == 0) {

                convertView = getLayoutInflater().inflate(R.layout.list_item_expandable_child, null);
                final TextView textView = convertView.findViewById(R.id.titleKlausur);
                textView.setText(entries.toIndex(groupPosition).getContent().content);

            } else {

                convertView = getLayoutInflater().inflate(R.layout.list_item_expandable_child_alt, null);

                final String location = entries.toIndex(groupPosition).getContent().file;

                final View.OnClickListener listener = v -> {
                    rawLocation = location;

                    if (ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                        ActivityCompat.requestPermissions(SchwarzesBrettActivity.this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, MY_PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE);
                    } else {
                        new FileDownloadTask().execute(rawLocation);
                    }
                };

                final ImageView iv = convertView.findViewById(R.id.imageViewIcon);
                iv.setImageResource(R.drawable.ic_file_download);
                iv.setColorFilter(Color.rgb(0x00, 0x91, 0xea));
                iv.setOnClickListener(listener);

                final TextView textView = convertView.findViewById(R.id.title);
                textView.setText(location.substring(location.lastIndexOf('/') + 1));
                textView.setPaintFlags(textView.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
                textView.setOnClickListener(listener);

            }

            return convertView;
        }

        @Override
        public int getGroupCount() {
            return entries.size();
        }

        @Override
        public int getChildrenCount(int groupPosition) {
            return entries.toIndex(groupPosition).getContent().file == null ? 2 : 3;
        }

        @Override
        public Object getGroup(int groupPosition) {
            return entries.toIndex(groupPosition).getContent().title;
        }

        @Override
        public Object getChild(int groupPosition, int childPosition) {
            return null;
        }

        @Override
        public long getGroupId(int groupPosition) {
            return groupPosition;
        }

        @Override
        public long getChildId(int groupPosition, int childPosition) {
            return childPosition;
        }

        @Override
        public boolean hasStableIds() {
            return true;
        }

        @Override
        public boolean isChildSelectable(int groupPosition, int childPosition) {
            return false;
        }
    }
}