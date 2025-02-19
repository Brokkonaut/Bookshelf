/*
 * This file is part of Bookshelf.
 *
 * Copyright (C) 2022. LoohpJames <jamesloohp@gmail.com>
 * Copyright (C) 2022. Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.loohp.bookshelf;

import com.loohp.bookshelf.utils.ColorUtils;
import com.loohp.bookshelf.utils.EnchantmentTableUtils;
import com.loohp.bookshelf.utils.ParticlesUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Particle.DustOptions;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.awt.print.Book;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class ParticleManager implements AutoCloseable {

    private final Bookshelf plugin;
    private final BookshelfManager bookshelfManager;
    private final Set<Block> openedBookshelves;
    private final Map<Block, Set<Block>> enchantingBoostingBlocks;
    private final Random random;

    private final int particleTaskId;

    public ParticleManager(Bookshelf plugin, BookshelfManager bookshelfManager) {
        this.plugin = plugin;
        this.bookshelfManager = bookshelfManager;
        this.openedBookshelves = Collections.newSetFromMap(new ConcurrentHashMap<>());
        this.enchantingBoostingBlocks = new ConcurrentHashMap<>();
        this.random = new Random();

        if (!Bookshelf.version.isLegacy()) {
            this.particleTaskId = Bukkit.getScheduler().runTaskTimerAsynchronously(this.plugin, () -> {
                DustOptions bookshelfColor1 = new DustOptions(ColorUtils.toBukkitColor(Bookshelf.bookshelfPrimaryColor), 1);
                DustOptions bookshelfColor2 = new DustOptions(ColorUtils.toBukkitColor(Bookshelf.bookshelfSecondaryColor), 1);
                DustOptions boostColor1 = new DustOptions(ColorUtils.toBukkitColor(Bookshelf.boostingPrimaryColor), 1);
                DustOptions boostColor2 = new DustOptions(ColorUtils.toBukkitColor(Bookshelf.boostingSecondaryColor), 1);

                if (Bookshelf.bookshelfParticlesEnabled) {
                    for (Block block : openedBookshelves) {
                        Location loc = block.getLocation();
                        Location loc2 = loc.clone().add(1, 1, 1);
                        for (Location pos : ParticlesUtils.getHollowCube(loc.add(-0.0625, -0.0625, -0.0625), loc2.add(0.0625, 0.0625, 0.0625), 0.1666)) {
                            if (random.nextInt(100) >= 95) {
                                int ranColor = random.nextInt(2) + 1;
                                if (ranColor == 1) {
                                    loc.getWorld().spawnParticle(Particle.REDSTONE, pos, 1, bookshelfColor2);
                                } else {
                                    loc.getWorld().spawnParticle(Particle.REDSTONE, pos, 1, bookshelfColor1);
                                }
                            }
                        }
                    }
                }
                if (Bookshelf.enchantingParticlesCount > 0) {
                    Set<Block> enchantEmitted = new HashSet<>();
                    for (Entry<Block, Set<Block>> entry : enchantingBoostingBlocks.entrySet()) {
                        Set<Block> blocks = entry.getValue();
                        if (!blocks.isEmpty()) {
                            Location tablePos = entry.getKey().getLocation().add(0.5, 0.5, 0.5);
                            tablePos.getWorld().spawnParticle(Particle.PORTAL, tablePos, Bookshelf.enchantingParticlesCount);
                            for (Block block : blocks) {
                                if (block.getType().equals(Material.BOOKSHELF) && !enchantEmitted.contains(block)) {
                                    Location loc = block.getLocation().clone();
                                    Location loc2 = loc.clone().add(1, 1, 1);
                                    for (Location pos : ParticlesUtils.getHollowCube(loc.add(-0.0625, -0.0625, -0.0625), loc2.add(0.0625, 0.0625, 0.0625), 0.1666)) {
                                        if (random.nextInt(100) >= 98) {
                                            int ranColor = random.nextInt(2) + 1;
                                            if (ranColor == 1) {
                                                loc.getWorld().spawnParticle(Particle.REDSTONE, pos, 1, boostColor2);
                                            } else {
                                                loc.getWorld().spawnParticle(Particle.REDSTONE, pos, 1, boostColor1);
                                            }
                                        }
                                    }
                                    enchantEmitted.add(block);
                                }
                            }
                        }
                    }
                }
            }, 0, 5).getTaskId();
        } else {
            this.particleTaskId = -1;
        }
    }

    public BookshelfManager getBookshelfManager() {
        return bookshelfManager;
    }

    public World getWorld() {
        return bookshelfManager.getWorld();
    }

    public void openBookshelf(Block bookshelf) {
        openedBookshelves.add(bookshelf);
    }

    public void closeBookshelf(Block bookshelf) {
        openedBookshelves.remove(bookshelf);
    }

    public void openEnchant(Block enchantingTable) {
        Set<Block> bookshelves = Collections.newSetFromMap(new ConcurrentHashMap<>());
        bookshelves.addAll(EnchantmentTableUtils.getBoostableBlockLocations(enchantingTable, block -> true));
        enchantingBoostingBlocks.put(enchantingTable, bookshelves);
    }

    public void closeEnchant(Block enchantingTable) {
        enchantingBoostingBlocks.remove(enchantingTable);
    }

    @Override
    public void close() {
        if (particleTaskId >= 0) {
            Bukkit.getScheduler().cancelTask(particleTaskId);
        }
    }

}
