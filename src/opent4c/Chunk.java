package opent4c;

import java.awt.Point;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import opent4c.utils.AssetsLoader;
import opent4c.utils.FilesPath;
import opent4c.utils.LoadingStatus;
import opent4c.utils.PointsManager;
import opent4c.utils.RunnableCreatorUtil;
import opent4c.utils.Smooth;
import opent4c.utils.ThreadsUtil;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import screens.MapManager;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Pixmap.Format;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.TextureData;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureAtlas.AtlasRegion;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Array;


/**
 * This is a chunk, part of a 9 chunks chunkmap.
 * @author synoga
 *
 */
public class Chunk{
	private static Logger logger = LogManager.getLogger(Chunk.class.getSimpleName());
	private static int watcher_delay_ms = 32;
	private static ScheduledFuture<?> watcher;
	private static LoadingStatus loadingStatus = LoadingStatus.INSTANCE;
	private static Map<String, Pixmap> cached_pixmaps = new HashMap<String, Pixmap>();
	public static final Point chunk_size = PointsManager.getPoint(3+Gdx.graphics.getWidth()/16,3+Gdx.graphics.getHeight()/8);
	private static final int SMOOTHING_DELTA = 50000;
	private static final int tileEngineFrequency_µs = 500; // objectif 100 sans perdre de fps
	private static final int spriteEngineFrequency_µs = 500; // objectif 500 sans perdre de fps
	private static final int debugEngineFrequency_ms = 1; // objectif 1 sans perdre de fps
	private static final int smoothEngineFrequency_ms = 50; // objectif 50 sans perdre de fps
	private static final int TILE = 1;
	private static final int SPRITE = 2;
	private static final int DEBUG = 3;
	private static final int SMOOTH = 4;
	private static List<Acteur> engineTileQueue = Collections.synchronizedList(new ArrayList<Acteur>());
	private static List<Acteur> engineSpriteQueue = Collections.synchronizedList(new ArrayList<Acteur>());
	private static List<Acteur> engineDebugQueue = Collections.synchronizedList(new ArrayList<Acteur>());
	private static List<Acteur> engineSmoothQueue = Collections.synchronizedList(new ArrayList<Acteur>());
	private static List<Smooth> smoothQueue = Collections.synchronizedList(new ArrayList<Smooth>());
	private static Point center = null;
	private static int upLimit;
	private static int downLimit;
	private static int leftLimit;
	private static int rightLimit;


////////////////////////////////////////////////////////////////////////////////////
//////////////////////////////////////CREATION//////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////
	
	public Chunk(){}
	
	/**
	 * Creates a new Chunk form a map name and a point on the map
	 * @param map 
	 * @param point
	 */
	public static void newChunk(String map, Point point) {
		loadingStatus.waitUntilTextureAtlasTilesCreated();
		stopChunkMapWatcher();
		setLimits(point);
		for(int y = getUpLimit() ; y <= getDownLimit() ; y++){
			for(int x = getLeftLimit() ; x <= getRightLimit() ; x++){
				int id = MapManager.getIdAtCoordOnMap("v2_worldmap",PointsManager.getPoint(x,y));
				addActeurforIdAndPoint(id,PointsManager.getPoint(x, y));
			}	
		}
		startChunkMapWatcher();
	}

	private static void setLimits(Point point) {
		setCenter(point);
		setUpLimit(point.y-(chunk_size.y/2)-1);
		setDownLimit(point.y+(chunk_size.y/2));
		setLeftLimit(point.x-(chunk_size.x/2)-1);
		setRightLimit(point.x+(chunk_size.x/2));		
	}

	/**
	 * Adds what points from an ID to a point on the map
	 * @param id
	 * @param point
	 */
	public static void addActeurforIdAndPoint(int id, Point point) {
		String atlas = SpriteData.getAtlasFromId(id);
		String tex = SpriteData.getTexFromId(id);
		if (isTileId(id)){
			addTileAtCoord(id, point);
			return;
		}else if(isSmoothingId(id)){
			addSmoothingAtCoord(id, point);
			return;
		}else if(atlas.equals("MIRROR")){
			addMirrorSpriteAtCoord(Integer.parseInt(tex), point);
			return;
		}else{
			addSpriteAtCoord(id, point);
			return;
		}
	}


////////////////////////////////////////////////////////////////////////////////////
//////////////////////////////////////TILES/////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////

