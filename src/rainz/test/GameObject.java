package rainz.test;

import javax.microedition.khronos.opengles.GL11;

import android.graphics.PointF;
import rainz.test.LevelController.DirectionEnum;
import rainz.test.ParticleSystem.Particle;

class GameObject {
  public int type = 0;
  public int team = 0;
    
  public class StateEnums {
    public static final int DEAD = -2;
    public static final int ZOMBIE = -1;
    public static final int BIRTH = 0;
    public static final int NORMAL = 1;
  }
  
  public int state = StateEnums.NORMAL;
  public long lastUpdateTime = 0;
  
  public float positionX = 0;
  public float positionZ = 0;
  public float speed = 0; // per millisecond
  public float angle = 0; // 0 to 359, CW due to X-Z plane
  public float angularSpeed = 0; // per millisecond
  public float dimensionX = 0;
  public float dimensionY = 0;
  public float dimensionZ = 0;
  public float dimensionRadius = 0; // used in collision detection
  public float viewHeight = 0;
  
  public int sightRadius = 10;
  
  public int fireCoolDown = 1000; // milliseconds
  public long lastFireTime = 0; 
  public float gunX = 0.0f;
  public float gunY = 0.0f;
  public float gunZ = 0.0f;
  
  public int currDir = DirectionEnum.HERE;
  
  public AI ai = null;
  
  public static final int MAX_ARMOR_LEVEL = 10;
  public static final int MAX_WEAPON_LEVEL = 10;
  
  public int maxHitPoints = 1;
  public int hitPoints = 0;
  public int armorLevel = 0;
  public int weaponLevel = 0;
  public int baseWeaponDamage = 10;

  public boolean wallDetection = false;
  public boolean sameTeamDetection = false;
  
  public GeometryData geometryData;
  
  public int weaponSound = GameManager.SOUND_PLASMA;
  
  public BulletSystem bulletSystem = null;
  public FragmentSystem fragmentSystem = null;
  public ExplosionSystem explosionSystem = null;
  public SatelliteSystem satelliteSystem = null;

  //public int bodyVBO = 0;
  //public int bodyTexVBO = 0;
  //public int bodyIndicesVBO = 0;
  
  private PointF boundingBox[] = new PointF[8];
  
  public int score = 100;
  
  public GameObject()
  {
    for (int i = 0; i < boundingBox.length; ++i)
      boundingBox[i] = new PointF();
    state = StateEnums.NORMAL;
  }

  public void render(GL11 gl)
  {
    if (state == StateEnums.ZOMBIE ||
        state == StateEnums.DEAD)
    {
      return;
    }
    
    gl.glPushMatrix();

    // Now render the object
    gl.glTranslatef(positionX, 0, positionZ);
    gl.glRotatef(-angle, 0, 1, 0);
    
    int [] vbos = GameManager.renderer.bufferVBOs;
    gl.glBindBuffer(GL11.GL_ARRAY_BUFFER, vbos[geometryData.vertexVBOIdx]);
    gl.glVertexPointer(3, GL11.GL_FLOAT, 0, 0);
    gl.glBindBuffer(GL11.GL_ARRAY_BUFFER, vbos[geometryData.texVBOIdx]);
    gl.glTexCoordPointer(2, GL11.GL_FLOAT, 0, 0);
    gl.glBindBuffer(GL11.GL_ELEMENT_ARRAY_BUFFER, vbos[geometryData.indicesVBOIdx]);
    gl.glDrawElements(GL11.GL_TRIANGLES, geometryData.vertexIndices.limit(), GL11.GL_UNSIGNED_SHORT, 0);
     
    gl.glPopMatrix();
  }
  
