package rainz.test;

import java.io.*;
import java.nio.ShortBuffer;
import java.util.*;

import org.w3c.dom.*;
import org.xml.sax.*;

import android.graphics.RectF;
import android.util.Log;

import javax.xml.parsers.*;

public class Collada {
  
  class GlobalState {
    public HashMap<String, ColladaObject> allIDs = new HashMap<String, ColladaObject>();
    
    public ColladaObject getObjectFromURL(String url)
    {
      if (url == null)
        return null;
      StringBuffer url_buf = new StringBuffer(url);
      if (url_buf.charAt(0) == '#')
      {
        url_buf.deleteCharAt(0);
        return allIDs.get(url_buf.toString());
      }
      return null;
    }
  }
  
  GlobalState globalState = new GlobalState();
  
  class ColladaFactory {
    private HashMap<String, String> m_collada_classes = new HashMap<String, String>();
    public ColladaFactory()
    {
      m_collada_classes.put("COLLADA", "ColladaRoot");
      
      m_collada_classes.put("library_geometries", "ColladaGeometry");
      m_collada_classes.put("instance_geometry", "ColladaGeometry");
      m_collada_classes.put("geometry", "ColladaGeometry");
      m_collada_classes.put("mesh", "ColladaMesh");
      m_collada_classes.put("vertices", "ColladaVertices");
      m_collada_classes.put("polylist", "ColladaPolyList");
      m_collada_classes.put("triangles", "ColladaTriangles");
      m_collada_classes.put("vcount", "ColladaVCount");
      m_collada_classes.put("p", "ColladaP");
      
      m_collada_classes.put("library_visual_scenes", "ColladaVisualScene");
      m_collada_classes.put("instance_visual_scene", "ColladaVisualScene");
      m_collada_classes.put("visual_scene", "ColladaVisualScene");
      m_collada_classes.put("scene", "ColladaScene");
      
      m_collada_classes.put("technique", "ColladaTechnique");
      m_collada_classes.put("technique_common", "ColladaTechnique");
      m_collada_classes.put("accessor", "ColladaAccessor");
      m_collada_classes.put("param", "ColladaParam");
      
      m_collada_classes.put("node", "ColladaNode");
      m_collada_classes.put("source", "ColladaSource");
      m_collada_classes.put("input", "ColladaInput");
      m_collada_classes.put("float_array", "ColladaFloatArray");
      m_collada_classes.put("int_array", "ColladaIntArray");
      
    }
    
    public ColladaObject createColladaObject(Element element)
    {
      String tag = element.getTagName();
      String cls_name = m_collada_classes.get(tag);
      if (cls_name == null)
      {
        //System.out.println("Skipping " + tag);
        return null;
      }
      //System.out.println("Creating " + tag);

      String full_class_name = Collada.this.getClass().getName() + "$" + cls_name;
      Class<? extends ColladaObject> cls;
      try
      {
        cls = Class.forName(full_class_name).asSubclass(ColladaObject.class);
      } catch (ClassNotFoundException e)
      {
        System.out.println("Cannot find class " + full_class_name);
        return null;
      }

      ColladaObject obj;

      if (tag.startsWith("library_"))
      {
        ColladaLibrary lib = new ColladaLibrary();
        lib.object_class = cls;
        obj = lib;
      }
      else if (tag.startsWith("instance_"))
      {
        ColladaInstance inst = new ColladaInstance();
        inst.object_class = cls;
        obj = inst;
      }
      else
      {
        java.lang.reflect.Constructor<? extends ColladaObject> co;
        try
        {
          co = cls.getConstructor(new Class[] { Collada.class });
        } catch (Exception e)
        {
          System.out.println("Unable to get constructor for " + full_class_name);
          return null;
        }
        try
        {
          obj = co.newInstance(Collada.this);
        } catch (Exception e)
        {
          System.out.println("Cannot create instance of " + full_class_name);
          return null;
        }
      }

      obj.xml_element = element;
      obj.id = element.getAttribute("id");
      if (!obj.id.equals(""))
        globalState.allIDs.put(obj.id, obj);
      obj.processSelf();
      return obj;
    }
    
  }
  
