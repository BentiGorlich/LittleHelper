package de.bentigorlich.littlehelper;

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.PendingIntent;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.speech.tts.TextToSpeech;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.util.Log;

import com.vdurmont.emoji.EmojiParser;

import junit.runner.Version;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;
import java.util.Set;

import static android.bluetooth.BluetoothDevice.EXTRA_DEVICE;
import static android.bluetooth.BluetoothProfile.A2DP;
import static android.bluetooth.BluetoothProfile.EXTRA_STATE;
import static android.bluetooth.BluetoothProfile.HEADSET;
import static android.bluetooth.BluetoothProfile.STATE_CONNECTED;
import static android.bluetooth.BluetoothProfile.STATE_DISCONNECTED;
import static android.content.ContentValues.TAG;
import static android.media.AudioManager.AUDIOFOCUS_GAIN;
import static android.media.AudioManager.AUDIOFOCUS_GAIN_TRANSIENT;
import static android.media.AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE;
import static android.media.AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK;
import static android.media.AudioManager.AUDIOFOCUS_LOSS;
import static android.media.AudioManager.AUDIOFOCUS_LOSS_TRANSIENT;
import static android.media.AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK;
import static android.net.ConnectivityManager.TYPE_WIFI;
import static android.net.wifi.WifiManager.EXTRA_NETWORK_INFO;
import static android.net.wifi.WifiManager.NETWORK_STATE_CHANGED_ACTION;
import static android.speech.tts.TextToSpeech.ACTION_TTS_QUEUE_PROCESSING_COMPLETED;

public class NotificationListener extends NotificationListenerService implements TextToSpeech.OnInitListener {

    public static final String STOP_INTENT_ACTION = "de.bentigorlich.LittleHelper.STOP_LISTENER";
    public static final String START_INTENT_ACTION = "de.bentigorlich.LittleHelper.START_LISTENER";

    //status
    public static boolean connected = false;

    private boolean ttsInit = false;
    private boolean isHeadsetPluggedIn = false;
	private boolean isBluetoothConnected = false;
    private boolean isMicPluggedIn = false;
    private boolean isRunning = true;
    private boolean isManuallyStarted = false;
    private boolean isScreenOff = false;
	private boolean isWifiConnected = false;

    private PowerManager.WakeLock wake;

    //all the last notifications
	private HashMap<String, Integer[]> oldNotifications = new HashMap<>();

    private TextToSpeech tts;
    private AudioChangeListener acl = new AudioChangeListener();

    //broadcast receiver
    private HeadsetPlugReceiver receiver_headset = new HeadsetPlugReceiver();
    private BluetoothConnectReceiver receiver_bluetooth = new BluetoothConnectReceiver();
    private NotificationButtonListener receiver_notification = new NotificationButtonListener();
	private TTSDoneListener receiver_tts_done = new TTSDoneListener();
    private ScreenChangeListener receiver_screenChange = new ScreenChangeListener();
	private PreferenceChangeListener preferenceChangeListener = new PreferenceChangeListener();
	private WiFiChangeListener receiver_wifi = new WiFiChangeListener();

    private Intent STOP_INTENT;
    private PendingIntent STOP_PENDING_INTENT;

    private Intent START_INTENT;
    private PendingIntent START_PENDING_INTENT;

