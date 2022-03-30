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

package com.loohp.bookshelf.utils;

import org.bukkit.World;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;

public class WorldUtils {

    private static Class<?> craftWorldClass;
    private static Method getHandleMethod;
    private static Class<?> worldServerClass;
    private static Class<?> dimensionManagerClass;
    private static Method getDimensionManagerMethod;
    private static Class<?> minecraftKeyClass;
    private static Method getMinecraftKeyMethod;

    static {
        try {
            craftWorldClass = NMSUtils.getNMSClass("org.bukkit.craftbukkit.%s.CraftWorld");
            getHandleMethod = craftWorldClass.getMethod("getHandle");
            worldServerClass = getHandleMethod.getReturnType();
            dimensionManagerClass = NMSUtils.getNMSClass("net.minecraft.server.%s.DimensionManager", "net.minecraft.world.level.dimension.DimensionManager");
            getDimensionManagerMethod = Arrays.stream(worldServerClass.getMethods()).filter(each -> each.getReturnType().equals(dimensionManagerClass)).findFirst().get();
            minecraftKeyClass = NMSUtils.getNMSClass("net.minecraft.server.%s.MinecraftKey", "net.minecraft.resources.MinecraftKey");
            getMinecraftKeyMethod = NMSUtils.reflectiveLookup(Method.class, () -> {
                Method method = dimensionManagerClass.getMethod("a");
                if (!method.getReturnType().equals(minecraftKeyClass)) {
                    throw new NoSuchMethodException();
                }
                return method;
            }, () -> {
                Method method = dimensionManagerClass.getMethod("r");
                if (!method.getReturnType().equals(minecraftKeyClass)) {
                    throw new NoSuchMethodException();
                }
                return method;
            }, () -> {
                Method method = dimensionManagerClass.getMethod("p");
                if (!method.getReturnType().equals(minecraftKeyClass)) {
                    throw new NoSuchMethodException();
                }
                return method;
            });
        } catch (ReflectiveOperationException e) {
            e.printStackTrace();
        }
    }

    public static String getNamespacedKey(World world) {
        try {
            Object craftWorldObject = craftWorldClass.cast(world);
            Object nmsWorldServerObject = getHandleMethod.invoke(craftWorldObject);
            Object nmsDimensionManagerObject = getDimensionManagerMethod.invoke(nmsWorldServerObject);
            return getMinecraftKeyMethod.invoke(nmsDimensionManagerObject).toString();
        } catch (IllegalAccessException | InvocationTargetException e) {
            e.printStackTrace();
        }
        return null;
    }

}
