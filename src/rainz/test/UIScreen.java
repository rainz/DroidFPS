package rainz.test;

import java.nio.FloatBuffer;

import javax.microedition.khronos.opengles.GL10;
import javax.microedition.khronos.opengles.GL11;

import rainz.test.GLRenderer.VBOEnum;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.Paint.Style;
import android.opengl.GLUtils;
import android.util.Log;
import android.view.MotionEvent;


public abstract class UIScreen {
  public FloatBuffer vBuffer;
  public FloatBuffer tBuffer;
  public int vSize = -1;
  public int tSize = -1;
  public int texturesDynamic[];
  public int textureStatic = -1;
  public int dynamicQuadStart = 0;
  public Widget[] widgets;
  public int screenWidth = 0;
  public int screenHeight = 0;
  
  public int vboStart = -1;
  
  public UIScreen(int widget_count, int dynamic_count)
  {
    vSize = GLRenderer.V_FLOATS_PER_QUAD*widget_count*4;
    vBuffer = Utils.getFloatBuffer(vSize/4);
    vBuffer.rewind();
    tSize = GLRenderer.T_FLOATS_PER_QUAD*widget_count*4;
    tBuffer = Utils.getFloatBuffer(tSize/4);
    tBuffer.rewind();
    widgets = new Widget[widget_count];
    dynamicQuadStart = widget_count - dynamic_count;
    
    texturesDynamic = new int[dynamic_count];
  }
  
  final public boolean drawWidget(int widget_idx)
  {
    if (widget_idx < dynamicQuadStart || widget_idx >= widgets.length)
    {
      Log.w("ScreenBase", "Invalid widget_idx!");
      return false;
    }
    Widget w = widgets[widget_idx];
    if (!w.visible)
      return false;
    if (w.bitmap != null)
    {
      w.drawDynamic();
      GLUtils.texImage2D(GL10.GL_TEXTURE_2D, 0, w.bitmap, 0);
    }    
    return true;
  }
  
  public final void onSurfaceChanged(int width, int height)
  {
    int dynamic_count = widgets.length - dynamicQuadStart;
    GameManager.gl.glGenTextures(dynamic_count, texturesDynamic, 0);
    
    screenWidth = width;
    screenHeight = height;

    vBuffer.rewind();
    tBuffer.rewind();
    
    initWidgets();
    
    vBuffer.rewind();
    tBuffer.rewind();
    //fillVBOs();
  }

  public final void fillVBOs()
  {
    if (vSize <= 0 || tSize <= 0)
      return;
    
    vBuffer.rewind();
    tBuffer.rewind();
    
    GL11 gl11 = GameManager.gl;
    int vbo_v = GameManager.renderer.bufferVBOs[vboStart];
    int vbo_t = GameManager.renderer.bufferVBOs[vboStart+1];
    gl11.glBindBuffer(GL11.GL_ARRAY_BUFFER, vbo_v);
    gl11.glBufferData(GL11.GL_ARRAY_BUFFER, vSize, vBuffer, GL11.GL_STATIC_DRAW);
    gl11.glBindBuffer(GL11.GL_ARRAY_BUFFER, vbo_t);
    gl11.glBufferData(GL11.GL_ARRAY_BUFFER, tSize, tBuffer, GL11.GL_STATIC_DRAW);
    UISystem.bScreenChanged = false;
  }
  
  abstract protected void initWidgets();
  
  public void positionWidget(int widget_idx, int left, int top, int right, int bottom, float[] tex_coord)
  {
    Widget widget = widgets[widget_idx];
    widget.parent = this;
    Rect rect = widget.rect;
    rect.left = left;
    rect.bottom = bottom;
    rect.right = right;
    rect.top = top;
    Utils.appendQuadYVertices(left, screenHeight-top, 0, right, screenHeight-bottom, 0, vBuffer);
    tBuffer.put(tex_coord);
  }
  
  public void positionWidgetPct(int widget_idx, float left_pct, float top_pct, float right_pct, float bottom_pct, float[] tex_coord)
  {
    int left = Math.round(screenWidth*left_pct);
    int right = Math.round(screenWidth*right_pct);
    int top = Math.round(screenHeight*top_pct);
    int bottom = Math.round(screenHeight*bottom_pct);
    positionWidget(widget_idx, left, top, right, bottom, tex_coord);    
  }
  
