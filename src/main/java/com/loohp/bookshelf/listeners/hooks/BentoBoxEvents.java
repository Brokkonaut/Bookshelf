package com.loohp.bookshelf.listeners.hooks;

import com.loohp.bookshelf.Bookshelf;
import com.loohp.bookshelf.api.events.PlayerOpenBookshelfEvent;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import world.bentobox.bentobox.BentoBox;
import world.bentobox.bentobox.api.user.User;
import world.bentobox.bentobox.database.objects.Island;
import world.bentobox.bentobox.lists.Flags;

import java.util.Optional;

public class BentoBoxEvents implements Listener {

    @EventHandler(priority = EventPriority.LOWEST)
    public void onBentoBoxCheck(PlayerOpenBookshelfEvent event) {

        if (!Bookshelf.bentoBoxHook) {
            return;
        }

        Player player = event.getPlayer();
        Location location = event.getLocation();

        User user = User.getInstance(player);
        Optional<Island> optisland = BentoBox.getInstance().getIslands().getIslandAt(location);

        if (!optisland.isPresent()) {
            return;
        }

        if (!optisland.get().isAllowed(user, Flags.CONTAINER)) {
            String message = BentoBox.getInstance().getLocalesManager().get("protection.protected").replace("[description]", BentoBox.getInstance().getLocalesManager().get("protection.flags.CONTAINER.hint"));
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
            event.setCancelled(true);
        }
    }

}
