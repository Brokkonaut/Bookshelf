package com.loohp.bookshelf.listeners;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.enchantments.EnchantmentOffer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.enchantment.PrepareItemEnchantEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;

import com.loohp.bookshelf.Bookshelf;
import com.loohp.bookshelf.BookshelfManager;
import com.loohp.bookshelf.EnchantmentOfferMapping;
import com.loohp.bookshelf.objectholders.BlockPosition;
import com.loohp.bookshelf.objectholders.BookshelfHolder;
import com.loohp.bookshelf.utils.EnchantmentTableUtils;
import com.loohp.bookshelf.utils.MCVersion;

public class EnchantingEvents implements Listener {
	
	@SuppressWarnings({ "unchecked", "deprecation" })
	@EventHandler(priority = EventPriority.HIGHEST)
	public void onPreEnchantTable(PrepareItemEnchantEvent event) {
		if (Bookshelf.version.isOlderOrEqualTo(MCVersion.V1_10)) {
			return;
		}
		if (!Bookshelf.enchantmentTable) {
			return;
		}
		
		if (event.isCancelled()) {
			return;
		}
		
		Block eTable = event.getEnchantBlock();
		List<Block> blocks = EnchantmentTableUtils.getBookshelves(eTable);
		Map<Enchantment, Map<String, Object>> enchants = new HashMap<>();
		int totalSlots = (int) (Bookshelf.bookShelfRows * 9 * 15);
		if (blocks.isEmpty()) {
			return;
		}
		for (Block block : blocks) {
			BookshelfHolder bookshelf = BookshelfManager.getBookshelfManager(block.getWorld()).getOrCreateBookself(new BlockPosition(block), null);
			Inventory inv = bookshelf.getInventory();
			for (int i = 0; i < inv.getSize(); i++) {
				ItemStack item = inv.getItem(i);
				if (item == null) {
					continue;
				}
				if (!item.getType().equals(Material.ENCHANTED_BOOK)) {
					continue;
				}
				EnchantmentStorageMeta meta = (EnchantmentStorageMeta) item.getItemMeta();
				Map<Enchantment, Integer> map = meta.getStoredEnchants();
				for (Entry<Enchantment, Integer> entry : map.entrySet()) {
					if (!enchants.containsKey(entry.getKey())) {
						HashMap<String, Object> value = new HashMap<String, Object>();
						value.put("Occurrence", (int) 1);
						List<Integer> lvl = new ArrayList<Integer>();
						lvl.add(entry.getValue());
						value.put("Level", lvl);
						enchants.put(entry.getKey(), value);
					} else {
						Map<String, Object> value = enchants.get(entry.getKey());
						value.put("Occurrence", (int) value.get("Occurrence") + 1);
						List<Integer> lvl = (List<Integer>) value.get("Level");
						lvl.add(entry.getValue());
						value.put("Level", lvl);
					}
				}
			}
		}

		if (enchants.isEmpty()) {
			return;
		}
		HashMap<Enchantment, HashMap<String, Integer>> list = new HashMap<>();
		int totalOccurrence = 0;
		for (Entry<Enchantment, Map<String, Object>> entry : enchants.entrySet()) {
			int occurrence = (int) entry.getValue().get("Occurrence");
			totalOccurrence = totalOccurrence + occurrence;
			List<Integer> levels = (List<Integer>) entry.getValue().get("Level");
			int sum = 0;
			for (int each : levels) {
				sum = sum + each;
			}
			int level = (int) Math.floor((double) sum / (double) levels.size());
			HashMap<String, Integer> map = new HashMap<String, Integer>();
			map.put("Occurrence", occurrence);
			map.put("Level", level);
			list.put(entry.getKey(), map);
		}
		if (list.isEmpty()) {
			return;
		}
		List<Object> pick = new ArrayList<>();
		for (Entry<Enchantment, HashMap<String, Integer>> entry : list.entrySet()) {
			if (entry.getKey().equals(Enchantment.MENDING)) {
				continue;
			}
			if (entry.getKey().equals(Enchantment.FROST_WALKER)) {
				continue;
			}
			if (entry.getKey().equals(Enchantment.BINDING_CURSE)) {
				continue;
			}
			if (entry.getKey().equals(Enchantment.VANISHING_CURSE)) {
				continue;
			}
			if (entry.getKey().equals(Enchantment.SOUL_SPEED)) {
				continue;
			}
			if (!entry.getKey().canEnchantItem(event.getItem()) && !event.getItem().getType().equals(Material.BOOK)) {
				continue;
			}
			int occurrence = entry.getValue().get("Occurrence");
			for (int i = 0; i < occurrence; i++) {
				if (!Bookshelf.version.isLegacy()) {
					pick.add(entry.getKey().getKey());
				} else {
					pick.add(entry.getKey().getName());
				}
			}
		}
		if (pick.isEmpty()) {
			return;
		}
		for (int i = pick.size() - 1; i < totalSlots; i++) {
			pick.add(null);
		}
		int additionneeded = (pick.size() * Bookshelf.eTableMulti) - pick.size();
		for (int i = 0; i < additionneeded; i++) {
			pick.add(null);
		}
		Player player = event.getEnchanter();
		EnchantmentOffer[] offers = event.getOffers();
		for (EnchantmentOffer offer : offers) {
			long seed = 0;
			if (!Bookshelf.enchantSeed.containsKey(player)) {		
				Bookshelf.enchantSeed.put(player, System.currentTimeMillis());
			}
			seed = Bookshelf.enchantSeed.get(player);
			Random random = new Random(seed);
			double ran = random.nextDouble();
			Object key = pick.get((int) (ran * pick.size()));
			if (key == null) {
				continue;
			}
			Enchantment ench = null;
			if (!Bookshelf.version.isLegacy()) {
				ench = Enchantment.getByKey((NamespacedKey) key);
			} else {
				ench = Enchantment.getByName((String) key);
			}
			offer.setEnchantment(ench);
			int level = list.get(ench).get("Level");
			if (offer.getEnchantmentLevel() > level) {
				offer.setEnchantmentLevel(level);
			}
		}
		Map<ItemStack, EnchantmentOffer[]> offermap = null;
		if (EnchantmentOfferMapping.enchantOffers.containsKey(player)) {
			offermap = EnchantmentOfferMapping.enchantOffers.get(player);
		} else {
			offermap = new HashMap<>();
			EnchantmentOfferMapping.enchantOffers.put(player, offermap);
		}
		offermap.put(event.getItem(), offers);
	}
	
