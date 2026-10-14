package de.danoeh.antennapod.playback.service.internal;

import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.annotation.StringRes;
import androidx.annotation.WorkerThread;
import androidx.media3.common.MediaItem;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.session.CommandButton;
import androidx.media3.session.MediaConstants;
import androidx.media3.session.SessionCommand;
import com.google.common.collect.ImmutableList;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.net.download.serviceinterface.DownloadServiceInterface;
import de.danoeh.antennapod.playback.service.R;
import de.danoeh.antennapod.storage.database.DBReader;
import de.danoeh.antennapod.storage.database.DBWriter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class SwipeActionCommands {
    public static final String TAG_QUEUE = "QueueFragment";
    public static final String TAG_DOWNLOADS = "DownloadsFragment";
    public static final String TAG_EPISODES = "EpisodesFragment";
    public static final String TAG_FEED = "ItemlistFragment";

    private static final String PREF_NAME = "SwipeActionsPrefs";
    private static final String KEY_PREFIX_SWIPEACTIONS = "PrefSwipeActions";
    private static final String KEY_PREFIX_NO_ACTION = "PrefNoSwipeAction";
    private static final String COMMAND_PREFIX = "swipe_action_";

    private static final String ADD_TO_QUEUE = "ADD_TO_QUEUE";
    private static final String REMOVE_FROM_INBOX = "REMOVE_FROM_INBOX";
    private static final String START_DOWNLOAD = "START_DOWNLOAD";
    private static final String MARK_FAV = "MARK_FAV";
    private static final String REMOVE_FROM_FAVORITES = "REMOVE_FROM_FAVORITES";
    private static final String TOGGLE_PLAYED = "MARK_PLAYED";
    private static final String REMOVE_FROM_QUEUE = "REMOVE_FROM_QUEUE";
    private static final String DELETE = "DELETE";
    private static final String REMOVE_FROM_HISTORY = "REMOVE_FROM_HISTORY";
    private static final String MOVE_TO_TOP = "MOVE_TO_TOP";
    private static final String MOVE_TO_BOTTOM = "MOVE_TO_BOTTOM";

    private static final List<Action> ACTIONS = Arrays.asList(
            new Action(ADD_TO_QUEUE, R.string.add_to_queue_label, R.drawable.ic_playlist_play_black),
            new Action(REMOVE_FROM_INBOX, R.string.remove_inbox_label, R.drawable.ic_check_black),
            new Action(START_DOWNLOAD, R.string.download_label, R.drawable.ic_download_black),
            new Action(MARK_FAV, R.string.add_to_favorite_label, R.drawable.ic_star_black),
            new Action(REMOVE_FROM_FAVORITES, R.string.remove_from_favorite_label, R.drawable.ic_star_border_black),
            new Action(TOGGLE_PLAYED, R.string.toggle_played_label, R.drawable.ic_mark_played_black),
            new Action(REMOVE_FROM_QUEUE, R.string.remove_from_queue_label, R.drawable.ic_playlist_remove_black),
            new Action(DELETE, R.string.delete_label, R.drawable.ic_delete_black),
            new Action(REMOVE_FROM_HISTORY, R.string.remove_history_label, R.drawable.ic_history_remove_black),
            new Action(MOVE_TO_TOP, R.string.move_to_top_label, R.drawable.ic_arrow_full_up_black),
            new Action(MOVE_TO_BOTTOM, R.string.move_to_bottom_label, R.drawable.ic_arrow_full_down_black));

    private static class Action {
        final String id;
        @StringRes
        final int title;
        @DrawableRes
        final int icon;

        Action(String id, @StringRes int title, @DrawableRes int icon) {
            this.id = id;
            this.title = title;
            this.icon = icon;
        }
    }

    public static List<CommandButton> getCommandButtons(Context context) {
        List<CommandButton> buttons = new ArrayList<>();
        for (Action action : ACTIONS) {
            Uri iconUri = new Uri.Builder()
                    .scheme(ContentResolver.SCHEME_ANDROID_RESOURCE)
                    .authority(context.getResources().getResourcePackageName(action.icon))
                    .appendPath(context.getResources().getResourceTypeName(action.icon))
                    .appendPath(context.getResources().getResourceEntryName(action.icon))
                    .build();
            buttons.add(new CommandButton.Builder(CommandButton.ICON_UNDEFINED)
                    .setSessionCommand(getSessionCommand(action.id))
                    .setDisplayName(context.getString(action.title))
                    .setIconUri(iconUri)
                    .build());
        }
        return buttons;
    }

    public static List<SessionCommand> getSessionCommands() {
        List<SessionCommand> commands = new ArrayList<>();
        for (Action action : ACTIONS) {
            commands.add(getSessionCommand(action.id));
        }
        return commands;
    }

    private static SessionCommand getSessionCommand(String actionId) {
        return new SessionCommand(COMMAND_PREFIX + actionId, Bundle.EMPTY);
    }

    public static boolean isSwipeActionCommand(SessionCommand command) {
        return command.customAction.startsWith(COMMAND_PREFIX);
    }

    @OptIn(markerClass = UnstableApi.class)
    public static ImmutableList<MediaItem> addToMediaItems(Context context, @Nullable String tag,
                                                           ImmutableList<MediaItem> mediaItems) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        if (tag == null || !prefs.getBoolean(KEY_PREFIX_NO_ACTION + tag, true)) {
            return mediaItems;
        }
        List<String> commands = new ArrayList<>();
        for (String actionId : prefs.getString(KEY_PREFIX_SWIPEACTIONS + tag, "").split(",")) {
            for (Action action : ACTIONS) {
                String command = getSessionCommand(action.id).customAction;
                if (action.id.equals(actionId) && !commands.contains(command)) {
                    commands.add(command);
                }
            }
        }
        if (commands.isEmpty()) {
            return mediaItems;
        }
        ImmutableList.Builder<MediaItem> builder = ImmutableList.builder();
        for (MediaItem mediaItem : mediaItems) {
            builder.add(mediaItem.buildUpon()
                    .setMediaMetadata(mediaItem.mediaMetadata.buildUpon().setSupportedCommands(commands).build())
                    .build());
        }
        return builder.build();
    }

    @WorkerThread
    @Nullable
    @OptIn(markerClass = UnstableApi.class)
    public static FeedItem performAction(Context context, SessionCommand command, Bundle args) throws Exception {
        FeedMedia media = DBReader.getFeedMedia(Long.parseLong(args.getString(MediaConstants.EXTRA_KEY_MEDIA_ID)));
        if (media == null || media.getItem() == null) {
            return null;
        }
        FeedItem item = media.getItem();
        switch (command.customAction.substring(COMMAND_PREFIX.length())) {
            case ADD_TO_QUEUE:
                if (item.isTagged(FeedItem.TAG_QUEUE)) {
                    DBWriter.removeQueueItem(context, true, item).get();
                } else {
                    DBWriter.addQueueItem(context, item).get();
                }
                break;
            case REMOVE_FROM_INBOX:
                if (item.isNew()) {
                    DBWriter.markItemsPlayed(FeedItem.UNPLAYED, false, Collections.singletonList(item)).get();
                }
                break;
            case START_DOWNLOAD:
                if (!media.isDownloaded()
                        && !DownloadServiceInterface.get().isDownloadingEpisode(media.getDownloadUrl())) {
                    DownloadServiceInterface.get().downloadNow(context, item, false);
                }
                break;
            case MARK_FAV:
            case REMOVE_FROM_FAVORITES:
                DBWriter.toggleFavoriteItem(item).get();
                break;
            case TOGGLE_PLAYED:
                int newState = item.getPlayState() == FeedItem.UNPLAYED ? FeedItem.PLAYED : FeedItem.UNPLAYED;
                DBWriter.markItemsPlayed(newState, false, Collections.singletonList(item)).get();
                break;
            case REMOVE_FROM_QUEUE:
                DBWriter.removeQueueItem(context, true, item).get();
                break;
            case DELETE:
                if (media.isDownloaded() && item.getFeed() != null && !item.getFeed().isLocalFeed()) {
                    DBWriter.deleteFeedMediaOfItem(context, media).get();
                }
                break;
            case REMOVE_FROM_HISTORY:
                DBWriter.deleteFromPlaybackHistory(item).get();
                break;
            case MOVE_TO_TOP:
                DBWriter.moveQueueItemsToTop(Collections.singletonList(item)).get();
                break;
            case MOVE_TO_BOTTOM:
                DBWriter.moveQueueItemsToBottom(Collections.singletonList(item)).get();
                break;
            default:
                return null;
        }
        return item;
    }
}
