package rainz.test;

import java.util.ArrayList;

import rainz.test.GameObject.StateEnums;
import rainz.test.LevelController.DirectionEnum;

import android.graphics.Point;
import android.graphics.PointF;
import android.media.AudioManager;

public class AI {

  public static AI newAttackMoveInstance(GameObject h, Point t)
  {
    return new AIAttackMove(h, t);
  }

  public static AI newPatrolInstance(GameObject h, PointF p1, PointF p2)
  {
    return new AIPatrol(h, p1, p2);
  }

  public static AI newGuardInstance(GameObject h)
  {
    return new AIGuard(h);
  }
  
  public static AI newUserInputInstance(GameObject h)
  {
    return new AIUserInput(h);
  }
  
  public GameObject host = null;
  protected AI(GameObject h)
  {
    host = h;
  }
  
  public void nextAction()
  {
    
  }

  public static int computeAngle(float x_from, float y_from, float x_to, float y_to)
  {
    int degree = (int)Math.toDegrees(Math.atan2(y_to - y_from, x_to - x_from));
    if (degree < 0)
      degree += 360;
    return degree;
  }
   
  public int[][] computeShortestPathMap(int row, int col)
  {
    int map_rows = LevelController.worldHeight;
    int map_cols = LevelController.worldWidth;
    // Here x and y of a Point are used as row and column
    Point dest = new Point(row, col);
    // Initialize shortest path map and distance map
    int[][] shortest_path_map = new int[map_rows][map_cols];
    for (int i = 0; i < map_rows; ++i)
      for (int j = 0; j < map_cols; ++j)
        shortest_path_map[i][j] = DirectionEnum.CANT_REACH;
    
    int[][] distance_map = new int[map_rows][map_cols];
    for (int i = 0; i < map_rows; ++i)
      for (int j = 0; j < map_cols; ++j)
        distance_map[i][j] = Integer.MAX_VALUE;
    
    class ProcessRecord {
      Point toPoint;
      Point fromPoint;
      int distance;
      public ProcessRecord(Point to, Point from, int dist)
      {
        toPoint = to;
        fromPoint = from;
        distance = dist;
      }
    }
    // The queue for breadth-first-search
    ArrayList<ProcessRecord> processQ = new ArrayList<ProcessRecord>();
    
    ProcessRecord rec = new ProcessRecord(dest, dest, 0);
    if (LevelController.pointBlocked(dest.x, dest.y))
      return shortest_path_map; // destination is in a wall??
    
    processQ.add(rec); // initial point: the final destination
    
    Point toP, fromP;
    int distance;
    for (int processIdx = 0; processIdx < processQ.size(); ++processIdx)
    {
      rec = processQ.get(processIdx);
      toP = rec.toPoint; // "to" point is the point being examined right now
      fromP = rec.fromPoint; // "from" point is for preventing us from going back
      distance = rec.distance;
      if (distance_map[toP.x][toP.y] <= distance)
        continue; // a shorter path already exists

      distance_map[toP.x][toP.y] = distance;
      shortest_path_map[toP.x][toP.y] = determineDir(toP, fromP);
      
      // Adding neighbor locations
      ArrayList<Point> neighbors = new ArrayList<Point>();
      // Down
      if (toP.x + 1 < map_rows)
        neighbors.add(new Point(toP.x + 1, toP.y));
      // Right
      if (toP.y + 1 < map_cols)
        neighbors.add(new Point(toP.x, toP.y + 1));
      // Left
      if (toP.y - 1 >= 0)
        neighbors.add(new Point(toP.x, toP.y - 1));
      // Up
      if (toP.x - 1 >= 0)
        neighbors.add(new Point(toP.x - 1, toP.y));
      for (int i = 0; i < neighbors.size(); ++i) {
        Point nb = neighbors.get(i);
        if (nb.equals(fromP))
          continue; // don't go back
        if (LevelController.pointBlocked(nb.x, nb.y))
          continue; // blocked
        // New point to explore. Append at the end of the queue
        ProcessRecord new_rec = new ProcessRecord(nb, toP, distance + 1);
        processQ.add(new_rec);
      }
    }
    
    return shortest_path_map;
  }

