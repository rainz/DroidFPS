package rainz.test;

import java.nio.Buffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.util.ArrayList;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;
import javax.microedition.khronos.opengles.GL11;
import javax.microedition.khronos.opengles.GL11Ext;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Region;
import android.opengl.GLU;
import android.opengl.GLUtils;
import android.opengl.GLSurfaceView.Renderer;
import android.os.Debug;
import android.util.Log;

public class GLRenderer implements Renderer {

  public static final int NUM_VBOS = 36;
  public int[] bufferVBOs = new int[NUM_VBOS];
  
  public class VBOEnum {
    static final int WORLD_START = 0;
    static final int BOUNDARY_START = WORLD_START + 2;
    static final int FLOOR_START = BOUNDARY_START + 2;
    static final int QUAD_START = FLOOR_START + 2;
    static final int MENU_START = QUAD_START + 2;
    static final int HUD_START = MENU_START + 2;
    static final int TANK_START = HUD_START + 2;
    static final int GUN_TURRET_START = TANK_START + 3;
    static final int SNIPER_TURRET_START = GUN_TURRET_START + 3;
  }

  public static final int VERT_PER_QUAD = 6;
  public static final int FACE_PER_BLOCK = 4;

  public static final int WALL_HEIGHT = 2;
  
  // ************* HUD ****************
  
  FloatBuffer vBufferHUD;
  FloatBuffer tBufferHUD;
  private int textureHUD[] = new int[1];
  private Canvas canvasHUD = null;
  private Bitmap bitmapHUD = null;
  private ShortBuffer bufferHUD = null;
  
  private static final int TEXT_SIZE = 16;

  public static final int HUD_DPAD = 0;
  public static final int HUD_FIRE = 1;
  public static final int HUD_FRAME_RATE = 2;
  public static final int HUD_SCORE = 3;
  public static final int HUD_TOTAL = 4;
  public Rect [] rectsHUD = new Rect[HUD_TOTAL];
  private StringBuilder frameRateText;
  private StringBuilder scoreText;
  Paint textPaint;
    
  // ************* World ****************
  public FloatBuffer worldVerticesBuffer;
  public FloatBuffer worldTextureBuffer;
  public int worldVertCount = 0;

  public FloatBuffer boundaryVerticesBuffer;
  public FloatBuffer boundaryColorBuffer;
  public int boundaryVertCount = VERT_PER_QUAD*5;
  
  public FloatBuffer floorVerticesBuffer;
  public FloatBuffer floorTextureBuffer;
  public int floorVertCount = 0;

  // ************* Quads ****************
  public static final int MAX_QUADS = 1024;
  public static final int V_FLOATS_PER_QUAD = 3*VERT_PER_QUAD;
  public static final int T_FLOATS_PER_QUAD = 2*VERT_PER_QUAD;
  // Use IntBuffer to work around FloatBuffer performance problem
  public IntBuffer vBufferQuadsInt;
  public IntBuffer tBufferQuadsInt;
  public float quadsVArray[];
  public float quadsTArray[];
  
  public float frameRate = 0;
  
  public float nearPlaneWidth = -1;
  public float nearPlaneHeight = -1;
  public float nearZ = -1;
  
  protected static int lastTexHandle = -1;
  
  public static int surfaceWidth = 0;
  public static int surfaceHeight = 0;

  private boolean bBlendWall = false;
  public boolean bFog = false;
  private final static float fogColor[] = { 0.5f, 0.5f, 0.5f, 1.0f };
  private final static int fogMode[]= { GL10.GL_EXP, GL10.GL_EXP2, GL10.GL_LINEAR };
  private int fogFilter = 0;

  private static FloatBuffer fogColorBuffer;

  public GLRenderer()
  {
    quadsVArray = new float[MAX_QUADS*V_FLOATS_PER_QUAD];
    quadsTArray = new float[MAX_QUADS*T_FLOATS_PER_QUAD];
    vBufferQuadsInt = Utils.getIntBuffer(MAX_QUADS*V_FLOATS_PER_QUAD);
    tBufferQuadsInt = Utils.getIntBuffer(MAX_QUADS*T_FLOATS_PER_QUAD);
    
    final int frameRateLen = 32;
    frameRateText = new StringBuilder(frameRateLen);
    frameRateText.ensureCapacity(frameRateLen);
    final int scoreLen = 16;
    scoreText = new StringBuilder(scoreLen);
    scoreText.ensureCapacity(scoreLen);
    tBufferHUD = Utils.getFloatBuffer(T_FLOATS_PER_QUAD*HUD_TOTAL);
    tBufferHUD.rewind();
    vBufferHUD = Utils.getFloatBuffer(V_FLOATS_PER_QUAD*HUD_TOTAL);
    vBufferHUD.rewind();
    
    for (int i = 0; i < rectsHUD.length; ++i)
      rectsHUD[i] = new Rect();

    bitmapHUD = Bitmap.createBitmap(64, TEXT_SIZE, Bitmap.Config.ARGB_4444);
    canvasHUD = new Canvas(bitmapHUD);
    bufferHUD = Utils.getShortBuffer(bitmapHUD.getWidth()*bitmapHUD.getHeight());
    bufferHUD.rewind();
    
    fogColorBuffer = Utils.getFloatBufferForArray(fogColor); 
  }
  
