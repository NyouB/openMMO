package t4cPlugin;

import java.awt.Point;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.HashMap;

import opent4c.MapPixel;
import opent4c.utils.LoadingStatus;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
@Deprecated
public class MapFile implements Serializable{
	
	private static Logger logger = LogManager.getLogger(MapFile.class.getSimpleName());
	
	/**
	 * C'est une carte de 3072 x 3072 pixels. Elle contient de quoi retrouver
	 * les informations par coordonnées ou par ID.
	 */
	private static final long serialVersionUID = -6379650965770348303L;
	private File nom;
	public HashMap<Point,MapPixel> pixels = new HashMap<Point,MapPixel>();
	public HashMap<Integer, MapPixel> ids = new HashMap<Integer, MapPixel>();
	
	private LoadingStatus loadingStatus = LoadingStatus.INSTANCE;
	
	public MapFile(File name){
		nom = name;
	}
	
	public void write(){
		try{
			FileOutputStream fout = new FileOutputStream(nom);
			ObjectOutputStream oos = new ObjectOutputStream(fout);   
			oos.writeObject(this);
			oos.close();
			logger.info(nom.getPath()+" écrit.");
		}catch(Exception ex){
		   ex.printStackTrace();
		   System.exit(1);
		}
	}
	
	public MapFile read(){
		MapFile map = null;
		try{
			FileInputStream fis = new FileInputStream(nom);
			BufferedInputStream bis = new BufferedInputStream(fis);
			ObjectInputStream ois = new ObjectInputStream(bis);
			map = (MapFile) ois.readObject();
			ois.close();
			logger.info(nom.getPath()+" lu.");
		}catch(Exception ex){
		   ex.printStackTrace();
		   System.exit(1);
		}
		return map;
	}
	
	public void addPixel(Point coord, boolean tuile, String atlas, String tex, Point offset, Point modulo, int id){
		pixels.put(coord, new MapPixel(tuile, atlas, tex, offset, modulo, id, "foo"));
	}
	
	public void addPixel(Point coord, MapPixel pixel){
		pixels.put(coord,pixel);
	}
	
	public MapPixel getPixel(Point coord){
		return pixels.get(coord);
	}

	public String getName() {
		return nom.getName();
	}
	
	/**
	 * Si notre pixel est une tuile, on lui applique l'effet de zone.
	 * On récupère le modulo de cette tuile puis on l'applique à ses coordonnées.
	 * On modifie le nom de la tuile du pixel en conséuquence.
	 * @param coord
	 */
	public void setZone(Point coord){
		if (pixels.containsKey(coord)){
			MapPixel pixel = pixels.get(coord);
			if ((pixel.getModulo().x>1 | pixel.getModulo().y>1) & pixel.isTuile()){//Si c'est une tuile
				//System.err.println("Modulo : "+pixel.tex+" "+pixel.modulo.x+","+pixel.modulo.y);
				int resultx = (coord.x % pixel.getModulo().x)+1;//On applique l'effet de zone
				int resulty = (coord.y % pixel.getModulo().y)+1;
				//System.exit(0);
				pixel.setTex(pixel.getTex().substring(0,pixel.getTex().indexOf('(')+1)+resultx+", "+resulty+")");//en remplacant le nom de la texture par le nom tenant compte de la zone.
				System.err.println("Tuile : "+pixels.get(coord).getTex());
			}
		}
	}
	
	public TextureRegion getCell(Point coord){
		TextureRegion tex = null;
		int resultx = 0;
		int resulty = 0;
		if (pixels.containsKey(coord)){
			MapPixel pixel = pixels.get(coord);
			TextureAtlas tile = loadingStatus.getTextureAtlasTile(pixel.getAtlas());
			if ((pixel.getModulo().x>1 | pixel.getModulo().y>1)){//Si c'est une tuile
				//System.err.println("Modulo : "+pixel.tex+" "+pixel.modulo.x+","+pixel.modulo.y);
				resultx = (coord.x % pixel.getModulo().x)+1;//On applique l'effet de zone
				resulty = (coord.y % pixel.getModulo().y)+1;
				//System.exit(0);
				tex = tile.findRegion(pixel.getTex().substring(0,pixel.getTex().indexOf('(')+1)+resultx+", "+resulty+")");//en remplacant le nom de la texture par le nom tenant compte de la zone.
				//System.err.println("Tuile : "+pixels.get(coord).tex+" "+pixel.tex.substring(0,pixel.tex.indexOf('(')+1)+resultx+", "+resulty+")");
			}else{
				tex =tile.findRegion(pixel.getTex());
			}
			if (tex == null){
				//System.err.println("Modulo : "+pixel.tex+" "+pixel.modulo.x+","+pixel.modulo.y);
				resultx = (coord.x % 10)+1;//On applique l'effet de zone
				resulty = (coord.y % 10)+1;
				//System.exit(0);
				tex = tile.findRegion(pixel.getTex().substring(0,pixel.getTex().indexOf('(')+1)+resultx+", "+resulty+")");//en remplacant le nom de la texture par le nom tenant compte de la zone.
				//System.err.println("Tuile : "+pixels.get(coord).tex+" "+pixel.tex.substring(0,pixel.tex.indexOf('(')+1)+resultx+", "+resulty+")");
			}
		}
		return tex;
	}
}