  public boolean onTouchEvent(MotionEvent event)
  {
    if (event.getAction() == MotionEvent.ACTION_UP)
    {
      int touch_x = (int)(event.getX()+0.5f);
      int touch_y = (int)(event.getY()+0.5f);
      int len = widgets.length;
      for (int i = 0; i < len; ++i)
      {
        Widget w = widgets[i];
        if (w.visible && w.rect.contains(touch_x, touch_y))
        {
          w.onClickUp();
          break;
        }
      }
    }    
    return true;  
  }
  
  @Override
  public void finalize()
  {
    int dynamic_count = widgets.length - dynamicQuadStart;
    GameManager.gl.glDeleteTextures(dynamic_count, texturesDynamic, 0);
  }
}

class HUDScreen extends UIScreen {
  
  public static final int HUD_DPAD = 0;
  public static final int HUD_FIRE = 1;
  public static final int HUD_FRAME_RATE = 2;
  public static final int HUD_SCORE = 3;
  public static final int HUD_LIFE = 4;
  public static final int HUD_TOTAL = 5;
  private Paint textPaint = new Paint();
  private Paint lifePaint = new Paint();
  
  public HUDScreen()
  {
    super(HUD_TOTAL, 3);

    vboStart = VBOEnum.HUD_START;
    
    textPaint.setTextSize(TextLabel.DEFAULT_TEXT_SIZE);
    textPaint.setAntiAlias(true);
    textPaint.setARGB(0xbb, 0xff, 0xff, 0xff);
    textPaint.setTextAlign(Paint.Align.LEFT);
    Typeface typeface = Typeface.create("San Serif", Typeface.BOLD);
    textPaint.setTypeface(typeface);
    
    widgets[HUD_DPAD] = new Widget();
    widgets[HUD_FIRE] = new Widget();
    
    widgets[HUD_FRAME_RATE] = new TextLabel(64, TextLabel.DEFAULT_TEXT_SIZE, textPaint);
    widgets[HUD_FRAME_RATE].visible = GameManager.bShowFrameRate;
    
    widgets[HUD_SCORE] = new TextLabel(64, TextLabel.DEFAULT_TEXT_SIZE, textPaint);
    
    widgets[HUD_LIFE] = new Widget(64, 8) {
      @Override
      public void drawDynamic()
      {
        final int MAX_LEN = 63;
        GameObject vehicle = GameManager.thePlayer.vehicle;
        float life_len = MAX_LEN*vehicle.hitPoints/(float)vehicle.maxHitPoints;
        if (vehicle.hitPoints > 0 && life_len < 1)
          life_len = 1;
        bitmap.eraseColor(0);
        lifePaint.setColor(Color.RED);
        lifePaint.setStyle(Style.FILL);
        canvas.drawRect(0, 0, life_len, 7, lifePaint);
        lifePaint.setColor(Color.GREEN);
        lifePaint.setStyle(Style.STROKE);
        canvas.drawRect(0, 0, MAX_LEN, 7, lifePaint);
      }
      
    };
  }
  
  @Override
  public void initWidgets()
  {
    TextureAtlas ta = TextureManager.textures[TextureManager.textureAll];
    textureStatic = ta.textureHandle;
    
    // Note that in Canvas coordinates, Y increases as it goes down
    final int margin = 5;
    final int dpad_side = screenWidth / 4;
    final int fire_side = dpad_side / 2;
    float texture_temp[] = new float[12];

    ta.getTexCoordsArray(8, 4, 12, 8, texture_temp);
    positionWidget(HUD_DPAD, margin, screenHeight-margin-dpad_side, margin+dpad_side, screenHeight-margin, texture_temp);
    
    ta.getTexCoordsArray(15, 0, 16, 1, texture_temp);
    positionWidget(HUD_FIRE, screenWidth-margin-fire_side, screenHeight-margin-fire_side, screenWidth-margin, screenHeight-margin, texture_temp);
    
    Utils.getQuadTexArray(texture_temp, 0, 0, 0, 1, 1);
    positionWidget(HUD_FRAME_RATE, 0, screenHeight-TextLabel.DEFAULT_TEXT_SIZE, 64, screenHeight, texture_temp);
    
    Utils.getQuadTexArray(texture_temp, 0, 0, 0, 1, 1);
    positionWidget(HUD_SCORE, screenWidth-64, 0, screenWidth, TextLabel.DEFAULT_TEXT_SIZE, texture_temp);
    
    Utils.getQuadTexArray(texture_temp, 0, 0, 0, 1, 1);
    positionWidget(HUD_LIFE, 0, 0, 64, 8, texture_temp);
  }
  
