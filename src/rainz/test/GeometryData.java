package rainz.test;

import java.nio.FloatBuffer;
import java.nio.ShortBuffer;

public class GeometryData {
  public float dimensionX = 0;
  public float dimensionY = 0;
  public float dimensionZ = 0;
  public FloatBuffer positions;
  public FloatBuffer normals;
  public FloatBuffer texCoords;
  public ShortBuffer vertexIndices;
  public ShortBuffer texIndices;
  public int vertexVBOIdx;
  public int texVBOIdx;
  public int indicesVBOIdx;
  
  public void computeDimensions()
  {
    float minX = Float.MAX_VALUE, maxX = Float.MIN_VALUE;
    float minY = Float.MAX_VALUE, maxY = Float.MIN_VALUE;
    float minZ = Float.MAX_VALUE, maxZ = Float.MIN_VALUE;
    for (int i = 0; i < positions.limit()/3; ++i)
    {
      float x = positions.get(3*i);
      float y = positions.get(3*i+1);
      float z = positions.get(3*i+2);
      if (x < minX)
        minX = x;
      if (x > maxX)
        maxX = x;
      if (y < minY)
        minY = y;
      if (y > maxY)
        maxY = y;
      if (z < minZ)
        minZ = z;
      if (z > maxZ)
        maxZ = z;
    }
    dimensionX = maxX - minX;
    dimensionY = maxY - minY;
    dimensionZ = maxZ - minZ;
    //System.out.println("Dimensions: x="+dimensionX+",y="+dimensionY+",z="+dimensionZ);
  }
}
