package t4cPlugin;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

import opent4c.utils.SpriteName;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import tools.DataInputManager;

public class DID {

	private static Logger logger = LogManager.getLogger(DID.class.getSimpleName());
	
/**
  
  StructureDid = Record
    HashMd5 : Array [0..15] Of Byte;
    TailleDecompressee : LongInt;
    TailleCompressee : LongInt;
    HashMd52 : Array [0..16] Of Byte;
    NomsSprites : Array Of StructureNomsSprites;
    CheckSum : Byte;
    NbEntree : LongInt;
  End;*/
	private ByteBuffer buf;
	private ByteBuffer header;
	private ByteBuffer bufUnZip;
	
	private byte clef = (byte) 0x99;
	
	
	private byte[] header_hashMd5 = new byte[16];
	private byte[] header_hashMd52 = new byte[17];


	//TODO Mauvais! Des variables internes ne doivent être utilisées à l'extérieur de la classe qu'avec des getter/setter!
	private static List<Sprite> sprites = new ArrayList<Sprite>();
	
	private int header_taille_unZip;
	private int header_taille_zip;
	private int taille_unZip;
	private int nb_sprites;
	//TODO pourquoi stocker ici le sprite black? Il n'est utilisé nul part...
	private static Sprite black;
	private static Map<Integer,SpriteName> ids = new HashMap<Integer,SpriteName>();
	private static Map<Integer,Sprite> sprites_with_ids = new HashMap<Integer,Sprite>();
	private static Map<Integer,Sprite> sprites_without_ids = new HashMap<Integer,Sprite>();


	public void decrypt(File f){
		Params.STATUS = "Lecture du fichier "+f.getName();
		logger.info("Lecture du fichier "+f.getName());
		//d'abord on récupère les ID depuis notre fichier
		//et on formate tout ça pour avoir une hashmap <int, string>
		String filePath = Params.IDS;
		try{
			BufferedReader buff = new BufferedReader(new FileReader(filePath));
			 
			try {
				String line;
				while ((line = buff.readLine()) != null) {
					int key = 0;
					String value = "";
					key = Integer.parseInt(line.substring(0, line.indexOf(' ')));
					value = line.substring(line.indexOf("Name: ")+6);
					SpriteName name = new SpriteName(value);
					ids.put(key,name);
					Params.STATUS = "ID mappée : "+key+"=>"+name.getName();
					logger.info("ID mappée : "+key+"=>"+name.getName());
				}
			} finally {
				buff.close();
			}
		} catch (IOException ioe) {
			ioe.printStackTrace();System.exit(1);
		}
		byte b1,b2,b3,b4;
		header = ByteBuffer.allocate(41);
		try {
			DataInputManager in = new DataInputManager (f);
			while (header.position()<header.capacity()){
				header.put(in.readByte());
			}
			header.rewind();
			for (int i=0 ; i<16 ; i++){
				header_hashMd5[i] = header.get();
			}
			//logger.info("	- HashMD5 : "+tools.ByteArrayToHexString.print(header_hashMd5));
			b1 = header.get();
			b2 = header.get();
			b3 = header.get();
			b4 = header.get();
			header_taille_unZip = tools.ByteArrayToNumber.bytesToInt(new byte[]{b4,b3,b2,b1});
			//logger.info("	- header_taille_unZip : "+header_taille_unZip);
			b1 = header.get();
			b2 = header.get();
			b3 = header.get();
			b4 = header.get();
			header_taille_zip = tools.ByteArrayToNumber.bytesToInt(new byte[]{b4,b3,b2,b1});
			//logger.info("	- header_taille_zip : "+header_taille_zip);
			for (int i=0 ; i<17 ; i++){
				header_hashMd52[i] = header.get();
			}
			//logger.info("	- HashMD52 : "+tools.ByteArrayToHexString.print(header_hashMd52));
			//logger.info("	- Taille header : "+header.position());

			buf = ByteBuffer.allocate(header_taille_zip);
			while (buf.position() < buf.capacity()){
				buf.put(in.readByte());
			}
			in.close();
		}catch(IOException exc){
			System.err.println("Erreur d'ouverture");
			exc.printStackTrace();
		}
		buf.rewind();
		
		//On opère la décompression Zlib
	     Inflater decompresser = new Inflater();
	     decompresser.setInput(buf.array(), 0, buf.capacity());
	     bufUnZip = ByteBuffer.allocate(header_taille_unZip);
	     try {
			taille_unZip = decompresser.inflate(bufUnZip.array());
		} catch (DataFormatException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	     decompresser.end();
		//logger.info("	- Taille décompressée : "+taille_unZip+"/"+header_taille_zip);

		//Ensuite on décrypte le fichier avec la clé
		for (int i=0; i<bufUnZip.capacity(); i++){
			bufUnZip.array()[i] ^= clef;
		}
		nb_sprites = taille_unZip/(64 + 256 + 4 + 8);
		//logger.info("	- Nombre de sprites : "+nb_sprites);

		for(int i=0 ; i<nb_sprites ; i++){
			//logger.info("Ajout du sprite "+i+"/"+nb_sprites);
			sprites.add(new Sprite(bufUnZip));
		}
	}
	
	public static Sprite getBlack() {
		return black;
	}


	public static void setBlack(Sprite black) {
		DID.black = black;
	}


	public static Map<Integer, SpriteName> getIds() {
		return ids;
	}


	public static void setIds(Map<Integer, SpriteName> ids) {
		DID.ids = ids;
	}


	public static Map<Integer, Sprite> getSprites_with_ids() {
		return sprites_with_ids;
	}


	public static void setSprites_with_ids(Map<Integer, Sprite> sprites_with_ids) {
		DID.sprites_with_ids = sprites_with_ids;
	}


	public static Map<Integer, Sprite> getSprites_without_ids() {
		return sprites_without_ids;
	}


	public static void setSprites_without_ids(
			Map<Integer, Sprite> sprites_without_ids) {
		DID.sprites_without_ids = sprites_without_ids;
	}
}
