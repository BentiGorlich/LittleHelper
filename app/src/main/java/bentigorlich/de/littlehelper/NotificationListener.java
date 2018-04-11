package bentigorlich.de.littlehelper;

import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.speech.tts.TextToSpeech;

import java.util.Locale;

public class NotificationListener extends NotificationListenerService implements TextToSpeech.OnInitListener {
    private boolean connected = false;
    private boolean ttsInit = false;

    public NotificationListener(){
        super();
    }

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        connected = true;
    }

    @Override
    public void onListenerDisconnected() {
        super.onListenerDisconnected();
        connected = false;
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        super.onNotificationPosted(sbn);
        if(connected && ttsInit){
            readNotification(sbn);
        }
    }

    private void readNotification(StatusBarNotification note){
        String toRead = note.getNotification().toString();
        TextToSpeech mtts = new TextToSpeech(this, this);
        mtts.setLanguage(Locale.GERMAN);
        mtts.speak(toRead, TextToSpeech.QUEUE_FLUSH, null);
    }

    @Override
    public void onInit(int i) {
        ttsInit = true;
    }
}
