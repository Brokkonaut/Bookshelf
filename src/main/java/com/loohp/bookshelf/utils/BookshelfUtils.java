package com.loohp.bookshelf.utils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;
import org.yaml.snakeyaml.external.biz.base64Coder.Base64Coder;

import com.loohp.bookshelf.Bookshelf;
import com.loohp.bookshelf.BookshelfManager;

public class BookshelfUtils {
	
	public static void safeRemoveBookself(String key) {
		Inventory remove = Bookshelf.keyToContentMapping.get(key);
		for (Player player : Bukkit.getOnlinePlayers()) {
			if (player.getOpenInventory() != null) {
				if (player.getOpenInventory().getTopInventory() != null) {
					Inventory viewing = player.getOpenInventory().getTopInventory();
					if (remove.equals(viewing)) {
						Bukkit.getScheduler().runTask(Bookshelf.plugin, () -> player.closeInventory());
					}
				}
			}
		}
		Bookshelf.removeBookshelfFromMapping(key);
		BookshelfManager.removeShelf(key);
	}
	
	public static List<Block> getAllBookshelvesInChunk(Chunk chunk) {
		List<Block> list = new ArrayList<Block>();
		for (int x = 0; x < 16; x++) {
			for (int z = 0; z < 16; z++) {
				for (int y = 0; y < 256; y++) {
					Block block = chunk.getBlock(x, y, z);
					if (block.getType().equals(Material.BOOKSHELF)) {
						list.add(block);
					}
				}
			}
		}
		return list;
	}
	
	public static List<String> getAllBookshelvesInChunk(ChunkSnapshot chunk) {
		List<String> list = new ArrayList<String>();
		for (int x = 0; x < 16; x++) {
			for (int z = 0; z < 16; z++) {
				for (int y = 0; y < 256; y++) {
					if (chunk.getBlockType(x, y, z).equals(Material.BOOKSHELF));
					int xExact = x + (chunk.getX() * 16);
					int zExact = z + (chunk.getZ() * 16);
					String key = chunk.getWorldName() + "_" + xExact + "_" + y + "_" + zExact;
					list.add(key);
				}
			}
		}
		return list;
	}
	
	public static void loadBookShelf(String loc){
		String hash = BookshelfManager.getInventoryHash(loc);
		Inventory inv = null;
		String bsTitle = BookshelfManager.getTitle(loc);
		try {
			inv = BookshelfUtils.fromBase64(hash, bsTitle);
		} catch (IOException e) {
			e.printStackTrace();
		}
		Bookshelf.addBookshelfToMapping(loc, inv);
	}
	
	public static void saveBookShelf(String loc, boolean remove){
		Inventory inv = Bookshelf.keyToContentMapping.get(loc);
		String hash = "";
		hash = BookshelfUtils.toBase64(inv);
		BookshelfManager.setInventoryHash(loc, hash);
		if (loc == null || loc.equals("null")) {
			return;
		}
		if (remove) {
			Bukkit.getScheduler().runTaskLater(Bookshelf.plugin, () -> Bookshelf.removeBookshelfFromMapping(loc), 300);
		}
	}
	
	public static void saveBookShelf(String loc){
		Inventory inv = Bookshelf.keyToContentMapping.get(loc);
		String hash = "";
		hash = BookshelfUtils.toBase64(inv);
		BookshelfManager.setInventoryHash(loc, hash);
		if (loc == null || loc.equals("null")) {
			return;
		}
	}
	
	public static String locKey(Location loc) {
		return loc.getWorld().getName() + "_" + loc.getBlockX() + "_" + loc.getBlockY() + "_" + loc.getBlockZ();
	}
	
	public static Location keyLoc(String key) {
		String[] breakdown = key.split("_");
		String worldString = "";
		List<String> list = new ArrayList<String>();
		for (int i = 0; i < (breakdown.length - 3); i++) {
			list.add(breakdown[i]);
		}
		worldString = String.join("_", list);
		World world = Bukkit.getWorld(worldString);
		int x = Integer.parseInt(breakdown[breakdown.length - 3]);
		int y = Integer.parseInt(breakdown[breakdown.length - 2]);
		int z = Integer.parseInt(breakdown[breakdown.length - 1]);
		return new Location(world, x, y, z);
	}
	
	public static String toBase64(Inventory inventory) throws IllegalStateException {
	    try {
	    	ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
	        BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream);
	            
	        // Write the size of the inventory
	        dataOutput.writeInt(inventory.getSize());
	            
	        // Save every element in the list
	        for (int i = 0; i < inventory.getSize(); i++) {
	            dataOutput.writeObject(inventory.getItem(i));
	        }
	            
	        // Serialize that array
	        dataOutput.close();
	        return Base64Coder.encodeLines(outputStream.toByteArray());
	    } catch (Exception e) {
	        throw new IllegalStateException("Unable to save item stacks.", e);
        }
	}
	    
	public static Inventory fromBase64(String data, String title) throws IOException {
		try {
			ByteArrayInputStream inputStream = new ByteArrayInputStream(Base64Coder.decodeLines(data));
			BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream);
	        Inventory inventory = null;
	        if (title.equals("")) {
	       	 	inventory = Bukkit.getServer().createInventory(null, dataInput.readInt());
	        } else {
	       	 	inventory = Bukkit.getServer().createInventory(null, dataInput.readInt(), title);
	        }
	   
	        // Read the serialized inventory
	        for (int i = 0; i < inventory.getSize(); i++) {
	            inventory.setItem(i, (ItemStack) dataInput.readObject());
	        }
	            
	        dataInput.close();
	        return inventory;
	    } catch (ClassNotFoundException e) {
	        throw new IOException("Unable to decode class type.", e);
	    }
	}
}
