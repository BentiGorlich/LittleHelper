package de.bentigorlich.littlehelper;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;
import android.preference.PreferenceScreen;
import android.preference.SwitchPreference;
import android.support.v7.app.ActionBar;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.view.MenuItem;
import android.view.View;
import android.widget.RelativeLayout;

import java.util.ArrayList;
import java.util.List;

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
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            isHome = false;
            addPreferencesFromResource(R.xml.pref_general);
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

                    SwitchPreference headphones = new SwitchPreference(this.getContext());
                    headphones.setTitle(R.string.pref_headphones_on);
                    headphones.setSummary(R.string.pref_headphones_on_description);
                    headphones.setKey("key_" + packageInfo.packageName + "_headphones_on");

                    SwitchPreference headset = new SwitchPreference(this.getContext());
                    headset.setTitle(R.string.pref_headset_on);
                    headset.setSummary(R.string.pref_headset_on_description);
                    headset.setKey("key_" + packageInfo.packageName + "_headset_on");

                    SwitchPreference bluetooth = new SwitchPreference(this.getContext());
                    bluetooth.setTitle(R.string.pref_bluetooth_on);
                    bluetooth.setSummary(R.string.pref_bluetooth_on_description);
                    bluetooth.setKey("key_" + packageInfo.packageName + "_bluetooth_on");

                    SwitchPreference screenOff = new SwitchPreference(this.getContext());
                    screenOff.setTitle(R.string.pref_only_screen_off);
                    screenOff.setSummary(R.string.pref_only_screen_off_description);
					screenOff.setKey("key_" + packageInfo.packageName + "_only_screen_off");

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
                    pc.addPreference(headphones);
                    pc.addPreference(headset);
                    pc.addPreference(bluetooth);
                    pc.addPreference(screenOff);
					pc.addPreference(blacklistWordsInTitle);
					pc.addPreference(blacklistWordsInText);

					screenOff.setDependency(useGeneral.getKey());
					bluetooth.setDependency(useGeneral.getKey());
					headset.setDependency(useGeneral.getKey());
					checkReplica.setDependency(useGeneral.getKey());
					headphones.setDependency(useGeneral.getKey());
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

	//show
	public void showLoadingAnimation() {
		RelativeLayout pageLoading = (RelativeLayout) findViewById(R.id.main_layoutPageLoading);
		pageLoading.setVisibility(View.VISIBLE);
	}


	//hide
	public void hideLoadingAnimation() {
		RelativeLayout pageLoading = (RelativeLayout) findViewById(R.id.main_layoutPageLoading);
		pageLoading.setVisibility(View.GONE);
	}
}
