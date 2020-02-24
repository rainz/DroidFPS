package rainz.test;

import java.io.IOException;
import java.io.InputStream;

import javax.microedition.khronos.opengles.GL10;
import javax.microedition.khronos.opengles.GL11;

import rainz.test.R;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.RectF;
import android.opengl.GLUtils;
import android.util.Log;

public abstract class TextureManager {

  public static final int MAX_TEXTURES = 64;
  public static TextureAtlas[] textures = new TextureAtlas[MAX_TEXTURES];
  public static int textureCount = 0;
  
  public static int textureAll = -1;
  public static int textureWall = -1;
  public static int textureUI = -1;
  
  public static void init()
  {
    // Load textures
    textureAll = TextureManager.createTexture(R.drawable.textures_all, 64);
    textureWall = TextureManager.createTexture(R.drawable.texture_wall, 64);
    textureUI = TextureManager.createTexture(R.drawable.texture_ui, 64);
  }
  
  public static void initGL(GL10 gl)
  {
    GL11 gl11 = (GL11)gl;
    for (int i = 0; i < textureCount; ++i)
    {
      textures[i].loadTexture(gl11);
    }
  }
  
  protected static int createTexture(int res, int block)
  {
    if (textureCount >= MAX_TEXTURES)
    {
      System.out.println("Warning: no more textures available!");
      return -1;
    }
    TextureAtlas ta = new TextureAtlas(res, block);
    int id = textureCount;
    textures[textureCount++] = ta;
    return id;
  }
  
  public static void setTexParamAll(GL10 gl)
  {
    for (int i = 0; i < textureCount; ++i)
    {
      textures[i].setTexParam(gl);
    }
  }
  
  // To do: getTextureCoordsByEnum
  
}


class TextureAtlas {
  
  public int textureHandle = -1;
  public int width = 0;
  public int height = 0;
  public int rows = 1;
  public int cols = 1;
  
  public int blockSize = 0;
  
  public boolean flipVertical = true;
  
  private Bitmap bitmap = null;
  
  public TextureAtlas(int res, int block)
  {
    bitmap = loadBitmap(res);
    if (bitmap == null)
      return;
    
    flipVertical = true; // for now
    width = bitmap.getWidth();
    height = bitmap.getHeight();
    blockSize = block;
    cols = width / blockSize;
    rows = height / blockSize;
  }

  public void loadTexture(GL11 gl)
  {
    int textures[] = new int[1];
    // Generate the texture pointer
    gl.glGenTextures(1, textures, 0);
    textureHandle = textures[0];
    setTexParam(gl);
  }

  public void setTexParam(GL10 gl)
  {
    GLRenderer.bindTexture(gl, textureHandle);
    if (!GameManager.bLinearTexture)
    {
      gl.glTexParameterf(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER,
          GL11.GL_NEAREST);
      gl.glTexParameterf(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER,
          GL11.GL_NEAREST);
    }
    else
    {
      gl.glTexParameterf(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER,
          GL11.GL_LINEAR);
      gl.glTexParameterf(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER,
          GL11.GL_LINEAR_MIPMAP_NEAREST);
    }
    gl.glTexParameterf(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_REPEAT);
    gl.glTexParameterf(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_REPEAT);
    gl.glTexParameterf(GL11.GL_TEXTURE_2D, GL11.GL_GENERATE_MIPMAP, GL11.GL_TRUE);
    GLUtils.texImage2D(GL10.GL_TEXTURE_2D, 0, bitmap, 0);    
  }
  
  public static Bitmap loadBitmap(int res)
  {
    // Get the texture from the Android resource directory
    InputStream is = GameManager.context.getResources().openRawResource(res);
    if (is == null)
    {
      Log.e("loadBitmap", "Cannot read bitmap!");
      return null;
    }
    Bitmap bitmap = null;
    try
    {
      // BitmapFactory is an Android graphics utility for images
      BitmapFactory.Options options = new BitmapFactory.Options();
      options.inScaled = false;
      bitmap = BitmapFactory.decodeStream(is, null, options);
    }
    finally
    {
      // Always clear and close
      try
      {
        is.close();
        is = null;
      }
      catch (IOException e)
      {
      }
    }

    if (bitmap == null)
    {
      Log.e("loadBitmap", "Cannot load bitmap!");
      return null;
    }
    return bitmap;
  }
  
