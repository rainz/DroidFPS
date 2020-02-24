package rainz.test;

import java.nio.FloatBuffer;

import javax.microedition.khronos.opengles.GL10;
import javax.microedition.khronos.opengles.GL11;

import rainz.test.GLRenderer.VBOEnum;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.opengl.GLUtils;
import android.util.Log;
import android.view.MotionEvent;

public class UISystem {
  public static final int HUD_TEXT_MAX = 1024;
  
  public static int screenWidth;
  public static int screenHeight;
  public static int screenWidthP2;
  public static int screenHeightP2;

  public static final int SCREEN_HUD = 0;
  public static final int SCREEN_TITLE = 1;
  public static final int SCREEN_OPTIONS = 2;
  public static final int SCREEN_TOTAL = 3;
  public static UIScreen[] allScreens = new UIScreen[SCREEN_TOTAL];
  private static UIScreen[] screenStack = new UIScreen[SCREEN_TOTAL];
  private static int stackCount = 0;
  public static boolean bScreenChanged = false;
  
  public static void init()
  {
    allScreens[SCREEN_HUD] = new HUDScreen();
    allScreens[SCREEN_TITLE] = new TitleScreen();
    allScreens[SCREEN_OPTIONS] = new OptionsScreen();
    setOnlyScreen(SCREEN_TITLE);
  }
  
  public static void onSurfaceChanged(int width, int height)
  {
    screenWidth = width;
    screenHeight = height;
    screenWidthP2 = Utils.minPower2(screenWidth);
    screenHeightP2 = Utils.minPower2(screenHeight);
    
    UIScreen scrn;
    for (int i = 0; i < SCREEN_TOTAL; ++i)
    {
      scrn = allScreens[i];
      if (scrn == null)
        continue;
      allScreens[i].onSurfaceChanged(width, height);
    }
    
    scrn = getTopScreen();
    if (scrn != null)
      scrn.fillVBOs();
    
  }
  

  public static boolean pushScreen(int screen_idx)
  {
    if (stackCount >= SCREEN_TOTAL)
    {
      Log.w("pushScreen", "screenTop reaches max!");
      return false;
    }
    
    screenStack[stackCount++] = allScreens[screen_idx];
    bScreenChanged = true;
    
    return true;
  }
  
  public static boolean popScreen()
  {
    if (stackCount <= 1)
    {
      Log.w("PopScreen", "Cannot pop: last screen!");
      return false;
    }
    --stackCount;
    bScreenChanged = true;
    
    return true;
  }
  
  public static boolean setOnlyScreen(int screen_idx)
  {
    if (screen_idx < 0 || screen_idx >= SCREEN_TOTAL)
    {
      Log.w("SetOnlyScreen", "Invalid index!");
      return false;      
    }
    stackCount = 0;
    return pushScreen(screen_idx);
  }
  
  public static UIScreen getTopScreen()
  {
    if (stackCount > 0)
      return screenStack[stackCount-1];
    else
      return null;
  }  
}

class Widget {
  protected UIScreen parent = null;
  protected Rect rect = new Rect(); // relative coordinates to parent
  public boolean visible = true;

  // Only for dynamic widgets
  protected Bitmap bitmap = null;
  protected Canvas canvas = null;

  public Widget()
  {
  }
  
  // Dynamic Widget
  public Widget(int w, int h)
  {
    w = Utils.minPower2(w);
    h = Utils.minPower2(h);
    bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_4444);
    canvas = new Canvas(bitmap);
  }
  
  public boolean onClickDown()
  {
    return true;
  }

  public boolean onClickUp()
  {
    return true;
  }
  
  public int appendToBuffers(int left, int top, FloatBuffer v_buf, FloatBuffer t_buf)
  {
    float v_left = left + rect.left;
    float v_right = left + rect.right;
    float v_top = top + rect.top;
    float v_bottom = top + rect.bottom;
    
    Utils.appendQuadYVertices(v_left, v_top, 0, v_right, v_bottom, 0, v_buf);

    float t_left = v_left/UISystem.screenWidthP2;
    float t_right = v_right/UISystem.screenWidthP2;
    float t_top = v_top/UISystem.screenHeightP2;
    float t_bottom = v_bottom/UISystem.screenHeightP2;
    Utils.appendQuadTexCoords(t_left, t_top, t_right, t_bottom, t_buf);
    Log.d("appendv", "left="+v_left+",top="+v_top+",right="+v_right+",bottom="+v_bottom);
    Log.d("appendt", "left="+t_left+",top="+t_top+",right="+t_right+",bottom="+t_bottom);
    return 1;
  }
  
  public void drawDynamic()
  {
    // Do nothing since the base class Widget has static image
  }
  
  /*
  public boolean drawTexture(Canvas canvas, int left, int top)
  {
    if (!needRepaint)
      return false;
    int actual_left = left+rect.left;
    int actual_top = top+rect.top;
    //canvas.save();
    //canvas.clipRect(actual_left, actual_top, left+rect.right, top+rect.bottom);
    //draw(canvas, actual_left, actual_top);
    draw(canvas, 100, 100);
    //canvas.restore();
    needRepaint = false;
    return true;
  }
  */
}

