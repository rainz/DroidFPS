package rainz.test;

import rainz.test.R;

import android.content.Context;
import android.graphics.Rect;
import android.media.AudioManager;
import android.media.SoundPool;
import android.opengl.GLSurfaceView;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;

public class MainGLSurface extends GLSurfaceView {

  public static final int TOUCH_FORWARD_LEFT = 100;
  public static final int TOUCH_BACK_LEFT = 101;
  public static final int TOUCH_FORWARD_RIGHT = 102;
  public static final int TOUCH_BACK_RIGHT = 103;
  
  public static final int PRIMARY_TOUCH = 0;
  public static final int SECONDARY_TOUCH = 1;
  private int[] touches = new int[2];
  
  public MainGLSurface(Context context)
  {
    super(context);

    GameManager.context = context;
    GameManager.surface = this;
    
    touches[PRIMARY_TOUCH] = AIUserInput.INPUT_OTHER;
    touches[SECONDARY_TOUCH] = AIUserInput.INPUT_OTHER;
  }
  
  @Override
  public boolean onTouchEvent(MotionEvent event)
  {
    if (UISystem.getTopScreen().onTouchEvent(event))
      return true;
  
    float touch_x = event.getX();
    float touch_y = event.getY();
    //float touch_pressure = event.getPressure();
    //float touch_size = event.getSize();
    int action = event.getAction();

    float center_x = getWidth()/2.0f;
    //float center_y = getHeight()/2.0f;

    if (GameManager.renderer.nearPlaneWidth < 0)
      return true; // not ready
    
    if (action == MotionEvent.ACTION_UP)
    {
      GameObject playerVehicle = GameManager.thePlayer.vehicle;
      if (touches[PRIMARY_TOUCH] == AIUserInput.INPUT_OTHER)
      //if (!handleOnScreen(touch_x, touch_y, false, PRIMARY_TOUCH))
      {
        float actual_x = (touch_x - center_x) / getWidth() * GameManager.renderer.nearPlaneWidth;
        float degree_turn = (float) Math.toDegrees(Math.atan(actual_x/GameManager.renderer.nearZ));
        AIUserInput ai = (AIUserInput) playerVehicle.ai;
        ai.autoTurn(Math.round(-degree_turn));
      }
      else
      {
        AIUserInput aiUser = (AIUserInput)playerVehicle.ai;
        switch (touches[PRIMARY_TOUCH])
        {
        case TOUCH_FORWARD_LEFT:
          aiUser.handleInput(AIUserInput.INPUT_FORWARD, false);
          aiUser.handleInput(AIUserInput.INPUT_LEFT_STRAFE, false);
          break;
        case TOUCH_FORWARD_RIGHT:
          aiUser.handleInput(AIUserInput.INPUT_FORWARD, false);
          aiUser.handleInput(AIUserInput.INPUT_RIGHT_STRAFE, false);
          break;
        case TOUCH_BACK_LEFT:
          aiUser.handleInput(AIUserInput.INPUT_BACK, false);
          aiUser.handleInput(AIUserInput.INPUT_LEFT_STRAFE, false);
          break;
        case TOUCH_BACK_RIGHT:
          aiUser.handleInput(AIUserInput.INPUT_BACK, false);
          aiUser.handleInput(AIUserInput.INPUT_RIGHT_STRAFE, false);
          break;
        default:
          aiUser.handleInput(touches[PRIMARY_TOUCH], false);
          break;
        }
      }
    }
    else if (action == MotionEvent.ACTION_DOWN)
    {
      handleOnScreen(touch_x, touch_y, true, PRIMARY_TOUCH);
    }
    else if (action == MotionEvent.ACTION_MOVE)
    {
    }
    

    try {
      Thread.sleep(20);
    }
    catch (InterruptedException e) {} //ignore 
  
    return true;
  }

