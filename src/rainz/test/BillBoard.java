package rainz.test;

import java.nio.Buffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import android.util.Log;

public class BillBoard {
  public float x;
  public float y;
  public float z;
  
  public float width;
  public float height;
  
  public float[] texCoords;
  
  public BillBoard()
  {
    
  }
  
  public BillBoard(float xx, float yy, float zz, float w, float h)
  {
    x = xx;
    if (yy >= 0)
      y = yy;
    else
      y = h/2;
    z = zz;
    width = w;
    height = h;
    texCoords = new float[12];
    TextureManager.textures[TextureManager.textureAll].getTexCoordsArray(4, 4, 7, 7, texCoords); // for now
  }

  public float[] getTexCoords()
  {
    return texCoords;
  }
  
  public void appendToBuffers(Buffer vBuffer, Buffer tBuffer, float sine, float cosine)
  {
    float texCoords[] = getTexCoords();
    if (texCoords == null)
      return;

    float offset = width/2;
    float offset_x = offset*sine;
    float offset_y = height/2;
    float offset_z = offset*cosine;
    Utils.appendQuadYVertices(x-offset_x, y+offset_y, z-offset_z, 
                              x+offset_x, y-offset_y, z+offset_z, vBuffer);
    if (tBuffer instanceof FloatBuffer)
      ((FloatBuffer)tBuffer).put(texCoords);
    else
      Utils.putFloatToIntBuffer((IntBuffer)tBuffer, texCoords, texCoords.length);
  }


  public void appendToArrays(float [] vArray, float [] tArray, int idx_quad, float sine, float cosine)
  {
    float texCoords[] = getTexCoords();
    if (texCoords == null)
      return;

    float offset = width/2;
    float offset_x = offset*sine;
    float offset_y = height/2;
    float offset_z = offset*cosine;
    Utils.getQuadYVertArray(vArray, idx_quad*GLRenderer.V_FLOATS_PER_QUAD, x-offset_x, y+offset_y, z-offset_z, 
                            x+offset_x, y-offset_y, z+offset_z);
    System.arraycopy(texCoords, 0, tArray, idx_quad*GLRenderer.T_FLOATS_PER_QUAD, texCoords.length);
  }
}
