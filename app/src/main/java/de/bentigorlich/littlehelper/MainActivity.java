package de.bentigorlich.littlehelper;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.provider.Settings;
import android.support.design.widget.TabLayout;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;

import static android.content.ContentValues.TAG;
public class MainActivity extends AppCompatActivity {

	private TabLayout logtabs;

    int id = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
		Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
		Log.i("toolbar", "started");
		logtabs = findViewById(R.id.logTabs);

		logtabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
			@Override
			public void onTabSelected(TabLayout.Tab tab) {
				populateForTab(tab);
			}

			@Override
			public void onTabUnselected(TabLayout.Tab tab) {

			}

			@Override
			public void onTabReselected(TabLayout.Tab tab) {

			}
		});

		refresh();

	}

	@Override
	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
		menu.add("Delete Logs");
		super.onCreateContextMenu(menu, v, menuInfo);
	}

	@Override
	public boolean onContextItemSelected(MenuItem item) {
		if (item.getTitle().equals("Delete Logs")) {
			Log.d(TAG, "deleting logs...");
		}
		return super.onContextItemSelected(item);
	}

	/**
	 * load the log file associated with the tab-name
	 *
	 * @param tab the tab containing the name
	 */
	private void populateForTab(TabLayout.Tab tab) {
		for (String fileName : fileList()) {
			String packageName = fileName.substring(0, fileName.length() - 4);
			Log.d(TAG, packageName);
			PackageManager packageManager = getApplicationContext().getPackageManager();
			try {
				String appName = (String) packageManager.getApplicationLabel(packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA));
				if (tab.getText().toString().equals(appName)) {
					LinearLayout logView = findViewById(R.id.logView);
					logView.removeAllViews();
					File currFile = new File(getFilesDir(), fileName);
					BufferedReader bfr = new BufferedReader(new FileReader(currFile));
					String line;

					ArrayList<String> lines = new ArrayList<>();
					while ((line = bfr.readLine()) != null) {
						lines.add(line);
					}
					bfr.close();

					int i = 0;
					if (lines.size() > 100) {
						i = lines.size() - 100;
					}
					while (i < lines.size()) {
						String currLine = lines.get(i);
						String[] args = currLine.split(":");
						if (args.length == 4) {
							TextView curr = new TextView(getApplicationContext());
							Calendar posted = Calendar.getInstance();
							posted.setTimeInMillis(Long.parseLong(args[0]));
							String date = posted.get(Calendar.DAY_OF_MONTH)
									+ "." + posted.get(Calendar.MONTH)
									+ "." + posted.get(Calendar.YEAR)
									+ ">" + posted.get(Calendar.HOUR_OF_DAY)
									+ ":" + posted.get(Calendar.MINUTE);
							curr.setText(date + " status: " + args[1] + "\n\tTitle = " + args[2] + "\n\tText = " + args[3]);
							curr.setTextSize(14);

							logView.addView(curr, 0);
						}
					}

				}
			} catch (PackageManager.NameNotFoundException ignored) {
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	private void refresh() {

		logtabs.removeAllTabs();

		for (String fileName : fileList()) {
			String packageName = fileName.substring(0, fileName.length() - 4);
			Log.d(TAG, "filename: " + fileName);
			PackageManager packageManager = getApplicationContext().getPackageManager();
			try {
				String appName = (String) packageManager.getApplicationLabel(packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA));
				TabLayout.Tab curr = logtabs.newTab();
				curr.setText(appName);
				logtabs.addTab(curr);
			} catch (PackageManager.NameNotFoundException ignored) {
			}
		}
		if (logtabs.getTabCount() == 0) {
			TextView nologs = new TextView(this);
			nologs.setText(R.string.nologs_warning);
			LinearLayout logView = findViewById(R.id.logView);
			logView.removeAllViews();
			logView.addView(nologs);
		} else {
			populateForTab(logtabs.getTabAt(logtabs.getSelectedTabPosition()));
		}

		if (!NotificationListener.connected) {
			AlertDialog.Builder builder = new AlertDialog.Builder(this);
			builder.setTitle("Permission missing...");
			builder.setMessage("The permission for reading notification is missing. You wanna fix that?!");
			builder.setPositiveButton("Open Settings", (dialog, which) -> {
				Intent intent = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
				startActivity(intent);
			});
			builder.setNegativeButton("No I want to stay boring", (dialog, which) -> {
				dialog.dismiss();
			});
			builder.create().show();
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.toolbar, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle action bar item clicks here. The action bar will
		// automatically handle clicks on the Home/Up button, so long
		// as you specify a parent activity in AndroidManifest.xml.
		int id = item.getItemId();

		//noinspection SimplifiableIfStatement
		switch (id) {
			case R.id.action_settings:
				Intent startSettings = new Intent(this, SettingsActivity.class);
				startActivity(startSettings);
				break;
			case R.id.action_refresh:
				refresh();
				break;
			case R.id.action_delete_logs:
				Log.i(TAG, "Deleting all log files...");
				File[] log_files = getFilesDir().listFiles();
				for (File curr : log_files) {
					boolean ignored = curr.delete();
					Log.d(TAG, "Deleted: " + curr.getName());
				}
				refresh();
				break;
			case R.id.action_kill_listener:
				Log.i(TAG, "Kill Service");
				stopService(new Intent(this, NotificationListener.class));
				break;
			case R.id.action_start_listener:
				Log.i(TAG, "Start Service");
				startService(new Intent(this, NotificationListener.class));
				break;
			case R.id.action_open_settings:
				Log.i(TAG, "Open notification listener settings");
				Intent intent = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
				startActivity(intent);
				break;
		}

		return super.onOptionsItemSelected(item);
    }

	@Override
	public void onBackPressed() {
		super.onBackPressed();
		finish();
	}
}