  protected boolean handleOnScreen(float touch_x, float touch_y, boolean bDown, int touch)
  {
    GameObject playerVehicle = GameManager.thePlayer.vehicle;
    AIUserInput aiUser = (AIUserInput)playerVehicle.ai;
    //Rect dpadRect = GameManager.renderer.rectsHUD[GLRenderer.HUD_DPAD];
    UIScreen scrn = UISystem.getTopScreen();
    Rect rectDpad = scrn.widgets[HUDScreen.HUD_DPAD].rect; // for now
    final int width = rectDpad.right - rectDpad.left;
    final int height = rectDpad.bottom - rectDpad.top;
    final float pct = 0.35f;
    
    // Check fire button
    //Rect rectFire = GameManager.renderer.rectsHUD[GLRenderer.HUD_FIRE];
    Rect rectFire = scrn.widgets[HUDScreen.HUD_FIRE].rect; // for now
    if (touch_x >= rectFire.left && touch_x <= rectFire.right &&
        touch_y >= rectFire.top && touch_y <= rectFire.bottom)
    {    
      aiUser.handleInput(AIUserInput.INPUT_FIRE, bDown);
      touches[touch] = AIUserInput.INPUT_FIRE;
      return true;
    }
    
    // Check DPAD
    if (touch_x < rectDpad.left || touch_x > rectDpad.right ||
        touch_y < rectDpad.top || touch_y > rectDpad.bottom)
    {
      touches[touch] = AIUserInput.INPUT_OTHER;
      return false;
    }
    
    boolean bLeft = false, bRight = false, bForward = false, bBack = false;
    if (touch_x <= rectDpad.left + width*pct)
    {
      aiUser.handleInput(AIUserInput.INPUT_LEFT_STRAFE, bDown);
      bLeft = true;
    }
    else if (touch_x >= rectDpad.right - width*pct)
    {
      aiUser.handleInput(AIUserInput.INPUT_RIGHT_STRAFE, bDown);
      bRight = true;
    }
    
    if (touch_y <= rectDpad.top + height*pct)
    {
      aiUser.handleInput(AIUserInput.INPUT_FORWARD, bDown);
      bForward = true;
    }
    else if (touch_y >= rectDpad.bottom - height*pct)
    {
      aiUser.handleInput(AIUserInput.INPUT_BACK, bDown);
      bBack = true;
    }
    
    if (bLeft)
    {
      if (bForward)
        touches[touch] = TOUCH_FORWARD_LEFT;
      else if (bBack)
        touches[touch] = TOUCH_BACK_LEFT;
      else
        touches[touch] = AIUserInput.INPUT_LEFT_STRAFE;
    }
    else if (bRight)
    {
      if (bForward)
        touches[touch] = TOUCH_FORWARD_RIGHT;
      else if (bBack)
        touches[touch] = TOUCH_BACK_RIGHT;
      else
        touches[touch] = AIUserInput.INPUT_RIGHT_STRAFE;      
    }
    else if (bForward)
      touches[touch] = AIUserInput.INPUT_FORWARD;
    else if (bBack)
      touches[touch] = AIUserInput.INPUT_BACK; 
    
    return true;
  }
  
  protected boolean handleKey(int key, KeyEvent e, boolean bDown)
  {
    GameObject playerVehicle = GameManager.thePlayer.vehicle;
    AIUserInput aiUser = (AIUserInput)playerVehicle.ai;
    
    if (key == KeyEvent.KEYCODE_DPAD_LEFT)
    {
      aiUser.handleInput(AIUserInput.INPUT_LEFT_TURN, bDown);
    }
    else if (key == KeyEvent.KEYCODE_DPAD_RIGHT)
    {
      aiUser.handleInput(AIUserInput.INPUT_RIGHT_TURN, bDown);
    }
    else if (key == KeyEvent.KEYCODE_DPAD_UP)
    {
      aiUser.handleInput(AIUserInput.INPUT_UP, bDown);
    }
    else if (key == KeyEvent.KEYCODE_DPAD_DOWN)
    {
      aiUser.handleInput(AIUserInput.INPUT_DOWN, bDown);
    }
    else if (key == KeyEvent.KEYCODE_DPAD_CENTER)
    {
    }
    else if (key == KeyEvent.KEYCODE_A)
    {
      aiUser.handleInput(AIUserInput.INPUT_LEFT_STRAFE, bDown);
    }
    else if (key == KeyEvent.KEYCODE_S)
    {
      aiUser.handleInput(AIUserInput.INPUT_BACK, bDown);
    }
    else if (key == KeyEvent.KEYCODE_D)
    {
      aiUser.handleInput(AIUserInput.INPUT_RIGHT_STRAFE, bDown);
    }
    else if (key == KeyEvent.KEYCODE_W)
    {
      aiUser.handleInput(AIUserInput.INPUT_FORWARD, bDown);
    }
    else if (key == KeyEvent.KEYCODE_SPACE)
    {
      aiUser.handleInput(AIUserInput.INPUT_FIRE, bDown);
    }
    else
      return false;
    
    return true;
    
  }
  
  @Override
  public boolean onKeyDown(int keyCode, KeyEvent event)
  {
    return handleKey(keyCode, event, true);
  }
  
  @Override
  public boolean onKeyUp(int keyCode, KeyEvent event)
  {
    return handleKey(keyCode, event, false);
  }
  
  @Override
  public void onPause()
  {
    super.onPause();
  }

  @Override
  public void onResume()
  {
    super.onResume();
  }
}