  @Override
  public void onSurfaceCreated(GL10 gl, EGLConfig config)
  {
    // Set the background color to black ( rgba ).
    gl.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
    // Enable Smooth Shading, default not really needed.
    gl.glShadeModel(GL10.GL_FLAT);
    // Enables depth testing.
    gl.glEnable(GL10.GL_DEPTH_TEST);
    // The type of depth testing to do.
    gl.glDepthFunc(GL10.GL_LEQUAL);
    // Depth buffer setup.
    gl.glClearDepthf(1.0f);
    // Really nice perspective calculations.
    gl.glHint(GL10.GL_PERSPECTIVE_CORRECTION_HINT, GL10.GL_NICEST);
    
    gl.glDisable(GL10.GL_DITHER);
    gl.glDisable(GL10.GL_BLEND);
    
    // Counter-clockwise winding.
    gl.glFrontFace(GL10.GL_CCW);
    // Enable face culling.
    gl.glEnable(GL10.GL_CULL_FACE);
    // What faces to remove with the face culling.
    gl.glCullFace(GL10.GL_BACK);
    
    // Enable Texture Mapping
    gl.glEnable(GL10.GL_TEXTURE_2D);
    
    lastTexHandle = -1;
    
    // fog
    if (bFog)
    {
      gl.glFogfv(GL10.GL_FOG_COLOR, fogColorBuffer);
      gl.glFogf(GL10.GL_FOG_DENSITY, 0.35f);
      gl.glHint(GL10.GL_FOG_HINT, GL10.GL_NICEST);
      gl.glFogf(GL10.GL_FOG_START, 2.0f);
      gl.glFogf(GL10.GL_FOG_END, 6.0f);
      gl.glEnable(GL10.GL_FOG);
    }
    
    // Init HUD stuff
    gl.glGenTextures(1, textureHUD, 0);
    textPaint = new Paint();
    textPaint.setTextSize(TEXT_SIZE); 
    //textPaint.setAntiAlias(true); 
    textPaint.setAntiAlias(false);
    textPaint.setARGB(0xbb, 0xff, 0xff, 0xff);
    
    GL11 gl11 = (GL11)gl;
    gl11.glGenBuffers(bufferVBOs.length, bufferVBOs, 0);    

    GameManager.gl = gl11;
    GameManager.renderer = this;
    
    TextureManager.initGL(gl);
  }

  @Override
  public void onSurfaceChanged(GL10 gl, int width, int height)
  {
    surfaceWidth = width;
    surfaceHeight = height;
    
    if (height < 1)
      height = 1;

    final float field_of_view_y = 45.0f;
    final float aspect_ratio = (float) width / height;
    final float near_z = 1.0f;
    final float far_z = 100.0f;
    
    nearPlaneHeight = near_z*Utils.tanDegree(field_of_view_y/2)*2;
    nearPlaneWidth = nearPlaneHeight*aspect_ratio;
    nearZ = near_z;

    lastTexHandle = -1;
    
    gl.glViewport(0, 0, width, height);
    gl.glMatrixMode(GL11.GL_PROJECTION);
    gl.glLoadIdentity();
    GLU.gluPerspective(gl, field_of_view_y, aspect_ratio, near_z, far_z);
    gl.glMatrixMode(GL11.GL_MODELVIEW);
    gl.glLoadIdentity();

    UISystem.onSurfaceChanged(width, height);
    
    Runtime.getRuntime().gc(); // force garbage collection
  }

  @Override
  public void onDrawFrame(GL10 gl)
  {
    //Debug.startMethodTracing("tank");
    if (GameManager.gameState == GameManager.GameStateEnums.LEVEL_INTRO)
    {
      GameManager.loadNextLevel();
    }
    
    LevelController.update();    
    final GL11 gl11 = GameManager.gl;
    
    if (bFog)
      gl.glFogx(GL10.GL_FOG_MODE, fogMode[fogFilter]);
    
    gl11.glMatrixMode(GL11.GL_MODELVIEW);
    gl11.glLoadIdentity();
    gl11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);

    gl.glEnableClientState(GL11.GL_VERTEX_ARRAY);
    gl.glEnableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
    
    if (GameManager.gameState == GameManager.GameStateEnums.GAME_ACTION ||
        GameManager.gameState == GameManager.GameStateEnums.GAME_PAUSED)
    {
      GameObject playerVehicle = GameManager.thePlayer.vehicle;
      GLU.gluLookAt(gl11, 
                    playerVehicle.positionX, playerVehicle.viewHeight, playerVehicle.positionZ, 
                    playerVehicle.positionX+10*Utils.cosDegree(playerVehicle.angle),
                    playerVehicle.viewHeight,
                    playerVehicle.positionZ+10*Utils.sinDegree(playerVehicle.angle),
                    0, 1, 0);      
      renderWorld(gl11);
    }
    drawUI(gl11);
    
