package rainz.test;

import java.util.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;


import rainz.test.GameManager.GameStateEnums;
import rainz.test.GameObject.StateEnums;
import rainz.test.ParticleSystem.Particle;

import android.graphics.PointF;
import android.graphics.RectF;
import android.media.AudioManager;
import android.util.Log;

public abstract class LevelController {
  
  public class MapEnum {
    public static final int GROUND = 0;
    public static final int WALL = 1;
  }

  public class DirectionEnum {
    public static final int HERE = 0;
    public static final int DOWN = 1;
    public static final int RIGHT = 2;
    public static final int LEFT = 3;
    public static final int UP = 4;
    public static final int CANT_REACH = Integer.MAX_VALUE;
  }

  public static int worldHeight = -1;
  public static int worldWidth = -1;
  public static int[][] worldData;
  
  public static RectF[] wallBlocks;
  public static BillBoard[] billBoards; 
  
  private static long lastSysTime = 0;
  public static long levelTimer = 0;
  public static long elapsedTime = 0;
  private static int frameCount = 0;
  private static long lastFRTime = 0;

  public static GameObject[] allObjects;
  public static int objEndIdx = 0;
  
  public static int cash = 0;
  
  public static boolean loadLevel(InputStream fileStream)
  {
    if (!loadMap(fileStream))
      return false;
    wallBlocks = computeWallBlocks();
    
    GameManager.renderer.initWorld(); // call after map loaded and blocks computed
    
    // Set up game objects
    int num_objects = 20; // for now
    allObjects = new GameObject[num_objects];
    /*
    for (int i = 0; i < num_objects; ++i)
      allObjects[i] = new GameObject();
    */
    objEndIdx = 0;

    GameObject gameObj = new Tank(0);
    GameManager.thePlayer.vehicle = gameObj;
    allObjects[objEndIdx++] = gameObj;
    gameObj.team = 1;
    gameObj.maxHitPoints = 500;
    gameObj.hitPoints = gameObj.maxHitPoints;
    gameObj.wallDetection = true;
    gameObj.positionX = 5;
    gameObj.positionZ = 10;
    gameObj.speed = 0.006f;
    gameObj.angularSpeed = 0.18f;
    gameObj.fireCoolDown = 100;
    gameObj.bulletSystem.defaultAge = 3000;
    gameObj.ai = AI.newUserInputInstance(gameObj);
    
    gameObj = new Tank(6); 
    allObjects[objEndIdx++] = gameObj;
    gameObj.positionX = 12;
    gameObj.positionZ = 5;
    PointF p1 = new PointF(10, 5);
    PointF p2 = new PointF(30, 5);
    AI ai = AI.newPatrolInstance(gameObj, p1, p2);
    gameObj.ai = ai;

    gameObj = new Tank(0); 
    allObjects[objEndIdx++] = gameObj;
    gameObj.positionX = 18;
    gameObj.positionZ = 15;
    p1 = new PointF(16, 15);
    p2 = new PointF(25, 15);
    ai = AI.newPatrolInstance(gameObj, p1, p2);
    gameObj.ai = ai;

    gameObj = new Tank(0); 
    allObjects[objEndIdx++] = gameObj;
    gameObj.positionX = 30;
    gameObj.positionZ = 30;
    p1 = new PointF(30, 25);
    p2 = new PointF(30, 35);
    ai = AI.newPatrolInstance(gameObj, p1, p2);
    gameObj.ai = ai;

    gameObj = new Tank(0); 
    allObjects[objEndIdx++] = gameObj;
    gameObj.positionX = 8;
    gameObj.positionZ = 46;
    p1 = new PointF(6, 46);
    p2 = new PointF(16, 46);
    ai = AI.newPatrolInstance(gameObj, p1, p2);
    gameObj.ai = ai;

    gameObj = new GunTurret(0); 
    allObjects[objEndIdx++] = gameObj;
    gameObj.positionX = 18;
    gameObj.positionZ = 36;
    ai = AI.newGuardInstance(gameObj);
    gameObj.ai = ai;

    gameObj = new SniperTurret(0); 
    allObjects[objEndIdx++] = gameObj;
    gameObj.positionX = 28;
    gameObj.positionZ = 36;
    ai = AI.newGuardInstance(gameObj);
    gameObj.ai = ai;
    
    levelTimer = 0;
    lastSysTime = 0;
    elapsedTime = 0;
    frameCount = 0;
    lastFRTime = 0;

    cash = 0;
    updateScoreWidget();
    
    return true;
  }
  