    @Override
    public void onCreate() {
        super.onCreate();

		PreferenceManager.getDefaultSharedPreferences(this).registerOnSharedPreferenceChangeListener(preferenceChangeListener);

        System.out.println("NotificationListener has started");
		isBluetoothConnected =
                BluetoothAdapter.getDefaultAdapter().getProfileConnectionState(HEADSET) == BluetoothAdapter.STATE_CONNECTED
                        || BluetoothAdapter.getDefaultAdapter().getProfileConnectionState(A2DP) == BluetoothAdapter.STATE_CONNECTED
        ;

		SharedPreferences shp = PreferenceManager.getDefaultSharedPreferences(this);
		WifiInfo wifiInfo = getApplicationContext().getSystemService(WifiManager.class).getConnectionInfo();
		String ssid = wifiInfo.getSSID();
		Log.d(TAG, ssid);
		isWifiConnected = shp.getBoolean("key_wifi_device_" + ssid, false);

        Log.i("toolbar", "sent Notification");

        tts = new TextToSpeech(this, this);
        tts.setLanguage(Locale.GERMAN);

        IntentFilter filter = new IntentFilter(AudioManager.ACTION_HEADSET_PLUG);
        this.registerReceiver(receiver_headset, filter);

        filter = new IntentFilter(STOP_INTENT_ACTION);
        filter.addAction(START_INTENT_ACTION);
        this.registerReceiver(receiver_notification, filter);

        filter = new IntentFilter(android.bluetooth.BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED);
        filter.addAction(android.bluetooth.BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED);
        this.registerReceiver(receiver_bluetooth, filter);

        filter = new IntentFilter(ACTION_TTS_QUEUE_PROCESSING_COMPLETED);
		this.registerReceiver(receiver_tts_done, filter);

        filter = new IntentFilter(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        this.registerReceiver(receiver_screenChange, filter);

		filter = new IntentFilter(NETWORK_STATE_CHANGED_ACTION);
		this.registerReceiver(receiver_wifi, filter);

		updateNotifications();
    }

    @Override
    public void onDestroy() {
		Log.i(TAG, "destroying service...");
        unregisterReceiver(this.receiver_notification);
        unregisterReceiver(this.receiver_headset);
        unregisterReceiver(this.receiver_bluetooth);
		unregisterReceiver(this.receiver_screenChange);
		unregisterReceiver(this.receiver_tts_done);
		unregisterReceiver(this.receiver_wifi);
		PreferenceManager.getDefaultSharedPreferences(this).unregisterOnSharedPreferenceChangeListener(preferenceChangeListener);
		tts.shutdown();
        abandonAudioFocus();
		removeNotification();
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
    }

    private boolean checkForRunningConditions() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        boolean alwaysOn = prefs.getBoolean("key_always_on", false);
        boolean withHeadphonesOn = prefs.getBoolean("key_headphones_on", true);
        boolean withHeadsetOn = prefs.getBoolean("key_headset_on", true);
        boolean withBluetoothHeadsetOn = prefs.getBoolean("key_bluetooth_on", true);
		boolean withWiFiOn = prefs.getBoolean("key_wifi_on", true);
		boolean onlyWhenScreenIsOff = prefs.getBoolean("key_only_screen_off", false);

		return connected && ttsInit && isRunning &&
				(
						!onlyWhenScreenIsOff || isScreenOff
				)
				&&
				(
						(alwaysOn)//always on
								|| (withHeadphonesOn && isHeadsetPluggedIn && !isMicPluggedIn)// headphones
								|| (withHeadsetOn && isHeadsetPluggedIn && isMicPluggedIn)//headset
								|| (withBluetoothHeadsetOn && isBluetoothConnected)//bluetooth
								|| (withWiFiOn && isWifiConnected) //wifi
								|| (isManuallyStarted)
				);
	}

    @Override
    public void onNotificationPosted(StatusBarNotification note) {
        try {
			Log.d(TAG, "checking " + note.getPackageName());
			boolean isActivatedForPackage = checkPreferences(note.getPackageName());
			SharedPreferences shp = PreferenceManager.getDefaultSharedPreferences(this);
			if (shp.getBoolean("key_log", true)) {
				if (isActivatedForPackage) {
					log(note);
				}
			}
			if (checkForRunningConditions()
					&& isActivatedForPackage
                    && !note.getPackageName().equals(this.getPackageName())
					&& !note.isOngoing()
					) {
                readNotification(note);
                updateNotifications();
            }
        } catch (NullPointerException ignored) {
        }
    }

