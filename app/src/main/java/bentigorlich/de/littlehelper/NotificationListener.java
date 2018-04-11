package bentigorlich.de.littlehelper;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.os.IBinder;
import android.provider.Settings;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.speech.tts.TextToSpeech;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.util.Log;

import com.vdurmont.emoji.EmojiParser;

import java.util.ArrayList;
import java.util.Locale;

import static android.content.ContentValues.TAG;

public class NotificationListener extends NotificationListenerService implements TextToSpeech.OnInitListener {

    //Settings
    private boolean alwaysOn;
    private boolean withHeadphonesOn;
    private boolean withHeadsetOn;

    //
    private boolean connected = false;
    private boolean ttsInit = false;
    private boolean isHeadsetPluggedIn = false;
    private boolean isRunning = true;

    //all the last notifications
    private ArrayList<StatusBarNotification> notifications = new ArrayList<>();

    private TextToSpeech mtts;
    private CustomReceiver receiver = new CustomReceiver();
    private Intent STOP_INTENT;
    private PendingIntent STOP_PENDING_INTENT;

    private static boolean running = true;

    @Override
    public void onCreate() {
        super.onCreate();
        if(checkCallingPermission("android.service.notification.NotificationListenerService") == PackageManager.PERMISSION_DENIED){
            boolean hasPermission = false;
            Log.i(TAG, "Permission to read notifications: " + String.valueOf(hasPermission));
            Intent intent = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
            startActivity(intent);
        }

        System.out.println("NotificationListener has started");

        STOP_INTENT = new Intent(this, CustomReceiver.class);
        STOP_INTENT.setAction("bentigorlich.de.TOGGLE_LISTENER");
        STOP_PENDING_INTENT = PendingIntent.getBroadcast(this, 0, STOP_INTENT, 0);

        updateNotifications();

        Log.i("main", "sent Notification");

        mtts = new TextToSpeech(this, this);
        mtts.setLanguage(Locale.GERMAN);

        IntentFilter filter = new IntentFilter();
        filter.addAction(AudioManager.ACTION_HEADSET_PLUG);
        filter.addAction(STOP_INTENT.getAction());

        this.registerReceiver(receiver, filter);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.i(TAG, "binded...");
        return super.onBind(intent);
    }

    @Override
    public void onListenerConnected() {
        connected = true;
        System.out.println("Listener is connected");
        this.startService(new Intent(this, this.getClass()));
    }

    @Override
    public void onListenerDisconnected() {
        System.out.println("Listener is disconnected");
        connected = false;
    }

    @Override
    public void onNotificationPosted(StatusBarNotification note) {
        try{
            if(connected && ttsInit && isHeadsetPluggedIn && isRunning){
                readNotification(note);
            }
        } catch (NullPointerException e){}
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        Log.i(TAG,"Notification removed");
    }

    private void saveLastNotification(StatusBarNotification note){
        String app = note.getPackageName();
        StatusBarNotification oldNote = null;
        for(StatusBarNotification sbn:notifications){
            if(app.equals(sbn.getPackageName())){
                oldNote = sbn;
                break;
            }
        }
        notifications.remove(oldNote);
        notifications.add(note);
    }

    private boolean checkReplicate(StatusBarNotification note){
        for(StatusBarNotification sbn: notifications) {
            String oldTitle = sbn.getNotification().extras.getCharSequence(Notification.EXTRA_TITLE).toString();
            String oldText = sbn.getNotification().extras.getCharSequence(Notification.EXTRA_TEXT).toString();
            String newTitle = note.getNotification().extras.getCharSequence(Notification.EXTRA_TITLE).toString();
            String newText = note.getNotification().extras.getCharSequence(Notification.EXTRA_TEXT).toString();
            if (oldTitle.equals(newTitle) && oldText.equals(newText)) {
                return true;
            }
        }
        return false;
    }

    private void readNotification(StatusBarNotification note){
        if(!checkReplicate(note) && !note.getPackageName().equals(this.getPackageName())) {
            String title = note.getNotification().extras.getCharSequence(Notification.EXTRA_TITLE).toString();
            String text = note.getNotification().extras.getCharSequence(Notification.EXTRA_TEXT).toString();
            String toRead = title + " " + text;
            toRead = EmojiParser.removeAllEmojis(toRead);
            Log.i(TAG, "new Notification: " + toRead);
            mtts.speak(toRead, TextToSpeech.QUEUE_FLUSH, null);
        }
        saveLastNotification(note);
    }

    @Override
    public void onInit(int i) {
        Log.i(TAG,"TTS is initialized");
        ttsInit = true;
    }

    private void updateNotifications(){
        Log.i(TAG, "notifications got updated");
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this);

        if(isRunning){
            builder.setContentTitle("Notification Listener is running.");
            builder.setContentText("click to stop");
            builder.addAction(R.drawable.ic_notifications_black_24dp, "stop listener", STOP_PENDING_INTENT);
        }else{
            builder.setContentTitle("Notification Listener stopped.");
            builder.setContentText("click to start");
            builder.addAction(R.drawable.ic_notifications_black_24dp, "start listener", STOP_PENDING_INTENT);
        }

        builder.setCategory("Information");
        builder.setAutoCancel(false);
        builder.setContentIntent(STOP_PENDING_INTENT);
        builder.setPriority(NotificationCompat.PRIORITY_DEFAULT);
        builder.setSmallIcon(R.drawable.ic_notifications_black_24dp);
        NotificationManagerCompat manager = NotificationManagerCompat.from(this);
        manager.notify(0, builder.build());
    }

    protected class CustomReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            try {
                if (intent.getAction().equals(AudioManager.ACTION_HEADSET_PLUG)) {
                    if (intent.getIntExtra("state", 0) == 1) {
                        isHeadsetPluggedIn = true;
                    } else {
                        isHeadsetPluggedIn = false;
                    }
                } else if (intent.getAction().equals(STOP_INTENT.getAction())) {
                    if (isRunning) {
                        isRunning = false;
                        updateNotifications();
                    } else {
                        isRunning = true;
                        updateNotifications();
                    }
                }
            }catch(NullPointerException e){}
        }
    }

}