  public float move(int dir)
  {
    long elapsed = LevelController.elapsedTime;
    float dist = speed*elapsed;
    float cos_val = (float) (dist*Utils.cosDegree(angle));
    float sin_val = (float) (dist*Utils.sinDegree(angle));
    float dx = 0, dy = 0;
    
    switch(dir)
    {
    case DirectionEnum.LEFT:
      dx = sin_val;
      dy = -cos_val;
      break;
    case DirectionEnum.RIGHT:
      dx = -sin_val;
      dy = cos_val;
      break;
    case DirectionEnum.UP:
      dx = cos_val;
      dy = sin_val;
      break;
    case DirectionEnum.DOWN:
      dx = -cos_val;
      dy = -sin_val;
      break;
    }
    
    positionX += dx;
    positionZ += dy;
    
    if (wallCollision() || objectsCollision())
    {
      positionX -= dx;
      positionZ -= dy;
      return -1;
    }
    
    return dist;
  }
  
  public float turn(int sign, float max_angle)
  {
    if (max_angle < 0)
    {
      System.out.println("Warning: max_angle < 0:"+max_angle);
      max_angle = -max_angle;
    }
    if (sign != 1 && sign != -1)
    {
      System.out.println("Warning: invalid value for sign:"+sign);
      if (sign > 0)
        sign = 1;
      else
        sign = -1;
    }
    long elapsed = LevelController.elapsedTime;
    float speed_degrees = angularSpeed*elapsed;
    if (speed_degrees > max_angle)
      speed_degrees = max_angle;
    
    float old_angle = angle;
    
    angle += speed_degrees*sign;
    angle %= 360;
    if (angle < 0)
      angle += 360;
    if (wallCollision() || objectsCollision())
    {
      angle = old_angle;
      return -1;
    }
    return speed_degrees;
  }
  
  public boolean turnTo(int target_angle)
  {
    target_angle %= 360;
    int sign;
    float diff = target_angle - angle;
    if (diff < 0)
      diff = -diff;
    
    if (target_angle > angle)
      sign = (diff > 180 ? -1 : 1);
    else if (target_angle < angle)
      sign = (diff > 180 ? 1 : -1);
    else
      return true;
    
    float turn_degree = (diff <= 180 ? diff : 360-diff); 
    turn(sign, turn_degree);
    return false;
  }
  
  public boolean fire(GameObject target)
  {
    long curr_time = LevelController.levelTimer; 
    if (curr_time - lastFireTime < fireCoolDown)
      return false;
    
    if (target == null)
    {
      LevelController.playSound(weaponSound);
      Particle bullet = bulletSystem.generate(null);
      if (bullet == null)
        return false;
    }
    else
    {
      // Direct hit
    }
    lastFireTime = curr_time;
    return true;
  }
 
  public boolean inSight(float x, float y)
  {
    float dx = positionX - x;
    float dy = positionZ - y;
    if (dx*dx + dy*dy > sightRadius*sightRadius)
      return false;
    
    // To do: fix me. 
    int from_x = (int)(positionX+0.5f), from_y = (int)(positionZ+0.5f);
    int to_x = (int)(x+0.5f), to_y = (int)(y+0.5f);
    int x_inc = dx > 0 ? -1 : (dx < 0 ? 1 : 0);
    int y_inc = dy > 0 ? -1 : (dy < 0 ? 1 : 0);
    while (from_x != to_x || from_y != to_y)
    {
      if (LevelController.pointBlocked(from_y, from_x))
        return false;
      int delta_x = from_x - to_x;
      if (delta_x < 0)
        delta_x = -delta_x;
      int delta_y = from_y - to_y;
      if (delta_y < 0)
        delta_y = - delta_y;
      if (delta_x >= delta_y)
        from_x += x_inc;
      else
        from_y += y_inc;
    }
    
    return true;
  }
  
  public int receiveDamage(int damage)
  {
    if (satelliteSystem != null)
    {
      if (damage <= satelliteSystem.hitPoints)
      {
        satelliteSystem.hitPoints -= damage;
        return damage;
      }
      else
      {
        damage -= satelliteSystem.hitPoints;
        satelliteSystem.hitPoints = 0;
      }
    }
    float factor = 1 - (float)armorLevel/MAX_ARMOR_LEVEL;
    damage = Math.round(factor*damage);
    if (damage < 1)
      damage = 1;
    hitPoints -= damage;
    if (hitPoints <= 0)
    {
      state = StateEnums.ZOMBIE;
    }
    return damage;
  }
  