	/**
	 * This function logs the given notification to a file named after the package name
	 *
	 * @param note the notification to log
	 */
	private void log(StatusBarNotification note) {
		File logfile = new File(this.getApplicationContext().getFilesDir(), note.getPackageName() + ".log");
		try {
			if (!logfile.exists()) {
				Log.i(TAG, String.valueOf(logfile.createNewFile()));
			}
			String title = note.getNotification().extras.getCharSequence(Notification.EXTRA_TITLE).toString();
			String text = note.getNotification().extras.getCharSequence(Notification.EXTRA_TEXT).toString();
			String toWrite = title.replaceAll(":", "") + ":" + text.replaceAll(":", "");
			String blacklisted = "";
			if (checkForBlacklistedWordsInText(note.getPackageName(), title + text) || checkForBlacklistedWordsInTitle(note.getPackageName(), title + text)) {
				blacklisted += "blacklisted";
			}
			SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
			String replica = "";
			if (checkReplicate(note) && prefs.getBoolean("key_" + note.getPackageName() + "_check_replica", prefs.getBoolean("key_check_replica", true))) {
				replica = "replica";
			}
			String status = blacklisted;
			if (status.length() > 0 && replica.length() > 0) {
				status += ", " + replica;
			} else if (status.length() == 0 && replica.length() > 0) {
				status = replica;
			}
			toWrite = Calendar.getInstance().getTimeInMillis() + ":" + status + ":" + toWrite;
			FileWriter fw = new FileWriter(logfile, true);
			fw.write(toWrite);
			fw.write("\n");
			fw.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * @param packageName the packageName to check conditions for
	 * @return true if notification reading is enabled for this package
	 */
	private boolean checkPreferences(String packageName) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
		return prefs.getBoolean("key_" + packageName, prefs.getBoolean("key_defaultTrue", false));
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        Log.i(TAG, "Notification removed");
    }

    private void saveLastNotification(StatusBarNotification note) {
		int id = note.getId();
		String appname = note.getPackageName();

		//if no set for app add one
		if (!oldNotifications.containsKey(appname)) {
			oldNotifications.put(appname, new Integer[100]);
		}
		int lowestIntIndex = 0;
		int lowestInt = 0;
		int currIndex = 0;
		Integer[] currSet = oldNotifications.get(appname);
		for (int oldID : currSet) {
			if (oldID < lowestInt) {
				lowestIntIndex = currIndex;
			}
			currIndex++;
		}
		//replace lowest id in Array with the new ID
		currSet[lowestIntIndex] = id;
    }

    private boolean checkReplicate(StatusBarNotification note) {
		int id = note.getId();
		String appname = note.getPackageName();

		Integer[] currSet = oldNotifications.get(appname);
		for (int oldID : currSet) {
			//check if id is a replica
			if (oldID == id) {
				return true;
			}
		}

        return false;
    }

	/**
	 * @param packageName the package name to get the blacklisted words for the title from
	 * @param text        the string to check for blacklisted words
	 * @return true if text contains a blacklisted word
	 */
	private boolean checkForBlacklistedWordsInTitle(String packageName, String text) {
		SharedPreferences shp = PreferenceManager.getDefaultSharedPreferences(this);
		String words = shp.getString("key_" + packageName + "_blacklist_words_title", "");
		Log.d(TAG, "blacklisted words: " + words);
		Log.d(TAG, text);
		String[] blacklistedWords = words.split(",");
		for (String word : blacklistedWords) {
			Log.d(TAG, " contains " + word + "? " + (text.toLowerCase().contains(word.toLowerCase()) && !word.equals("")));
			if (text.toLowerCase().contains(word.toLowerCase()) && !word.equals(""))
				return true;
		}
		return false;
	}

	/**
	 * @param packageName the package name to get the blacklisted words for the text from
	 * @param text        the string to check for blacklisted words
	 * @return true if text contains a blacklisted word
	 */
	private boolean checkForBlacklistedWordsInText(String packageName, String text) {
		SharedPreferences shp = PreferenceManager.getDefaultSharedPreferences(this);
		String words = shp.getString("key_" + packageName + "_blacklist_words_text", "");
		Log.d(TAG, "blacklisted words: " + words);
		String[] blacklistedWords = words.split(",");
		Log.d(TAG, text);
		for (String word : blacklistedWords) {
			Log.d(TAG, " contains " + word + "? " + (text.toLowerCase().contains(word.toLowerCase()) && !word.equals("")));
			if (text.toLowerCase().contains(word.toLowerCase()) && !word.equals(""))
				return true;
		}
		return false;
	}

	/**
	 * Reads out a notification via Googles tts and removes all emotes first
	 * condition is given cia preferences:
	 * --> check for blacklisted words
	 * --> request audiofocus if not already occupied
	 * --> acquire wakelock if it's not null
	 * --> check if note is replica (if so desired by prefs)
	 *
	 * @param note the notification to read out
	 */
	private void readNotification(StatusBarNotification note) {
		Log.d(TAG, "in readNotification");
		SharedPreferences shp = PreferenceManager.getDefaultSharedPreferences(this);
		String title = note.getNotification().extras.getCharSequence(Notification.EXTRA_TITLE).toString();
		title = EmojiParser.removeAllEmojis(title);
		String text = note.getNotification().extras.getCharSequence(Notification.EXTRA_TEXT).toString();
		text = EmojiParser.removeAllEmojis(text);
		String toRead = title + ": " + text;
		if (!checkForBlacklistedWordsInTitle(note.getPackageName(), title) && !checkForBlacklistedWordsInText(note.getPackageName(), text)) {
			Log.d(TAG, "not blacklisted");
			if (!acl.hasAudioFocus()) {
				requestAudioFocus();
				PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
				if (powerManager != null) {
					wake = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "littlehelper:ReadingNotificationOut");
					if (wake != null) {
						wake.acquire();
					}
				}
			}
			boolean valid = true;
			//if should use general -> check if general_check_replica is true -> if so check for replica
			if (shp.getBoolean("key_" + note.getPackageName() + "_use_general", true)) {
				if (shp.getBoolean("key_check_replica", true)) {
					valid = !checkReplicate(note);
					saveLastNotification(note);
				}
			}
			//if not general check app specific prefs and if so -> check for replica
			else {
				if (shp.getBoolean("key_" + note.getPackageName() + "_check_replica", shp.getBoolean("key_check_replica", true))) {
					valid = !checkReplicate(note);
					saveLastNotification(note);
				}
			}
			Log.d(TAG, "valid: " + valid);
			if (valid) {
				Log.i(TAG, "new Notification: " + toRead);
				tts.speak(toRead, TextToSpeech.QUEUE_ADD, null);
			}
		} else {
			Log.d(TAG, "blacklisted");
		}
	}