  ColladaFactory colladaFactory = new ColladaFactory();
  
  abstract public class ColladaObject {
    public ColladaObject() {}
 
    public String id;
    public Element xml_element;
 
    public boolean checkTag(String tag)
    {
      return (tag.equals(xml_element.getTagName()));
    }
    
    protected void processSelf() {}
    protected void processChild(ColladaObject child){}
    
    final protected void visitChildren()
    {
      NodeList children = xml_element.getChildNodes();      
      for (int i = 0; i < children.getLength(); ++i)
      {
        Node node = children.item(i);
        if (node.getNodeType() != Node.ELEMENT_NODE) {
          continue;
        }
        ColladaObject child = colladaFactory.createColladaObject((Element)node);
        if (child == null)
          continue;
        child.visitChildren();
        processChild(child);
      }
      
      return;
    }
 
  }
  
  abstract public class ColladaParameterizedObject extends ColladaObject {
    protected Class<?> object_class;

    public boolean typeCheck(ColladaObject value)
    {
      return value.getClass().equals(object_class);
    }
  }
  
  public class ColladaRoot extends ColladaObject {
    public ColladaRoot () {}
 
    public ColladaLibrary lib_geometries;
    public ColladaLibrary lib_visual_scenes;
    public ColladaScene scene;    
    
    @Override
    protected void processChild(ColladaObject child)
    {
      if (child instanceof ColladaLibrary) {
        if (child.checkTag("library_geometries"))
        {
          if (lib_geometries == null)
            lib_geometries = (ColladaLibrary)child;
          else
            lib_geometries.putAll((ColladaLibrary)child);
        }
        else if (child.checkTag("library_visual_scenes"))
        {
          if (lib_visual_scenes == null)
            lib_visual_scenes = (ColladaLibrary)child;
          else
            lib_visual_scenes.putAll(((ColladaLibrary)child));
        }
      }
      else if (child instanceof ColladaScene)
        scene = (ColladaScene)child;
    }
  }
  
  public class ColladaLibrary extends ColladaParameterizedObject {
    private HashMap<String, ColladaObject> m_objects = new HashMap<String, ColladaObject>();
    
    public ColladaObject get(String key)
    {
      return m_objects.get(key);
    }
    
    public ColladaObject put(String key, ColladaObject value)
    {
      if (typeCheck(value))
        return m_objects.put(key,value);
      //System.out.println("Adding a "+value.getClass().getName()+ " to a library for "+object_class.getName());
      return null;
        
    }
    
    public Set<String> keySet()
    {
      return m_objects.keySet();
    }
    
    public void putAll(ColladaLibrary lib)
    {
      m_objects.putAll(lib.m_objects);
    }
    
    @Override
    public void processChild(ColladaObject child)
    {
      if (typeCheck(child) == false)
        return;
      if (child.id == null)
      {
        //System.out.println("Library: id-less child:"+child.xml_element.getTagName());
        return;
      }
      m_objects.put(child.id, child);
    }
    
  }
  
  public class ColladaInstance extends ColladaParameterizedObject {
    private HashMap<String, ColladaObject> m_additional = new HashMap<String, ColladaObject>();
    
    public ColladaObject prototype;
    
    public ColladaObject get(String key)
    {
      return m_additional.get(key);
    }
    
    public ColladaObject put(String key, ColladaObject value)
    {
      return m_additional.put(key,value);
    }

    public Set<String> keySet()
    {
      return m_additional.keySet();
    }
    
    @Override
    public void processChild(ColladaObject child)
    {
      m_additional.put(child.xml_element.getTagName(), child);
    }
    
    @Override
    public void processSelf()
    {
      ColladaObject type = globalState.getObjectFromURL(xml_element.getAttribute("url"));
      if (type == null)
        return;
      
      if (typeCheck(type))
        prototype = type;
      //else
        //System.out.println("Instantiating a "+type.getClass().getName()+ " for "+object_class.getName());
    }
  }

