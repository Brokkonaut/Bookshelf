package com.loohp.bookshelf;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.World.Environment;
import org.bukkit.block.Block;
import org.bukkit.entity.HumanEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.inventory.Inventory;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import com.google.common.collect.Iterables;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.loohp.bookshelf.api.events.PlayerCloseBookshelfEvent;
import com.loohp.bookshelf.api.events.PlayerOpenBookshelfEvent;
import com.loohp.bookshelf.objectholders.BlockPosition;
import com.loohp.bookshelf.objectholders.BookshelfHolder;
import com.loohp.bookshelf.objectholders.ChunkPosition;
import com.loohp.bookshelf.utils.BookshelfUtils;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.TranslatableComponent;
import net.md_5.bungee.chat.ComponentSerializer;

public class BookshelfManager implements Listener, AutoCloseable {

	public static final String DEFAULT_BOOKSHELF_NAME_PLACEHOLDER = "\0\0\0DEFAULT\0\0\0";
	public static final String DEFAULT_BOOKSHELF_NAME_JSON = ComponentSerializer.toString(new TranslatableComponent(Bookshelf.version.isLegacy() ? "tile.bookshelf.name" : "block.minecraft.bookshelf"));
	public static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z");
	public static final int DATA_VERSION = 0;
	
	private static final Map<World, BookshelfManager> BOOKSHELF_MANAGER = Collections.synchronizedMap(new LinkedHashMap<>());
	
	public static BookshelfManager getBookshelfManager(World world) {
		return BOOKSHELF_MANAGER.get(world);
	}
	
	public static List<World> getWorlds() {
		synchronized (BOOKSHELF_MANAGER) {
			return new ArrayList<>(BOOKSHELF_MANAGER.keySet());
		}
	}
	
	public static Iterable<BookshelfHolder> getAllLoadedBookshelves() {
		Iterable<BookshelfHolder> itr = new ArrayList<>();
		for (BookshelfManager manager : BOOKSHELF_MANAGER.values()) {
			itr = Iterables.concat(itr, manager.getLoadedBookshelves());
		}
		return Iterables.unmodifiableIterable(itr);
	}
	
	public synchronized static BookshelfManager loadWorld(Bookshelf plugin, World world) {
		BookshelfManager manager = BOOKSHELF_MANAGER.get(world);
		if (manager != null) {
			return manager;
		}
		manager = new BookshelfManager(plugin, world);
		BOOKSHELF_MANAGER.put(world, manager);
		return manager;
	}
	
	private final Bookshelf plugin;
	private final World world;
	private final File bookshelfFolder;
	private final File dataFile;
	private final Map<ChunkPosition, Map<BlockPosition, BookshelfHolder>> loadedBookshelves;	
	private final ParticleManager particleManager;
	private final Object lock;
	private final int autoSaveTask;
	private final ExecutorService asyncExecutor;
	private final AtomicBoolean isClosed;
	