  public void getTexCoords(int r_from, int c_from, int r_to, int c_to, RectF coords)
  {
    final float edge_bleed_delta = 0.02f;
    coords.left = (float)c_from/cols;
    coords.right = (float)c_to/cols;
    coords.top = (float)r_from/rows;
    coords.bottom = (float)r_to/rows;
    
    float edge_row = edge_bleed_delta*(r_to - r_from)/rows;
    float edge_col = edge_bleed_delta*(c_to - c_from)/cols;
    coords.left += edge_col;
    coords.top += edge_row;
    coords.right -= edge_col;
    coords.bottom -= edge_row;

    if (!flipVertical)
    {
      coords.top = 1 - coords.top;
      coords.bottom = 1 - coords.bottom;
    }      
  }
  
  public void getTexCoordsArray(int r_from, int c_from, int r_to, int c_to, float coords[])
  {
    if (coords == null || coords.length < GLRenderer.T_FLOATS_PER_QUAD)
    {
      System.out.println("getTexCoordsArray(): invalid input");
      return;
    }
    RectF rect = Utils.getRectF();
    getTexCoords(r_from, c_from, r_to, c_to, rect);
    Utils.getQuadTexArray(coords, 0, rect.left, rect.top, rect.right, rect.bottom);
    Utils.returnRectF(rect);
  }

  public void getTexCoordsArrayStrip1(int r_from, int c_from, int r_to, int c_to, float coords[])
  {
    if (coords == null || coords.length < 2*4)
    {
      System.out.println("getTexCoordsArray(): invalid input");
      return;
    }
    RectF rect = Utils.getRectF();
    getTexCoords(r_from, c_from, r_to, c_to, rect);
    int idx = 0;    
    coords[idx++] = rect.left;
    coords[idx++] = rect.bottom;
    coords[idx++] = rect.right;
    coords[idx++] = rect.bottom;
    coords[idx++] = rect.left;
    coords[idx++] = rect.top;   
    coords[idx++] = rect.right;
    coords[idx++] = rect.top;
    Utils.returnRectF(rect);
  }

  public void getTexCoordsArrayStrip4(int r_from, int c_from, int r_to, int c_to, float coords[])
  {
    if (coords == null || coords.length < 20)
    {
      System.out.println("getTexCoordsArray(): invalid input");
      return;
    }
    RectF rect = Utils.getRectF();
    getTexCoords(r_from, c_from, r_to, c_to, rect);
    int idx = 0;    
    coords[idx++] = rect.left;
    coords[idx++] = rect.bottom;
    coords[idx++] = rect.left;
    coords[idx++] = rect.top;
    coords[idx++] = rect.right;
    coords[idx++] = rect.bottom;   
    coords[idx++] = rect.right;
    coords[idx++] = rect.top;

    coords[idx++] = rect.left;
    coords[idx++] = rect.bottom;
    coords[idx++] = rect.left;
    coords[idx++] = rect.top;

    coords[idx++] = rect.right;
    coords[idx++] = rect.bottom;   
    coords[idx++] = rect.right;
    coords[idx++] = rect.top;

    coords[idx++] = rect.left;
    coords[idx++] = rect.bottom;
    coords[idx++] = rect.left;
    coords[idx++] = rect.top;
    
    Utils.returnRectF(rect);
  }
  
  @Override
  public void finalize()
  {
    if (textureHandle < 0)
      return;
    int textures[] = new int[1];
    textures[0] = textureHandle;
    GameManager.gl.glDeleteTextures(1, textures, 0);
  }
}