	/**
	* Adds a tile on a chunkMap
	* @param id
	* @param point
	*/
	private static void addTileAtCoord(int id, Point point) {
		TextureRegion texRegion = getTileTextureRegionFromIdAndPoint(id, point);
		Acteur act = new Acteur(texRegion,point,PointsManager.getPoint(0, 0));
		act.setZIndex(0);
		addActorToChunkEngine(act,TILE);
	}

////////////////////////////////////////////////////////////////////////////////////
//////////////////////////////////////SMOOTHING/////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////
	
	
	/**
	 * Adds a smoothing tile on the chunk.
	 * @param id
	 * @param point
	 */
	private static void addSmoothingAtCoord(int id, Point point) {
		String smooth_info = SpriteData.getTexFromId(id);
		String tmpl = smooth_info.substring(0, smooth_info.indexOf(" T1"));
		String t1 = smooth_info.substring(smooth_info.indexOf("T1")+3, smooth_info.indexOf(" T2"));
		String t2 = smooth_info.substring(smooth_info.indexOf("T2")+3).trim();
		createSmoothing(tmpl, t1, t2, point);
		ThreadsUtil.executeInGraphicalThread(RunnableCreatorUtil.getSmoothTemplateTileRunnable(tmpl, point));
	}

	
	
	/**
	 * Adds all smoothing effects for a point.
	 * @param tmpl
	 * @param t1
	 * @param t2
	 * @param point
	 */
	public static void createSmoothing(String tmpl, String t1, String t2, final Point point) {
		int color = getSmoothingColor(tmpl);
		Pixmap template = getCachedPixmap(tmpl);
		addSmoothTile1(Integer.parseInt(t1), point);
		addSmoothTile2ToQueue(new Smooth(Integer.parseInt(t2), point, template, color));
		//ThreadsUtil.executeInGraphicalThread(RunnableCreatorUtil.getSmoothTile2Runnable(Integer.parseInt(t2), point, template, color, this));
	}

	/**
	 * Queues tile2 loading not to freeze graphic thread.
	 * @param id
	 * @param point
	 * @param template
	 * @param color
	 */
	private static void addSmoothTile2ToQueue(Smooth smooth) {
		smoothQueue.add(smooth);
	}

	/**
	 * Adds smoothing tile 1 (background).
	 * @param id1
	 * @param point
	 */
	private static void addSmoothTile1(int id1, Point point) {
		TextureRegion tex = getTileTextureRegionFromIdAndPoint(id1, point);
		Acteur act = new Acteur(tex, point, PointsManager.getPoint(0, 0));
		act.setZIndex(0);
		addActorToChunkEngine(act,TILE);
	}
	
	/**
	 * Adds smoothing tile 2 (foreground).
	 * @param id
	 * @param point
	 * @param template
	 * @param color
	 */
	public static void addSmoothingTile2(Smooth smooth) {
		TextureRegion smooth_tex = new TextureRegion(new Texture(getSmoothedTile(smooth)));
		Acteur act = new Acteur(smooth_tex, smooth.getPoint(), PointsManager.getPoint(0, 0));
		act.setZIndex(1);
		addActorToChunkEngine(act, SMOOTH);
	}

	/**
	 * Adds smoothing template.
	 * @param template
	 * @param point
	 */
	public static void addTemplateTile(String tmpl, Point point) {
		Pixmap template = getCachedPixmap(tmpl);
		TextureRegion tmplTex = new TextureRegion(new Texture(template));
		Acteur act = new Acteur(tmplTex, point, PointsManager.getPoint(0, 0));
		act.setZIndex(2);
		act.getColor().a = 0.3f;
		addActorToChunkEngine(act, DEBUG);
	}