  public class ColladaScene extends ColladaObject {
    ColladaInstance visual_scene;
    @Override
    public void processChild(ColladaObject child)
    {
      if (child instanceof ColladaInstance) {
        if (child.checkTag("instance_visual_scene"))
          visual_scene = (ColladaInstance)child;
      }
    }
  }
  
  public class ColladaNode extends ColladaObject {
    
  }
  
  class ColladaFloatArrayBase extends ColladaObject {
    public float values[];
    @Override
    public void processSelf()
    {
      int count;
      String value_str = xml_element.getFirstChild().getNodeValue();
      String [] tokens = value_str.split(" ");
      String attr = xml_element.getAttribute("count");
      if (!attr.equals(""))
        count = Integer.parseInt(attr);
      else
        count = tokens.length;
      values = new float[count];
      for (int i = 0; i < count; ++i)
        values[i] = Float.parseFloat(tokens[i]);
    }
  }
  
  class ColladaIntArrayBase extends ColladaObject {
    public int values[];
    @Override
    public void processSelf()
    {
      int count;
      String value_str = xml_element.getFirstChild().getNodeValue();
      String [] tokens = value_str.split(" ");
      String attr = xml_element.getAttribute("count");
      if (!attr.equals(""))
        count = Integer.parseInt(attr);
      else
        count = tokens.length;
      values = new int[count];
      for (int i = 0; i < count; ++i)
        values[i] = Integer.parseInt(tokens[i]);
    }
  }
  
  public class ColladaFloatArray extends ColladaFloatArrayBase {  
  }

  public class ColladaIntArray extends ColladaIntArrayBase {  
  }
  
  public class ColladaParam extends ColladaObject {
    public String name;
    public String sid;
    public String type;
    public String semantic;
    @Override
    public void processSelf()
    {
      name = xml_element.getAttribute("name");
      sid = xml_element.getAttribute("sid");
      type = xml_element.getAttribute("type");
      semantic = xml_element.getAttribute("semantic");
    }
  }
  
  public class ColladaAccessor extends ColladaObject {
    public int count = 0;
    public int offset = 0;
    public ColladaObject source;
    public int stride = 1;
    public HashMap<String, ColladaParam> items = new HashMap<String, ColladaParam>();
    @Override
    public void processSelf()
    {
      String attr = xml_element.getAttribute("count");
      if (!attr.equals(""))
        count = Integer.parseInt(attr);
      attr = xml_element.getAttribute("offset");
      if (!attr.equals(""))
        offset = Integer.parseInt(attr);
      attr = xml_element.getAttribute("stride");
      if (!attr.equals(""))
        stride = Integer.parseInt(attr);
      attr = xml_element.getAttribute("source");
      source = globalState.getObjectFromURL(attr);
    }
    
    @Override
    public void processChild(ColladaObject child)
    {
      if (child instanceof ColladaParam)
        items.put(child.xml_element.getTagName(), (ColladaParam)child);
    }
  }
  
  public class ColladaTechnique extends ColladaObject {
    public HashMap<String, ColladaObject> items = new HashMap<String, ColladaObject>();
    @Override
    public void processChild(ColladaObject child)
    {
      items.put(child.xml_element.getTagName(), child);
    }
  }
  
  public class ColladaSource extends ColladaObject {
    public ColladaFloatArray float_array;
    public ColladaTechnique technique;    
    
    @Override
    public void processChild(ColladaObject child)
    {
      if (child instanceof ColladaFloatArray)
        float_array = (ColladaFloatArray)child;
      else if (child instanceof ColladaTechnique)
        technique = (ColladaTechnique)child;
    }
  }
  
