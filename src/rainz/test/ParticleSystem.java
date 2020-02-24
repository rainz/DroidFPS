package rainz.test;

import java.nio.Buffer;
import java.nio.FloatBuffer;

import rainz.test.GameObject.StateEnums;

public abstract class ParticleSystem {

  public GameObject host = null;
  public Particle particles[];
  public int endIdx = 0;
  public int defaultAge = 2000; // in milliseconds
  protected float defaultPointSize = 0.1f;
  
  public float texCoords[];
    
  protected static float allTexCoords[][];
  protected static final int TEXTURE_BULLET = 0;
  protected static final int TEXTURE_FLAME = 1;
  protected static final int TEXTURE_SATELLITE = 2;
  protected static final int TEXTURE_EXPLODE_START = 3;
  protected static final int TEXUTRE_EXPLODE_COUNT = 16;

  public ParticleSystem(int count, GameObject h)
  {
    host = h;
    particles = new Particle[count];
    for (int i = 0; i < particles.length; ++i)
    {
      particles[i] = new Particle();
    }
  }
  
  public static void init()
  {
    final int num_textures = TEXTURE_EXPLODE_START + TEXUTRE_EXPLODE_COUNT;
    allTexCoords = new float[num_textures][2*GLRenderer.VERT_PER_QUAD]; // for now
    TextureManager.textures[TextureManager.textureAll].getTexCoordsArray(12, 1, 13, 2, allTexCoords[TEXTURE_BULLET]);
    TextureManager.textures[TextureManager.textureAll].getTexCoordsArray(0, 4, 4, 8, allTexCoords[TEXTURE_FLAME]);
    TextureManager.textures[TextureManager.textureAll].getTexCoordsArray(12, 2, 13, 3, allTexCoords[TEXTURE_SATELLITE]);
    for (int i = 0; i < 16; ++i)
    {
      int row = i / 4 * 2, col = 2 * (i % 4) + 8;
      TextureManager.textures[TextureManager.textureAll].getTexCoordsArray(row, col, row+2, col+2, allTexCoords[i+TEXTURE_EXPLODE_START]);
    }
  }

  public abstract void initParticle(Particle p);

  public abstract void updateParticles();
  
  public Particle generate(float particleTexCoords[])
  {
    if (endIdx >= particles.length)
      return null;
    Particle p = particles[endIdx];
    p.texCoords = (particleTexCoords == null ? texCoords : particleTexCoords);
    p.width = defaultPointSize;
    p.height = defaultPointSize;
    ++ endIdx;
    initParticle(p);
    p.age = defaultAge;
    p.isExploding = false;
    return p;
  }
  
  public void generateRest()
  {
    Particle p;
    do {
      p = generate(null);
    }
    while (p != null);
  }
  
  public void removeParticle(int i)
  {
    Particle p = particles[i];
    if (i >= endIdx)
    {
      System.out.println("Warning: removal index out of range:"+i+", end="+endIdx);
      return;
    }
    if (i < endIdx - 1)
    {
      // swap with the last particle alive
      particles[i] = particles[endIdx - 1];
      particles[endIdx - 1] = p;
    }
    --endIdx;    
  }

  public int appendAllToBuffers(Buffer vBuffer, Buffer tBuffer, float sine, float cosine)
  {
    for (int i = 0; i < endIdx; ++i)
    {
      Particle p = particles[i];
      p.appendToBuffers(vBuffer, tBuffer, sine, cosine);
    }
    return endIdx;
  }
  
  public int appendAllToArrays(float [] vArray, float [] tArray, int idx_quad, float sine, float cosine)
  {
    for (int i = 0; i < endIdx; ++i)
    {
      Particle p = particles[i];
      p.appendToArrays(vArray, tArray, idx_quad+i, sine, cosine);
    }
    return endIdx;
  }
  
  public class Particle extends BillBoard {
    public int age = 0;
    
    // Either ...
    public float angleXZ = 0;
    public float acceleration = 0; // per millisecond
    public float currentSpeed = 0; // per millisecond
    
    // ... Or ...
    public float speedX = 0;
    public float speedY = 0;
    public float speedZ = 0;
    public float accX = 0;
    public float accY = 0;
    public float accZ = 0;
    public float rotateX = 0;
    public float rotateY = 0;
    public float rotateZ = 0;
    
    public boolean isExploding = false;
    
    public Particle()
    {
      super();
    }
    
