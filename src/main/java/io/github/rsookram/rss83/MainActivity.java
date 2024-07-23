package io.github.rsookram.rss83;

import android.app.Activity;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Insets;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import java.util.List;
import java.util.concurrent.TimeUnit;

public class MainActivity extends Activity {

    private static boolean hasSetUpSync = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.main_activity);

        if (!hasSetUpSync) {
            hasSetUpSync = true;

            JobScheduler jobScheduler = getSystemService(JobScheduler.class);
            jobScheduler.schedule(
                    new JobInfo.Builder(1, new ComponentName(getApplicationContext(), SyncJobService.class))
                            .setPeriodic(TimeUnit.HOURS.toMillis(24))
                            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_UNMETERED)
                            .setRequiresBatteryNotLow(true)
                            .build()
            );

            Item.cleanup(this);
        }

        List<Item> items = Item.getAll(this);

        ListView listView = findViewById(R.id.list);
        listView.setAdapter(new Adapter(this, items));

        applySystemUiVisibility(listView);
    }

    private void applySystemUiVisibility(View content) {
        getWindow().setDecorFitsSystemWindows(false);

        content.setOnApplyWindowInsetsListener((v, insets) -> {
            Insets systemInsets = insets.getInsets(WindowInsets.Type.systemBars());
            v.setPadding(
                    systemInsets.left, systemInsets.top, systemInsets.right, systemInsets.bottom
            );

            return insets;
        });
    }

    private static class Adapter extends ArrayAdapter<Item> {

        Adapter(Context context, List<Item> items) {
            super(context, -1 /* unused */, items);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            Context context = parent.getContext();

            ViewGroup view = (ViewGroup) convertView;
            if (view == null) {
                view = (ViewGroup) LayoutInflater.from(context)
                        .inflate(R.layout.item, parent, false);
            }

            Item item = getItem(position);

            ((TextView) view.findViewById(R.id.title)).setText(item.title);
            ((TextView) view.findViewById(R.id.feed)).setText(item.feed);

            view.setOnClickListener(v ->
                    v.getContext()
                            .startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(item.url)))
            );

            return view;
        }
    }
}
