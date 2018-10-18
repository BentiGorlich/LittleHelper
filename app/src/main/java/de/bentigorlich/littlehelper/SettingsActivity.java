package de.bentigorlich.littlehelper;

import android.annotation.TargetApi;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.drawable.Drawable;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.MultiSelectListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;
import android.preference.PreferenceScreen;
import android.preference.SwitchPreference;
import android.support.annotation.NonNull;
import android.support.v7.app.ActionBar;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.view.MenuItem;
import android.view.View;
import android.widget.RelativeLayout;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Set;

/**
 * A {@link PreferenceActivity} that presents a set of application settings. On
 * handset devices, settings are presented as a single list. On tablets,
 * settings are split by category, with category headers shown to the left of
 * the list of settings.
 * <p>
 * See <a href="http://developer.android.com/design/patterns/settings.html">
 * Android Design: Settings</a> for design guidelines and the <a
 * href="http://developer.android.com/guide/topics/ui/settings.html">Settings
 * API Guide</a> for more information on developing a Settings UI.
 */
public class SettingsActivity extends AppCompatPreferenceActivity {

    protected static boolean isHome = true;

    /**
     * A preference value change listener that updates the preference's summary
     * to reflect its new value.
     */
    private static Preference.OnPreferenceChangeListener sBindPreferenceSummaryToValueListener = new Preference.OnPreferenceChangeListener() {
        @Override
        public boolean onPreferenceChange(Preference preference, Object value) {
            String stringValue = value.toString();

            if (preference instanceof ListPreference) {
                // For list preferences, look up the correct display value in
                // the preference's 'entries' list.
                ListPreference listPreference = (ListPreference) preference;
                int index = listPreference.findIndexOfValue(stringValue);

                // Set the summary to reflect the new value.
                preference.setSummary(
                        index >= 0
                                ? listPreference.getEntries()[index]
                                : null);

            } else {
                // For all other preferences, set the summary to the value's
                // simple string representation.
                preference.setSummary(stringValue);
            }
            return true;
        }
    };

    /**
     * Helper method to determine if the device has an extra-large screen. For
     * example, 10" tablets are extra-large.
     */
    private static boolean isXLargeTablet(Context context) {
        return (context.getResources().getConfiguration().screenLayout
                & Configuration.SCREENLAYOUT_SIZE_MASK) >= Configuration.SCREENLAYOUT_SIZE_XLARGE;
    }

    /**
     * Binds a preference's summary to its value. More specifically, when the
     * preference's value is changed, its summary (line of text below the
     * preference title) is updated to reflect the value. The summary is also
     * immediately updated upon calling this method. The exact display format is
     * dependent on the type of preference.
     *
     * @see #sBindPreferenceSummaryToValueListener
     */
    private static void bindPreferenceSummaryToValue(Preference preference) {
        // Set the listener to watch for value changes.
        preference.setOnPreferenceChangeListener(sBindPreferenceSummaryToValueListener);

        // Trigger the listener immediately with the preference's
        // current value.
        sBindPreferenceSummaryToValueListener.onPreferenceChange(preference,
                PreferenceManager
                        .getDefaultSharedPreferences(preference.getContext())
                        .getString(preference.getKey(), ""));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setupActionBar();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
            switch (item.getItemId()) {
                case android.R.id.home:
                    if (isHome) {
                        startActivity(new Intent(this, MainActivity.class));
                        return true;
                    }
                    break;
            }
        return super.onOptionsItemSelected(item);
    }

