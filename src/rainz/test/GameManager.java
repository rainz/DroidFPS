package rainz.test;

import java.io.IOException;
import java.util.Random;

import javax.microedition.khronos.opengles.GL10;
import javax.microedition.khronos.opengles.GL11;

import rainz.test.GLRenderer.VBOEnum;

import android.content.Context;
import android.media.AudioManager;
import android.media.SoundPool;
import android.os.Vibrator;
import android.util.Log;

public abstract class GameManager {
  
  class GameStateEnums {
    static final int TITLE_SCREEN = 0;
    static final int GAME_ACTION = 1;
    static final int GAME_PAUSED = 2;
    static final int LEVEL_INTRO = 3;
    static final int LEVEL_END = 4;
  }
  public static int gameState = GameStateEnums.TITLE_SCREEN;

  class LevelResultEnum {
    static final int NOT_FINISHED = 0;
    static final int PLAYER_WIN = 1;
    static final int PLAYER_LOSE = 2;
  }
  
  public static GamePlayer thePlayer;
  
  public static Random random;
  
  public static GL11 gl;
  
  public static Context context;
  
  public static MainGLSurface surface;
  public static GLRenderer renderer;

  // Sound
  public static final int SOUND_EXPLODE = 0;
  public static final int SOUND_PLASMA = 1;
  public static final int SOUND_GUN = 2;
  public static SoundPool soundPool;
  public static int soundEffects[] = new int[3];
  public static AudioManager audioManager;
 
  // Vibration
  public static Vibrator vibrator;
  
  // All geometry data & texture IDs
  public static final int GEO_TANK = 0;
  public static final int GEO_GUN_TURRET = 1;
  public static final int GEO_SNIPER_TURRET = 2;
  public static final int GEO_TOTAL = 3;
  public static GeometryData [] allGeoData = new GeometryData[GEO_TOTAL];
  
  public static int currentLevel = 1;    
  
  
  // Game options
  public static boolean bLinearTexture = true;
  public static boolean bPlaySound = true;
  public static boolean bShowFrameRate = true; // not configurable from "Options" screen  
  
  private static void setGeometryVBOs(int geo_id, int vbo_start)
  {
    GeometryData geo = allGeoData[geo_id];
    geo.vertexVBOIdx = vbo_start;
    geo.texVBOIdx = vbo_start + 1;
    geo.indicesVBOIdx = vbo_start + 2;
  }
  
  // Called after surface context is available but before gl handle
  public static synchronized void init()
  {
    Utils.init();
    
    random = new Random(System.currentTimeMillis());

    soundPool = new SoundPool(10, AudioManager.STREAM_MUSIC, 0);
    soundEffects[SOUND_EXPLODE] = soundPool.load(context, R.raw.explode, 1);   
    soundEffects[SOUND_PLASMA] = soundPool.load(context, R.raw.plasma, 1);
    soundEffects[SOUND_GUN] = soundPool.load(context, R.raw.gun, 1);
    audioManager = (AudioManager)context.getSystemService(Context.AUDIO_SERVICE);

    vibrator = (Vibrator)context.getSystemService(Context.VIBRATOR_SERVICE);
    
    TextureManager.init();

    ParticleSystem.init();

    // Load models
    Collada collada = new Collada();
    try {
      allGeoData[GEO_TANK] = collada.loadGeometryData(context.getAssets().open("tank.xml"), 0, 0, 4, 4, 0.1f);
      setGeometryVBOs(GEO_TANK, VBOEnum.TANK_START);
      allGeoData[GEO_GUN_TURRET] = collada.loadGeometryData(context.getAssets().open("gun_turret.xml"), 12, 4, 16, 8, 0.08f);
      setGeometryVBOs(GEO_GUN_TURRET, VBOEnum.GUN_TURRET_START);
      allGeoData[GEO_SNIPER_TURRET] = collada.loadGeometryData(context.getAssets().open("sniper.xml"), 12, 8, 16, 12, 0.002f);
      setGeometryVBOs(GEO_SNIPER_TURRET, VBOEnum.SNIPER_TURRET_START);
    }
    catch (Exception e)
    {
      e.printStackTrace();
    }

    UISystem.init();
    
    thePlayer = new GamePlayer();
  }
  
  public static void loadNextLevel()
  {
    // To do: show level intro text
    
    try {
      LevelController.loadLevel(context.getAssets().open("GameMap.csv"));
    }
    catch (IOException e) {
      e.printStackTrace();
      return;
    }
    UISystem.setOnlyScreen(UISystem.SCREEN_HUD);
    Runtime.getRuntime().gc(); // force garbage collection

    // To do: show start button, the next part should be done in a callback
    
    gameState = GameStateEnums.GAME_ACTION;
  }

  public static void endScreen()
  {
    gameState = GameStateEnums.LEVEL_END;      
  }

  public static int levelResult()
  {
    int friendly_base = 0;
    int enemy_base = 0;
    // To do: find out how many friendly and enemy bases are left
    if (/*player dead && */ friendly_base == 0)
      return LevelResultEnum.PLAYER_LOSE;
    else if (enemy_base == 0 /* && time out && enemies_killed > goal*/)
      return LevelResultEnum.PLAYER_WIN;
    else
      return  LevelResultEnum.NOT_FINISHED;
  }

}

class GamePlayer {
  public int cash = 0;
  
  public GameObject vehicle = new GameObject();
  
  public int current_kills = 0;
}
