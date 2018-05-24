package de.bentigorlich.littlehelper;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.widget.EditText;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

public class ShowLogActivity extends AppCompatActivity {

	EditText log;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.textview);
		String packageName = "";
		packageName = getIntent().getStringExtra("package");
		if (packageName == null) {
			onBackPressed();
		} else {
			if (packageName.equals("")) {
				onBackPressed();
			}
		}
		log = (EditText) findViewById(R.id.logView);

		File logfile = new File(this.getFilesDir(), packageName + ".log");
		try {
			BufferedReader reader = new BufferedReader(new FileReader(logfile));
			String content = "", line = "";
			for (int i = 0; (line = reader.readLine()) != null; i++) {
				if (i != 0) {
					content += "\n";
				}
				content += line;
			}
			reader.close();
			log.setText(content);
		} catch (IOException e) {
			log.setText(e.getMessage());
		}
	}

	@Override
	public void onBackPressed() {
		startActivity(new Intent(this, MainActivity.class));
	}
}