	/**
	 * Detects template smoothing color
	 * @param tmpl
	 * @return
	 */
	private static int getSmoothingColor(String tmpl) {
		int result = 0;
		if (tmpl.startsWith("Tmpl3")){
			result = 11010303;
		}else if (tmpl.startsWith("Tmpl1")){
			result = 255;
		}
		return result;
	}

	/**
	 * Creates smoothing tile 2.
	 * @param template
	 * @param id
	 * @param point
	 * @param color
	 * @return
	 */
	private static Pixmap getSmoothedTile(Smooth smooth) {
		TextureRegion tile = getTileTextureRegionFromIdAndPoint(smooth.getId(), smooth.getPoint());
		String atlas = SpriteData.getAtlasFromId(smooth.getId());
		int regionX = tile.getRegionX();
		int regionY = tile.getRegionY();
		FileHandle handle = new FileHandle(FilesPath.getAtlasTuileDirectoryPath()+atlas+"-1.png");
		Pixmap source = new Pixmap(handle);
		Pixmap result = new Pixmap(32, 16, Format.RGBA8888);
		for(int y = 0 ; y < 16 ; y++){
			for(int x = 0 ; x < 32 ; x++){
				int pixel = smooth.getTemplate().getPixel(x, y);
				if(!isPixelColor1(pixel, smooth.getColor()))result.drawPixel(x, y, source.getPixel(x+regionX, y+regionY));
			}
		}
		return result;
	}

	private static boolean isPixelColor1(int pixel, int color) {
		if(pixel > color-SMOOTHING_DELTA && pixel < color+SMOOTHING_DELTA)return true;
		return false;
	}

////////////////////////////////////////////////////////////////////////////////////
//////////////////////////////////////PIXMAPS///////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////
	
	/**
	 * caches all smoothing template pixmaps
	 */
	public static void cacheSmoothingTemplatePixmaps() {
		UpdateDataCheckStatus.setStatus("Mise en cache des templates de smoothing.");
		TextureAtlas tileAtlas = loadingStatus.getTextureAtlasTile("GenericMerge1");
		cacheTemplateAtlas(tileAtlas);
		tileAtlas = loadingStatus.getTextureAtlasTile("GenericMerge3");
		cacheTemplateAtlas(tileAtlas);
		UpdateDataCheckStatus.setStatus("Mise en cache terminée.");
	}

	/**
	 * puts a whole atlas into pixmap cache
	 * @param tileAtlas
	 */
	private static void cacheTemplateAtlas(TextureAtlas tileAtlas) {
		Array<AtlasRegion> tiles = tileAtlas.getRegions();
		Iterator<AtlasRegion> iter = tiles.iterator();
		while(iter.hasNext()){
			cachePixmap(iter.next());
		}		
	}

	/**
	 * puts a Pixmap into cache <name,pixmap>
	 * @param tile
	 */
	private static void cachePixmap(AtlasRegion tile) {
		int regionX = tile.getRegionX();
		int regionY = tile.getRegionY();
		//logger.info("ATLAS : "+atlas+" TEX : "+tmpl+" XY : "+regionX+";"+regionY);
		Texture tileTex = tile.getTexture();
		TextureData txData = tileTex.getTextureData();
		txData.prepare();
		Pixmap px = txData.consumePixmap();
		Pixmap result = new Pixmap(32, 16, Format.RGBA8888);
		for(int y = 0 ; y < 16 ; y++){
			for(int x = 0 ; x < 32 ; x++){
				result.drawPixel(x, y, px.getPixel(x+regionX, y+regionY));
			}
		}
		cached_pixmaps.put(tile.name, result);
	}

	/**
	 * Gets a smoothing template pixmap from a name
	 * @param tmpl
	 * @return
	 */
	private static Pixmap getCachedPixmap(String tmpl) {
		return cached_pixmaps.get(tmpl);
	}

////////////////////////////////////////////////////////////////////////////////////
//////////////////////////////////////SPRITES///////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////
	
