package rainz.test;

import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;

import android.graphics.PointF;
import android.graphics.RectF;

public abstract class Utils {
  
  public static void init()
  {
    // Init sine table
    for (int i = 0; i < sineTable.length; ++i)
      sineTable[i] = (float) Math.sin(Math.toRadians((double)i/PRECISION_INVERSE));
    
    // Init object pools
    pools = new ObjectPool[POOL_COUNT];
    final int numObjects = 32; 
    ObjectPool rect_pool = new ObjectPool(numObjects);
    ObjectPool point_pool = new ObjectPool(numObjects);
    for (int i = 0; i < numObjects; ++i)
    {
      rect_pool.returnObject(new RectF());
      point_pool.returnObject(new PointF());
    }
    pools[POOL_RECTF] = rect_pool;
    pools[POOL_POINTF] = point_pool;
  }
  
  public static float abs(float x)
  {
    return x > 0 ? x : -x;
  }
  
  // **************** Sin and Cos **********************************
  
  private static final int PRECISION_INVERSE = 10;
  private static final int SINE_TABLE_SIZE = 360*PRECISION_INVERSE;
  private static float sineTable[] = new float[SINE_TABLE_SIZE];
  
  public static float sinDegree(float degree)
  {
    int dgr = (int)(degree*PRECISION_INVERSE+0.5f);
    if (dgr >= 0 && dgr < SINE_TABLE_SIZE)
      return sineTable[dgr]; 
    dgr %= SINE_TABLE_SIZE;
    if (dgr < 0)
      dgr += SINE_TABLE_SIZE;
    return sineTable[dgr];
  }

  public static float cosDegree(float degree)
  {
    return sinDegree(degree + 90);
  }

  public static float tanDegree(float degree)
  {
    return sinDegree(degree)/sinDegree(degree + 90);
  }
  
  public static int minPower2(int x)
  {
    int p2 = 1;
    while (p2 < x)
      p2 *= 2;
    return p2;
  }
  
  // ************** Native Buffers *********************************
  
  public static FloatBuffer getFloatBuffer(int num)
  {
    ByteBuffer buf = ByteBuffer.allocateDirect(num*4);
    buf.order(ByteOrder.nativeOrder());
    FloatBuffer float_buffer = buf.asFloatBuffer();
    return float_buffer;
  }

  public static FloatBuffer getFloatBufferForArray(float [] array)
  {
    FloatBuffer buf = getFloatBuffer(array.length);
    buf.put(array);
    buf.position(0);
    return buf;
  }

  public static IntBuffer getIntBuffer(int num)
  {
    ByteBuffer buf = ByteBuffer.allocateDirect(num*4);
    buf.order(ByteOrder.nativeOrder());
    IntBuffer int_buffer = buf.asIntBuffer();
    return int_buffer;
  }

  public static IntBuffer getIntBufferForArray(int [] array)
  {
    IntBuffer buf = getIntBuffer(array.length);
    buf.put(array);
    buf.position(0);
    return buf;
  }

  public static ShortBuffer getShortBuffer(int num)
  {
    ByteBuffer buf = ByteBuffer.allocateDirect(num*2);
    buf.order(ByteOrder.nativeOrder());
    ShortBuffer short_buffer = buf.asShortBuffer();
    return short_buffer;
  }
  
  public static ShortBuffer getShortBufferForArray(short [] array)
  {
    ShortBuffer buf = getShortBuffer(array.length);
    buf.put(array);
    buf.position(0);
    return buf;
  }
  
  // Workaround for slow FloatBuffer put problem
  private static final int ARRAY_MAX_INT = 4096;
  private static int intArrayConvert[] = new int[ARRAY_MAX_INT];
  
  public static void putFloatToIntBuffer(IntBuffer buf, float [] floatArray, int len)
  {   
    for (int i = 0; i < len; ++i)
      intArrayConvert[i] = Float.floatToIntBits(floatArray[i]);
    buf.put(intArrayConvert, 0, len);
  }
  
//************** Draw quads *********************************
  public static float[] getQuadYVertArray(float [] vArray, int offset, float x1, float y1, float z1, float x2, float y2, float z2)
  {
    vArray[offset+0] = x1;
    vArray[offset+1] = y2;
    vArray[offset+2] = z1;

    vArray[offset+3] = x2;
    vArray[offset+4] = y2;
    vArray[offset+5] = z2;

    vArray[offset+6] = x1;
    vArray[offset+7] = y1;
    vArray[offset+8] = z1;

    vArray[offset+9] = x1;
    vArray[offset+10] = y1;
    vArray[offset+11] = z1;
    
    vArray[offset+12] = x2;
    vArray[offset+13] = y2;
    vArray[offset+14] = z2;
    
    vArray[offset+15] = x2;
    vArray[offset+16] = y1;
    vArray[offset+17] = z2;
    return vArray;
  }
  