    public void stop()
    {
      currentSpeed = 0;
      acceleration = 0;
      
      speedX = 0;
      speedY = 0;
      speedZ = 0;
      accX = 0;
      accY = 0;
      accZ = 0;      
    }
  }
}

class BulletSystem extends ParticleSystem {
  public static final int EXPLODE_TIME = 60;
  public float bulletSpeed = 0.01f;
  
  public BulletSystem(int count, GameObject h)
  {
    super(count, h);
    defaultAge = 1000;
    defaultPointSize = 0.1f;
    texCoords = allTexCoords[TEXTURE_BULLET];    
  }
  
  @Override
  public void initParticle(Particle p)
  {
    p.x = host.positionX + host.gunX*Utils.cosDegree(host.angle);
    p.y = host.gunY;
    p.z = host.positionZ + host.gunZ*Utils.sinDegree(host.angle);
    p.currentSpeed = bulletSpeed + host.speed;
    p.angleXZ = host.angle;
  }

  @Override
  public void updateParticles()
  {
    final long elapsed = LevelController.elapsedTime; 
    for (int i = 0; i < endIdx; ++i)
    {
      Particle p = particles[i];
      p.age -= (int)elapsed;
      if (p.age < 0)
      {
        removeParticle(i);
        continue;
      }

      if (p.isExploding)
        continue;
      
      // Particle still alive, update its position
      p.currentSpeed += p.acceleration*elapsed;
      float dist = p.currentSpeed*elapsed;
      float old_x = p.x, old_z = p.z;
      p.x += dist*Utils.cosDegree(p.angleXZ);
      p.z += dist*Utils.sinDegree(p.angleXZ);
      final int col = (int) Math.floor(p.x);
      final int row = (int) Math.floor(p.z);
      if (LevelController.pointBlocked(row, col))
      {
        p.x = old_x;
        p.z = old_z;
        explodeParticle(p);
        //System.out.println("bullet hits wall");
      }
    }
  }
  
  public static void explodeParticle(Particle p)
  {
    if (p.age > EXPLODE_TIME)
      p.age = EXPLODE_TIME;
    p.stop();
    p.isExploding = true;
    p.texCoords = allTexCoords[TEXTURE_FLAME];
    p.width *=3;
    p.height *=3;
  }
}

class FragmentSystem extends ParticleSystem {
  
  public FragmentSystem(int count, GameObject h)
  {
    super(count, h);
    defaultPointSize = h.dimensionX/5;
    defaultAge = 2500;
    texCoords = allTexCoords[TEXTURE_FLAME];
  }
  
  @Override
  public void initParticle(Particle p)
  {
    p.age = defaultAge;
    
    int randNum = GameManager.random.nextInt();
    // I need 6 random numbers here.
    // An int has 32 bits, so 5 bits each for a total of 30 bits.
    // Use the last two bits as directions for speedX and speedZ.

    int rand = randNum & 31; // to get bottom 5 bits
    randNum >>= 5;
    p.x = host.positionX + host.dimensionX*(rand/31.0f-0.5f); 
    
    rand = randNum & 31; // to get bottom 5 bits
    randNum >>= 5;
    p.y = host.dimensionY*(rand/31.0f); 
    
    rand = randNum & 31;
    randNum >>= 5; 
    p.z = host.positionZ + host.dimensionZ*(rand/31.0f-0.5f);

    rand = randNum & 31;
    randNum >>= 5; 
    p.speedX = 0.002f*(1+rand/31.0f);
    
    rand = randNum & 31;
    randNum >>= 5; 
    p.speedY = 0.02f*(1+rand/31.0f);

    rand = randNum & 31;
    randNum >>= 5; 
    p.speedZ = 0.002f*(1+rand/31.0f);
    
    rand = randNum & 1; // to get bottom bit
    randNum >>= 1;
    if (rand == 0)
      p.speedX = -p.speedX;

    rand = randNum & 1; // to get bottom bit
    //nextRand >>= 1;
    if (rand == 0)
      p.speedZ = -p.speedZ;
    
    p.accX = 0;
    p.accY = -0.0001f;
    p.accZ = 0;
  }