	/**
	 * Adds a Sprite on a chunkMap
	 * @param id
	 * @param point
	 */
	private static void addSpriteAtCoord(int id, Point point) {
		MapPixel px = SpriteData.getPixelFromId(id);
		if (px == null){
			//logger.warn("Not Present in pixel_index : "+id+" => "+point.x+";"+point.y);
			addUnknownTile(point);
			return;
		}
		TextureRegion texRegion = getSpriteTextureRegionFromIdAndPoint(id, point);
		Acteur act = new Acteur(texRegion, point, px.getOffset());
		act.setZIndex((point.y));
		addActorToChunkEngine(act, SPRITE);
		int id2 = MapManager.getIdAtCoordOnMap("v2_worldmap",PointsManager.getPoint(point.x, point.y+1));
		if (isTileId(id2)) {
			addTileAtCoord(id2, point);
			addAnchorTile(point);
		}
	}


////////////////////////////////////////////////////////////////////////////////////
//////////////////////////////////////MIRRORS///////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////
	
	private static void addMirrorSpriteAtCoord(int id, final Point point) {
		MapPixel px = SpriteData.getPixelFromId(id);
		if (px == null){
			logger.warn("Not Present in pixel_index : "+id+" => "+point.x+";"+point.y);
			addUnknownTile(point);
			return;
		}
		TextureRegion texRegion = getSpriteTextureRegionFromIdAndPoint(id, point);
		Sprite sp = new Sprite(texRegion);
		sp.flip(true, false);
		Acteur mir = new Acteur(sp, point, px.getOffset2());
		mir.setZIndex((point.y));
		addActorToChunkEngine(mir, SPRITE);
		int id2 = MapManager.getIdAtCoordOnMap("v2_worldmap",PointsManager.getPoint(point.x, point.y+1));
		if (isTileId(id2)) {
			addTileAtCoord(id2, point);
			addAnchorTile(point);
		}
	}

////////////////////////////////////////////////////////////////////////////////////
//////////////////////////////////////UTILS/////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////	
	
	/**
	 * Starts the chunk loader engine
	 */
	public static void startChunkEngine(){
		logger.info("Démarrage de la chunkEngine");
		ThreadsUtil.executePeriodicallyInThread(RunnableCreatorUtil.getChunkMapTileEngineRunnable(), 0, tileEngineFrequency_µs, TimeUnit.MICROSECONDS);
		ThreadsUtil.executePeriodicallyInThread(RunnableCreatorUtil.getChunkMapSpriteEngineRunnable(), 0, spriteEngineFrequency_µs, TimeUnit.MICROSECONDS);
		ThreadsUtil.executePeriodicallyInThread(RunnableCreatorUtil.getChunkMapDebugEngineRunnable(), 0, debugEngineFrequency_ms, TimeUnit.MILLISECONDS);
		ThreadsUtil.executePeriodicallyInThread(RunnableCreatorUtil.getChunkMapSmoothEngineRunnable(), 0, smoothEngineFrequency_ms, TimeUnit.MILLISECONDS);
		ThreadsUtil.executePeriodicallyInThread(RunnableCreatorUtil.getChunkMapSmoothQueueRunnable(), 0, smoothEngineFrequency_ms, TimeUnit.MILLISECONDS);
		ThreadsUtil.executePeriodicallyInThread(RunnableCreatorUtil.getChunkMapCleanEngineRunnable(), 0, 1, TimeUnit.SECONDS);
	}
	
	private static void addActorToChunkEngine(Acteur act, int type) {
		switch(type){
		case TILE :
			engineTileQueue.add(act);
			break;
		case SPRITE : 
			engineSpriteQueue.add(act);
			break;
		case DEBUG : 
			engineDebugQueue.add(act);
			break;
		case SMOOTH : 
			engineSmoothQueue.add(act);
			break;
		}
	}
	