    gl.glDisableClientState(GL11.GL_VERTEX_ARRAY);
    gl.glDisableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
    
    //Debug.stopMethodTracing();
  }
  
  public static void switchTo2D(GL11 gl)
  {
    gl.glDisable(GL10.GL_DEPTH_TEST);
    gl.glMatrixMode(GL10.GL_PROJECTION);
    gl.glPushMatrix();
    gl.glLoadIdentity();
    gl.glOrthof(0, surfaceWidth, 0, surfaceHeight, -1, 1);           
    gl.glMatrixMode(GL10.GL_MODELVIEW);    
    gl.glLoadIdentity();
  }
  
  public static void switchTo3D(GL11 gl)
  {    
    gl.glEnable(GL10.GL_DEPTH_TEST);
    gl.glMatrixMode(GL10.GL_PROJECTION);    
    gl.glPopMatrix();
    gl.glMatrixMode(GL10.GL_MODELVIEW);
  }

  /*
  private void drawHUDOld(GL11 gl)
  {    
    switchTo2D(gl);
    gl.glEnable(GL10.GL_BLEND);    
    gl.glBlendFunc(GL10.GL_SRC_ALPHA, GL10.GL_ONE_MINUS_SRC_ALPHA);
    
    //Drawable background = m_context.getResources().getDrawable(R.drawable.icon); 
    //background.setBounds(0, 0, 256, 256); 
    //background.draw(canvasHUD); // draw the background to our bitmap
    
    bitmapHUD.eraseColor(0);
    
    scoreText.delete(0, scoreText.capacity());
    scoreText.append('$');
    scoreText.append(LevelController.cash);
    //textPaint.setTextAlign(Paint.Align.RIGHT);
    canvasHUD.drawText(scoreText.toString(), 0, 32, textPaint);

    if (GameManager.bShowFrameRate)
    {
      frameRateText.delete(0, frameRateText.capacity());
      frameRateText.append((int)frameRate);
      //textPaint.setTextAlign(Paint.Align.LEFT);
      canvasHUD.drawText(frameRateText.toString(), 0, 16, textPaint);
    }
   
    bitmapHUD.copyPixelsToBuffer(bufferHUD);
    bufferHUD.rewind();
    TextureAtlas ta = TextureManager.textures[TextureManager.textureAll];
    final int tex_w = bitmapHUD.getWidth(), tex_h = bitmapHUD.getHeight();
    gl.glTexSubImage2D(GL10.GL_TEXTURE_2D, 0, ta.width-tex_w, ta.height-tex_h, tex_w, tex_h, GL10.GL_RGBA, GL10.GL_UNSIGNED_SHORT_4_4_4_4, bufferHUD);
      
    int vbo_v = bufferVBOs[VBOEnum.HUD_START];
    int vbo_t = bufferVBOs[VBOEnum.HUD_START+1];
    gl.glBindBuffer(GL11.GL_ARRAY_BUFFER, vbo_v);
    gl.glVertexPointer(3, GL10.GL_FLOAT, 0, 0);
    gl.glBindBuffer(GL11.GL_ARRAY_BUFFER, vbo_t);
    gl.glTexCoordPointer(2, GL10.GL_FLOAT, 0, 0);
    gl.glDrawArrays(GL10.GL_TRIANGLES, 0, HUD_TOTAL*VERT_PER_QUAD);
    
    gl.glDisable(GL10.GL_BLEND);
    switchTo3D(gl);
  }
  */
  
  private void drawUI(GL11 gl)
  {    
    switchTo2D(gl);
    gl.glEnable(GL10.GL_BLEND);    
    gl.glBlendFunc(GL10.GL_SRC_ALPHA, GL10.GL_ONE_MINUS_SRC_ALPHA);
    
    UIScreen scrn = UISystem.getTopScreen(); // for now
    if (UISystem.bScreenChanged)
      scrn.fillVBOs();
    int vbo_start = scrn.vboStart;
    
    // Draw static widgets
    bindTexture(gl, scrn.textureStatic);
    gl.glBindBuffer(GL11.GL_ARRAY_BUFFER, GameManager.renderer.bufferVBOs[vbo_start]);
    gl.glVertexPointer(3, GL10.GL_FLOAT, 0, 0);
    gl.glBindBuffer(GL11.GL_ARRAY_BUFFER, GameManager.renderer.bufferVBOs[vbo_start+1]);
    gl.glTexCoordPointer(2, GL10.GL_FLOAT, 0, 0);
    gl.glDrawArrays(GL10.GL_TRIANGLES, 0, scrn.dynamicQuadStart*VERT_PER_QUAD);

    int widgets_len = scrn.widgets.length;
    // Draw dynamic widgets
    for (int i = scrn.dynamicQuadStart; i < widgets_len; ++i)
    {
      // Texture for drawing dynamic widgets
      bindTexture(gl, scrn.texturesDynamic[i - scrn.dynamicQuadStart]);
      gl.glTexParameterf(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_MIN_FILTER, GL10.GL_NEAREST); 
      gl.glTexParameterf(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_MAG_FILTER, GL10.GL_NEAREST);
      gl.glTexParameterf(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_WRAP_S, GL10.GL_REPEAT);
      gl.glTexParameterf(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_WRAP_T, GL10.GL_REPEAT);
      
      if (scrn.drawWidget(i))
        gl.glDrawArrays(GL10.GL_TRIANGLES, i*VERT_PER_QUAD, VERT_PER_QUAD);
    }

    gl.glDisable(GL10.GL_BLEND);
    switchTo3D(gl);
  }
  
  public void renderWorld(GL11 gl)
  {
    final int objEndIdx = LevelController.objEndIdx;
    final GameObject [] allObjects = LevelController.allObjects;
    final BillBoard[] billBoards = LevelController.billBoards;
    int quad_idx = 0;
    
    if (bBlendWall)
    {
      gl.glEnable(GL10.GL_BLEND);    
      gl.glDisable(GL10.GL_CULL_FACE);
      gl.glBlendFunc(GL10.GL_SRC_ALPHA, GL10.GL_ONE);
    }
    
    // Render walls
    bindTexture(gl, TextureManager.textures[TextureManager.textureWall].textureHandle);

    gl.glBindBuffer(GL11.GL_ARRAY_BUFFER, bufferVBOs[VBOEnum.WORLD_START]);
    gl.glVertexPointer(3, GL11.GL_FLOAT, 0, 0);
    gl.glBindBuffer(GL11.GL_ARRAY_BUFFER, bufferVBOs[VBOEnum.WORLD_START+1]);
    gl.glTexCoordPointer(2, GL11.GL_FLOAT, 0, 0);
    gl.glDrawArrays(GL11.GL_TRIANGLES, 0, worldVertCount);
    //gl.glDrawArrays(GL11.GL_TRIANGLE_STRIP, 0, worldVertCount);

    if (bBlendWall)
    {
      gl.glDisable(GL10.GL_BLEND);
      gl.glEnable(GL10.GL_CULL_FACE);
    }
    
    bindTexture(gl, TextureManager.textures[TextureManager.textureAll].textureHandle);
    // Render floor
    gl.glBindBuffer(GL11.GL_ARRAY_BUFFER, bufferVBOs[VBOEnum.FLOOR_START]);
    gl.glVertexPointer(3, GL11.GL_FLOAT, 0, 0);
    gl.glBindBuffer(GL11.GL_ARRAY_BUFFER, bufferVBOs[VBOEnum.FLOOR_START+1]);
    gl.glTexCoordPointer(2, GL11.GL_FLOAT, 0, 0);
    gl.glDrawArrays(GL11.GL_TRIANGLES, 0, floorVertCount);
    
    // Render objects
    final float ang = -GameManager.thePlayer.vehicle.angle;
    final float sine = Utils.sinDegree(ang);
    final float cosine = Utils.cosDegree(ang);
    for (int i = 0; i < objEndIdx; ++i )
    {
      final GameObject obj = allObjects[i];
      obj.render(gl);

      quad_idx += obj.bulletSystem.appendAllToBuffers(vBufferQuadsInt, tBufferQuadsInt, sine, cosine);
      quad_idx += obj.fragmentSystem.appendAllToBuffers(vBufferQuadsInt, tBufferQuadsInt, sine, cosine);        
      quad_idx += obj.explosionSystem.appendAllToBuffers(vBufferQuadsInt, tBufferQuadsInt, sine, cosine);
      //quad_idx += obj.bulletSystem.appendAllToArrays(quadsVArray, quadsTArray, quad_idx, sine, cosine);
      //quad_idx += obj.fragmentSystem.appendAllToArrays(quadsVArray, quadsTArray, quad_idx, sine, cosine);        
      //quad_idx += obj.explosionSystem.appendAllToArrays(quadsVArray, quadsTArray, quad_idx, sine, cosine);
      if (obj.satelliteSystem != null)
        quad_idx += obj.satelliteSystem.appendAllToBuffers(vBufferQuadsInt, tBufferQuadsInt, sine, cosine);
    }
    
    /*
    if (!GameManager.bBoundaryTexture)
    {
      gl.glDisable(GL10.GL_TEXTURE_2D);
      gl.glEnableClientState(GL10.GL_COLOR_ARRAY);
      
      gl.glBindBuffer(GL11.GL_ARRAY_BUFFER, bufferVBOs[VBOEnum.BOUNDARY_START]);
      gl.glVertexPointer(3, GL11.GL_FLOAT, 0, 0);
      gl.glBindBuffer(GL11.GL_ARRAY_BUFFER, bufferVBOs[VBOEnum.BOUNDARY_START+1]);
      gl.glColorPointer(4, GL11.GL_FLOAT, 0, 0);
      gl.glDrawArrays(GL11.GL_TRIANGLES, 0, boundaryVertCount);
      
      gl.glDisableClientState(GL10.GL_COLOR_ARRAY);
      gl.glEnable(GL10.GL_TEXTURE_2D);
    }
    */
    
    // Render quad objects      
    final int bbSize = billBoards.length;
    for (int i = 0; i < bbSize; ++i)
    {
      //billBoards[i].appendToArrays(quadsVArray, quadsTArray, quad_idx, sine, cosine);
      billBoards[i].appendToBuffers(vBufferQuadsInt, tBufferQuadsInt, sine, cosine);
      ++quad_idx;
    }
    Utils.putFloatToIntBuffer(vBufferQuadsInt, quadsVArray, quad_idx*V_FLOATS_PER_QUAD);
    Utils.putFloatToIntBuffer(tBufferQuadsInt, quadsTArray, quad_idx*T_FLOATS_PER_QUAD);
    vBufferQuadsInt.rewind();
    tBufferQuadsInt.rewind();
    
    if (quad_idx > 0) {
      gl.glDepthMask(false);
      gl.glEnable(GL11.GL_BLEND);
      //gl.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
      gl.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
      gl.glBindBuffer(GL11.GL_ARRAY_BUFFER, bufferVBOs[VBOEnum.QUAD_START]);
      gl.glBufferData(GL11.GL_ARRAY_BUFFER, quad_idx*V_FLOATS_PER_QUAD*4, vBufferQuadsInt, GL11.GL_STATIC_DRAW);
      gl.glVertexPointer(3, GL11.GL_FLOAT, 0, 0);
      gl.glBindBuffer(GL11.GL_ARRAY_BUFFER, bufferVBOs[VBOEnum.QUAD_START+1]);
      gl.glBufferData(GL11.GL_ARRAY_BUFFER, quad_idx*T_FLOATS_PER_QUAD*4, tBufferQuadsInt, GL11.GL_STATIC_DRAW);
      gl.glTexCoordPointer(2, GL11.GL_FLOAT, 0, 0);
      gl.glDrawArrays(GL11.GL_TRIANGLES, 0, quad_idx*VERT_PER_QUAD);
      gl.glDisable(GL11.GL_BLEND);
      gl.glDepthMask(true);
    }
  }
  
  public void initWorld()
  {
    RectF[] wallBlocks = LevelController.wallBlocks;
    worldVertCount = (wallBlocks.length*FACE_PER_BLOCK+FACE_PER_BLOCK)*VERT_PER_QUAD;
    worldVerticesBuffer = Utils.getFloatBuffer(worldVertCount*3);
    worldTextureBuffer = Utils.getFloatBuffer(worldVertCount*2);

    floorVertCount = VERT_PER_QUAD;
    floorVerticesBuffer = Utils.getFloatBuffer(floorVertCount*3);
    floorTextureBuffer = Utils.getFloatBuffer(floorVertCount*2);
    
    setupBlocks(WALL_HEIGHT);
    //worldVertCount = setupBlocksStrip(3, worldVerticesBuffer, worldTextureBuffer);
    
    /*
    if (!GameManager.bBoundaryTexture)
    {
      float colors [] = {
          0.6f, 0.6f, 0.6f, 1.0f,
          0.6f, 0.6f, 0.6f, 1.0f,
          0.6f, 0.6f, 0.6f, 1.0f,
          0.6f, 0.6f, 0.6f, 1.0f,
          0.6f, 0.6f, 0.6f, 1.0f,
          0.6f, 0.6f, 0.6f, 1.0f
      };
      boundaryVerticesBuffer = Utils.getFloatBuffer(boundaryVertCount*3);
      boundaryColorBuffer = Utils.getFloatBuffer(boundaryVertCount*4);

      // boundary walls
      // North
      int mapCols = LevelController.worldWidth;
      int mapRows = LevelController.worldHeight;
      Utils.appendQuadYVertices(0, WALL_HEIGHT, 0,
                                mapCols, 0, 0,
                                boundaryVerticesBuffer);
      // West
      Utils.appendQuadYVertices(0, WALL_HEIGHT, mapRows,
                                0, 0,  0,
                                boundaryVerticesBuffer);
      // South
      Utils.appendQuadYVertices(mapCols, WALL_HEIGHT, mapRows,
                                0, 0,  mapRows,
                                boundaryVerticesBuffer);
      // West
      Utils.appendQuadYVertices(mapCols, WALL_HEIGHT, 0,
                                mapCols, 0,  mapRows,
                                boundaryVerticesBuffer);    
      
      
      // Floor
      Utils.appendQuadXVertices(0, 0, 0, mapCols, 0, mapRows, boundaryVerticesBuffer);
      
      // Ceiling
      //Utils.appendQuadXVertices(0, height, mapRows, mapCols, height, 0, vertex_buffer);

      for (int i = 0; i < 5; ++i)
        boundaryColorBuffer.put(colors);

      boundaryVerticesBuffer.position(0);
      boundaryColorBuffer.position(0);
    }
    */

    initVBOs(); // do this after static world has been loaded

  }

  public static void bindTexture(GL10 gl, int tex_handle)
  {
    if (tex_handle == lastTexHandle)
      return;
    gl.glBindTexture(GL11.GL_TEXTURE_2D, tex_handle);
    lastTexHandle = tex_handle;
  }
  
  protected void initVBOs()
  {
    GL11 gl = GameManager.gl;

    // VBOs for world
    gl.glBindBuffer(GL11.GL_ARRAY_BUFFER, bufferVBOs[VBOEnum.WORLD_START]);
    gl.glBufferData(GL11.GL_ARRAY_BUFFER, worldVertCount*3*4, worldVerticesBuffer, GL11.GL_STATIC_DRAW);
    gl.glBindBuffer(GL11.GL_ARRAY_BUFFER, bufferVBOs[VBOEnum.WORLD_START+1]);
    gl.glBufferData(GL11.GL_ARRAY_BUFFER, worldVertCount*2*4, worldTextureBuffer, GL11.GL_STATIC_DRAW);

    /*
    if (!GameManager.bBoundaryTexture)
    {
      gl.glBindBuffer(GL11.GL_ARRAY_BUFFER, bufferVBOs[VBOEnum.BOUNDARY_START]);
      gl.glBufferData(GL11.GL_ARRAY_BUFFER, boundaryVertCount*3*4, boundaryVerticesBuffer, GL11.GL_STATIC_DRAW);
      gl.glBindBuffer(GL11.GL_ARRAY_BUFFER, bufferVBOs[VBOEnum.BOUNDARY_START+1]);
      gl.glBufferData(GL11.GL_ARRAY_BUFFER, boundaryVertCount*4*4, boundaryColorBuffer, GL11.GL_STATIC_DRAW);      
    }
    */
    
    // VBOs for floor
    gl.glBindBuffer(GL11.GL_ARRAY_BUFFER, bufferVBOs[VBOEnum.FLOOR_START]);
    gl.glBufferData(GL11.GL_ARRAY_BUFFER, floorVertCount*3*4, floorVerticesBuffer, GL11.GL_STATIC_DRAW);
    gl.glBindBuffer(GL11.GL_ARRAY_BUFFER, bufferVBOs[VBOEnum.FLOOR_START+1]);
    gl.glBufferData(GL11.GL_ARRAY_BUFFER, floorVertCount*2*4, floorTextureBuffer, GL11.GL_STATIC_DRAW);

    // VBOs for tank
    GeometryData geoData = GameManager.allGeoData[GameManager.GEO_TANK];
    int vbo_start = VBOEnum.TANK_START;
    gl.glBindBuffer(GL11.GL_ARRAY_BUFFER, bufferVBOs[vbo_start]);
    gl.glBufferData(GL11.GL_ARRAY_BUFFER, geoData.positions.limit()*4, geoData.positions, GL11.GL_STATIC_DRAW);
    gl.glBindBuffer(GL11.GL_ARRAY_BUFFER, bufferVBOs[vbo_start+1]);
    gl.glBufferData(GL11.GL_ARRAY_BUFFER, geoData.texCoords.limit()*4, geoData.texCoords, GL11.GL_STATIC_DRAW);
    gl.glBindBuffer(GL11.GL_ELEMENT_ARRAY_BUFFER, bufferVBOs[vbo_start+2]);
    gl.glBufferData(GL11.GL_ELEMENT_ARRAY_BUFFER, geoData.vertexIndices.limit()*2, geoData.vertexIndices, GL11.GL_STATIC_DRAW);
    
    // VBOs for gun turret
    vbo_start = VBOEnum.GUN_TURRET_START;
    geoData = GameManager.allGeoData[GameManager.GEO_GUN_TURRET];
    gl.glBindBuffer(GL11.GL_ARRAY_BUFFER, bufferVBOs[vbo_start]);
    gl.glBufferData(GL11.GL_ARRAY_BUFFER, geoData.positions.limit()*4, geoData.positions, GL11.GL_STATIC_DRAW);
    gl.glBindBuffer(GL11.GL_ARRAY_BUFFER, bufferVBOs[vbo_start+1]);
    gl.glBufferData(GL11.GL_ARRAY_BUFFER, geoData.texCoords.limit()*4, geoData.texCoords, GL11.GL_STATIC_DRAW);
    gl.glBindBuffer(GL11.GL_ELEMENT_ARRAY_BUFFER, bufferVBOs[vbo_start+2]);
    gl.glBufferData(GL11.GL_ELEMENT_ARRAY_BUFFER, geoData.vertexIndices.limit()*2, geoData.vertexIndices, GL11.GL_STATIC_DRAW);

    // VBOs for sniper turret
    vbo_start = VBOEnum.SNIPER_TURRET_START;
    geoData = GameManager.allGeoData[GameManager.GEO_SNIPER_TURRET];
    gl.glBindBuffer(GL11.GL_ARRAY_BUFFER, bufferVBOs[vbo_start]);
    gl.glBufferData(GL11.GL_ARRAY_BUFFER, geoData.positions.limit()*4, geoData.positions, GL11.GL_STATIC_DRAW);
    gl.glBindBuffer(GL11.GL_ARRAY_BUFFER, bufferVBOs[vbo_start+1]);
    gl.glBufferData(GL11.GL_ARRAY_BUFFER, geoData.texCoords.limit()*4, geoData.texCoords, GL11.GL_STATIC_DRAW);
    gl.glBindBuffer(GL11.GL_ELEMENT_ARRAY_BUFFER, bufferVBOs[vbo_start+2]);
    gl.glBufferData(GL11.GL_ELEMENT_ARRAY_BUFFER, geoData.vertexIndices.limit()*2, geoData.vertexIndices, GL11.GL_STATIC_DRAW);
  }

  private void setupBlocks(float height)
  {
    RectF[] blocks = LevelController.wallBlocks;
    TextureAtlas ta = TextureManager.textures[TextureManager.textureAll];
    float texture_ground[] = new float[12];
    ta.getTexCoordsArray(4, 0, 8, 4, texture_ground);
    float texture_wall[] = new float[12];
    ta.getTexCoordsArray(8, 0, 12, 4, texture_wall);

    float tex_tmp[] = new float[T_FLOATS_PER_QUAD];
    
    final float WALL_TEX_SCALE = 3.0f;
    
    for (int i = 0; i < blocks.length; ++i)
    {
      RectF rect = blocks[i];
      // Front
      Utils.appendQuadYVertices(rect.left,  height, rect.bottom,
                                rect.right,      0, rect.bottom,
                                worldVerticesBuffer);
      Utils.getQuadTexArray(tex_tmp, 0, 0, 0, (rect.right-rect.left)/WALL_TEX_SCALE, height/WALL_TEX_SCALE);
      worldTextureBuffer.put(tex_tmp);
      
      // Left
      Utils.appendQuadYVertices(rect.left,  height, rect.top,
                                rect.left,       0, rect.bottom,
                                worldVerticesBuffer);
      Utils.getQuadTexArray(tex_tmp, 0, 0, 0, (rect.bottom-rect.top)/WALL_TEX_SCALE, height/WALL_TEX_SCALE);
      worldTextureBuffer.put(tex_tmp);
      
      // Right
      Utils.appendQuadYVertices(rect.right, height, rect.bottom,
                                rect.right,      0, rect.top,
                                worldVerticesBuffer);
      Utils.getQuadTexArray(tex_tmp, 0, 0, 0, (rect.bottom-rect.top)/WALL_TEX_SCALE, height/WALL_TEX_SCALE);
      worldTextureBuffer.put(tex_tmp);
      // Back
      Utils.appendQuadYVertices(rect.right, height, rect.top,
                                rect.left,       0, rect.top,
                                worldVerticesBuffer);
      Utils.getQuadTexArray(tex_tmp, 0, 0, 0, (rect.right-rect.left)/WALL_TEX_SCALE, height/WALL_TEX_SCALE);
      worldTextureBuffer.put(tex_tmp);
        
      // Top
      //Utils.appendQuadXVertices(rect.left,  height, rect.top,
      //                          rect.right, height, rect.bottom,
      //                          worldVerticesBuffer);
      //Utils.getQuadTexArray(tex_tmp, 0, 0, 0, (rect.right-rect.left)/WALL_TEX_SCALE, rect.bottom-rect.top/WALL_TEX_SCALE);
      //worldTextureBuffer.put(tex_tmp);
      // Bottom
      //Utils.appendQuadXVertices(rect.left,       0, rect.bottom,
      //                          rect.right,      0, rect.top,
      //                          worldVerticesBuffer);
      //Utils.getQuadTexArray(tex_tmp, 0, 0, 0, (rect.right-rect.left)/WALL_TEX_SCALE, rect.bottom-rect.top/WALL_TEX_SCALE);
      //worldTextureBuffer.put(tex_tmp);
      
    }
    
    // boundary walls
    int mapCols = LevelController.worldWidth;
    int mapRows = LevelController.worldHeight;
    // North
    Utils.appendQuadYVertices(0, height, 0,
                              mapCols, 0, 0,
                              worldVerticesBuffer);
    Utils.getQuadTexArray(tex_tmp, 0, 0, 0, mapCols/WALL_TEX_SCALE, height/WALL_TEX_SCALE);
    worldTextureBuffer.put(tex_tmp);
    // West
    Utils.appendQuadYVertices(0, height, mapRows,
                              0, 0,  0,
                              worldVerticesBuffer);
    Utils.getQuadTexArray(tex_tmp, 0, 0, 0, mapRows/WALL_TEX_SCALE, height/WALL_TEX_SCALE);
    worldTextureBuffer.put(tex_tmp);
    // South
    Utils.appendQuadYVertices(mapCols, height, mapRows,
                              0, 0,  mapRows,
                              worldVerticesBuffer);
    Utils.getQuadTexArray(tex_tmp, 0, 0, 0, mapCols/WALL_TEX_SCALE, height/WALL_TEX_SCALE);
    worldTextureBuffer.put(tex_tmp);
    // East
    Utils.appendQuadYVertices(mapCols, height, 0,
                              mapCols, 0,  mapRows,
                              worldVerticesBuffer);
    Utils.getQuadTexArray(tex_tmp, 0, 0, 0, mapRows/WALL_TEX_SCALE, height/WALL_TEX_SCALE);
    worldTextureBuffer.put(tex_tmp);
    /*
    for (int i = 0; i < FACE_PER_BLOCK*blocks.length+4; ++i)
      worldTextureBuffer.put(texture_wall);
    */
    
    if (worldVertCount < worldVerticesBuffer.position()/3)
    {
      Log.w("setupBlocks", "worldVertCount is too small!");
    }
    worldVerticesBuffer.rewind();
    worldTextureBuffer.rewind();
     
    // Floor
    Utils.getQuadTexArray(tex_tmp, 0, 0, 0, 1, 1);
    Utils.appendQuadXVertices(0, 0, 0, mapCols, 0, mapRows, floorVerticesBuffer);
    floorTextureBuffer.put(texture_ground);
    
    if (floorVertCount < floorVerticesBuffer.position()/3)
    {
      Log.w("setupBlocks", "floorVertCount is too small!");      
    }
    floorVerticesBuffer.rewind();
    floorTextureBuffer.rewind();
    
    // Ceiling
    //Utils.appendQuadXVertices(0, height, mapRows, mapCols, height, 0, vertex_buffer);
    //texture_buffer.put(texture_ground);
    
    
    return;
  }
  
  private static int setupBlocksStrip(float height, FloatBuffer vertex_buffer, FloatBuffer texture_buffer)
  {
    RectF[] blocks = LevelController.wallBlocks;
    TextureAtlas ta = TextureManager.textures[TextureManager.textureAll];
    float texture_ground[] = new float[8];
    ta.getTexCoordsArrayStrip1(4, 0, 8, 4, texture_ground);
    float texture_wall[] = new float[20];
    ta.getTexCoordsArrayStrip4(8, 0, 12, 4, texture_wall);

    // Ground
    float v_ground_strip[] = {
      0, 0, LevelController.worldHeight,
      LevelController.worldWidth, 0, LevelController.worldHeight,
      0, 0, 0,
      LevelController.worldWidth, 0, 0
    };
    vertex_buffer.put(v_ground_strip);
    texture_buffer.put(texture_ground);
    
    // Walls
    for (int i = 0; i < blocks.length; ++i)
    {
      RectF rect = blocks[i];
      // Four faces of a block, excluding top & bottom, triangle strips
      float vert_strip [] = {
          rect.left, height, rect.bottom,
          rect.left, 0, rect.bottom,
          rect.right, height, rect.bottom,
          rect.right, 0, rect.bottom,
          
          rect.right, height, rect.top,
          rect.right, 0, rect.top,
          rect.left, height, rect.top,
          rect.left, 0, rect.top,
          
          rect.left, height, rect.bottom,
          rect.left, 0, rect.bottom
        };
      
      // append degenerated triangle trip
      int pos = vertex_buffer.position();
      float degen_v[] = {
          vertex_buffer.get(pos-3), vertex_buffer.get(pos-2), vertex_buffer.get(pos-1),  
          vert_strip[0], vert_strip[1], vert_strip[2]
      };
      vertex_buffer.put(degen_v);
      
      pos = texture_buffer.position();
      float degen_t[] = {
          texture_buffer.get(pos-2), texture_buffer.get(pos-1),
          texture_wall[0], texture_wall[1]
      };
      texture_buffer.put(degen_t);
    
      vertex_buffer.put(vert_strip);
      texture_buffer.put(texture_wall);
    }
 
    int vert_count = vertex_buffer.position()/3;
    
    // Rewind
    vertex_buffer.position(0);
    texture_buffer.position(0);
    
    return vert_count;
  }
  
  @Override
  public void finalize()
  {
    // Clean up VBOs
    GL11 gl = GameManager.gl;
    if (bufferVBOs != null)
    {
      gl.glDeleteBuffers(bufferVBOs.length, bufferVBOs, 0);
    }

    gl.glDeleteTextures(1, textureHUD, 0);
    
    bitmapHUD.recycle();
  }
  
}