  public class ColladaInput extends ColladaObject {
    public String semantic;
    public ColladaObject source;
    public int offset = 0;
    public int set = 0;
    @Override
    public void processSelf()
    {
      String tmp_str = xml_element.getAttribute("source");
      source = (ColladaObject)globalState.getObjectFromURL(tmp_str);

      semantic = xml_element.getAttribute("semantic");
      
      tmp_str = xml_element.getAttribute("offset");
      if (!tmp_str.equals(""))
        offset = Integer.parseInt(tmp_str);
      
      tmp_str = xml_element.getAttribute("set");
      if (!tmp_str.equals(""))
        set = Integer.parseInt(tmp_str);
    }
  }
  
  public class ColladaVisualScene extends ColladaObject {
    public HashMap<String, ColladaNode> nodes = new HashMap<String, ColladaNode>();
    @Override
    public void processChild(ColladaObject child)
    {
      if (child instanceof ColladaNode)
        nodes.put(child.id, (ColladaNode)child);
    }
    
  }
  
  public class ColladaVertices extends ColladaObject {
    public HashMap<String, ColladaInput> inputs = new HashMap<String, ColladaInput>();
    @Override
    public void processChild(ColladaObject child)
    {
      if (child instanceof ColladaInput)
      {
        ColladaInput input = (ColladaInput)child; 
        inputs.put(input.semantic, input);
      }
    }
  }
  
  
  public interface ColladaPrimitive {
  }
  
  public class ColladaVCount extends ColladaIntArrayBase {    
  }
  public class ColladaP extends ColladaIntArrayBase {    
  }
  
  
  public class ColladaPolyList extends ColladaObject implements ColladaPrimitive {
    public int count = 0;
    public String material;
    public ColladaVCount vcount;
    public ColladaP p;
    public HashMap<String, ColladaInput> inputs = new HashMap<String, ColladaInput>();
    
    @Override
    public void processChild(ColladaObject child)
    {
      if (child instanceof ColladaVCount)
        vcount = (ColladaVCount)child;
      else if (child instanceof ColladaP)
        p = (ColladaP)child;
      else if (child instanceof ColladaInput)
      {
        ColladaInput inp = (ColladaInput)child;
        if (!inp.semantic.equals(""))
          inputs.put(inp.semantic, inp);
      }
    }
    
    @Override
    public void processSelf()
    {
      String attr = xml_element.getAttribute("count");
      if (!attr.equals(""))
        count = Integer.parseInt(attr);
      material = xml_element.getAttribute("material");
    }
  }

  public class ColladaTriangles extends ColladaObject implements ColladaPrimitive {
    public int count = 0;
    public String material;
    public ColladaP p;
    public HashMap<String, ColladaInput> inputs = new HashMap<String, ColladaInput>();
    
    @Override
    public void processChild(ColladaObject child)
    {
      if (child instanceof ColladaP)
        p = (ColladaP)child;
      else if (child instanceof ColladaInput)
      {
        ColladaInput inp = (ColladaInput)child;
        if (!inp.semantic.equals(""))
          inputs.put(inp.semantic, inp);
      }
    }
    
    @Override
    public void processSelf()
    {
      String attr = xml_element.getAttribute("count");
      if (!attr.equals(""))
        count = Integer.parseInt(attr);
      material = xml_element.getAttribute("material");
    }
  }
  
  public class ColladaMesh extends ColladaObject {
    public HashMap<String, ColladaSource> sources = new HashMap<String, ColladaSource>();
    public ColladaVertices vertices;
    public ArrayList<ColladaPrimitive> primitives = new ArrayList<ColladaPrimitive>();
    @Override
    public void processChild(ColladaObject child)
    {
      if (child instanceof ColladaSource)
        sources.put(child.id, (ColladaSource)child);
      else if (child instanceof ColladaVertices)
        vertices = (ColladaVertices)child;
      else if (child instanceof ColladaPrimitive)
        primitives.add((ColladaPrimitive)child);
    }
  }
  
  public class ColladaGeometry extends ColladaObject {
    public ColladaMesh mesh;
    @Override
    public void processChild(ColladaObject child)
    {
      if (child instanceof ColladaMesh)
        mesh = (ColladaMesh)child;
    }
    
  }