	@SuppressWarnings("unchecked")
	private BookshelfManager(Bookshelf plugin, World world) {
		this.lock = new Object();
		this.isClosed = new AtomicBoolean(false);
		ThreadFactory factory = new ThreadFactoryBuilder().setNameFormat("Bookshelf World Processing Thread #%d (" + world.getName() + ")").build();
		this.asyncExecutor = new ThreadPoolExecutor(8, 16, 5000, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(), factory);
		
		this.plugin = plugin;
		this.world = world;
		if (world.getEnvironment().equals(Environment.NETHER)) {
			this.bookshelfFolder = new File(world.getWorldFolder(), "DIM-1/bookshelf");
		} else if (world.getEnvironment().equals(Environment.THE_END)) {
			this.bookshelfFolder = new File(world.getWorldFolder(), "DIM1/bookshelf");
		} else {
			this.bookshelfFolder = new File(world.getWorldFolder(), "bookshelf");
		}
		this.bookshelfFolder.mkdirs();
		this.loadedBookshelves = new ConcurrentHashMap<>();
		
		this.dataFile = new File(bookshelfFolder, "data.json");
		JSONObject dataJson = null;
		if (!dataFile.exists()) {
			dataJson = new JSONObject();
			dataJson.put("DataVersion", DATA_VERSION);
			dataJson.put("MCVersionID", Bookshelf.version.getNumber());
			dataJson.put("MinecraftVersion", Bookshelf.exactMinecraftVersion);
			dataJson.put("DateCreated", DATE_FORMAT.format(new Date()));
		} else {
			try (InputStreamReader reader = new InputStreamReader(new FileInputStream(dataFile), StandardCharsets.UTF_8)) {
				dataJson = (JSONObject) new JSONParser().parse(reader);
				if (dataJson.containsKey("MCVersionID") && Bookshelf.version.getNumber() < (long) dataJson.get("MCVersionID")) {
					Bukkit.getConsoleSender().sendMessage(ChatColor.RED + "[Bookshelf] WARNING: Minecraft version downgrade is not supported!");
					Bukkit.getConsoleSender().sendMessage(ChatColor.RED + "[Bookshelf] WARNING: Minecraft version downgrade is not supported!");
					Bukkit.getConsoleSender().sendMessage(ChatColor.RED + "[Bookshelf] WARNING: Minecraft version downgrade is not supported!");
					Bukkit.getConsoleSender().sendMessage(ChatColor.RED + "[Bookshelf] WARNING: Minecraft version downgrade is not supported!");
					Bukkit.getConsoleSender().sendMessage(ChatColor.RED + "[Bookshelf] WARNING: Minecraft version downgrade is not supported!");
				}
				if (dataJson.containsKey("DataVersion") && DATA_VERSION < (long) dataJson.get("DataVersion")) {
					Bukkit.getConsoleSender().sendMessage(ChatColor.RED + "[Bookshelf] WARNING: Plugin version downgrade is not supported!");
					Bukkit.getConsoleSender().sendMessage(ChatColor.RED + "[Bookshelf] WARNING: Plugin version downgrade is not supported!");
					Bukkit.getConsoleSender().sendMessage(ChatColor.RED + "[Bookshelf] WARNING: Plugin version downgrade is not supported!");
					Bukkit.getConsoleSender().sendMessage(ChatColor.RED + "[Bookshelf] WARNING: Plugin version downgrade is not supported!");
					Bukkit.getConsoleSender().sendMessage(ChatColor.RED + "[Bookshelf] WARNING: Plugin version downgrade is not supported!");
				}
				dataJson.put("DataVersion", DATA_VERSION);
				dataJson.put("MCVersionID", Bookshelf.version.getNumber());
				dataJson.put("MinecraftVersion", Bookshelf.exactMinecraftVersion);
			} catch (Throwable e) {
				Bukkit.getConsoleSender().sendMessage(ChatColor.RED + "[Bookshelf] Unable to read data file at " + dataFile.getPath());
				e.printStackTrace();
			}
		}
		if (dataJson != null) {
			try (PrintWriter pw = new PrintWriter(new OutputStreamWriter(new FileOutputStream(dataFile), StandardCharsets.UTF_8))) {
				Gson g = new GsonBuilder().setPrettyPrinting().create();
	            String prettyJsonString = g.toJson(dataJson);
	            pw.println(prettyJsonString);
	            pw.flush();
			} catch (Throwable e) {
				Bukkit.getConsoleSender().sendMessage(ChatColor.RED + "[Bookshelf] Unable to save data file at " + dataFile.getPath());
				e.printStackTrace();
			}
		}
		
		int spawnchunks = world.getLoadedChunks().length;
		AtomicInteger done = new AtomicInteger(0);
		
		executeAsyncTask(() -> {
			long start = System.currentTimeMillis();
			long lastDone = 0;
			while (done.get() < spawnchunks) {
				Bukkit.getConsoleSender().sendMessage("[Bookshelf] Preparing bookshelves in spawn chunks in " + world.getName() + ": " + Math.round((double) ((double) done.get() / (double) spawnchunks) * 100) + "%");
				if ((System.currentTimeMillis() - start) > 30000) {
					return;
				}
				if (lastDone != done.get()) {
					start = System.currentTimeMillis();
					lastDone = done.get();
				}
				try {
					TimeUnit.MILLISECONDS.sleep(500);
				} catch (InterruptedException ignore) {}
			}
		});
		
		for (Chunk chunk : world.getLoadedChunks()) {
			loadChunk(new ChunkPosition(chunk), true);
			done.incrementAndGet();
		}
		
		Bukkit.getConsoleSender().sendMessage("[Bookshelf] Preparing bookshelves in spawn chunks in " + world.getName() + ": 100%");
		
		Bukkit.getPluginManager().registerEvents(this, plugin);
		
		particleManager = new ParticleManager(plugin);
		
		autoSaveTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
			for (ChunkPosition chunk : loadedBookshelves.keySet()) {
				saveChunk(chunk, false);
				if (!plugin.isEnabled()) {
					return;
				}
			}
		}, 6000, 6000).getTaskId();
	}
	
	private void executeAsyncTask(Runnable task) {
		if (!isClosed.get()) {
			asyncExecutor.execute(task);
		}
	}
	
	private void executeAsyncTaskLater(Runnable task, long delay) {
		Bukkit.getScheduler().runTaskLater(plugin, () -> {
			if (!isClosed.get()) {
				asyncExecutor.execute(task);
			}
		}, delay);
	}
	
	public World getWorld() {
		return world;
	}
	
	public File getBookshelfFolder() {
		return bookshelfFolder;
	}
	
	public int size() {
		return loadedBookshelves.values().stream().mapToInt(each -> each.size()).sum();
	}
	
	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onChunkLoad(ChunkLoadEvent event) {
		if (!event.getWorld().equals(world)) {
			return;
		}
		executeAsyncTask(() -> {
			loadChunk(new ChunkPosition(event.getChunk()), true);
		});
	}
	
	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onChunkUnload(ChunkUnloadEvent event) {
		if (!event.getWorld().equals(world)) {
			return;
		}
		executeAsyncTask(() -> {
			saveChunk(new ChunkPosition(event.getChunk()), true);
		});
	}
	
	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onWorldUnload(WorldUnloadEvent event) {
		executeAsyncTaskLater(() -> close(), 10);
	}
	
	public Iterable<BookshelfHolder> getLoadedBookshelves() {
		Iterable<BookshelfHolder> itr = new ArrayList<>();
		for (Map<BlockPosition, BookshelfHolder> map : loadedBookshelves.values()) {
			itr = Iterables.concat(itr, map.values());
		}
		return Iterables.unmodifiableIterable(itr);
	}
	
	@SuppressWarnings("deprecation")
	public BookshelfHolder getOrCreateBookself(BlockPosition position, String title) {
		if (!position.getWorld().equals(world)) {
			return null;
		}
		Map<BlockPosition, BookshelfHolder> chunkEntry = loadedBookshelves.get(position.getChunkPosition());
		if (chunkEntry == null) {
			chunkEntry = loadChunk(position.getChunkPosition(), false);
		}
		BookshelfHolder bookshelf = chunkEntry.get(position);
		if (bookshelf == null) {
			bookshelf = new BookshelfHolder(position, title, null);
			Inventory inventory = Bukkit.createInventory(bookshelf, Bookshelf.bookShelfRows * 9, title == null ? DEFAULT_BOOKSHELF_NAME_PLACEHOLDER : title);
			bookshelf.setInventory(inventory);
			chunkEntry.put(position, bookshelf);
		}
		return bookshelf;
	}
	
	@SuppressWarnings("deprecation")
	public void move(BlockPosition position, int x, int y, int z) {
		if (!position.getWorld().equals(world)) {
			throw new IllegalArgumentException("Position not in world");
		}
		ChunkPosition originalChunkPosition = position.getChunkPosition();
		Map<BlockPosition, BookshelfHolder> originalChunkEntry = loadedBookshelves.get(originalChunkPosition);
		if (originalChunkEntry == null) {
			originalChunkEntry = loadChunk(originalChunkPosition, false);
		}
		BookshelfHolder bookshelf = originalChunkEntry.get(position);
		if (bookshelf == null) {
			return;
		}
		originalChunkEntry.remove(position);
		BlockPosition newPos = new BlockPosition(world, x, y, z);
		ChunkPosition newChunkPosition = newPos.getChunkPosition();
		bookshelf.setPosition(newPos);
		if (newChunkPosition.equals(originalChunkPosition)) {
			originalChunkEntry.put(newPos, bookshelf);
		} else {
			Map<BlockPosition, BookshelfHolder> newChunkEntry = loadedBookshelves.get(newChunkPosition);
			if (newChunkEntry == null) {
				newChunkEntry = loadChunk(newChunkPosition, false);
			}
			newChunkEntry.put(newPos, bookshelf);
		}
	}
	
	public void remove(BlockPosition position) {
		if (!position.getWorld().equals(world)) {
			throw new IllegalArgumentException("Position not in world");
		}
		Map<BlockPosition, BookshelfHolder> chunkEntry = loadedBookshelves.get(position.getChunkPosition());
		if (chunkEntry == null) {
			chunkEntry = loadChunk(position.getChunkPosition(), false);
		}
		BookshelfHolder bookshelf = chunkEntry.get(position);
		if (bookshelf == null) {
			return;
		}
		Inventory inv = bookshelf.getInventory();
		List<HumanEntity> viewers = inv.getViewers();
		for (HumanEntity player : viewers.toArray(new HumanEntity[viewers.size()])) {
			player.closeInventory();
		}
		chunkEntry.remove(position);
	}
	
	@SuppressWarnings("deprecation")
	private Map<BlockPosition, BookshelfHolder> loadChunk(ChunkPosition chunk, boolean checkPresence) {
		if (!chunk.getWorld().equals(world)) {
			throw new IllegalArgumentException("Position not in world");
		}
		synchronized (lock) {
			Map<BlockPosition, BookshelfHolder> chunkEntry = loadedBookshelves.get(chunk);
			if (chunkEntry != null) {
				return chunkEntry;
			}
			chunkEntry = new ConcurrentHashMap<>();
			loadedBookshelves.put(chunk, chunkEntry);
			File regionFolder = new File(bookshelfFolder, "r." + (chunk.getChunkX() >> 5) + "." + (chunk.getChunkZ() >> 5));
			File chunkFile = new File(regionFolder, "c." + chunk.getChunkX() + "." + chunk.getChunkZ() + ".json");
			if (chunkFile.exists()) {
				try (InputStreamReader reader = new InputStreamReader(new FileInputStream(chunkFile), StandardCharsets.UTF_8)) {
					JSONObject json = (JSONObject) new JSONParser().parse(reader);
					for (Object obj : json.keySet()) {
						String key = obj.toString();
						BlockPosition position = BookshelfUtils.keyPos(world, key);
						if (!checkPresence || position.getBlock().getType().equals(Material.BOOKSHELF)) {
							JSONObject entry = (JSONObject) json.get(key);
							String title = entry.containsKey("Title") ? entry.get("Title").toString() : null;
							BookshelfHolder bookshelf = new BookshelfHolder(position, title, null);
							Inventory inventory = BookshelfUtils.fromBase64(entry.get("Inventory").toString(), title == null ? DEFAULT_BOOKSHELF_NAME_PLACEHOLDER : title, bookshelf);
							bookshelf.setInventory(inventory);
							chunkEntry.put(position, bookshelf);
						}
					}
				} catch (Throwable e) {
					e.printStackTrace();
				}
			}
			return chunkEntry;
		}
	}
	
	@SuppressWarnings("unchecked")
	public void saveChunk(ChunkPosition chunk, boolean unload) {
		if (!chunk.getWorld().equals(world)) {
			throw new IllegalArgumentException("Position not in world");
		}
		synchronized (lock) {
			Map<BlockPosition, BookshelfHolder> chunkEntry = loadedBookshelves.get(chunk);
			if (chunkEntry == null) {
				return;
			}			
			File regionFolder = new File(bookshelfFolder, "r." + (chunk.getChunkX() >> 5) + "." + (chunk.getChunkZ() >> 5));
			if (!chunkEntry.isEmpty()) {
				regionFolder.mkdirs();
				File chunkFile = new File(regionFolder, "c." + chunk.getChunkX() + "." + chunk.getChunkZ() + ".json");
				try (PrintWriter pw = new PrintWriter(new OutputStreamWriter(new FileOutputStream(chunkFile), StandardCharsets.UTF_8))) {
					JSONObject toSave = new JSONObject();
					for (BookshelfHolder bookshelf : chunkEntry.values()) {
						String key = BookshelfUtils.posKey(bookshelf.getPosition());
						JSONObject entry = new JSONObject();
						if (bookshelf.getTitle() != null) {
							entry.put("Title", bookshelf.getTitle());
						}
						entry.put("Inventory", BookshelfUtils.toBase64(bookshelf.getInventory()));
						toSave.put(key, entry);
					}
		        	Gson g = new GsonBuilder().setPrettyPrinting().create();
		            String prettyJsonString = g.toJson(toSave);
		            pw.println(prettyJsonString);
		            pw.flush();
				} catch (Throwable e) {
					Bukkit.getConsoleSender().sendMessage(ChatColor.RED + "[Bookshelf] Unable to save chunk " + chunk.getChunkX() + ", " + chunk.getChunkZ() + " in " + regionFolder.getPath());
					e.printStackTrace();
				}
			} else {
				File chunkFile = new File(regionFolder, "c." + chunk.getChunkX() + "." + chunk.getChunkZ() + ".json");
				if (chunkFile.exists()) {
					chunkFile.delete();
				}
			}
			if (unload) {
				loadedBookshelves.remove(chunk);
			}
		}
	}
	
	@Override
	public void close() {
		Bukkit.getScheduler().cancelTask(autoSaveTask);
		HandlerList.unregisterAll(this);
		particleManager.close();
		BOOKSHELF_MANAGER.remove(world);
		synchronized (lock) {
			Bukkit.getConsoleSender().sendMessage("[Bookshelf] Saving bookshelves for world " + world.getName());
			for (ChunkPosition chunk : loadedBookshelves.keySet()) {
				saveChunk(chunk, true);
			}
			Bukkit.getConsoleSender().sendMessage("[Bookshelf] (" + world.getName() + "): All bookshelves are saved");
		}
		isClosed.set(true);
		asyncExecutor.shutdown();
	}
	
	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onBookshelfOpen(PlayerOpenBookshelfEvent event) {
		particleManager.openBookshelf(event.getBlock());
	}
	
	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onBookshelfClose(PlayerCloseBookshelfEvent event) {
		particleManager.closeBookshelf(event.getBlock());
	}
	
	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onEnchantmentTableOpen(InventoryOpenEvent event) {
		Inventory inventory = event.getInventory();
		if (inventory.getType().equals(InventoryType.ENCHANTING)) {
			Location location = inventory.getLocation();
			if (location != null) {
				particleManager.openEnchant(location.getBlock());
			}
		}
	}
	
	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onEnchantmentTableClose(InventoryCloseEvent event) {
		Inventory inventory = event.getInventory();
		if (inventory.getType().equals(InventoryType.ENCHANTING)) {
			Location location = inventory.getLocation();
			if (location != null) {
				particleManager.closeEnchant(location.getBlock());
			}
		}
	}
	
	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onBlockBreak(BlockBreakEvent event) {
		Block block = event.getBlock();
		if (block.getType().equals(Material.BOOKSHELF)) {
			particleManager.removeEnchantBookshelf(block);
		}
	}
	
	@EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled = true)
	public void onEntityExplode(EntityExplodeEvent event) {
		for (Block block : event.blockList()) {
			if (block.getType().equals(Material.BOOKSHELF)) {
				particleManager.removeEnchantBookshelf(block);
			}
		}
	}
	
	@EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled = true)
	public void onBlockExplode(BlockExplodeEvent event) {
		Block origin = event.getBlock();
		if (origin.getType().equals(Material.BOOKSHELF)) {
			particleManager.removeEnchantBookshelf(origin);
		}
		for (Block block : event.blockList()) {
			if (block.getType().equals(Material.BOOKSHELF)) {
				particleManager.removeEnchantBookshelf(block);
			}
		}
	}
	
}
