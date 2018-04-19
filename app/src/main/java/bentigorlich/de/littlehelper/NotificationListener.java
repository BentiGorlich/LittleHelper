package bentigorlich.de.littlehelper;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.opengl.Visibility;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.speech.tts.TextToSpeech;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationCompatExtras;
import android.support.v4.app.NotificationManagerCompat;
import android.util.Log;

import com.vdurmont.emoji.EmojiParser;

import java.util.ArrayList;
import java.util.Locale;

import static android.content.ContentValues.TAG;

public class NotificationListener extends NotificationListenerService implements TextToSpeech.OnInitListener {

	public static final String STOP_INTENT_ACTION = "de.bentigorlich.LittleHelper.TOGGLE_LISTENER";

	//Settings
	private boolean alwaysOn;
	private boolean withHeadphonesOn;
	private boolean withHeadsetOn;

	//status
	public static boolean connected = false;

	private boolean ttsInit = false;
	private boolean isHeadsetPluggedIn = false;
	private boolean isMicPluggedIn = false;
	private boolean isRunning = true;

	//all the last notifications
	private ArrayList<StatusBarNotification> notifications = new ArrayList<>();

	private TextToSpeech mtts;
	private PlugReceiver receiver_audio = new PlugReceiver();
	private StopReceiver receiver_stop = new StopReceiver();
	private Intent STOP_INTENT;
	private PendingIntent STOP_PENDING_INTENT;

	private static boolean running = true;

	@Override
	public void onCreate() {
		super.onCreate();

		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
		alwaysOn = prefs.getBoolean(getString(R.string.key_always_on), false);
		withHeadphonesOn = prefs.getBoolean(getString(R.string.key_headphones_on), false);
		withHeadsetOn = prefs.getBoolean(getString(R.string.key_headset_on), false);

		System.out.println("NotificationListener has started");


		updateNotifications();

		Log.i("toolbar", "sent Notification");

		mtts = new TextToSpeech(this, this);
		mtts.setLanguage(Locale.GERMAN);

		IntentFilter filter = new IntentFilter(AudioManager.ACTION_HEADSET_PLUG);
		this.registerReceiver(receiver_audio, filter);

		filter = new IntentFilter(STOP_INTENT_ACTION);
		this.registerReceiver(receiver_stop, filter);
	}

	@Override
	public void onDestroy() {
		removeNotification();
		unregisterReceiver(this.receiver_stop);
		unregisterReceiver(this.receiver_audio);
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

	@Override
	public void onNotificationPosted(StatusBarNotification note) {
		try {
			if (connected && ttsInit && isRunning && checkPreferences(note.getPackageName()) &&
					(
							(alwaysOn) ||
									(withHeadphonesOn && isHeadsetPluggedIn && !isMicPluggedIn) ||
									(withHeadsetOn && isHeadsetPluggedIn && isMicPluggedIn)
					)) {
				readNotification(note);
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
		if (!checkReplicate(note) && !note.getPackageName().equals(this.getPackageName())) {
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
		if (isRunning) {
			title = "Notification Listener is running.";
			text += "click to stop";
			builder.addAction(R.drawable.ic_notifications_black_24dp, "stop listener", STOP_PENDING_INTENT);
		} else {
			title = "Notification Listener stopped.";
			text += "click to start";
			builder.addAction(R.drawable.ic_notifications_black_24dp, "start listener", STOP_PENDING_INTENT);
		}
		if (isHeadsetPluggedIn) {
			text += " | headphones are plugged in";
		}
		if (isMicPluggedIn) {
			text += " | mic is plugged in";
		}
		if (connected) {
			text += " | listener is connected";
		}
		builder.setContentTitle(title);
		builder.setContentText(text);
		builder.setOngoing(true);
		builder.setCategory("Information");
		builder.setAutoCancel(false);
		builder.setPriority(NotificationCompat.PRIORITY_DEFAULT);
		builder.setSmallIcon(R.drawable.ic_notifications_black_24dp);
		NotificationManagerCompat manager = NotificationManagerCompat.from(this);
		manager.notify(0, builder.build());
	}

	private void removeNotification() {
		NotificationManagerCompat manager = NotificationManagerCompat.from(this);
		manager.cancel(0);
	}

	protected class PlugReceiver extends BroadcastReceiver {

		@Override
		public void onReceive(Context context, Intent intent) {
			try {
				Log.i(TAG, intent.getAction());
				if (intent.getAction().equals(AudioManager.ACTION_HEADSET_PLUG)) {
					if (intent.getIntExtra("state", 0) == 1) {
						isHeadsetPluggedIn = true;
					} else {
						isHeadsetPluggedIn = false;
					}
					if (intent.getIntExtra("microphone", 0) == 1) {
						isMicPluggedIn = true;
					} else {
						isMicPluggedIn = false;
					}
				}
				updateNotifications();
			} catch (NullPointerException e) {
				e.printStackTrace();
			}
		}
	}

	private class StopReceiver extends BroadcastReceiver {

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
}