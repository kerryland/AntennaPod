package de.danoeh.antennapod.storage.importexport;

import android.Manifest;
import android.app.Activity;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.documentfile.provider.DocumentFile;
import de.danoeh.antennapod.event.FeedUpdateRunningEvent;
import de.danoeh.antennapod.event.MessageEvent;
import de.danoeh.antennapod.storage.preferences.UserPreferences;
import de.danoeh.antennapod.ui.notifications.NotificationUtils;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

public class AutomaticDatabaseExportWorker {
    private static final AutomaticDatabaseExportWorker INSTANCE = new AutomaticDatabaseExportWorker();
    private static Context appContext;
    private static boolean registered;

    public static void init(Context context) {
        appContext = context.getApplicationContext();
        if (!registered) {
            EventBus.getDefault().register(INSTANCE);
            registered = true;
        }
    }

    private AutomaticDatabaseExportWorker() {
    }

    @Subscribe(threadMode = ThreadMode.POSTING)
    public void onFeedUpdateFinished(FeedUpdateRunningEvent event) {
        if (event.state == FeedUpdateRunningEvent.State.FINISHED) {
            runIfNeeded(appContext);
        }
    }

    public static void runIfNeeded(Context context) {
        String folderUri = UserPreferences.getAutomaticExportFolder();
        if (folderUri == null) {
            return;
        }
        long lastBackup = UserPreferences.getLastBackupTime();
        if (lastBackup > 0 && System.currentTimeMillis() - lastBackup < TimeUnit.DAYS.toMillis(1)) {
            return;
        }
        try {
            export(context, folderUri);
        } catch (Exception e) {
            showErrorNotification(context, e);
        }
    }

    private static void export(Context context, String folderUri) throws IOException {
        DocumentFile documentFolder = DocumentFile.fromTreeUri(context, Uri.parse(folderUri));
        if (documentFolder == null || !documentFolder.exists() || !documentFolder.canWrite()) {
            promptFolderReselection(context);
            return;
        }
        String filename = String.format("AntennaPodBackup-%s.db",
                new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date()));
        DocumentFile exportFile = documentFolder.createFile("application/x-sqlite3", filename);
        if (exportFile == null || !exportFile.canWrite()) {
            throw new IOException("Unable to create export file");
        }
        DatabaseExporter.exportToDocument(exportFile.getUri(), context);
        List<DocumentFile> files = new ArrayList<>(Arrays.asList(documentFolder.listFiles()));
        Iterator<DocumentFile> itr = files.iterator();
        while (itr.hasNext()) {
            DocumentFile file = itr.next();
            if (!file.getName().matches("AntennaPodBackup-\\d\\d\\d\\d-\\d\\d-\\d\\d\\.db")) {
                itr.remove();
            }
        }
        Collections.sort(files, (o1, o2) -> Long.compare(o2.lastModified(), o1.lastModified()));
        boolean hasDeletionFailed = false;
        for (int i = 5; i < files.size(); i++) {
            boolean isDeleted = files.get(i).delete();
            if (!hasDeletionFailed && !isDeleted) {
                hasDeletionFailed = true;
            }
        }
        if (hasDeletionFailed) {
            throw new IOException("Unable to delete some database backup files");
        }
    }

    private static void promptFolderReselection(Context context) {
        String folderUri = UserPreferences.getAutomaticExportFolder();
        if (folderUri == null) {
            return;
        }
        String message = context.getString(R.string.automatic_database_export_reselect_folder);
        if (EventBus.getDefault().hasSubscriberForEvent(MessageEvent.class)) {
            EventBus.getDefault().post(new MessageEvent(message,
                    AutomaticDatabaseExportWorker::openAutomaticBackupReselect,
                    context.getString(R.string.automatic_database_export_reselect_action), true));
            return;
        }

        Intent intent = new Intent();
        intent.setClassName(context,
                "de.danoeh.antennapod.ui.screen.preferences.PreferenceActivity");
        intent.putExtra("OpenAutomaticBackup", true);
        PendingIntent pendingIntent = PendingIntent.getActivity(context,
                R.id.pending_intent_backup_reselect, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification notification = new NotificationCompat.Builder(context,
                        NotificationUtils.CHANNEL_ID_USER_ACTION)
                .setContentTitle(context.getString(
                        R.string.automatic_database_export_folder_inaccessible))
                .setContentText(message)
                .setContentIntent(pendingIntent)
                .setSmallIcon(R.drawable.ic_notification_sync_error)
                .setAutoCancel(true)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .build();
        NotificationManager nm = (NotificationManager) context
                .getSystemService(Context.NOTIFICATION_SERVICE);
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED) {
            nm.notify(R.id.notification_id_backup_reselect, notification);
        }
    }

    private static void openAutomaticBackupReselect(Context context) {
        Intent intent = new Intent();
        intent.setClassName(context,
                "de.danoeh.antennapod.ui.screen.preferences.PreferenceActivity");
        intent.putExtra("OpenAutomaticBackup", true);
        if (!(context instanceof Activity)) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        context.startActivity(intent);
    }

    private static void showErrorNotification(Context context, Exception exception) {
        final String description = context.getString(R.string.automatic_database_export_error)
                + " " + exception.getMessage();
        if (EventBus.getDefault().hasSubscriberForEvent(MessageEvent.class)) {
            EventBus.getDefault().post(new MessageEvent(description));
            return;
        }

        Intent intent = context.getPackageManager().getLaunchIntentForPackage(
                context.getPackageName());
        PendingIntent pendingIntent = PendingIntent.getActivity(context,
                R.id.pending_intent_backup_error, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification notification = new NotificationCompat.Builder(context,
                        NotificationUtils.CHANNEL_ID_SYNC_ERROR)
                .setContentTitle(context.getString(R.string.automatic_database_export_error))
                .setContentText(exception.getMessage())
                .setStyle(new NotificationCompat.BigTextStyle().bigText(description))
                .setContentIntent(pendingIntent)
                .setSmallIcon(R.drawable.ic_notification_sync_error)
                .setAutoCancel(true)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .build();
        NotificationManager nm = (NotificationManager) context
                .getSystemService(Context.NOTIFICATION_SERVICE);
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED) {
            nm.notify(R.id.notification_id_backup_error, notification);
        } else {
            showToast(context, description);
        }
    }

    private static void showToast(Context context, String message) {
        new Handler(Looper.getMainLooper()).post(() ->
                Toast.makeText(context, message, Toast.LENGTH_LONG).show());
    }
}
