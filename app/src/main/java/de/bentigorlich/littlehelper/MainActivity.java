package de.bentigorlich.littlehelper;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.provider.Settings;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import static android.content.ContentValues.TAG;

public class MainActivity extends AppCompatActivity {

	TextView status;
	Button btn_start;
	Button btn_kill;
	Button btn_fix;
    int id = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
		Log.i("toolbar", "started");

		status = (TextView) findViewById(R.id.status_text);
		btn_start = (Button) findViewById(R.id.start_btn);
		btn_fix = (Button) findViewById(R.id.fix_btn);
		btn_kill = (Button) findViewById(R.id.kill_btn);

		btn_fix.setOnClickListener(v -> {
			Intent intent = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
			startActivity(intent);
		});

		btn_kill.setOnClickListener(v -> {
			Log.i(TAG, "Kill Service");
			stopService(new Intent(this, NotificationListener.class));
		});

		btn_start.setOnClickListener(v -> {
			Log.i(TAG, "Start Service");
			startService(new Intent(this, NotificationListener.class));
		});

		refresh();

	}

	private void refresh() {
		boolean hasPermission = NotificationListener.connected;
		Log.i(TAG, "Permission to read notifications: " + String.valueOf(hasPermission));
		if (hasPermission) {
			btn_fix.setVisibility(View.INVISIBLE);
			status.setText(R.string.permission_granted);
			status.setTextColor(Color.GREEN);
		} else {
			btn_fix.setVisibility(View.VISIBLE);
			status.setText(R.string.permission_denied);
			status.setTextColor(Color.RED);
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
		}

		return super.onOptionsItemSelected(item);
    }

	@Override
	public void onBackPressed() {
		finish();
	}
}
