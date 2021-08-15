package com.loohp.bookshelf.listeners;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import com.loohp.bookshelf.Bookshelf;
import com.loohp.bookshelf.BookshelfManager;
import com.loohp.bookshelf.utils.NMSUtils;

import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.util.concurrent.Future;
import net.md_5.bungee.api.ChatColor;

public class PacketListenerEvents implements Listener {
	
	public static final String HANDLER = "packet_handler";
	public static final String CHANNEL_NAME = "bookshelf_listener";
	
	private static Class<?> craftPlayerClass;
	private static Method craftPlayerGetHandleMethod;
	private static Field playerConnectionField;
	private static Field networkManagerField;
	private static Field channelField;
	private static Class<?> craftChatMessageClass;
	private static Class<?> nmsIChatBaseComponentClass;
	private static Method craftChatMessageFromComponentMethod;
	private static Class<?> nmsChatSerializerClass;
	private static Method nmsChatSerializerFromJSONMethod;
	private static Class<?> nmsPacketPlayOutOpenWindowClass;
	private static Field nmsPacketPlayOutOpenWindowTitleField;
	
	static {
		try {
			craftPlayerClass = NMSUtils.getNMSClass("org.bukkit.craftbukkit.%s.entity.CraftPlayer");
			craftPlayerGetHandleMethod = craftPlayerClass.getMethod("getHandle");
			try {
				playerConnectionField = craftPlayerGetHandleMethod.getReturnType().getField("playerConnection");
			} catch (NoSuchFieldException e) {
				playerConnectionField = craftPlayerGetHandleMethod.getReturnType().getField("b");
			}
			try {
				networkManagerField = playerConnectionField.getType().getField("networkManager");
			} catch (NoSuchFieldException e) {
				networkManagerField = playerConnectionField.getType().getField("a");
			}
			try {
				channelField = networkManagerField.getType().getField("channel");
			} catch (NoSuchFieldException e) {
				channelField = networkManagerField.getType().getField("k");
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		try {
			craftChatMessageClass = NMSUtils.getNMSClass("org.bukkit.craftbukkit.%s.util.CraftChatMessage");
			nmsIChatBaseComponentClass = NMSUtils.getNMSClass("net.minecraft.server.%s.IChatBaseComponent", "net.minecraft.network.chat.IChatBaseComponent");
			craftChatMessageFromComponentMethod = craftChatMessageClass.getMethod("fromComponent", nmsIChatBaseComponentClass);
			nmsChatSerializerClass = NMSUtils.getNMSClass("net.minecraft.server.%s.IChatBaseComponent$ChatSerializer", "net.minecraft.network.chat.IChatBaseComponent$ChatSerializer");
			nmsChatSerializerFromJSONMethod = nmsChatSerializerClass.getMethod("a", String.class);
			nmsPacketPlayOutOpenWindowClass = NMSUtils.getNMSClass("net.minecraft.server.%s.PacketPlayOutOpenWindow", "net.minecraft.network.protocol.game.PacketPlayOutOpenWindow");
			nmsPacketPlayOutOpenWindowTitleField = Stream.of(nmsPacketPlayOutOpenWindowClass.getDeclaredFields()).filter(each -> each.getType().equals(nmsIChatBaseComponentClass)).findFirst().get();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public PacketListenerEvents() {
		for (Player player : Bukkit.getOnlinePlayers()) {
			onJoin(new PlayerJoinEvent(player, ""));
		}
	}
	
	@EventHandler
	public void onJoin(PlayerJoinEvent event) {
		try {
			Channel channel = (Channel) channelField.get(networkManagerField.get(playerConnectionField.get(craftPlayerGetHandleMethod.invoke(craftPlayerClass.cast(event.getPlayer())))));
			Future<?> future = channel.eventLoop().submit(() -> {
				try {
					channel.pipeline().remove(CHANNEL_NAME);
				} catch (Throwable e) {}
	        });
			Bukkit.getScheduler().runTaskAsynchronously(Bookshelf.plugin, () -> {
				try {
					future.get(5000, TimeUnit.MILLISECONDS);
				} catch (InterruptedException | ExecutionException | TimeoutException e) {}
				Bukkit.getScheduler().runTask(Bookshelf.plugin, () -> {
					channel.pipeline().addBefore(HANDLER, CHANNEL_NAME, new ChannelDuplexHandler() {
						@Override
					    public void write(ChannelHandlerContext context, Object packet, ChannelPromise promise) throws Exception {
							if (packet != null) {
								if (nmsPacketPlayOutOpenWindowClass.isInstance(packet)) {
									nmsPacketPlayOutOpenWindowTitleField.setAccessible(true);
									if (ChatColor.stripColor(craftChatMessageFromComponentMethod.invoke(null, nmsPacketPlayOutOpenWindowTitleField.get(packet)).toString()).equals(BookshelfManager.DEFAULT_BOOKSHELF_NAME_PLACEHOLDER)) {
										nmsPacketPlayOutOpenWindowTitleField.set(packet, nmsChatSerializerFromJSONMethod.invoke(null, BookshelfManager.DEFAULT_BOOKSHELF_NAME_JSON));
									}
								}
							}
							super.write(context, packet, promise);
					    }
					    
					    @Override
					    public void channelRead(ChannelHandlerContext ctx, Object packet) throws Exception {
					    	super.channelRead(ctx, packet);
					    }
					});
				});
			});
		} catch (Throwable e) {
			e.printStackTrace();
		}
	}
	
	@EventHandler
	public void onQuit(PlayerQuitEvent event) {
		try {
			Channel channel = (Channel) channelField.get(networkManagerField.get(playerConnectionField.get(craftPlayerGetHandleMethod.invoke(craftPlayerClass.cast(event.getPlayer())))));
			channel.eventLoop().submit(() -> {
				try {
					channel.pipeline().remove(CHANNEL_NAME);
				} catch (Throwable e) {}
	        });
		} catch (Throwable e) {
			e.printStackTrace();
		}
	}

}