  @Override
  public void updateParticles()
  {
    if (host.state == StateEnums.ZOMBIE && endIdx <= 0)
    {
      host.state = StateEnums.DEAD;
      return;
    }
    
    final long elapsed = LevelController.elapsedTime; 
    for (int i = 0; i < endIdx; ++i)
    {
      Particle p = particles[i];
      p.age -= (int)elapsed;
      if (p.age < 0)
      {
        removeParticle(i);
        --i;
        continue;
      }
    
      // Particle still alive, update its position
      p.speedX += p.accX*elapsed;
      p.speedY += p.accY*elapsed;
      p.speedZ += p.accZ*elapsed;
      p.x += p.speedX*elapsed;
      p.y += p.speedY*elapsed;
      if (p.y < 0)
      {
        p.y = 0;
        p.stop();
      }
      p.z += p.speedZ*elapsed;
    }
  }

}

class ExplosionSystem extends ParticleSystem {
  
  public final int explosionTotalTime = 640;
  
  private int explosionTime;
  
  public ExplosionSystem(int count, GameObject h)
  {
    super(count, h);
    defaultPointSize = h.dimensionY*5;
    defaultAge = explosionTotalTime;
    texCoords = null;
    explosionTime = 0;
  }
  
  @Override
  public void initParticle(Particle p)
  {
    p.x = host.positionX;
    p.y = defaultPointSize/3;
    p.z = host.positionZ;
    p.stop();
  }

  @Override
  public void updateParticles()
  {
    final long elapsed = LevelController.elapsedTime;
    if (endIdx > 0)
      explosionTime += elapsed; // Explosion has started

    final int len = particles.length;
    for (int i = 0; i < endIdx; ++i)
    {
      Particle p = particles[i];
      p.age -= (int)elapsed;
      if (p.age < 0)
      {
        removeParticle(i);
        continue;
      }
      if (explosionTime >= (float)i*explosionTotalTime/len &&
          explosionTime < (i+3.0f)*explosionTotalTime/len)
        p.texCoords = allTexCoords[i+TEXTURE_EXPLODE_START];
      else
        p.texCoords = null;
    }

  }
    
  @Override
  public void generateRest()
  {
    final int len = particles.length;
    for (int i = 0; i < len; ++i)
      generate(null);
  }
}

class SatelliteSystem extends ParticleSystem {
  public static final int SATELLITES_PER_RING = 4;
  public static final int POINTS_PER_RING = 200;
  public static final int ROTATE_SPEED = 180;
  public float satelliteAngles = 0;
  public int currentCount = 0;
  public int numRings = 0;
  public float inclination[];
  public float azimuth[];
  public int hitPoints = 0;
  
  public SatelliteSystem(GameObject h, int num_rings)
  {
    super(num_rings*SATELLITES_PER_RING, h);
    numRings = num_rings;
    hitPoints = POINTS_PER_RING*num_rings;
    inclination = new float[num_rings*SATELLITES_PER_RING];
    azimuth = new float[num_rings*SATELLITES_PER_RING];
    for (int i = 0; i < particles.length; i += SATELLITES_PER_RING)
    {
      for (int j = 0; j < SATELLITES_PER_RING; ++j)
      {
        inclination[i+j] = (i/num_rings)*180.0f/num_rings;
        azimuth[i+j] = j*360.0f/SATELLITES_PER_RING;
      }
    }
    texCoords = allTexCoords[TEXTURE_SATELLITE];
    defaultPointSize = 1.0f;
    generateRest();
  }

  @Override
  public void initParticle(Particle p)
  {
  }

  @Override
  public void updateParticles()
  {
    if (hitPoints <= 0)
    {
      endIdx = 0;
      return;
    }
    final long elapsed = LevelController.elapsedTime;
    float radius = host.dimensionRadius*1.5f;
    for (int i = 0; i < endIdx; ++i)
    {
      azimuth[i] += elapsed*ROTATE_SPEED/1000.0f;
      azimuth[i] %= 360;
      Particle p = particles[i];
      float ci = Utils.cosDegree(inclination[i]);
      float si = Utils.sinDegree(inclination[i]);
      float ca = Utils.cosDegree(azimuth[i]);
      float sa = Utils.sinDegree(azimuth[i]);
      p.x = host.positionX + radius*ci*sa;
      p.y = host.dimensionY/2 + radius*si*ca;
      p.z = host.positionZ + radius*ci*ca;
    }
    
  }
  
  @Override
  public void generateRest()
  {
    final int len = particles.length;
    for (int i = 0; i < len; ++i)
    {
      generate(null);
    }
  }
}