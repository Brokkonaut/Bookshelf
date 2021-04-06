package com.loohp.bookshelf.api.events;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.Inventory;

import com.loohp.bookshelf.Bookshelf;
import com.loohp.bookshelf.utils.BookshelfUtils;

public class PlayerOpenBookshelfEvent extends Event implements Cancellable {
	
	private Player player;
	private Block block;
	private BlockFace blockface;
	private Location location;
	private String key;
	private Inventory inventory;
	private boolean cancelled;
	
	public PlayerOpenBookshelfEvent (Player player, String key, BlockFace blockface, boolean cancelled) {
		this.player = player;
		this.key = key;
		this.location = BookshelfUtils.keyLoc(key);
		this.block = location.getBlock();
		this.blockface = blockface;
		this.inventory = Bookshelf.keyToContentMapping.get(key);
		this.cancelled = cancelled;
	}
	
	public Player getPlayer() {
		return player;
	}

	public Block getBlock() {
		return block;
	}

	public Location getLocation() {
		return location;
	}

	public String getKey() {
		return key;
	}
	
	public BlockFace getClickedBlockFace() {
		return blockface;
	}

	public Inventory getInventory() {
		return inventory;
	}

	@Override
	public boolean isCancelled() {
		return cancelled;
	}

	@Override
	public void setCancelled(boolean cancelled) {
		this.cancelled = cancelled;
	}

	private static final HandlerList HANDLERS = new HandlerList();

	@Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }

}
