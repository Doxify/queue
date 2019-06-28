package org.ipvp.queue.task;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.TextComponent;
import org.ipvp.queue.QueuePlugin;
import org.ipvp.queue.QueuedPlayer;

import static net.md_5.bungee.api.ChatColor.*;

public class PositionNotificationTask implements Runnable {

    private QueuePlugin plugin;

    public PositionNotificationTask(QueuePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        plugin.getQueued().stream().filter(QueuedPlayer::isInQueue).forEach(p -> {
            String queueServer = p.getQueue().getTarget().getName().substring(0, 1).toUpperCase() + p.getQueue().getTarget().getName().substring(1);
//            p.getHandle().sendMessage(TextComponent.fromLegacyText(YELLOW + "You are currently in queue for " + RED + queueServer + YELLOW + "."));
            p.getHandle().sendMessage(TextComponent.fromLegacyText(String.format(GOLD + "You are in position " + GREEN + "%d " + GOLD + "of " + YELLOW + "%d" + GOLD + " for " +  RED + queueServer + GOLD + ".", p.getPosition() + 1, p.getQueue().size())));
            p.getHandle().sendMessage(TextComponent.fromLegacyText(GREEN + "Players with ranks get higher queue priority: " + RED + "saphron.org/store"));
            if (p.getQueue().isPaused()) {
                p.getHandle().sendMessage(TextComponent.fromLegacyText(RED + "The queue you are currently in is paused."));
            }
        });
    }
}