    /**
     * Set up the {@link android.app.ActionBar}, if the API is available.
     */
    private void setupActionBar() {
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            // Show the Up button in the action bar.
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean onIsMultiPane() {
        return isXLargeTablet(this);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public void onBuildHeaders(List<Header> target) {
        loadHeadersFromResource(R.xml.pref_headers, target);
    }

    /**
     * This method stops fragment injection in malicious applications.
     * Make sure to deny any unknown fragments here.
     */
    protected boolean isValidFragment(String fragmentName) {
        return PreferenceFragment.class.getName().equals(fragmentName)
                || AppsFragment.class.getName().equals(fragmentName)
                || GeneralFragment.class.getName().equals(fragmentName)
                || ProfilesFragment.class.getName().equals(fragmentName);
    }

    /**
     * This fragment shows general preferences only. It is used when the
     * activity is showing a two-pane settings UI.
     */
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public static class AppsFragment extends PreferenceFragment {
        List<SwitchPreference> apps = new ArrayList<>();

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            isHome = false;

            PreferenceScreen screen = getPreferenceManager().createPreferenceScreen(this.getContext());

            SwitchPreference defaultValue = new SwitchPreference(this.getContext());
            defaultValue.setTitle(getString(R.string.key_defaultTrue_title));
            defaultValue.setSummary(getString(R.string.key_defaultTrue_description));
            defaultValue.setKey("key_defaultTrue");
            defaultValue.setDefaultValue(false);
            defaultValue.setSelectable(true);
            screen.addPreference(defaultValue);

            SwitchPreference allOn = new SwitchPreference(this.getContext());
            allOn.setTitle(getString(R.string.key_allOn_description));
            allOn.setKey("key_allOn");
            allOn.setDefaultValue(false);
            allOn.setSelectable(true);
            allOn.setOnPreferenceChangeListener((preference, o) -> {
                SwitchPreference allOn1 = (SwitchPreference) preference;
                if (!allOn1.isChecked()) {
                    for (SwitchPreference curr : apps) {
                        curr.setChecked(true);
                    }
                    allOn1.setChecked(false);
                }
                return true;
            });
            screen.addPreference(allOn);

            SwitchPreference allOff = new SwitchPreference(this.getContext());
            allOff.setTitle(getString(R.string.key_allOff_description));
            allOff.setDefaultValue(false);
            allOff.setKey("key_allOff");
            allOff.setPersistent(false);
            allOff.setSelectable(true);
            allOff.setOnPreferenceChangeListener((preference, o) -> {
                SwitchPreference allOff1 = (SwitchPreference) preference;
                if (!allOff1.isChecked()) {
                    for (SwitchPreference curr : apps) {
                        curr.setChecked(false);
                    }
                    allOff1.setChecked(false);
                }
                return true;
            });
            screen.addPreference(allOff);

            ArrayList<SwitchPreference> toAdd = new ArrayList<>();

            PackageManager pm = this.getContext().getPackageManager();
            //get a list of installed apps.
            List<ApplicationInfo> packages = pm.getInstalledApplications(PackageManager.GET_META_DATA);

            for (ApplicationInfo packageInfo : packages) {

                if (!this.getContext().getPackageName().equals(packageInfo.packageName)) {
                    String str = packageInfo.processName;
                    if (pm.getApplicationLabel(packageInfo) != null) {
                        str = pm.getApplicationLabel(packageInfo).toString();
                    }
                    Drawable icon = packageInfo.loadIcon(pm);

                    SwitchPreference curr = new SwitchPreference(this.getContext());
                    curr.setIcon(icon);
                    curr.setTitle(str);
                    curr.setDefaultValue(defaultValue.isChecked());
                    curr.setKey("key_" + packageInfo.packageName);
                    toAdd.add(curr);
                }
            }

            toAdd.sort((sw1, sw2) -> sw1.getTitle().toString().compareToIgnoreCase(sw2.getTitle().toString()));
            for (SwitchPreference sw : toAdd) {
                screen.addPreference(sw);
                apps.add(sw);
            }

            setPreferenceScreen(screen);
            setHasOptionsMenu(true);
        }

        @Override
        public boolean onOptionsItemSelected(MenuItem item) {
            int id = item.getItemId();
            if (id == android.R.id.home) {
                startActivity(new Intent(getActivity(), SettingsActivity.class));
                isHome = true;
                return true;
            }
            return super.onOptionsItemSelected(item);
        }

        @Override
        public void onDestroy() {
            super.onDestroy();
            isHome = true;
        }
    }

    /**
     * This fragment shows general preferences only. It is used when the
     * activity is showing a two-pane settings UI.
     */
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public static class GeneralFragment extends PreferenceFragment {
		private static final int REQUEST_ENABLE_BT = 22500;

		MultiSelectListPreference bluetooth;
		MultiSelectListPreference wifi;

		@Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            isHome = false;
            addPreferencesFromResource(R.xml.pref_general);

			bluetooth = new MultiSelectListPreference(this.getContext());
			bluetooth.setKey("key_bluetooth_devices");
			wifi = new MultiSelectListPreference(this.getContext());
			wifi.setKey("key_wifi_devices");

			for (int i = 0; i < getPreferenceScreen().getPreferenceCount(); i++) {
				Preference curr = getPreferenceScreen().getPreference(i);
				if (curr.getKey() != null) {
					if (curr.getKey().equals(getString(R.string.key_cat_devices))) {
						PreferenceCategory cat = (PreferenceCategory) curr;
						cat.addPreference(bluetooth);
						cat.addPreference(wifi);
						break;
					}
				}
			}

			bluetooth.setTitle(R.string.pref_bluetooth_devices);
			bluetooth.setDependency("key_bluetooth_on");

			BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
			if (mBluetoothAdapter != null) {
				if (!mBluetoothAdapter.isEnabled()) {
					Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
					startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
				} else {
					populateBTDevices();
				}
			}

			wifi.setTitle(R.string.pref_wifi_devices);
			wifi.setDependency("key_wifi_on");

			WifiManager wifiManager = this.getContext().getSystemService(WifiManager.class);
			if (wifiManager != null) {
				if (!wifiManager.isWifiEnabled()) {
					wifiManager.setWifiEnabled(true);
				}
				populateWiFiDevices(wifiManager);
			}

			setHasOptionsMenu(true);
        }

		private void populateWiFiDevices(@NonNull WifiManager wifiManager) {
			List<WifiConfiguration> wifis = wifiManager.getConfiguredNetworks();
			String[] wifiNames = new String[wifis.size()];
			String[] wifiKeys = new String[wifis.size()];
			int i = 0;
			for (WifiConfiguration curr : wifis) {
				wifiNames[i] = curr.SSID.replace("\"", "");
				wifiKeys[i] = curr.SSID.replace("\"", "");
				i++;
			}
			wifi.setEntries(wifiNames);
			wifi.setEntryValues(wifiKeys);
		}

		private void populateBTDevices() {
			BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
			Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();
			String[] deviceNames = new String[pairedDevices.size()];
			String[] deviceKeys = new String[pairedDevices.size()];
			int i = 0;
			for (BluetoothDevice curr : pairedDevices) {
				deviceNames[i] = curr.getName();
				deviceKeys[i] = curr.getName();
				i++;
			}
			bluetooth.setEntries(deviceNames);
			bluetooth.setEntryValues(deviceKeys);
		}

		@Override
		public void onActivityResult(int requestCode, int resultCode, Intent data) {
			if (requestCode == REQUEST_ENABLE_BT && resultCode == RESULT_OK) {
				populateBTDevices();
			}
		}

		@Override
        public boolean onOptionsItemSelected(MenuItem item) {
            int id = item.getItemId();
            if (id == android.R.id.home) {
                startActivity(new Intent(getActivity(), SettingsActivity.class));
                isHome = true;
                return true;
            }
            return super.onOptionsItemSelected(item);
        }

        @Override
        public void onDestroy() {
            super.onDestroy();
            isHome = true;
        }
    }

