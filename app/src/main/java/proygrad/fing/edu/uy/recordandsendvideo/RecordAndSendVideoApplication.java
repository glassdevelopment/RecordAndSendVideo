package proygrad.fing.edu.uy.recordandsendvideo;

import android.app.Application;

import com.squareup.otto.Bus;
import com.squareup.otto.ThreadEnforcer;

/**
 * Created by gonzalomelov on 3/16/15.
 */
public class RecordAndSendVideoApplication extends Application {
  private static RecordAndSendVideoApplication instance;

  public static Bus bus = new Bus(ThreadEnforcer.MAIN);

  public RecordAndSendVideoApplication() {
    super();
    instance = this;
  }

  public static RecordAndSendVideoApplication getContext() {
    return instance;
  }

}