	/**
	 * Gets a tile TextureRegion from a point and an ID.
	 * @param id
	 * @param point
	 * @return
	 */
	private static TextureRegion getTileTextureRegionFromIdAndPoint(int id, Point point) {
		TextureRegion texRegion = null;
		TextureAtlas texAtlas = null;
		String atlas = SpriteData.getAtlasFromId(id);
		if (atlas.equals("Atlas non mappé") | atlas.equals("NA")){
			logger.warn(id+" => "+atlas+" @ "+point.x+";"+point.y);
			return getUnknownTile();
		}
		texAtlas = loadingStatus.getTextureAtlasTile(atlas);
		if(texAtlas == null){
			texAtlas = AssetsLoader.load(atlas);
			if(texAtlas == null){
					logger.warn("Atlas missing : "+id+" => "+atlas+" @ "+point.x+";"+point.y);
					return getUnknownTile();
			}
		}
		texRegion = texAtlas.findRegion(getModuledTexNameFromPoint(atlas, id, point));
		if(texRegion == null){
			logger.warn("TextureRegion missing : "+id+" => "+atlas+" : "+getModuledTexNameFromPoint(atlas, id, point)+" @ "+point.x+";"+point.y);
			return getUnknownTile();
		}
		return texRegion;
	}
	
	/**
	 * Gets a sprite TextureRegion from a point and an ID.
	 * @param id
	 * @param point
	 * @return
	 */
	private static TextureRegion getSpriteTextureRegionFromIdAndPoint(int id, Point point) {
		String atlas = SpriteData.getAtlasFromId(id);
		String tex = SpriteData.getTexFromId(id);
		if (atlas.equals("Atlas non mappé") | atlas.equals("NA")){
			//logger.warn(id+" => "+atlas+" : "+tex+" @ "+point.x+";"+point.y);
			return getUnknownTile();
		}
		TextureAtlas texAtlas = loadingStatus.getTextureAtlasSprite(atlas);			
		if(texAtlas == null){
			texAtlas = AssetsLoader.load(atlas);
			if(texAtlas == null){
				//logger.warn("Atlas missing : "+id+" => "+atlas+" : "+tex+" @ "+point.x+";"+point.y);
				return getUnknownTile();
			}
		}
		TextureRegion texRegion = texAtlas.findRegion(tex);
		if(texRegion == null){
			//logger.warn("TextureRegion missing : "+id+" => "+atlas+" : "+tex+" @ "+point.x+";"+point.y);
			return getUnknownTile();
		}
		return texRegion;
	}
	
	/**
	 * Adds an anchor tile from a point
	 * @param point
	 */
	private static void addAnchorTile(Point point) {
		Acteur act = new Acteur(getAnchorTile(),PointsManager.getPoint(point.x, point.y),PointsManager.getPoint(0, 0));
		act.setZIndex(100000);
		addActorToChunkEngine(act, DEBUG);
	}
	
	/**
	 * True if ID is smoothing.
	 * @param id
	 * @return
	 */
	private static boolean isSmoothingId(int id) {
		String atlas = SpriteData.getAtlasFromId(id);
		if (!atlas.equals("NA"))return false;
		String tex = SpriteData.getTexFromId(id);
		if (!(tex.startsWith("Tmpl3 ") || tex.startsWith("Tmpl1 ")))return false;
		if(tex.contains("Tmpl") && tex.contains("T1") && tex.contains("T2"))return true;
		return false;
	}

	/**
	 * True if ID is a tile
	 * @param id
	 * @return
	 */
	private static boolean isTileId(int id) {
		if(SpriteData.getTexFromId(id).startsWith("modulos(")) return true;
		return false;
	}

	/**
	 * Adds an Unknown Tile on a point.
	 * @param point
	 */
	private static void addUnknownTile(Point point) {
		Acteur act = new Acteur(getUnknownTile(),PointsManager.getPoint(point.x, point.y),PointsManager.getPoint(0, 0));
		act.setZIndex(100000);
		addActorToChunkEngine(act, DEBUG);
	}
		
	/**
	 * @return the unknown atlas
	 */
	public static TextureAtlas getUtilsAtlas() {
		return loadingStatus.getTextureAtlasSprite("Utils");
	}

	/**
	 * @param moduloY2 
	 * @param moduloX 
	 * @param px
	 * @param point
	 * @return a textureRegion name with zone effect
	 */
	public static String getModuledTexNameFromPoint(String atlas, int id, Point point) {
		String tx = SpriteData.getTexFromId(id);
		int moduloX = Integer.parseInt(tx.substring(tx.indexOf('(')+1,tx.indexOf(',')));
		int moduloY = Integer.parseInt(tx.substring(tx.indexOf(',')+1,tx.indexOf(')')));
		return atlas+" ("+((point.x % moduloX)+1)+", "+((point.y % moduloY)+1)+")";	
	}

