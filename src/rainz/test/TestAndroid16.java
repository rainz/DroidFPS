package rainz.test;

import android.app.Activity;
import android.os.Bundle;
import android.view.Window;
import android.view.WindowManager;

public class TestAndroid16 extends Activity {
  
  /** Called when the activity is first created. */
  @Override
  public void onCreate(Bundle savedInstanceState)
  {
    super.onCreate(savedInstanceState);
    this.requestWindowFeature(Window.FEATURE_NO_TITLE);
    getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                         WindowManager.LayoutParams.FLAG_FULLSCREEN);
    MainGLSurface mySurface = new MainGLSurface(this);
    GLRenderer myRenderer = new GLRenderer();
    GameManager.init();
    mySurface.setRenderer(myRenderer);
    mySurface.setFocusable(true);
    setContentView(mySurface);
  }

  @Override
  protected void onResume()
  {
    super.onResume();
    GameManager.surface.onResume();
  }

  @Override
  protected void onPause()
  {
    super.onPause();
    GameManager.surface.onPause();
  }
}