  protected int determineDir(Point startPoint, Point endPoint)
  {
    int dir;
    int row_diff = endPoint.x - startPoint.x;
    int col_diff = endPoint.y - startPoint.y;
    if (row_diff == 0 && col_diff == 0)
      dir = DirectionEnum.HERE;
    else if (row_diff == 0 && col_diff == -1)
      dir = DirectionEnum.LEFT;
    else if (row_diff == 0 && col_diff == 1)
      dir = DirectionEnum.RIGHT;
    else if (row_diff == -1 && col_diff == 0)
      dir = DirectionEnum.UP;
    else if (row_diff == 1 && col_diff == 0)
      dir = DirectionEnum.DOWN;
    else
      dir = DirectionEnum.CANT_REACH;
    
    return dir;
  }
}

class AIPatrol extends AI {
  public PointF point1;
  public PointF point2;
  
  private int direction = 1; // 1: p1 to p2, -1: p2 to p1
  private double lastDistance;
  private int patrolAngle = 0;

  public AIPatrol(GameObject h, PointF p1, PointF p2) {
    super(h);
    point1 = p1;
    point2 = p2;
    lastDistance = computeDistance();
    patrolAngle = computeAngle(point1.x, point1.y, point2.x, point2.y);
  }
  
  private float computeDistance()
  {
    PointF target = (direction == 1 ? point2 : point1);
    float dx = host.positionX - target.x;
    float dy = host.positionZ - target.y;
    return dx*dx + dy*dy;    
  }

  @Override
  public void nextAction()
  {
    GameObject playerVehicle = GameManager.thePlayer.vehicle;

    if (host.inSight(playerVehicle.positionX, playerVehicle.positionZ) && playerVehicle.state == StateEnums.NORMAL)
    {
      int dir_angle = AI.computeAngle(host.positionX, host.positionZ, playerVehicle.positionX, playerVehicle.positionZ);
      //System.out.println("tankX="+tank.positionX+",tankY="+tank.positionY+",playerX="+playerVehicle.positionX+",playerY="+playerVehicle.positionY+",angle="+dir_angle);
      if (host.turnTo(dir_angle))
        host.fire(null);
      return;
    }

    float new_dist = computeDistance();
    if (new_dist > lastDistance)
    {
      direction = -direction;
      lastDistance = computeDistance();
    }
    else if (new_dist < lastDistance)
      lastDistance = new_dist;

    /*
    if (Utils.abs(host.positionX - point1.x) < 0.5 && 
        Utils.abs(host.positionZ - point1.y) < 0.5)
    {
      direction = 1;
    }
    else if (Utils.abs(host.positionX - point2.x) < 0.5 && 
             Utils.abs(host.positionZ - point2.y) < 0.5)
    {
      direction = -1;
    }
    */
    
    
    
    int dir_angle = (direction == 1 ? patrolAngle : (180 + patrolAngle));
    if (host.turnTo(dir_angle))
    {
      if (host.move(DirectionEnum.UP) < 0)
        direction = -direction;
    }
    
  }
}

class AIGuard extends AI {

  public AIGuard(GameObject h) {
    super(h);
  }

  @Override
  public void nextAction()
  {
    GameObject playerVehicle = GameManager.thePlayer.vehicle;

    if (host.inSight(playerVehicle.positionX, playerVehicle.positionZ) && playerVehicle.state == StateEnums.NORMAL)
    {
      int dir_angle = AI.computeAngle(host.positionX, host.positionZ, playerVehicle.positionX, playerVehicle.positionZ);
      //System.out.println("tankX="+tank.positionX+",tankY="+tank.positionY+",playerX="+playerVehicle.positionX+",playerY="+playerVehicle.positionY+",angle="+dir_angle);
      if (host.turnTo(dir_angle))
        host.fire(null);
      return;
    }
  }
}


class AIAttackMove extends AI {
  public Point target;
  int[][] shortestPathMap;

  public AIAttackMove(GameObject h, Point t) {
    super(h);
    target = t;
    shortestPathMap = computeShortestPathMap(t.x, t.y);
  }

  @Override
  public void nextAction()
  {
    if (host.positionX % host.dimensionX == 0 && host.positionZ % host.dimensionZ == 0)
    {
      // Update direction only when the object "snaps into" this block
      int next_i = (int)(host.positionZ / host.dimensionZ);
      int next_j = (int)(host.positionX / host.dimensionX);
      host.currDir = shortestPathMap[next_i][next_j];
    }
    host.move(host.currDir);
  }
}