class WidgetContainer extends Widget {
  protected static final int MAX_WIDGET = 20;
  public Widget widgets[] = new Widget[MAX_WIDGET];
  public int widgetCount = 0;
  
  @Override
  public int appendToBuffers(int left, int top, FloatBuffer v_buf, FloatBuffer t_buf)
  {
    int actual_count = 0;
    final int actual_left = left + rect.left;
    final int actual_top = top + rect.top;
    for (int i = 0; i < widgetCount; ++i)
    {
      Widget wid = widgets[i];
      actual_count += wid.appendToBuffers(actual_left, actual_top, v_buf, t_buf);
    }
    return actual_count;
  }
  
  public boolean addWidget(Widget wid)
  {
    if (widgetCount >= MAX_WIDGET)
      return false;
    widgets[widgetCount++] = wid;
    return true;
  }
  
  /*
  @Override
  public boolean drawTexture(Canvas canvas, int left, int top)
  {
    boolean repaint = super.drawTexture(canvas, left, top);
    for (int i = 0; i < widgetCount; ++i)
    {
      Widget wid = widgets[i];
      if (wid.drawTexture(canvas, left+rect.left, top+rect.top))
        repaint = true;
    }
    return repaint;
  }
  */
}

class ScreenObj extends WidgetContainer {

  protected Bitmap bitmap;
  protected Canvas canvas;
  public FloatBuffer vBuffer;
  public FloatBuffer tBuffer;
  
  public ScreenObj()
  {
    parent = null;
    rect.left = 0;
    rect.right = UISystem.screenWidth;
    rect.top = 0;
    rect.bottom = UISystem.screenHeight;
    
    bitmap = Bitmap.createBitmap(UISystem.screenWidthP2, 
                                 UISystem.screenHeightP2,
                                 Bitmap.Config.ARGB_4444);
    canvas = new Canvas(bitmap);
    // To do: MAX_WIDGET might not be enough since sub-containers might have more
    vBuffer = Utils.getFloatBuffer(GLRenderer.V_FLOATS_PER_QUAD*MAX_WIDGET);
    tBuffer = Utils.getFloatBuffer(GLRenderer.T_FLOATS_PER_QUAD*MAX_WIDGET);
    vBuffer.rewind();
    tBuffer.rewind();
  }
  
  public int buildRects()
  {
    vBuffer.rewind();
    tBuffer.rewind();
    // To do: use actual_count
    int actual_count = appendToBuffers(0, 0, vBuffer, tBuffer);
    vBuffer.rewind();
    tBuffer.rewind();
    return actual_count;
  }
  
  public boolean buildScreen()
  {
    bitmap.eraseColor(0xaaaaaaaa);
    //return drawTexture(canvas, 0, 0);
    return true;
  }

}

class TextLabel extends Widget {
  public StringBuilder textBuilder = new StringBuilder(UISystem.HUD_TEXT_MAX);
  public static final int DEFAULT_TEXT_SIZE = 16;
  public int textSize = 16;
  private Paint paint;  
  
  public TextLabel(int w, int h, Paint pnt)
  {
    super(w, h);
    paint = pnt;
  }
  
  @Override
  public void drawDynamic()
  {
    bitmap.eraseColor(0);
    canvas.drawText(textBuilder.toString(), 0, textSize, paint);
  }
}

class ToggleMenu extends Widget {
  public boolean active = false;
  
  public ToggleMenu()
  {
    super();
  }

}
