package proygrad.fing.edu.uy.recordandsendvideo;

import android.app.Activity;
import android.content.Intent;
import android.opengl.GLES20;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;

import java.io.File;
import java.io.IOException;

import proygrad.fing.edu.uy.recordandsendvideo.gles.EglCore;
import proygrad.fing.edu.uy.recordandsendvideo.gles.WindowSurface;

/**
 * Created by gonzalomelov on 3/16/15.
 */
public class CameraRecorderActivity extends Activity implements AdapterView.OnItemSelectedListener, SurfaceHolder.Callback, MoviePlayer.PlayerFeedback {
  public static final String TAG = "Recorder";

  private SurfaceView mSurfaceView;
  private String[] mMovieFiles;
  private int mSelectedMovie;
  private boolean mShowStopLabel;
  private MoviePlayer.PlayTask mPlayTask;
  private boolean mSurfaceHolderReady = false;

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

    mSurfaceView = (SurfaceView) findViewById(R.id.playMovie_surface);
    mSurfaceView.getHolder().addCallback(this);

    // Populate file-selection spinner.
    Spinner spinner = (Spinner) findViewById(R.id.playMovieFile_spinner);
    // Need to create one of these fancy ArrayAdapter thingies, and specify the generic layout
    // for the widget itself.
    File mediaStorageDir = new File(Environment.getExternalStoragePublicDirectory(
        Environment.DIRECTORY_PICTURES), "BackgroundRecording");
    // This location works best if you want the created images to be shared
    // between applications and persist after your app has been uninstalled.

    // Create the storage directory if it does not exist
    if (!mediaStorageDir.exists()){
      if (!mediaStorageDir.mkdirs()) {
        Log.d("BackgroundRecording", "failed to create directory");
      }
    }

    mMovieFiles = MiscUtils.getFiles(mediaStorageDir, "*.mp4");
    ArrayAdapter<String> adapter = new ArrayAdapter<String>(this,
        android.R.layout.simple_spinner_item, mMovieFiles);
    adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
    // Apply the adapter to the spinner.
    spinner.setAdapter(adapter);
    spinner.setOnItemSelectedListener(this);

    updateControls();
  }

  @Override
  protected void onPause() {
    Log.d(TAG, "PlayMovieSurfaceActivity onPause");
    super.onPause();
    // We're not keeping track of the state in static fields, so we need to shut the
    // playback down.  Ideally we'd preserve the state so that the player would continue
    // after a device rotation.
    //
    // We want to be sure that the player won't continue to send frames after we pause,
    // because we're tearing the view down.  So we wait for it to stop here.
    if (mPlayTask != null) {
      stopPlayback();
      mPlayTask.waitForStop();
    }
  }

  @Override
  public void surfaceCreated(SurfaceHolder holder) {
    // There's a short delay between the start of the activity and the initialization
    // of the SurfaceHolder that backs the SurfaceView.  We don't want to try to
    // send a video stream to the SurfaceView before it has initialized, so we disable
    // the "play" button until this callback fires.
    Log.d(TAG, "surfaceCreated");
    mSurfaceHolderReady = true;
    updateControls();
  }

  @Override
  public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
    // ignore
    Log.d(TAG, "surfaceChanged fmt=" + format + " size=" + width + "x" + height);
  }

  @Override
  public void surfaceDestroyed(SurfaceHolder holder) {
    // ignore
    Log.d(TAG, "Surface destroyed");
  }

  /*
   * Called when the movie Spinner gets touched.
   */
  @Override
  public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
    Spinner spinner = (Spinner) parent;
    mSelectedMovie = spinner.getSelectedItemPosition();

    Log.d(TAG, "onItemSelected: " + mSelectedMovie + " '" + mMovieFiles[mSelectedMovie] + "'");
  }

  @Override public void onNothingSelected(AdapterView<?> parent) {}

  /**
   * onClick handler for "play"/"stop" button.
   */
  public void clickPlayStop(@SuppressWarnings("unused") View unused) {
    if (mShowStopLabel) {
      Log.d(TAG, "stopping movie");
      stopPlayback();
      // Don't update the controls here -- let the task thread do it after the movie has
      // actually stopped.
      //mShowStopLabel = false;
      //updateControls();
    } else {
      if (mPlayTask != null) {
        Log.w(TAG, "movie already playing");
        return;
      }

      Log.d(TAG, "starting movie");
      SpeedControlCallback callback = new SpeedControlCallback();
      SurfaceHolder holder = mSurfaceView.getHolder();
      Surface surface = holder.getSurface();

      // Don't leave the last frame of the previous video hanging on the screen.
      // Looks weird if the aspect ratio changes.
      clearSurface(surface);

      MoviePlayer player = null;
      try {
        player = new MoviePlayer(
            new File(new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "BackgroundRecording"), mMovieFiles[mSelectedMovie]), surface, callback);
      } catch (IOException ioe) {
        Log.e(TAG, "Unable to play movie", ioe);
        surface.release();
        return;
      }

      AspectFrameLayout layout = (AspectFrameLayout) findViewById(R.id.playMovie_afl);
      int width = player.getVideoWidth();
      int height = player.getVideoHeight();
      layout.setAspectRatio((double) width / height);
      //holder.setFixedSize(width, height);

      mPlayTask = new MoviePlayer.PlayTask(player, this);

      mShowStopLabel = true;
      updateControls();
      mPlayTask.execute();
    }
  }

  /**
   * Requests stoppage if a movie is currently playing.
   */
  private void stopPlayback() {
    if (mPlayTask != null) {
      mPlayTask.requestStop();
    }
  }

  @Override   // MoviePlayer.PlayerFeedback
  public void playbackStopped() {
    Log.d(TAG, "playback stopped");
    mShowStopLabel = false;
    mPlayTask = null;
    updateControls();
  }

  /**
   * Updates the on-screen controls to reflect the current state of the app.
   */
  private void updateControls() {
    Button play = (Button) findViewById(R.id.play_stop_button);
    if (mShowStopLabel) {
      play.setText("Stop");
    } else {
      play.setText("Play");
    }
    play.setEnabled(mSurfaceHolderReady);
  }

  /**
   * Clears the playback surface to black.
   */
  private void clearSurface(Surface surface) {
    // We need to do this with OpenGL ES (*not* Canvas -- the "software render" bits
    // are sticky).  We can't stay connected to the Surface after we're done because
    // that'd prevent the video encoder from attaching.
    //
    // If the Surface is resized to be larger, the new portions will be black, so
    // clearing to something other than black may look weird unless we do the clear
    // post-resize.
    EglCore eglCore = new EglCore();
    WindowSurface win = new WindowSurface(eglCore, surface, false);
    win.makeCurrent();
    GLES20.glClearColor(0, 0, 0, 0);
    GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
    win.swapBuffers();
    win.release();
    eglCore.release();
  }

}