class AIUserInput extends AI {
  public static final int INPUT_MIN = 0;
  /*
  public static final int INPUT_FORWARD = INPUT_MIN;
  public static final int INPUT_BACK = 1;
  public static final int INPUT_LEFT_TURN = 2;
  public static final int INPUT_RIGHT_TURN = 3;
  public static final int INPUT_LEFT_STRAFE = 4;
  public static final int INPUT_RIGHT_STRAFE = 5;
  public static final int INPUT_UP = 6;
  public static final int INPUT_DOWN = 7;
  public static final int INPUT_FIRE = 8;
  public static final int INPUT_FIRE_ALT = 9;
  */
  // Sum of opposition inputs = INPUT_SUM
  public static final int INPUT_FORWARD = 0;
  public static final int INPUT_BACK = 7;
  public static final int INPUT_LEFT_TURN = 1;
  public static final int INPUT_RIGHT_TURN = 6;
  public static final int INPUT_LEFT_STRAFE = 2;
  public static final int INPUT_RIGHT_STRAFE = 5;
  public static final int INPUT_UP = 3;
  public static final int INPUT_DOWN = 4;
  public static final int INPUT_SUM = INPUT_FORWARD + INPUT_BACK;
  public static final int INPUT_FIRE = 8;
  public static final int INPUT_FIRE_ALT = 9;
  public static final int INPUT_MAX = INPUT_FIRE_ALT;
  public static final int INPUT_OTHER = Integer.MAX_VALUE;
  
  public boolean userInputs[] = new boolean[INPUT_MAX+1];
  
  private int autoTurnDegrees = 0;
  
  public AIUserInput(GameObject h)
  {
    super(h);
    for (int i = 0; i < userInputs.length; ++i)
      userInputs[i] = false;
  }

  @Override
  public void nextAction()
  {
    
    if (userInputs[INPUT_FORWARD])
    {
      if (host.move(DirectionEnum.UP) < 0)
        userInputs[INPUT_FORWARD] = false;
    }
    else if (userInputs[INPUT_BACK])
    {
      if (host.move(DirectionEnum.DOWN) < 0)
        userInputs[INPUT_BACK] = false;
    }
    
    if (userInputs[INPUT_LEFT_TURN])
    {
      float turned = host.turn(-1, 360);
      if (turned < 0)
        userInputs[INPUT_LEFT_TURN] = false;
      else if (autoTurnDegrees > 0)
      {
        autoTurnDegrees -= Math.round(turned);
        if (autoTurnDegrees <= 0)
        {
          userInputs[INPUT_LEFT_TURN] = false;
          autoTurnDegrees = 0;
        }
      }
    }
    else if (userInputs[INPUT_RIGHT_TURN])
    {
      float turned = host.turn(1, 360);
      if (turned < 0)
        userInputs[INPUT_RIGHT_TURN] = false;
      else if (autoTurnDegrees > 0)
      {
        autoTurnDegrees -= Math.round(turned);
        if (autoTurnDegrees <= 0)
        {
          userInputs[INPUT_RIGHT_TURN] = false;
          autoTurnDegrees = 0;
        }
      }
    }
    
    if (userInputs[INPUT_LEFT_STRAFE])
    {
      float strafed = host.move(DirectionEnum.LEFT);
      if (strafed < 0)
        userInputs[INPUT_LEFT_STRAFE] = false;
    }
    else if (userInputs[INPUT_RIGHT_STRAFE])
    {
      float strafed = host.move(DirectionEnum.RIGHT);
      if (strafed < 0)
        userInputs[INPUT_RIGHT_STRAFE] = false;
    }
    
    if (userInputs[INPUT_UP])
    {
      host.viewHeight += host.speed*LevelController.elapsedTime;
    }
    else if (userInputs[INPUT_DOWN])
    {
      host.viewHeight -= host.speed*LevelController.elapsedTime;
    }
    
    if (userInputs[INPUT_FIRE])
    {
      host.fire(null);
    }

    if (userInputs[INPUT_FIRE_ALT])
    {
      // To do: fire alt 
      //host.fire(null);
    }
  }
  
  public void handleInput(int input, boolean bDown)
  {
    if (input < INPUT_MIN || input > INPUT_MAX)
    {
      //System.out.println("Warning: invalid input:"+input);
      return;
    }
    if (input == INPUT_LEFT_TURN || input == INPUT_RIGHT_TURN)
    {
      autoTurnDegrees = 0;
      userInputs[INPUT_LEFT_TURN] = false;
      userInputs[INPUT_RIGHT_TURN] = false;
    }
    userInputs[input] = bDown;
    if (bDown && input <= INPUT_SUM)
      userInputs[INPUT_SUM-input] = false;
  }
  
  public void autoTurn(int degrees)
  {
    autoTurnDegrees = degrees >= 0 ? degrees : -degrees;
    userInputs[degrees > 0 ? INPUT_LEFT_TURN : INPUT_RIGHT_TURN] = true;
  }
}