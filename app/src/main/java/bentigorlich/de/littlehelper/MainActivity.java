package bentigorlich.de.littlehelper;

import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

public class MainActivity extends AppCompatActivity {

    EditText mEdit;
    Button btn;
    int id = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        Log.i("main","started");

        mEdit = findViewById(R.id.noteText);
        btn = findViewById(R.id.sendNote);
        btn.setOnClickListener(this::sendNotification);
    }

    public void sendNotification(View view){
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this);
        builder.setContentText(mEdit.getText());
        builder.setCategory("Test");
        builder.setAutoCancel(true);
        builder.setContentTitle("new Notification");
        builder.setContentIntent(null);
        builder.setPriority(NotificationCompat.PRIORITY_DEFAULT);
        builder.setSmallIcon(R.drawable.ic_notifications_black_24dp);
        NotificationManagerCompat manager = NotificationManagerCompat.from(this);
        manager.notify(id, builder.build());
        Log.i("main", "sent Notification");
        id++;
    }

}