  public int weaponDamage()
  {
    return (baseWeaponDamage + 2*weaponLevel);
  }
  
  private void computeBoundingBox()
  {
    // To do: compute sine & cosine once instead of multiple times.
    PointF p;
    float dx_half = dimensionX/2;
    float dz_half = dimensionZ/2;
    float sine = Utils.sinDegree(angle);
    float cosine = Utils.cosDegree(angle);
    // Four corner points
    p = boundingBox[0];
    p.x = positionX - dx_half;
    p.y = positionZ - dz_half;
    rotatePoint(p, sine, cosine);
    p = boundingBox[1];
    p.x = positionX + dx_half;
    p.y = positionZ - dz_half;
    rotatePoint(p, sine, cosine);
    p = boundingBox[2];
    p.x = positionX + dx_half;
    p.y = positionZ + dz_half;
    rotatePoint(p, sine, cosine);
    p = boundingBox[3];
    p.x = positionX - dx_half;
    p.y = positionZ + dz_half;
    rotatePoint(p, sine, cosine);
    // Four mid-points
    p = boundingBox[4];
    p.x = positionX;
    p.y = positionZ - dz_half;
    rotatePoint(p, sine, cosine);
    p = boundingBox[5];
    p.x = positionX;
    p.y = positionZ + dz_half;
    rotatePoint(p, sine, cosine);
    p = boundingBox[6];
    p.x = positionX + dx_half;
    p.y = positionZ;
    rotatePoint(p, sine, cosine);
    p = boundingBox[7];
    p.x = positionX - dx_half;
    p.y = positionZ;
    rotatePoint(p, sine, cosine);
  }
  
  /*
  private void rotatePoint(PointF p, float degree)
  {
    float px = p.x - positionX;
    float py = p.y - positionZ;
    float sine = Utils.sinDegree(degree);
    float cosine = Utils.cosDegree(degree);
    p.x = px*cosine + py*sine + positionX;
    p.y = px*sine - py*cosine + positionZ;
  }
  */
  private void rotatePoint(PointF p, float sine, float cosine)
  {
    float px = p.x - positionX;
    float py = p.y - positionZ;
    p.x = px*cosine + py*sine + positionX;
    p.y = px*sine - py*cosine + positionZ;    
  }
  
  public boolean pointInRotatedRect(float pos_x, float pos_z)
  {
    // Rotate the point and the square to an XZ-aligned position then compare

    PointF p = Utils.getPointF();
    p.x = pos_x;
    p.y = pos_z;
    float sine = Utils.sinDegree(-angle);
    float cosine = Utils.cosDegree(-angle);
    rotatePoint(p, sine, cosine);
    float dx_half = dimensionX/2;
    float dz_half = dimensionZ/2;
    boolean result = !(p.x < positionX-dx_half ||
                       p.x > positionX+dx_half ||
                       p.y < positionZ-dz_half ||
                       p.y > positionZ+dz_half);
    
    Utils.returnPointF(p);
    return result;
  }
  
  protected boolean wallCollision()
  {
    if (!wallDetection)
      return false;

    computeBoundingBox();
    int len = boundingBox.length;
    for (int i = 0; i < len; ++i)
    {
      PointF p = boundingBox[i];
      int col = (int)(p.x);
      int row = (int)(p.y);
      if (LevelController.pointBlocked(row, col))
        return true;
    }
    
    return false;
  }
  
  protected boolean objectsCollision()
  {    
    GameObject objs[] = LevelController.allObjects;
    int len = LevelController.objEndIdx;
    for (int i = 0; i < len; ++i)
    {
      GameObject other = objs[i];
      if (other == this || other.state != StateEnums.NORMAL && other.state != StateEnums.BIRTH)
        continue;
      if (!sameTeamDetection && this.team == other.team)
        continue;
      float min_dist = dimensionRadius + other.dimensionRadius;
      float dx = positionX - other.positionX;
      float dz = positionZ - other.positionZ;
      if (dx*dx + dz*dz < min_dist*min_dist)
        return true;
    }
    
    return false;
  }
  