    /**
     * This fragment is for profile management
     */
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public static class ProfilesFragment extends PreferenceFragment {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            isHome = false;
            PreferenceScreen screen = getPreferenceManager().createPreferenceScreen(this.getContext());
			setPreferenceScreen(screen);
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this.getContext());

            PackageManager pm = this.getContext().getPackageManager();
            //get a list of installed apps.
            List<ApplicationInfo> packages = pm.getInstalledApplications(PackageManager.GET_META_DATA);

            for (ApplicationInfo packageInfo : packages) {

                String str = packageInfo.processName;
                if (pm.getApplicationLabel(packageInfo) != null) {
                    str = pm.getApplicationLabel(packageInfo).toString();
                }
                Drawable icon = packageInfo.loadIcon(pm);

                if (prefs.getBoolean("key_" + packageInfo.packageName, false)) {
                    PreferenceScreen curr = getPreferenceManager().createPreferenceScreen(this.getContext());
                    curr.setIcon(icon);
                    curr.setTitle(str);
                    curr.setPersistent(false);
                    curr.setEnabled(true);
                    curr.setKey("pref_key_" + packageInfo.packageName);

					screen.addPreference(curr);

                    PreferenceCategory pc = new PreferenceCategory(this.getContext());
                    pc.setTitle(str);
                    pc.setOrder(Preference.DEFAULT_ORDER);
                    pc.setKey("pref_key_" + packageInfo.packageName + "_category");
                    pc.setEnabled(true);
                    pc.setPersistent(false);

                    curr.addPreference(pc);

					SwitchPreference useGeneral = new SwitchPreference((this.getContext()));
					useGeneral.setKey("key_" + packageInfo.packageName + "_use_general");
					useGeneral.setSummary(R.string.pref_use_general_description);
					useGeneral.setTitle(R.string.pref_use_general);
					useGeneral.setDefaultValue(true);
					useGeneral.setDisableDependentsState(true);

					SwitchPreference checkReplica = new SwitchPreference(this.getContext());
					checkReplica.setTitle(R.string.pref_check_replica);
					checkReplica.setSummary(R.string.pref_check_replica_description);
					checkReplica.setKey("key_" + packageInfo.packageName + "_check_replica");

					EditTextPreference blacklistWordsInTitle = new EditTextPreference(this.getContext());
					blacklistWordsInTitle.setTitle(R.string.pref_blacklist_words_title);
					blacklistWordsInTitle.setSummary(R.string.pref_blacklist_words_title_description);
                    blacklistWordsInTitle.setKey("key_" + packageInfo.packageName + "_blacklist_words_title");
					blacklistWordsInTitle.setDefaultValue("");

					EditTextPreference blacklistWordsInText = new EditTextPreference(this.getContext());
					blacklistWordsInText.setTitle(R.string.pref_blacklist_words_text);
					blacklistWordsInText.setSummary(R.string.pref_blacklist_words_text_description);
                    blacklistWordsInText.setKey("key_" + packageInfo.packageName + "_blacklist_words_text");
					blacklistWordsInText.setDefaultValue("");

					pc.addPreference(useGeneral);
					pc.addPreference(checkReplica);
					pc.addPreference(blacklistWordsInTitle);
					pc.addPreference(blacklistWordsInText);

					PreferenceCategory log = new PreferenceCategory(this.getContext());
					log.setTitle(R.string.pref_category_logs);
					curr.addPreference(log);

					for (EditTextPreference currLog : getLog(this.getContext(), packageInfo.packageName)) {
						log.addPreference(currLog);
					}

					checkReplica.setDependency(useGeneral.getKey());
				}
			}
            setHasOptionsMenu(true);
        }

        @Override
        public boolean onOptionsItemSelected(MenuItem item) {
            int id = item.getItemId();
            if (id == android.R.id.home) {
                startActivity(new Intent(getActivity(), SettingsActivity.class));
                isHome = true;
                return true;
            }
            return super.onOptionsItemSelected(item);
        }

        @Override
        public void onDestroy() {
            super.onDestroy();
            isHome = true;
        }
    }

	@NonNull
	public static ArrayList<EditTextPreference> getLog(Context context, String packageName) {
		try {
			File log = new File(context.getFilesDir().getAbsolutePath(), packageName + ".log");
			BufferedReader bfw = new BufferedReader(new FileReader(log));
			ArrayList<EditTextPreference> erg = new ArrayList<>();
			String line = "";
			while ((line = bfw.readLine()) != null) {
				String[] args = line.split(":");
				if (args.length == 4) {
					EditTextPreference curr = new EditTextPreference(context);
					Calendar posted = Calendar.getInstance();
					posted.setTimeInMillis(Long.parseLong(args[0]));
					String date = posted.get(Calendar.DAY_OF_MONTH)
							+ "." + posted.get(Calendar.MONTH)
							+ "." + posted.get(Calendar.YEAR)
							+ ">" + posted.get(Calendar.HOUR_OF_DAY)
							+ ":" + posted.get(Calendar.MINUTE);
					curr.setTitle(date + " status: " + args[1]);
					curr.setSummary("\tTitle = " + args[2] + "\n\tText = " + args[3]);
					curr.setEnabled(false);
					erg.add(0, curr);
				}
			}
			bfw.close();
			return erg;
		} catch (java.io.IOException ignored) {
		}
		return new ArrayList<>();
	}

	//show
	public void showLoadingAnimation() {
		RelativeLayout pageLoading = findViewById(R.id.main_layoutPageLoading);
		pageLoading.setVisibility(View.VISIBLE);
	}


	//hide
	public void hideLoadingAnimation() {
		RelativeLayout pageLoading = findViewById(R.id.main_layoutPageLoading);
		pageLoading.setVisibility(View.GONE);
	}
}
