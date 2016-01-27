/**
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 3
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 *
 */

package autosaveworld.features.worldregen.storage;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;

public class AnvilRegion {

	private final File regionfolder;
	private final int columnX;
	private final int columnZ;
	public AnvilRegion(File regionfolder, String filename) throws Throwable {
		this.regionfolder = regionfolder;
		String[] split = filename.split("[.]");
		if (split.length != 4) {
			throw new IllegalArgumentException("Not a region file");
		}
		this.columnX = Integer.parseInt(split[1]);
		this.columnZ = Integer.parseInt(split[2]);
	}

	private final int[] timestamps = new int[1024];
	private final HashMap<Coord, ChunkInfo> chunks = new HashMap<Coord, ChunkInfo>();

	public int getX() {
		return columnX;
	}

	public int getZ() {
		return columnZ;
	}

	public List<Coord> getChunks() {
		return new ArrayList<Coord>(chunks.keySet());
	}

	public void removeChunk(Coord chunkcoord) {
		chunks.remove(chunkcoord);
	}

	public void loadFromDisk() throws IOException {
		File regionfile = new File(regionfolder, "r." + Integer.toString(columnX) + "." + Integer.toString(columnZ) + ".mca");
		if (!regionfile.exists()) {
			return;
		}
		RandomAccessFile raf = new RandomAccessFile(regionfile, "rw");
		if (raf.length() < 8096) {
			raf.close();
			return;
		}
		int[] locations = new int[1024];
		for (int i = 0; i < locations.length; i++) {
			locations[i] = raf.readInt();
		}
		for (int i = 0; i < timestamps.length; i++) {
			timestamps[i] = raf.readInt();
		}
		for (int z = 0; z < 32; z++) {
			for (int x = 0; x < 32; x++) {
				int location = locations[x + z * 32];
				if (location != 0) {
					raf.seek((location >> 8) * 4096);
					int reallength = raf.readInt();
					int compressiontype = raf.readByte();
					byte[] data = new byte[reallength - 1];
					raf.read(data);
					Coord coord = new Coord(x, z);
					chunks.put(coord, new ChunkInfo(compressiontype, data));
				}
			}
		}
		raf.close();
	}

	public void saveToDisk() throws IOException {
		File regionfile = new File(regionfolder, "r." + Integer.toString(columnX) + "." + Integer.toString(columnZ) + ".mca");
		if (chunks.isEmpty()) {
			delete();
			return;
		}
		if (!regionfile.exists()) {
			regionfile.createNewFile();
		}
		RandomAccessFile raf = new RandomAccessFile(regionfile, "rw");
		ArrayList<ChunkInfo> chunkbuffers = new ArrayList<ChunkInfo>();
		int[] locations = new int[1024];
		int currentoffset = 2;
		for (Entry<Coord, ChunkInfo> entry : chunks.entrySet()) {
			chunkbuffers.add(entry.getValue());
			int newsize = getBlocks(entry.getValue().data.length);
			locations[entry.getKey().getX() + entry.getKey().getZ() * 32] = ((currentoffset << 8) | newsize);
			currentoffset += newsize;
		}
		for (int location : locations) {
			raf.writeInt(location);
		}
		for (int timestamp : timestamps) {
			raf.writeInt(timestamp);
		}
		for (ChunkInfo info : chunkbuffers) {
			raf.writeInt(info.data.length + 1);
			raf.writeByte(info.compressiontype);
			raf.write(info.data);
			byte[] empty = new byte[getBlocks(info.data.length) * 4096 - 5 - info.data.length];
			raf.write(empty);
		}
		raf.close();
	}

	public void delete() {
		File regionfile = new File(regionfolder, "r." + Integer.toString(columnX) + "." + Integer.toString(columnZ) + ".mca");
		regionfile.delete();	
	}

	private int getBlocks(int chunkdatasize) {
		int chunkdatawithsize = chunkdatasize + 5;
		if ((chunkdatawithsize & 4095) == 0) {
			return chunkdatawithsize / 4096;
		}
		return (chunkdatawithsize / 4096) + 1;
	}

	private static class ChunkInfo {
		private int compressiontype;
		private byte[] data;

		public ChunkInfo(int compressiontype, byte[] data) {
			this.compressiontype = compressiontype;
			this.data = data;
		}
	}

}