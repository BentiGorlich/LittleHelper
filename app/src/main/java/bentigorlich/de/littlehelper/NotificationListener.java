package bentigorlich.de.littlehelper;

import android.app.Notification;
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

    //all the last notifications
    private ArrayList<StatusBarNotification> notifications = new ArrayList<>();

    private int speechID = 0;
    private TextToSpeech mtts;
    private CustomReceiver receiver = new CustomReceiver();

    private static boolean running = false;

    @Override
    public void onCreate() {
        if(running){
            this.stopSelf();
        }
        running = true;
        super.onCreate();
        System.out.println("NotificationListener has started");

        boolean hasPermission = false;
        if(checkCallingPermission("android.service.notification.NotificationListenerService") == PackageManager.PERMISSION_DENIED){
            hasPermission = false;
            Intent intent = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
            startActivity(intent);
        }else if(checkCallingPermission("android.service.notification.NotificationListenerService") == PackageManager.PERMISSION_GRANTED){
            hasPermission = true;
        }

        Log.i(TAG, "Permission to read notifications: " + String.valueOf(hasPermission));
        mtts = new TextToSpeech(this, this);
        mtts.setLanguage(Locale.GERMAN);

        this.registerReceiver(receiver, new IntentFilter(AudioManager.ACTION_HEADSET_PLUG));
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
    }

    @Override
    public void onListenerDisconnected() {
        System.out.println("Listener is disconnected");
        connected = false;
    }

    @Override
    public void onNotificationPosted(StatusBarNotification note) {
        try{
            if(connected && ttsInit && isHeadsetPluggedIn){
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
        if(!checkReplicate(note)) {
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

    protected class CustomReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            if(intent.getAction().equals(AudioManager.ACTION_HEADSET_PLUG)){
                if(intent.getIntArrayExtra("state") != null) {
                    if (intent.getIntArrayExtra("state")[0] == 1) {
                        isHeadsetPluggedIn = true;
                    } else {
                        isHeadsetPluggedIn = false;
                    }
                }
            }
        }
    }

}