  private static float [] vertCoordArray = new float[18];
  public static void appendQuadYVertices(float x1, float y1, float z1, float x2, float y2, float z2, Buffer vBuffer)
  {
    getQuadYVertArray(vertCoordArray, 0, x1, y1, z1, x2, y2, z2);
    
    if (vBuffer instanceof FloatBuffer)
      ((FloatBuffer)vBuffer).put(vertCoordArray);
    else
      Utils.putFloatToIntBuffer((IntBuffer)vBuffer, vertCoordArray, GLRenderer.V_FLOATS_PER_QUAD);
  }
  
  public static float[] getQuadXVertArray(float [] vArray, int offset, float x1, float y1, float z1, float x2, float y2, float z2)
  {
    vArray[offset+0] = x1;
    vArray[offset+1] = y2;
    vArray[offset+2] = z2;

    vArray[offset+3] = x2;
    vArray[offset+4] = y2;
    vArray[offset+5] = z2;

    vArray[offset+6] = x1;
    vArray[offset+7] = y1;
    vArray[offset+8] = z1;

    vArray[offset+9] = x1;
    vArray[offset+10] = y1;
    vArray[offset+11] = z1;
    
    vArray[offset+12] = x2;
    vArray[offset+13] = y2;
    vArray[offset+14] = z2;
    
    vArray[offset+15] = x2;
    vArray[offset+16] = y1;
    vArray[offset+17] = z1;
    return vArray;
  }
  
  public static void appendQuadXVertices(float x1, float y1, float z1, float x2, float y2, float z2, Buffer vBuffer)
  {
    getQuadXVertArray(vertCoordArray, 0, x1, y1, z1, x2, y2, z2);
    
    if (vBuffer instanceof FloatBuffer)
      ((FloatBuffer)vBuffer).put(vertCoordArray);
    else
      putFloatToIntBuffer((IntBuffer)vBuffer, vertCoordArray, GLRenderer.V_FLOATS_PER_QUAD);
  }

  public static float[] getQuadTexArray(float [] tArray, int offset, float left, float top, float right, float bottom)
  {
    int idx = offset;
    tArray[idx++] = left;
    tArray[idx++] = bottom;
    tArray[idx++] = right;
    tArray[idx++] = bottom;  
    tArray[idx++] = left;
    tArray[idx++] = top;
    tArray[idx++] = left;
    tArray[idx++] = top;
    tArray[idx++] = right;
    tArray[idx++] = bottom;  
    tArray[idx++] = right;
    tArray[idx++] = top;
    return tArray;
  }

  private static float [] texCoordArray = new float[12];
  public static void appendQuadTexCoords(float left, float top, float right, float bottom, Buffer tBuffer)
  {
    getQuadTexArray(texCoordArray, 0, left, top, right, bottom);

    if (tBuffer instanceof FloatBuffer)
      ((FloatBuffer)tBuffer).put(texCoordArray);
    else
      putFloatToIntBuffer((IntBuffer)tBuffer, texCoordArray, GLRenderer.T_FLOATS_PER_QUAD);
  }
  
  // **************** Pool Manager ******************************
  
  private static ObjectPool[] pools;
  
  public static final int POOL_RECTF = 0;
  public static final int POOL_POINTF = 1;
  public static final int POOL_COUNT = POOL_POINTF + 1;

  public static RectF getRectF()
  {
    return (RectF) pools[POOL_RECTF].getObject();
  }
  
  public static void returnRectF(RectF r)
  {
    pools[POOL_RECTF].returnObject(r);
  }
  
  public static PointF getPointF()
  {
    return (PointF) pools[POOL_POINTF].getObject();
  }
  
  public static void returnPointF(PointF p)
  {
    pools[POOL_POINTF].returnObject(p);
  }
  
}


class ObjectPool {
  public Object objectStack[];
  public int topIdx = 0;
  
  public ObjectPool(int n)
  {
    objectStack = new Object[n];
  }
  
  public void returnObject(Object obj)
  {
    if (topIdx >= objectStack.length)
    {
      System.out.printf("Returning more object then originally allocated!");
      return;
    }
    objectStack[topIdx] = obj;
    ++topIdx;
  }
  
  public Object getObject()
  {
    if (topIdx > 0)
    {
      --topIdx;
      return objectStack[topIdx];
    }
    return null;
  }
 
}