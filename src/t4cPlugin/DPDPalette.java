package t4cPlugin;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Iterator;

public class DPDPalette {

	/**
	 * PStructurePalette = ^StructurePalette;
  StructurePalette = Record
    Nom : Array [0..63] Of Char;
    Pixels : Array [0..255] Of Record
      Rouge : Byte;
      Vert : Byte;
      Bleu : Byte;
    End;
  End;
	 * @param bufUnZip
	 */
	
	public String nom = "";
	
	ArrayList<Pixel> pixels = new ArrayList<Pixel>();
	
	class Pixel{
		short red;
		short green;
		short blue;
		public Pixel(short b, short c, short d) {
			red = b;
			green = c;
			blue = d;
		}
	}
	
	public DPDPalette(ByteBuffer buf) {
		//new Fast_Forward(buf, 64, false, "DPD");
		byte[] bytes = new byte[64];
		buf.get(bytes);
		nom = new String(bytes);
		nom = nom.substring(0, nom.indexOf(0x00));
		//logger.info("		- Nom : "+nom);
		//new Fast_Forward(buf, 768, false, nom);
		for (int i=0 ; i<256 ; i++){
			short tmp1,tmp2,tmp3;
			byte b;
			b = buf.get();
			tmp1 = tools.ByteArrayToNumber.bytesToShort(new byte[]{0,b});
			b = buf.get();
			tmp2 = tools.ByteArrayToNumber.bytesToShort(new byte[]{0,b});
			b = buf.get();
			tmp3 = tools.ByteArrayToNumber.bytesToShort(new byte[]{0,b});
			pixels.add(new Pixel (tmp1, tmp2, tmp3));
			//pixels.add(new Pixel (buf.get(),buf.get(), buf.get())); 
		}
		//logger.info("		- Nombre de pixels extraits : "+pixels.size());
	}

	public ByteBuffer getPixels() {
		ByteBuffer palette = ByteBuffer.allocate(pixels.size()*3);
		Iterator<Pixel> iter = pixels.iterator();
		while (iter.hasNext()){
			Pixel p = iter.next();
			palette.put((byte)((p.red)&0xFF));
			palette.put((byte)((p.green)&0xFF));
			palette.put((byte)((p.blue)&0xFF));
		}
		palette.rewind();
		return palette;
	}
	
}
