package t4cPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import t4cPlugin.utils.FilesPath;
import t4cPlugin.utils.LoadingStatus;
import t4cPlugin.utils.RunnableCreatorUtil;
import t4cPlugin.utils.ThreadsUtil;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.tools.texturepacker.TexturePacker.Settings;

/**
 * C'est la classe qui gère la création et le chargement des ressources externes (Atlas pour le moment)
 * Les musiques seront chargées ici aussi.
 * @author synoga
 *
 */
public enum AssetsLoader {
	
	INSTANCE;
	
	private static Logger logger = LogManager.getLogger(AssetsLoader.class.getSimpleName());
	
	private static LoadingStatus loadingStatus = LoadingStatus.INSTANCE;
	
	/**
	 * On empacte les sprites dans des atlas.
	 * Pour retrouver plus tard les ressources graphiques,
	 * il faut chercher un atlas correspondant au nom de dossier,
	 * puis une région d'atlas correspondant au nom du sprite.
	 */
	public static void pack_sprites(){
		Settings settings = new Settings();
		settings.pot = false;
		settings.maxWidth = 1284;
		settings.maxHeight = 772;
		settings.rotation = false;
		settings.ignoreBlankImages = false;
		settings.edgePadding = false;
		settings.flattenPaths = true;
		settings.grid = true;
		settings.limitMemory = true;

		FileLister explorer = new FileLister();
		List<File> sprites = new ArrayList<File>();
		sprites.addAll(explorer.listerDir(new File(FilesPath.getSpritePath())));
		Iterator<File> iter_sprites = sprites.iterator();
		String last ="";
		while (iter_sprites.hasNext()){
			final File f = iter_sprites.next();
			final File at = new File(FilesPath.getAtlasSpritesFilePath(f.getName()));
			Params.STATUS = "Pack Sprites : "+at.getName();
			if (!f.getName().equals(last) & f.isDirectory() & !at.exists()){
				loadingStatus.addSpritesAtlasToPackage(f.getName());
				executeSpritePacking(f, settings);
				last = f.getName();
			}
		}
	}
	
	private static void executeSpritePacking(File f, Settings s) {
		Runnable r = RunnableCreatorUtil.getSpritePackerRunnable(f, s);
		ThreadsUtil.executeInThread(r);
	}
	
	/**
	 * On empacte les tuiles dans des atlas.
	 * Pour retrouver plus tard les ressources graphiques,
	 * il faut chercher un atlas correspondant au nom de dossier,
	 * puis une région d'atlas correspondant au nom de la tuile.
	 */
	public static void pack_tuiles(){
		Settings settings = new Settings();
		settings.pot = false;
		settings.maxWidth = 1152;
		settings.maxHeight = 640;
		settings.rotation = false;
		settings.ignoreBlankImages = false;
		settings.edgePadding = false;
		settings.flattenPaths = true;
		settings.grid = true;
		settings.limitMemory = true;

		FileLister explorer = new FileLister();
		List<File> tuiles = new ArrayList<File>();
		tuiles.addAll(explorer.listerDir(new File(FilesPath.getTuilePath())));
		Iterator<File> iter_tuiles = tuiles.iterator();
		String last ="";
		while (iter_tuiles.hasNext()){
			File f = iter_tuiles.next();
			final File at = new File(FilesPath.getAtlasTilesFilePath(f.getName()));
			Params.STATUS = "Pack Tuiles : "+at.getName();
			if (!f.getName().equals(last) & f.isDirectory() & !at.exists()){
				loadingStatus.addTilesAtlasToPackage(f.getName());
				executeTuilesPacking(f, settings);
			}
			last = f.getName();
		}
	}
	
	private static void executeTuilesPacking(File f, Settings settings) {
		Runnable r = RunnableCreatorUtil.getTuilePackerRunnable(f, settings);
		ThreadsUtil.executeInThread(r);
	}
	
	/**
	 * On fait une liste de nos atlas de sprites, et on les charge tous.
	 */
	public static void loadSprites(){
		logger.info("LoadSprites");
		
		loadingStatus.waitUntilSpritesPackaged();
		
		FileLister explorer = new FileLister();
		List<File> spritlas = new ArrayList<File>();
		spritlas.addAll(explorer.lister(new File(FilesPath.getAtlasSpritePath()), ".atlas"));
		Iterator<File> iter_spritlas = spritlas.iterator();
		while(iter_spritlas.hasNext()){
			loadingStatus.addOneSpriteAtlas();
			final String name = iter_spritlas.next().getName();
			Gdx.app.postRunnable(RunnableCreatorUtil.getTextureAtlasSpriteCreatorRunnable(name));		
		}
	}
	
	/**
	 * On cherche un atlas de sprites en particulier, puis on le charge
	 * @param name : nom de l'atlas recherché.
	 * @return : l'atlas chargé.
	 */
	public static TextureAtlas load(final String name){
		logger.info("Loading Sprite Atlas : " +name);
		loadingStatus.addOneSpriteAtlas();
		Gdx.app.postRunnable(RunnableCreatorUtil.getForceTextureAtlasSpriteCreatorRunnable(name));		
		TextureAtlas ta = loadingStatus.waitForTextureAtlasSprite(name);
		logger.info("Sprite Atlas : " +name+" loaded.");
		return ta;
	}
	
	/**
	 * On fait une liste de nos atlas de tuiles, puis on les charge.
	 */
	public static void loadSols() {
		logger.info("LoadSols");
		
		//Ensure tiles are packaged before try to use them
		loadingStatus.waitUntilTilesPackaged();
		
		List<File> tuilas = new ArrayList<File>();
		
		FileLister explorer = new FileLister();
		tuilas.addAll(explorer.lister(new File(FilesPath.getAtlasTuilePath()), ".atlas"));
		//Keep the number of tiles' atlas. Will be used to know if all atlas are processed.
		//TODO use the same method than sprite
		loadingStatus.setNbTilesAs(tuilas.size());
		
		Iterator<File> iter_tuilas = tuilas.iterator();
		while(iter_tuilas.hasNext()){
			final String name = iter_tuilas.next().getName();
			Gdx.app.postRunnable(RunnableCreatorUtil.getTextureAtlasTileCreatorRunnable(name));
		}
//		logger.info("LoadSols OK");
	}
	
	/**
	 * Dans libGDX on doit se débarasser manuellement d'un certain nombre d'objets.
	 * une liste se trouve sur le wiki de libGDX.
	 */
	public static void dispose(){
		//TODO ensure all elements are loaded and no thread will update a map during the iteration
		Iterator<TextureAtlas> iter_tuiles = loadingStatus.getTexturesAtlasTiles().iterator();
		while(iter_tuiles.hasNext()){
			iter_tuiles.next().dispose();
		}
		Iterator<TextureAtlas> iter_sprites = loadingStatus.getTexturesAtlasSprites().iterator();
		while(iter_sprites.hasNext()){
			iter_sprites.next().dispose();
		}
	}

}