  public static boolean loadMap(InputStream fileStream)
  {
    BufferedReader reader;
    try
    {
      //reader = new BufferedReader(new FileReader(file_name));
      reader = new BufferedReader(new InputStreamReader(fileStream));
      String line = null;
      ArrayList< ArrayList<Integer> > map_array = new ArrayList< ArrayList<Integer> >();
      while ((line = reader.readLine()) != null)
      {
        String tokens[] = line.split(",");
        ArrayList<Integer> line_vals = new ArrayList<Integer>();
        for (int i = 0; i < tokens.length; ++i)
        {
          if (tokens[i].trim().equals(""))
            continue;
          line_vals.add(Integer.parseInt(tokens[i]));
        }
        if (worldWidth < 0)
          worldWidth = line_vals.size();
        else if (worldWidth != line_vals.size()){
          System.out.println("Inconsistent column widths!");
          return false;
        }
        map_array.add(line_vals);
      }
      
      worldHeight = map_array.size();
      worldData = new int[worldHeight][worldWidth];

      ArrayList<BillBoard> boards = new ArrayList<BillBoard>();
      for (int i = 0; i < worldHeight; ++i)
      {
        ArrayList<Integer> row_vals = map_array.get(i);
        for (int j = 0; j < worldWidth; ++j)
        {
          int val = row_vals.get(j);
          worldData[i][j] = val;
          if (val == 2)
          {
            boards.add(new BillBoard(j, -1, i, 1, 3)); // for now
          }
        }
        System.out.println();
      }
      reader.close();

      billBoards = new BillBoard[boards.size()];
      for (int i = 0; i < billBoards.length; ++i)
        billBoards[i] = boards.get(i);
    }
    catch (IOException e)
    {
      e.printStackTrace();
      return false;
    }
    return true;
  }
  

  public static boolean pointBlocked(int row, int col) 
  {
    // x & y are in map coordinates
    return (row < 0 || row >= worldHeight || 
            col < 0 || col >= worldWidth ||
            worldData[row][col] != MapEnum.GROUND);
  }
  
