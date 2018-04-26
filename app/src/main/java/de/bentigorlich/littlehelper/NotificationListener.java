package de.bentigorlich.littlehelper;

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.os.Build;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.speech.tts.TextToSpeech;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.util.Log;

import com.vdurmont.emoji.EmojiParser;

import java.util.ArrayList;
import java.util.Locale;

import static android.bluetooth.BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED;
import static android.bluetooth.BluetoothProfile.EXTRA_STATE;
import static android.bluetooth.BluetoothProfile.STATE_CONNECTED;
import static android.bluetooth.BluetoothProfile.STATE_DISCONNECTED;
import static android.content.ContentValues.TAG;
import static android.media.AudioManager.AUDIOFOCUS_GAIN;
import static android.media.AudioManager.AUDIOFOCUS_LOSS;
import static android.media.AudioManager.AUDIOFOCUS_LOSS_TRANSIENT;
import static android.media.AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK;
import static android.speech.tts.TextToSpeech.ACTION_TTS_QUEUE_PROCESSING_COMPLETED;

public class NotificationListener extends NotificationListenerService implements TextToSpeech.OnInitListener {

    public static final String STOP_INTENT_ACTION = "de.bentigorlich.LittleHelper.TOGGLE_LISTENER";

    //Settings
    private boolean alwaysOn;
    private boolean withHeadphonesOn;
    private boolean withHeadsetOn;
    private boolean withBluetoothHeadsetOn;

    //status
    public static boolean connected = false;

    private boolean ttsInit = false;
    private boolean isHeadsetPluggedIn = false;
    private boolean isBluetoothPluggedIn = false;
    private boolean isMicPluggedIn = false;
    private boolean isRunning = true;
    private boolean hasAudioFocus = false;

    //all the last notifications
    private ArrayList<StatusBarNotification> notifications = new ArrayList<>();

    private TextToSpeech mtts;
    private HeadsetPlugReceiver receiver_headset = new HeadsetPlugReceiver();
    private BluetoothConnectReceiver receiver_bluetooth = new BluetoothConnectReceiver();
    private NotificationButtonListener receiver_notification = new NotificationButtonListener();
    private TTSDoneListener tts_done = new TTSDoneListener();
    private Intent STOP_INTENT;
    private PendingIntent STOP_PENDING_INTENT;

    @Override
    public void onCreate() {
        super.onCreate();


        System.out.println("NotificationListener has started");


        updateNotifications();

        Log.i("toolbar", "sent Notification");

        mtts = new TextToSpeech(this, this);
        mtts.setLanguage(Locale.GERMAN);

        IntentFilter filter = new IntentFilter(AudioManager.ACTION_HEADSET_PLUG);
        this.registerReceiver(receiver_headset, filter);

        filter = new IntentFilter(STOP_INTENT_ACTION);
        this.registerReceiver(receiver_notification, filter);

        filter = new IntentFilter(ACTION_CONNECTION_STATE_CHANGED);
        this.registerReceiver(receiver_bluetooth, filter);

        filter = new IntentFilter(ACTION_TTS_QUEUE_PROCESSING_COMPLETED);
        this.registerReceiver(tts_done, filter);
    }

    @Override
    public void onDestroy() {
        removeNotification();
        unregisterReceiver(this.receiver_notification);
        unregisterReceiver(this.receiver_headset);
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
        updateNotifications();
    }

    @Override
    public void onListenerDisconnected() {
        System.out.println("Listener is disconnected");
        connected = false;
        updateNotifications();
    }

    private boolean checkForRunningConditions() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        alwaysOn = prefs.getBoolean(getString(R.string.key_always_on), false);
        withHeadphonesOn = prefs.getBoolean(getString(R.string.key_headphones_on), true);
        withHeadsetOn = prefs.getBoolean(getString(R.string.key_headset_on), true);
        withBluetoothHeadsetOn = prefs.getBoolean(getString(R.string.key_bluetooth_on), true);

