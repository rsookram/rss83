package io.github.rsookram.rss83;

import android.app.job.JobParameters;
import android.app.job.JobService;

import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import javax.net.ssl.HttpsURLConnection;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

public class SyncJobService extends JobService {

    private CompletableFuture<?> future;

    @Override
    public boolean onStartJob(JobParameters params) {
        if (future == null || future.isDone()) {
            future = CompletableFuture.runAsync(() -> sync(params));
        }
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        future.cancel(false);
        return false;
    }

    @Override
    public void onNetworkChanged(JobParameters params) {
        // Override to silence log from superclass
    }

    private void sync(JobParameters params) {
        Map<URL, Item> results = new HashMap<>(Item.FEED_URLS.length);

        for (URL feedUrl : Item.FEED_URLS) {
            if (future.isCancelled()) {
                break;
            }

            Document document = get(feedUrl);
            if (document == null) {
                continue;
            }

            Item item = Item.parse(feedUrl, document);
            if (item != null) {
                results.put(feedUrl, item);
            }
        }

        Item.update(this, results);

        jobFinished(params, false);
    }

    private Document get(URL url) {
        try {
            HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
            connection.setRequestMethod("GET");

            if (connection.getResponseCode() != 200) {
                return null;
            }

            DocumentBuilder documentBuilder = DocumentBuilderFactory.newInstance()
                    .newDocumentBuilder();

            try (InputStream stream = connection.getInputStream()) {
                return documentBuilder.parse(stream);
            }
        } catch (IOException | SAXException e) {
            return null;
        } catch (ParserConfigurationException e) {
            throw new RuntimeException(e);
        }
    }
}
