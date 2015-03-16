package proygrad.fing.edu.uy.recordandsendvideo;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

/**
 * Created by gonzalomelov on 3/16/15.
 */
public class CameraRecorderActivity extends Activity {
  private static final String TAG = "Recorder";

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.main);

    final TextView status = (TextView)findViewById(R.id.status);

    Button btnStart = (Button) findViewById(R.id.start_service);
    btnStart.setOnClickListener(new View.OnClickListener()
    {
      public void onClick(View v)
      {
        Intent intent = new Intent(CameraRecorderActivity.this, BackgroundVideoRecorderService.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startService(intent);
        status.setText("Recording");
        finish();
      }
    });

    Button btnStop = (Button) findViewById(R.id.stop_service);
    btnStop.setOnClickListener(new View.OnClickListener()
    {
      public void onClick(View v)
      {
        stopService(new Intent(CameraRecorderActivity.this, BackgroundVideoRecorderService.class));
        status.setText("Stopped");
      }
    });
  }

}