        return connected && ttsInit && isRunning &&
                (
                        (alwaysOn) || //always on
                                (withHeadphonesOn && isHeadsetPluggedIn && !isMicPluggedIn) || // headphones
                                (withHeadsetOn && isHeadsetPluggedIn && isMicPluggedIn) || //headset
                                (withBluetoothHeadsetOn && isBluetoothPluggedIn) //bluetooth
                );

    }

    @Override
    public void onNotificationPosted(StatusBarNotification note) {
        try {
            if (checkForRunningConditions()
                    && checkPreferences(note.getPackageName())
                    && !note.getPackageName().equals(this.getPackageName())) {

                readNotification(note);
                updateNotifications();
            }
        } catch (NullPointerException e) {
        }
    }

    private boolean checkPreferences(String packagename) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        return prefs.getBoolean("key_" + packagename, true) || prefs.getBoolean("key_allAppsOn", true);
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        Log.i(TAG, "Notification removed");
    }

    private void saveLastNotification(StatusBarNotification note) {
        String app = note.getPackageName();
        StatusBarNotification oldNote = null;
        for (StatusBarNotification sbn : notifications) {
            if (app.equals(sbn.getPackageName())) {
                oldNote = sbn;
                break;
            }
        }
        notifications.remove(oldNote);
        notifications.add(note);
    }

    private boolean checkReplicate(StatusBarNotification note) {
        for (StatusBarNotification sbn : notifications) {
            String oldTitle = sbn.getNotification().extras.getCharSequence(Notification.EXTRA_TITLE).toString();
            String oldText = sbn.getNotification().extras.getCharSequence(Notification.EXTRA_TEXT).toString();
            String newTitle = note.getNotification().extras.getCharSequence(Notification.EXTRA_TITLE).toString();
            String newText = note.getNotification().extras.getCharSequence(Notification.EXTRA_TEXT).toString();
            if (oldTitle.equals(newTitle) && oldText.equals(newText)) {
                Log.i(TAG, "Notification is a replica");
                return true;
            }
        }
        Log.i(TAG, "Notification isn't a replica");
        return false;
    }

    private void readNotification(StatusBarNotification note) {
        if (!checkReplicate(note)) {
            if (!hasAudioFocus) {
                requestAudioFocus();
            }
            String title = note.getNotification().extras.getCharSequence(Notification.EXTRA_TITLE).toString();
            String text = note.getNotification().extras.getCharSequence(Notification.EXTRA_TEXT).toString();
            String toRead = title + " " + text;
            toRead = EmojiParser.removeAllEmojis(toRead);
            Log.i(TAG, "new Notification: " + toRead);
            Log.w(TAG, String.valueOf(mtts.speak(toRead, TextToSpeech.QUEUE_ADD, null)));
        }
        saveLastNotification(note);
    }

    @TargetApi(Build.VERSION_CODES.N)
    private void requestAudioFocus() {
        AudioManager am = (AudioManager) this.getApplicationContext().getSystemService(Context.AUDIO_SERVICE);
        am.requestAudioFocus(this::onAudioFocusChange, AudioAttributes.CONTENT_TYPE_SPEECH, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK);
    }

    @TargetApi(Build.VERSION_CODES.N)
    private void abondonAudioFocus() {
        AudioManager am = (AudioManager) this.getApplicationContext().getSystemService(Context.AUDIO_SERVICE);
        am.abandonAudioFocus(this::onAudioFocusChange);
    }

    private void onAudioFocusChange(int i) {
        switch (i) {
            case AUDIOFOCUS_GAIN:
                hasAudioFocus = true;
                Log.i(TAG, "gained audio focus");
                break;
            case AUDIOFOCUS_LOSS:
                hasAudioFocus = false;
                Log.i(TAG, "lost audio focus");
                break;
            case AUDIOFOCUS_LOSS_TRANSIENT:
                hasAudioFocus = false;
                Log.i(TAG, "lost audio focus");
                break;
            case AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                hasAudioFocus = false;
                Log.i(TAG, "lost audio focus");
                break;
        }
    }

    @Override
    public void onInit(int i) {
        Log.i(TAG, "TTS is initialized");
        ttsInit = true;
    }

    private void updateNotifications() {
        Log.i(TAG, "notifications got updated");

        STOP_INTENT = new Intent(STOP_INTENT_ACTION);
        STOP_INTENT.setAction(STOP_INTENT_ACTION);
        STOP_PENDING_INTENT = PendingIntent.getBroadcast(getApplicationContext(), (int) System.currentTimeMillis(), STOP_INTENT, 0);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this);
        String text = "";
        String title;
        if (checkForRunningConditions()) {
            title = "Notification Listener is running.";
            builder.addAction(R.drawable.ic_notifications_black_24dp, "stop listener", STOP_PENDING_INTENT);
        } else {
            title = "Notification Listener stopped.";
        }
        if (!isRunning) {
            text = "- manually stopped\n";
            builder.addAction(R.drawable.ic_notifications_black_24dp, "start listener", STOP_PENDING_INTENT);
        }
        if (isHeadsetPluggedIn && !isMicPluggedIn) {
            text += "- headphones are plugged in\n";
        }
        if (isMicPluggedIn && isHeadsetPluggedIn) {
            text += "- headset is plugged in\n";
        }
        if (isBluetoothPluggedIn) {
            text += "- Bluetooth is connected\n";
        }
        if (connected) {
            text += "- listener is connected\n";
        }
        //remove the last char if it is \n
        if (text.lastIndexOf("\n") == text.length() - 1 && text.length() != 0) {
            text = text.substring(0, text.length() - 1);
        }
        builder.setContentTitle(title);
        builder.setContentText(text);
        builder.setOngoing(true);
        builder.setCategory("Information");
        builder.setAutoCancel(false);
        builder.setPriority(NotificationCompat.PRIORITY_DEFAULT);
        builder.setSmallIcon(R.drawable.ic_notifications_black_24dp);
        builder.setStyle(new NotificationCompat.BigTextStyle()
                .bigText(text));
        NotificationManagerCompat manager = NotificationManagerCompat.from(this);
        manager.notify(0, builder.build());
    }

    private void removeNotification() {
        NotificationManagerCompat manager = NotificationManagerCompat.from(this);
        manager.cancel(0);
    }

    protected class HeadsetPlugReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            try {
                Log.i(TAG, intent.getAction());
                if (intent.getAction().equals(AudioManager.ACTION_HEADSET_PLUG)) {
                    if (intent.getIntExtra("state", 0) == 1) {
                        isHeadsetPluggedIn = true;
                        Log.i(TAG, "Headphones are plugged in");
                    } else {
                        isHeadsetPluggedIn = false;
                        Log.i(TAG, "Headphones are plugged out");
                    }
                    if (intent.getIntExtra("microphone", 0) == 1) {
                        isMicPluggedIn = true;
                        Log.i(TAG, "Headset is plugged in");
                    } else {
                        isMicPluggedIn = false;
                    }
                } else if (intent.getAction().equals(ACTION_CONNECTION_STATE_CHANGED)) {
                    if (intent.getIntExtra(EXTRA_STATE, 0) == STATE_CONNECTED) {
                        isBluetoothPluggedIn = true;
                        Log.i(TAG, "Bluetooth is connected");
                    } else if (intent.getIntExtra(EXTRA_STATE, 0) == STATE_DISCONNECTED) {
                        isBluetoothPluggedIn = false;
                        Log.i(TAG, "Bluetooth is disconnected");
                    }
                }
                updateNotifications();
            } catch (NullPointerException e) {
                e.printStackTrace();
            }
        }
    }

    private class BluetoothConnectReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            try {
                Log.i(TAG, intent.getAction());
                if (intent.getAction().equals(ACTION_CONNECTION_STATE_CHANGED)) {
                    if (intent.getIntExtra(EXTRA_STATE, 0) == STATE_CONNECTED) {
                        isBluetoothPluggedIn = true;
                        Log.i(TAG, "Bluetooth is connected");
                    } else if (intent.getIntExtra(EXTRA_STATE, 0) == STATE_DISCONNECTED) {
                        isBluetoothPluggedIn = false;
                        Log.i(TAG, "Bluetooth is disconnected");
                    }
                }
                updateNotifications();
            } catch (NullPointerException e) {
                e.printStackTrace();
            }
        }
    }

    private class NotificationButtonListener extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            try {
                Log.i(TAG, intent.getAction());
                if (intent.getAction().equals(STOP_INTENT_ACTION)) {
                    Log.i(TAG, "Notification Button got clicked");
                    isRunning = !isRunning;
                }
                updateNotifications();
            } catch (NullPointerException e) {
                e.printStackTrace();
            }
        }
    }

    private class TTSDoneListener extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            try {
                Log.i(TAG, intent.getAction());
                if (intent.getAction().equals(ACTION_TTS_QUEUE_PROCESSING_COMPLETED)) {
                    Log.i(TAG, "TTS is done");
                    abondonAudioFocus();
                }
                updateNotifications();
            } catch (NullPointerException e) {
                e.printStackTrace();
            }
        }
    }
}