	/**
	 * @return the chunk's center Point
	 */
	public static Point getCenter() {
		return center;
	}

	/**
	 * Sets the Chunk's center Point
	 * @param center
	 */
	public static void setCenter(Point point) {
		center = point;
	}
	
	/**
	 * @return the "Unknown" tile
	 */
	private static TextureRegion getUnknownTile(){
		TextureAtlas texAtlas = getUtilsAtlas();
		return texAtlas.findRegion("Unknown");
	}
	
	/**
	 * @return the "Anchor" tile
	 */
	private static TextureRegion getAnchorTile(){
		TextureAtlas texAtlas = getUtilsAtlas();
		return texAtlas.findRegion("Anchor");
	}
	
	/**
	 * Starts a clock which will check the camera's position on chunk so that it can
	 * move the chunkMap if needed.
	 */
	public static void startChunkMapWatcher() {
		//logger.warn("Démarrage du ChunkWatcher");
		Runnable r = RunnableCreatorUtil.getChunkMapWatcherRunnable();
		watcher = ThreadsUtil.executePeriodicallyInThread(r, watcher_delay_ms, watcher_delay_ms, TimeUnit.MILLISECONDS);
	}

	/**
	 * Stops the clock checking if chunks need to be moved.
	 */
	public static void stopChunkMapWatcher() {
		//logger.warn("Arrêt du ChunkWatcher.");
		if (watcher != null) watcher.cancel(true);
	}

	/**
	 * Moves chunks to given direction (numpad directions)
	 */
	public static void move(int direction){
		switch(direction){
		case 1 :
			moveDown();
			moveLeft();
			break;
		case 2 :
			moveDown();
			break;
		case 3 :
			moveDown();
			moveRight();
			break;
		case 4 :
			moveLeft();
			break;
		case 6 :
			moveRight();
			break;
		case 7 :
			moveUp();
			moveLeft();
			break;
		case 8 :
			moveUp();
			break;
		case 9 :
			moveUp();
			moveRight();
			break;
		}
	}

	private static void moveRight() {
		Point newCenter = PointsManager.getPoint(getCenter().x+1,getCenter().y);
		setLimits(newCenter);
		//logger.info("Camera Moving to : "+newCenter.x+";"+newCenter.y);
		for(int y = getUpLimit() ; y <= getDownLimit() ; y++){
			int id = MapManager.getIdAtCoordOnMap("v2_worldmap",PointsManager.getPoint(getRightLimit(), y));
			addActeurforIdAndPoint(id, PointsManager.getPoint(getRightLimit(), y));
		}
	}
	
	private static void moveLeft() {
		Point newCenter = PointsManager.getPoint(getCenter().x-1,getCenter().y);
		setLimits(newCenter);
		//logger.info("Camera Moving to : "+newCenter.x+";"+newCenter.y);
		for(int y = getUpLimit() ; y <= getDownLimit() ; y++){
			int id = MapManager.getIdAtCoordOnMap("v2_worldmap",PointsManager.getPoint(getLeftLimit(), y));
			addActeurforIdAndPoint(id, PointsManager.getPoint(getLeftLimit(), y));
		}
	}
	
	private static void moveUp() {
		Point newCenter = PointsManager.getPoint(getCenter().x,getCenter().y-1);
		setLimits(newCenter);
		//logger.info("Camera Moving to : "+newCenter.x+";"+newCenter.y);
		for(int x = getLeftLimit() ; x <= getRightLimit() ; x++){
			int id = MapManager.getIdAtCoordOnMap("v2_worldmap",PointsManager.getPoint(x, getUpLimit()));
			addActeurforIdAndPoint(id, PointsManager.getPoint(x, getUpLimit()));
		}
	}