	@EventHandler(priority = EventPriority.HIGHEST)
	public void onEnchant(EnchantItemEvent event) {
		if (Bookshelf.version.isOlderOrEqualTo(MCVersion.V1_10)) {
			return;
		}
		if (!Bookshelf.enchantmentTable) {
			return;
		}
		
		if (event.isCancelled()) {
			return;
		}
		
		Player player = event.getEnchanter();
		if (EnchantmentOfferMapping.enchantOffers.containsKey(player)) {
			Map<ItemStack, EnchantmentOffer[]> playerOffers = EnchantmentOfferMapping.enchantOffers.get(player);
			if (playerOffers == null) {
				return;
			}
			EnchantmentOffer[] offers = playerOffers.get(event.getItem());
			if (offers == null || offers.length <= event.whichButton()) {
				return;
			}
			EnchantmentOffer offer = offers[event.whichButton()];
			Map<Enchantment, Integer> orginal = event.getEnchantsToAdd();
			List<Enchantment> removelist = new ArrayList<Enchantment>();
			for (Entry<Enchantment, Integer> entry : orginal.entrySet()) {
				if (entry.getKey().conflictsWith(offer.getEnchantment()) || entry.getKey().equals(offer.getEnchantment())) {
					removelist.add(entry.getKey());
				}
			}
			for (Enchantment ench : removelist) {
				orginal.remove(ench);
			}
			orginal.put(offer.getEnchantment(), offer.getEnchantmentLevel());
			EnchantmentOfferMapping.enchantOffers.get(player).remove(event.getItem());
		}
		Bookshelf.enchantSeed.remove(player);
	}

}