  @Override 
  public boolean onTouchEvent(MotionEvent event)
  { 
    return false; // Let MainGLSurface handle this.  
  }
}

class TitleScreen extends UIScreen {
  public static final int WIDGET_TITLE = 0;
  public static final int WIDGET_START = 1;
  public static final int WIDGET_OPTION = 2;
  public static final int WIDGET_TOTAL = 3;
  
  public TitleScreen()
  {
    super(WIDGET_TOTAL, 0);
    
    vboStart = VBOEnum.MENU_START;
    
    widgets[WIDGET_TITLE] = new Widget();
    widgets[WIDGET_START] = new Widget() {
      public boolean onClickUp()
      {
        super.onClickUp();
        GameManager.gameState = GameManager.GameStateEnums.LEVEL_INTRO;
        return true;
      }
    };
    widgets[WIDGET_OPTION] = new Widget() {
      @Override
      public boolean onClickUp()
      {
        super.onClickUp();
        UISystem.pushScreen(UISystem.SCREEN_OPTIONS);
        return true;
      }
    };
  }

  @Override
  public void initWidgets()
  {
    TextureAtlas ta = TextureManager.textures[TextureManager.textureUI];
    textureStatic = ta.textureHandle;
 
    float texture_temp[] = new float[12];

    ta.getTexCoordsArray(14, 8, 16, 16, texture_temp);
    positionWidgetPct(WIDGET_TITLE, 0.1f, 0.1f, 0.9f, 0.4f, texture_temp);
    
    ta.getTexCoordsArray(0, 0, 1, 4, texture_temp);
    positionWidgetPct(WIDGET_START, 0.4f, 0.4f, 0.6f, 0.5f, texture_temp);
    
    ta.getTexCoordsArray(1, 0, 2, 4, texture_temp);
    positionWidgetPct(WIDGET_OPTION, 0.4f, 0.6f, 0.6f, 0.7f, texture_temp);

  }
}

class OptionsScreen extends UIScreen {
  public static final int WIDGET_TEXTURE = 0;
  public static final int WIDGET_SOUND = 1;
  public static final int WIDGET_OK = 2;
  public static final int WIDGET_CANCEL = 3;
  public static final int WIDGET_TOTAL = 4;
  
  public OptionsScreen()
  {
    super(WIDGET_TOTAL, 0);
    
    vboStart = VBOEnum.MENU_START;
    
    widgets[WIDGET_TEXTURE] = new Widget() {
      public boolean onClickUp()
      {
        super.onClickUp();
        
        return true;
      }
    };
    widgets[WIDGET_SOUND] = new Widget() {
      public boolean onClickUp()
      {
        super.onClickUp();
        
        return true;
      }
    };
    widgets[WIDGET_OK] = new Widget() {
      public boolean onClickUp()
      {
        super.onClickUp();
        
        return true;
      }
    };
    widgets[WIDGET_CANCEL] = new Widget() {
      public boolean onClickUp()
      {
        super.onClickUp();
        UISystem.popScreen();        
        return true;
      }
    };
  }

  @Override
  public void initWidgets()
  {
    TextureAtlas ta = TextureManager.textures[TextureManager.textureUI];
    textureStatic = ta.textureHandle;
 
    float texture_temp[] = new float[12];

    ta.getTexCoordsArray(2, 0, 3, 4, texture_temp);
    positionWidgetPct(WIDGET_TEXTURE, 0.4f, 0.2f, 0.6f, 0.3f, texture_temp);
    
    ta.getTexCoordsArray(3, 0, 4, 4, texture_temp);
    positionWidgetPct(WIDGET_SOUND, 0.4f, 0.4f, 0.6f, 0.5f, texture_temp);

    ta.getTexCoordsArray(4, 0, 5, 4, texture_temp);
    positionWidgetPct(WIDGET_OK, 0.2f, 0.6f, 0.4f, 0.7f, texture_temp);

    ta.getTexCoordsArray(5, 0, 6, 4, texture_temp);
    positionWidgetPct(WIDGET_CANCEL, 0.7f, 0.6f, 0.9f, 0.7f, texture_temp);
  }
  
}