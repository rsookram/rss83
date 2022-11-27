package io.github.rsookram.rss83;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONException;
import org.json.JSONObject;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.net.MalformedURLException;
import java.net.URL;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class Item {

    public static final URL[] FEED_URLS = {
            // TODO: Define feeds
            url(""),
    };

    public final String feed;
    public final String title;
    public final String url;
    public final long timestampSeconds;

    public Item(String feed, String title, String url, long timestampSeconds) {
        this.feed = feed;
        this.title = title;
        this.url = url;
        this.timestampSeconds = timestampSeconds;
    }

    public String serialize() {
        try {
            return new JSONObject()
                    .put("feed", feed)
                    .put("title", title)
                    .put("url", url)
                    .put("timestamp", timestampSeconds)
                    .toString();
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }

    public static Item deserialize(String str) {
        try {
            JSONObject jsonObject = new JSONObject(str);

            return new Item(
                    jsonObject.getString("feed"),
                    jsonObject.getString("title"),
                    jsonObject.getString("url"),
                    jsonObject.getInt("timestamp")
            );
        } catch (JSONException e) {
            return null;
        }
    }

    public static List<Item> getAll(Context context) {
        SharedPreferences prefs = prefs(context);

        return prefs
                .getAll()
                .values()
                .stream()
                .filter(o -> o instanceof String)
                .map(o -> Item.deserialize((String) o))
                .filter(Objects::nonNull)
                // Sort newest first
                .sorted(Comparator.comparingLong(item -> -item.timestampSeconds))
                .collect(Collectors.toList());
    }

    public static void update(Context context, Map<URL, Item> results) {
        SharedPreferences.Editor editor = prefs(context).edit();
        for (Map.Entry<URL, Item> result : results.entrySet()) {
            editor.putString(result.getKey().toString(), result.getValue().serialize());
        }
        editor.apply();
    }

    public static void cleanup(Context context) {
        SharedPreferences prefs = prefs(context);
        Set<String> feedsToRemove = new HashSet<>(prefs.getAll().keySet());

        Arrays.stream(FEED_URLS)
                .map(URL::toString)
                .forEach(feedsToRemove::remove);

        if (!feedsToRemove.isEmpty()) {
            SharedPreferences.Editor editor = prefs.edit();
            for (String url : feedsToRemove) {
                editor.remove(url);
            }
            editor.apply();
        }
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences("items", Context.MODE_PRIVATE);
    }

    public static Item parse(URL feedUrl, Document document) {
        Node nameNode = document.getElementsByTagName("title").item(0);

        String name = nameNode != null ? nameNode.getTextContent() : null;
        name = name == null || name.isEmpty() ? feedUrl.toString() : name;

        NodeList entryNodes = document.getElementsByTagName("entry");
        if (entryNodes.getLength() > 0) {
            return parseAtom(entryNodes.item(0), name);
        } else {
            NodeList itemNodes = document.getElementsByTagName("item");
            if (itemNodes.getLength() > 0) {
                return parseRss(itemNodes.item(0), name);
            }
        }
        return null;
    }

    private static Item parseAtom(Node node, String name) {
        String title = "";
        String url = "";
        Instant instant = null;

        for (int i = 0; i < node.getChildNodes().getLength(); i++) {
            Node child = node.getChildNodes().item(i);

            switch (child.getNodeName()) {
                case "title": {
                    title = child.getTextContent();
                    break;
                }
                case "link": {
                    if (child instanceof Element) {
                        url = ((Element) child).getAttribute("href");
                    }
                    break;
                }
                case "updated":
                case "published": {
                    if (instant == null) {
                        instant = getTimestamp(
                                child.getTextContent(),
                                DateTimeFormatter.ISO_OFFSET_DATE_TIME
                        );
                    }
                    break;
                }
            }
        }

        return new Item(
                name,
                title,
                url,
                instant != null ? instant.getEpochSecond() : getTimestampFallback()
        );
    }

    private static Item parseRss(Node node, String name) {
        String title = "";
        String url = "";
        String timestamp = "";

        for (int i = 0; i < node.getChildNodes().getLength(); i++) {
            Node child = node.getChildNodes().item(i);

            switch (child.getNodeName()) {
                case "title": {
                    title = child.getTextContent();
                    break;
                }
                case "link": {
                    url = child.getTextContent();
                    break;
                }
                case "pubDate": {
                    timestamp = child.getTextContent();
                    break;
                }
            }
        }

        Instant instant = getTimestamp(
                timestamp,
                DateTimeFormatter.RFC_1123_DATE_TIME
        );

        return new Item(
                name,
                title,
                url,
                instant != null ? instant.getEpochSecond() : getTimestampFallback()
        );
    }

    private static Instant getTimestamp(String timestamp, DateTimeFormatter formatter) {
        Instant instant = null;

        try {
            instant = formatter.parse(timestamp, Instant::from);

            // Disallow entries from the future
            Instant now = Instant.now();
            if (instant.isAfter(now)) {
                instant = now;
            }
        } catch (DateTimeParseException ignored) {
        }

        return instant;
    }

    private static long getTimestampFallback() {
        return OffsetDateTime.now()
                .withHour(0)
                .withMinute(0)
                .toEpochSecond();
    }

    private static URL url(String url) {
        try {
            return new URL(url);
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
    }
}