  public void update(long elapsed)
  {
    if (ai != null && state == StateEnums.NORMAL)
      ai.nextAction();
    
    // Update bullet system
    bulletSystem.updateParticles();
    fragmentSystem.updateParticles();
    explosionSystem.updateParticles();
    if (satelliteSystem != null)
      satelliteSystem.updateParticles();
  }

}

class Tank extends GameObject {
  public Tank(int level)
  {
    super();

    maxHitPoints = 100 + level*50;
    hitPoints = maxHitPoints;
    
    geometryData = GameManager.allGeoData[GameManager.GEO_TANK];

    // To do: update these values if model changes

    gunX = 2.0f;
    gunY = 1.0f;
    gunZ = 2.0f;
    dimensionX = 3.6f;
    dimensionY = 0.8401922f;
    dimensionZ = 2.4f;
    dimensionRadius = dimensionX/2;
    
    viewHeight = 2*dimensionY;
    speed = dimensionX/800;
    angularSpeed = 0.09f;
    
    sightRadius = (int) (6*dimensionX);
    baseWeaponDamage = 15;
    weaponSound = GameManager.SOUND_PLASMA;
    fireCoolDown = 1000;
    
    bulletSystem = new BulletSystem(30, this);
    bulletSystem.defaultPointSize = 0.5f;
    
    fragmentSystem = new FragmentSystem(10, this);
    
    explosionSystem = new ExplosionSystem(8, this);
    
    if (level > 5)
    {
      satelliteSystem = new SatelliteSystem(this, 1);
    }
  }
}
class GunTurret extends GameObject {
  public GunTurret(int level)
  {
    super();

    maxHitPoints = 50 + level*25;
    hitPoints = maxHitPoints;
    
    geometryData = GameManager.allGeoData[GameManager.GEO_GUN_TURRET];

    gunX = 2.0f;
    gunY = 1.0f;
    gunZ = 2.0f;
 
    dimensionX = geometryData.dimensionX;
    dimensionY = geometryData.dimensionY;
    dimensionZ = geometryData.dimensionZ;
    
    dimensionRadius = dimensionX/2;
    
    viewHeight = 2*dimensionY;
    speed = 0;
    angularSpeed = 0.2f;
    
    sightRadius = (int) (6*dimensionX);
    baseWeaponDamage = 5;
    weaponSound = GameManager.SOUND_GUN;
    fireCoolDown = 200;
    
    bulletSystem = new BulletSystem(30, this);
    bulletSystem.defaultPointSize = 0.1f;
    bulletSystem.bulletSpeed = 0.02f;
    
    fragmentSystem = new FragmentSystem(10, this);
    
    explosionSystem = new ExplosionSystem(8, this);
  }
}

class SniperTurret extends GameObject {
  public SniperTurret(int level)
  {
    super();

    maxHitPoints = 200 + level*100;
    hitPoints = maxHitPoints;
    
    geometryData = GameManager.allGeoData[GameManager.GEO_SNIPER_TURRET];

    gunX = 2.0f;
    gunY = 1.0f;
    gunZ = 2.0f;
 
    dimensionX = geometryData.dimensionX;
    dimensionY = geometryData.dimensionY;
    dimensionZ = geometryData.dimensionZ;
    
    dimensionRadius = dimensionX/2;
    
    viewHeight = 2*dimensionY;
    speed = 0;
    angularSpeed = 0.2f;
    
    sightRadius = (int) (6*dimensionX);
    baseWeaponDamage = 50;
    weaponSound = GameManager.SOUND_GUN;
    fireCoolDown = 2000;
    
    bulletSystem = new BulletSystem(30, this);
    bulletSystem.defaultPointSize = 0.1f;
    bulletSystem.bulletSpeed = 0.02f;
    
    fragmentSystem = new FragmentSystem(10, this);
    
    explosionSystem = new ExplosionSystem(8, this);
  }
}