	private static void moveDown() {
		Point newCenter = PointsManager.getPoint(getCenter().x,getCenter().y+1);
		setLimits(newCenter);
		//logger.info("Camera Moving to : "+newCenter.x+";"+newCenter.y);
		for(int x = getLeftLimit() ; x <= getRightLimit() ; x++){
			int id = MapManager.getIdAtCoordOnMap("v2_worldmap",PointsManager.getPoint(x, getDownLimit()));
			addActeurforIdAndPoint(id, PointsManager.getPoint(x, getDownLimit()));
		}
	}
	

	public static boolean isPointInChunk(Point point){
		if(point.x < getLeftLimit()) return false;
		if(point.x > getRightLimit()) return false;
		if(point.y < getUpLimit()) return false;
		if(point.y > getDownLimit()) return false;
		return true;
	}
	
	public static int getRightLimit() {
		return rightLimit;
	}

	public static void setRightLimit(int limit) {
		rightLimit = limit;
	}

	public static int getDownLimit() {
		return downLimit;
	}

	public static void setDownLimit(int limit) {
		downLimit = limit;
	}

	public static int getLeftLimit() {
		return leftLimit;
	}

	public static void setLeftLimit(int limit) {
		leftLimit = limit;
	}

	public static int getUpLimit() {
		return upLimit;
	}

	public static void setUpLimit(int limit) {
		upLimit = limit;
	}

	public static void loadLastFromTileQueueIfNeeded() {
		if(engineTileQueue.size() != 0){
			//logger.info("Tile load : "+engineTileQueue.size());
			Acteur to_load = engineTileQueue.get(0);
			ThreadsUtil.executeInGraphicalThread(RunnableCreatorUtil.getChunkMapEngineAddToTilesRunnable(to_load));
			engineTileQueue.remove(0);
		}
	}
	
	public static void loadLastFromSpriteQueueIfNeeded() {
		if(engineSpriteQueue.size() != 0){
			//logger.info("Sprite load : "+engineSpriteQueue.size());
			Acteur to_load = engineSpriteQueue.get(0);
			ThreadsUtil.executeInGraphicalThread(RunnableCreatorUtil.getChunkMapEngineAddToSpritesRunnable(to_load));
			engineSpriteQueue.remove(0);
		}
	}

	public static void loadLastFromDebugQueueIfNeeded() {
		if(engineDebugQueue.size() != 0){
			//logger.info("Template load : "+engineTemplateQueue.size());
			Acteur to_load = engineDebugQueue.get(0);
			ThreadsUtil.executeInGraphicalThread(RunnableCreatorUtil.getChunkMapEngineAddToDebugRunnable(to_load));
			engineDebugQueue.remove(0);
		}
	}

	public static void loadLastFromSmoothEngineQueueIfNeeded() {
		if(engineSmoothQueue.size() != 0){
			//logger.info("Template load : "+engineTemplateQueue.size());
			Acteur to_load = engineSmoothQueue.get(0);
			ThreadsUtil.executeInGraphicalThread(RunnableCreatorUtil.getChunkMapEngineAddToSmoothRunnable(to_load));
			engineSmoothQueue.remove(0);
		}
	}

	public static void loadLastFromSmoothQueueIfNeeded() {
		if(smoothQueue.size() != 0){
			//logger.info("Template load : "+engineTemplateQueue.size());
			Smooth to_load = smoothQueue.get(0);
			ThreadsUtil.executeInGraphicalThread(RunnableCreatorUtil.getChunkMapSmoothQueueAddToSmoothRunnable(to_load));
			smoothQueue.remove(0);
		}
	}

	public static void addSmoothToSmoothEngine(Smooth to_load) {
		addSmoothingTile2(to_load);
	}

	public static String getEngineInfos() {
		StringBuilder sb = new StringBuilder();
		sb.append(" Queue sizes : Sprite/Smooth/Debug/Tile : ");
		sb.append(engineSpriteQueue.size());
		sb.append("/");		
		sb.append(smoothQueue.size());
		sb.append("/");
		sb.append(engineDebugQueue.size());
		sb.append("/");
		sb.append(engineTileQueue.size());
		
		return sb.toString();
	}
}