  public ColladaRoot loadCollada(InputStream fileStream) throws SAXException,
    IOException, ParserConfigurationException
  {
    DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
    //Document document = builder.parse(fileName);
    Document document = builder.parse(fileStream);
    Element docElement = document.getDocumentElement();
    ColladaObject rootObject = colladaFactory.createColladaObject(docElement);
    rootObject.visitChildren();
    return (ColladaRoot)rootObject;
  }
 
  
  public GeometryData loadGeometryData(InputStream fileStream, int left, int top, int right, int bottom, float scale) throws SAXException,
    IOException, ParserConfigurationException
  {
    ArrayList<GeometryData> geoDataArray = new ArrayList<GeometryData>();
    ColladaRoot rootCollada = loadCollada(fileStream);
    Set<String> geoKeys = rootCollada.lib_geometries.keySet();
    for (String geoKey: geoKeys)
    {     
      GeometryData geoData = new GeometryData();
      ColladaGeometry geo = (ColladaGeometry)rootCollada.lib_geometries.get(geoKey);
      for (ColladaPrimitive primitive: geo.mesh.primitives)
      {
        if (!(primitive instanceof ColladaTriangles))
        {
          //System.out.println("Not a triangle, skipping");
          continue;
        }
        ColladaTriangles triangles = (ColladaTriangles)primitive;
        short [] vertexIndices = new short[triangles.p.values.length/2];
        short [] texIndices = new short[triangles.p.values.length/2];
        for (int idx = 0; idx < triangles.p.values.length; idx += 2)
        {
          vertexIndices[idx/2] = (short) triangles.p.values[idx];
          texIndices[idx/2] = (short) triangles.p.values[idx+1];
        }
        geoData.vertexIndices = Utils.getShortBufferForArray(vertexIndices);
        geoData.texIndices = Utils.getShortBufferForArray(texIndices);
        ColladaInput inp = triangles.inputs.get("TEXCOORD");
        ColladaSource src;
        if (inp != null)
        {
          src = (ColladaSource) inp.source;
          RectF rect = new RectF();
          TextureManager.textures[TextureManager.textureAll].getTexCoords(left, top, right, bottom, rect);
          mapTextureCoords(src.float_array.values, rect);
          geoData.texCoords = Utils.getFloatBufferForArray(src.float_array.values);
        }
        else
        {
          geoData.texCoords = Utils.getFloatBufferForArray(new float[0]);
        }
        src = (ColladaSource) geo.mesh.vertices.inputs.get("POSITION").source;
        swapAxesAndScale(src.float_array.values, scale);
        geoData.positions = Utils.getFloatBufferForArray(src.float_array.values);
        src = (ColladaSource) geo.mesh.vertices.inputs.get("NORMAL").source;
        geoData.normals = Utils.getFloatBufferForArray(src.float_array.values);        
        geoData.computeDimensions();
        
        geoDataArray.add(geoData);
        //printGeometryData(geoData);
      }
    }
    GeometryData geoMerged = mergeGeometries(geoDataArray);
    geoMerged.computeDimensions();
    return geoMerged;
  }
  
  private static void mergeIndices(ShortBuffer dest, ShortBuffer src, short start)
  {
    for (int i = 0; i < src.limit(); ++i)
    {
      short val = (short) (src.get(i) + start);
      dest.put(val);
    }
  }
  