    private void requestAudioFocus() {
        Log.i(TAG, "Requested Audio Focus");
        AudioManager am = (AudioManager) this.getApplicationContext().getSystemService(Context.AUDIO_SERVICE);
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			if (am != null) {
				AudioAttributes.Builder attributes = new AudioAttributes.Builder();
				attributes.setContentType(AudioAttributes.CONTENT_TYPE_SPEECH);
				attributes.setUsage(AudioAttributes.USAGE_ASSISTANT);

				AudioFocusRequest.Builder builder = new AudioFocusRequest.Builder(AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK);
				builder.setAudioAttributes(attributes.build());
				builder.setOnAudioFocusChangeListener(acl);

				am.requestAudioFocus(builder.build());
			}
		} else {
			if (am != null) {
				am.requestAudioFocus(acl, AudioAttributes.CONTENT_TYPE_SPEECH, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK);
			}
		}
    }

    private void abandonAudioFocus() {
        Log.i(TAG, "abandoned audio focus");
        AudioManager am = (AudioManager) this.getApplicationContext().getSystemService(Context.AUDIO_SERVICE);
        if (am != null) {
            am.abandonAudioFocus(acl);
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

        START_INTENT = new Intent(START_INTENT_ACTION);
        START_INTENT.setAction(START_INTENT_ACTION);
        START_PENDING_INTENT = PendingIntent.getBroadcast(getApplicationContext(), (int) System.currentTimeMillis(), START_INTENT, 0);

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
            builder.addAction(R.drawable.ic_notifications_black_24dp, "resume listener", STOP_PENDING_INTENT);
        }
        if (isManuallyStarted) {
            text += "- manually started\n";
            builder.addAction(R.drawable.ic_notifications_black_24dp, "start automatically", START_PENDING_INTENT);
        } else {
            builder.addAction(R.drawable.ic_notifications_black_24dp, "start manually", START_PENDING_INTENT);
        }
        if (isHeadsetPluggedIn && !isMicPluggedIn) {
            text += "- headphones are plugged in\n";
        }
        if (isMicPluggedIn && isHeadsetPluggedIn) {
            text += "- headset is plugged in\n";
		}
		if (isWifiConnected) {
			text += "- Wi-Fi is connected\n";
		}
		if (isBluetoothConnected) {
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
		builder.setOngoing(checkForRunningConditions() || !isRunning || isManuallyStarted);
        builder.setCategory("Information");
        builder.setAutoCancel(false);
        builder.setPriority(NotificationCompat.PRIORITY_DEFAULT);
        builder.setSmallIcon(R.drawable.ic_notifications_black_24dp);
		builder.setStyle(new NotificationCompat.BigTextStyle().bigText(text));
		builder.setContentIntent(PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), 0));
        NotificationManagerCompat manager = NotificationManagerCompat.from(this);
        manager.notify(0, builder.build());
    }

    private void removeNotification() {
        NotificationManagerCompat manager = NotificationManagerCompat.from(this);
        manager.cancel(0);
    }

    private class HeadsetPlugReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            try {
                Log.i("HeadsetPlugReceiver", intent.getAction());
                if (intent.getAction().equals(AudioManager.ACTION_HEADSET_PLUG)) {
                    if (intent.getIntExtra("state", 0) == 1) {
                        isHeadsetPluggedIn = true;
                        Log.i("HeadsetPlugReceiver", "Headphones are plugged in");
                    } else {
                        isHeadsetPluggedIn = false;
                        Log.i("HeadsetPlugReceiver", "Headphones are plugged out");
                    }
                    if (intent.getIntExtra("microphone", 0) == 1) {
                        isMicPluggedIn = true;
                        Log.i("HeadsetPlugReceiver", "Headset is plugged in");
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

    private class BluetoothConnectReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            try {
                Log.i("BluetoothConnectReceiver", intent.getAction());
                if (intent.getAction().equals(android.bluetooth.BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED)
                        || intent.getAction().equals(android.bluetooth.BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED)) {
                    if (intent.getIntExtra(EXTRA_STATE, 0) == STATE_CONNECTED) {
						SharedPreferences shp = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
						String name = ((BluetoothDevice) intent.getParcelableExtra(EXTRA_DEVICE)).getName();
						Set<String> wifi_devices = shp.getStringSet("key_bluetooth_devices", null);
						Log.d("BluetoothChangeListener", "bluetooth is connected to " + name);
						if (wifi_devices != null) {
							for (String device : wifi_devices) {
								if (device.equals(name.replace("\"", ""))) {
									isBluetoothConnected = true;
									Log.d("BluetoothChangeListener", "bluetooth-device is on list");
									break;
								}
							}
						}
                    } else if (intent.getIntExtra(EXTRA_STATE, 0) == STATE_DISCONNECTED) {
						isBluetoothConnected = false;
                        Log.i("BluetoothConnectReceiver", "Bluetooth is disconnected");
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
                if (intent.getAction().equals(STOP_INTENT_ACTION)) {
                    Log.i(TAG, "Notification Stop-Button got clicked");
                    isRunning = !isRunning;
                    if (!isRunning) {
                        tts.stop();
                        abandonAudioFocus();
                    }
                } else if (intent.getAction().equals(START_INTENT_ACTION)) {
                    Log.i(TAG, "Notification Start-Button got clicked");
                    isManuallyStarted = !isManuallyStarted;
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
                if (intent.getAction().equals(ACTION_TTS_QUEUE_PROCESSING_COMPLETED)) {
                    Log.i("TTSDoneListener", "TTS is done");
                    abandonAudioFocus();
                    if (wake != null) {
                        wake.release();
                        wake = null;
                    }
                }
                updateNotifications();
            } catch (NullPointerException e) {
                e.printStackTrace();
            }
        }
    }

    private class AudioChangeListener implements AudioManager.OnAudioFocusChangeListener {

        private boolean hasAudioFocus = false;

        @Override
        public void onAudioFocusChange(int focusChange) {
            switch (focusChange) {
                case AUDIOFOCUS_GAIN:
                case AUDIOFOCUS_GAIN_TRANSIENT:
                case AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE:
                case AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK:
                    hasAudioFocus = true;
                    Log.i("AudioChangeListener", "gained audio focus");
                    break;
                case AUDIOFOCUS_LOSS:
                case AUDIOFOCUS_LOSS_TRANSIENT:
                case AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                    hasAudioFocus = false;
                    Log.i("AudioChangeListener", "lost audio focus");
                    break;
                default:
                    Log.i("AudioChangeListener", "Audio focus smth..." + focusChange);
                    break;
            }
        }

        boolean hasAudioFocus() {
            return hasAudioFocus;
        }
    }

	private class ScreenChangeListener extends BroadcastReceiver {

		@Override
		public void onReceive(Context context, Intent intent) {
			if (intent.getAction().equals(Intent.ACTION_SCREEN_OFF)) {
				isScreenOff = true;
				Log.i("ScreenChangeListener", "locked");
			} else if (intent.getAction().equals(Intent.ACTION_SCREEN_ON)) {
				isScreenOff = false;
				Log.i("ScreenChangeListener", "unlocked");
			}
			updateNotifications();
		}
	}

	private class PreferenceChangeListener implements SharedPreferences.OnSharedPreferenceChangeListener {

		@Override
		public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
			if (key.equals("key_wifi_devices") || key.equals("key_bluetooth_devices")) {
				Log.d(TAG, "key: " + key + " value: " + sharedPreferences.getStringSet(key, null));
			}
			updateNotifications();
		}
	}

	private class WiFiChangeListener extends BroadcastReceiver {

		@Override
		public void onReceive(Context context, Intent intent) {
			Log.d("WiFiChangeListener", intent.getAction());
			if (intent.getAction().equals(NETWORK_STATE_CHANGED_ACTION)) {
				NetworkInfo netInfo = intent.getParcelableExtra(EXTRA_NETWORK_INFO);
				if (netInfo.getType() == TYPE_WIFI) {
					if (netInfo.isConnectedOrConnecting()) {
						SharedPreferences shp = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
						WifiInfo wifiInfo = getApplicationContext().getSystemService(WifiManager.class).getConnectionInfo();
						String ssid = wifiInfo.getSSID();
						Set<String> wifi_devices = shp.getStringSet("key_wifi_devices", null);
						Log.d("WiFiChangeListener", "wifi is connected to " + ssid);
						if (wifi_devices != null) {
							for (String device : wifi_devices) {
								if (device.equals(ssid.replace("\"", ""))) {
									isWifiConnected = true;
									Log.d("WiFiChangeListener", "wifi is on list");
									break;
								}
							}
						}
					} else {
						isWifiConnected = false;
						Log.d("WiFiChangeListener", "wifi is disconnected");
					}
				}
			}
			updateNotifications();
		}
	}
}