  public static RectF[] computeWallBlocks()
  {
    if (worldHeight < 0 || worldWidth < 0)
      return null;
    
    ArrayList<RectF> blocks = new ArrayList<RectF>();
    boolean visited[][] = new boolean[worldHeight][worldWidth];
    for (int i = 0; i < worldHeight; ++i)
    {
      for (int j = 0; j < worldWidth; ++j)
      {
        if (worldData[i][j] != MapEnum.WALL || visited[i][j])
          continue;
        //First expand to the right
        int right = j;
        do {
          ++right;
        } while (right < worldWidth && 
                 worldData[i][right] == MapEnum.WALL &&
                 !visited[i][right]);
        // Then expand downwards
        int bottom = i;
        boolean bExpandable = true;
        do {
          ++bottom;
          if (bottom >= worldHeight)
            break;
          for (int col = j; col < right; ++col)
          {
            bExpandable = (worldData[bottom][col] == MapEnum.WALL &&
                           !visited[bottom][col]);
            if (!bExpandable)
              break;
          }
        } while (bExpandable);
        
        for (int ii = i; ii < bottom; ++ii)
          for (int jj = j; jj < right; ++jj )
            visited[ii][jj] = true;
        blocks.add(new RectF(j, i, right, bottom));
      }
    }
    RectF[] blocks_array = new RectF[blocks.size()];
    for (int i = 0; i < blocks_array.length; ++i)
      blocks_array[i] = blocks.get(i);
    return blocks_array;
  }

  
  public static void update()
  {
    final long sysTime = System.currentTimeMillis(); 
    elapsedTime = 0;
    switch (GameManager.gameState) {
    case GameStateEnums.GAME_ACTION:
      /*
      if (lastFRTime > 0)
      {
        ++frameCount;
        final long time_diff = sysTime - lastFRTime;
        if (time_diff > 1000)
        {
          GameManager.renderer.frameRate = frameCount*1000.0f/time_diff;
          lastFRTime = sysTime;
          frameCount = 0;
          GameManager.renderer.bUpdateHUD = true;
        }
      }
      else
        lastFRTime = sysTime;
      */
      if (lastSysTime > 0)
      {
        elapsedTime = sysTime - lastSysTime;
        levelTimer += elapsedTime;
        
        if (elapsedTime > 0)
        {
          GameManager.renderer.frameRate = 1000.0f/elapsedTime;
          TextLabel wid = (TextLabel)UISystem.allScreens[UISystem.SCREEN_HUD].widgets[HUDScreen.HUD_FRAME_RATE];
          StringBuilder frameRateText = wid.textBuilder;
          frameRateText.delete(0, frameRateText.capacity());
          frameRateText.append((int)GameManager.renderer.frameRate);
        }
      }
      break;
    default:
      break;
    }
    lastSysTime = sysTime;

    if (elapsedTime <= 0)
      return;
    
    for (int i = 0; i < objEndIdx; ++i)
    {
      GameObject obj = allObjects[i];
      if (obj.state == StateEnums.DEAD)
      {
        removeObject(i);
        //System.out.println("Obj removed:"+i);
        --i;
        continue;
      }
      BulletSystem ps = obj.bulletSystem;
      obj.update(elapsedTime);

      for (int j = 0; j < objEndIdx; ++j)
      {
        GameObject otherObj = allObjects[j];
        if (obj.team == otherObj.team)
          continue;
        // bullets test
        for (int k = 0; k < ps.endIdx; ++k)
        {
          Particle p = ps.particles[k];
          if (p.isExploding)
            continue;
          if (otherObj.state == StateEnums.NORMAL &&
              otherObj.pointInRotatedRect(p.x, p.z))
          {
            //System.out.println("bullet "+i+" hits object "+j);
            otherObj.receiveDamage(obj.weaponDamage());
            GameObject player = GameManager.thePlayer.vehicle;
            /*if (otherObj == player)
              GameManager.vibrator.vibrate(100);*/
            if (otherObj.hitPoints <= 0)
            {
              //System.out.println("Obj "+j+" becomes zombie");
              LevelController.playSound(GameManager.SOUND_EXPLODE);
              otherObj.state = StateEnums.ZOMBIE;
              otherObj.fragmentSystem.generateRest();
              otherObj.explosionSystem.generateRest();
              if (obj == player)
              {
                cash += obj.score;
                updateScoreWidget();
              }
            }
            BulletSystem.explodeParticle(p);
          }
        }
      }
    }
  }
  
  private static void updateScoreWidget()
  {
    TextLabel wid = (TextLabel)UISystem.allScreens[UISystem.SCREEN_HUD].widgets[HUDScreen.HUD_SCORE];
    StringBuilder scoreText = wid.textBuilder;
    scoreText.delete(0, scoreText.capacity());
    scoreText.append('$');
    scoreText.append(cash);
  }
  
  public static void removeObject(int i)
  {
    final GameObject p = allObjects[i];
    if (i >= objEndIdx)
    {
      System.out.println("Warning: removal index out of range:"+i+", end="+objEndIdx);
      return;
    }
    if (i < objEndIdx - 1)
    {
      // swap with the last particle alive
      allObjects[i] = allObjects[objEndIdx - 1];
      allObjects[objEndIdx - 1] = p;
    }
    --objEndIdx;    
  }
  
  public static void playSound(int sound_id)
  {
    float streamVolumeCurrent = GameManager.audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);   
    float streamVolumeMax = GameManager.audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);       
    float volume = streamVolumeCurrent / streamVolumeMax;
    
    GameManager.soundPool.play(GameManager.soundEffects[sound_id], volume, volume, 1, 0, 1.0f);
  }
}