  public GeometryData mergeGeometries(ArrayList<GeometryData> geoArray)
  {
    int positionsLen = 0;
    int normalsLen = 0;
    int texCoordsLen = 0;
    int vertexIndicesLen = 0;
    int texIndicesLen = 0;
    for (GeometryData geo: geoArray)
    {
      if (geo.texCoords.limit() < 1)
        continue;
      positionsLen += geo.positions.limit();
      normalsLen += geo.normals.limit();
      texCoordsLen += geo.texCoords.limit();
      vertexIndicesLen += geo.vertexIndices.limit();
      texIndicesLen += geo.texIndices.limit();
    }
    
    GeometryData geoMerged = new GeometryData();
    geoMerged.positions = Utils.getFloatBuffer(positionsLen);
    geoMerged.normals = Utils.getFloatBuffer(normalsLen);
    geoMerged.texCoords = Utils.getFloatBuffer(texCoordsLen);
    geoMerged.vertexIndices = Utils.getShortBuffer(vertexIndicesLen);
    geoMerged.texIndices = Utils.getShortBuffer(texIndicesLen);
    short startV = 0, startT = 0;
    for (GeometryData geo: geoArray)
    {
      if (geo.texCoords.limit() < 1)
        continue;
      mergeIndices(geoMerged.vertexIndices, geo.vertexIndices, startV);
      mergeIndices(geoMerged.texIndices, geo.texIndices, startT);
      startV += (short) (geo.positions.limit()/3);
      startT += (short) (geo.texCoords.limit()/2);
      geoMerged.positions.put(geo.positions);
      geoMerged.normals.put(geo.normals);
      geoMerged.texCoords.put(geo.texCoords);
    }
    geoMerged.positions.position(0);
    geoMerged.normals.position(0);
    geoMerged.texCoords.position(0);
    geoMerged.vertexIndices.position(0);
    geoMerged.texIndices.position(0);
    return geoMerged;
  }


  public static void swapAxesAndScale(float [] array, float scale)
  {
    for (int i = 0; i < array.length/3; ++i)
    {
      // Convert from Z_UP to Y_UP then scale
      float old_x = array[3*i];
      float old_y = array[3*i+1];
      float old_z = array[3*i+2];
      array[3*i] = old_y*scale;
      array[3*i+1] = old_z*scale;
      array[3*i+2] = old_x*scale;
    }
  }
  
  public static void mapTextureCoords(float [] array, RectF rect)
  {
    float width = Math.abs(rect.right - rect.left);
    float height = Math.abs(rect.top - rect.bottom);
    for (int i = 0; i < array.length/2; ++i)
    {
      float old_s = array[2*i];
      /*
      while (old_s < 0)
        ++old_s;
      while (old_s > 1)
        --old_s;
      */
      if (old_s < 0)
        old_s = 0;
      else if (old_s > 1)
        old_s = 1;
      float old_t = array[2*i+1];
      /*
      while (old_t < 0)
        ++old_t;
      while (old_t > 1)
        --old_t;
      */
      if (old_t < 0)
        old_t = 0;
      else if (old_t > 1)
        old_t = 1;
      array[2*i] = rect.left + width*old_s;
      array[2*i+1] = rect.top + height*old_t;
    }
  }
  
  public void printGeometryData(GeometryData geo)
  {
    int count = geo.positions.limit();
    System.out.println("float positions["+count+"] = {");
    for (int i = 0; i < count; ++i)
      System.out.print(geo.positions.get(i)+"f,");
    System.out.println("};");
    count = geo.normals.limit();
    System.out.println("float normals["+count+"] = {");
    for (int i = 0; i < count; ++i)
      System.out.print(geo.normals.get(i)+"f,");
    System.out.println("};");
    count = geo.texCoords.limit();
    System.out.println("float texCoords["+count+"] = {");
    for (int i = 0; i < count; ++i)
      System.out.print(geo.texCoords.get(i)+"f,");
    System.out.println("};");
    count = geo.vertexIndices.limit();
    System.out.println("short vertexIndices["+count+"] = {");
    for (int i = 0; i < count; ++i)
      System.out.print(geo.vertexIndices.get(i)+",");
    System.out.println("};");
    count = geo.texIndices.limit();
    System.out.println("short texIndices["+count+"] = {");
    for (int i = 0; i < count; ++i)
      System.out.print(geo.texIndices.get(i)+",");
    System.out.println("};");
    System.out.println("float dimensionX="+geo.dimensionX+"f");
    System.out.println("float dimensionY="+geo.dimensionY+"f");
    System.out.println("float dimensionZ="+geo.dimensionZ+"f");
    System.out.println();
  }
  

}
