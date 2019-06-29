package org.ipvp.queue;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.event.*;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.config.Configuration;
import net.md_5.bungee.config.ConfigurationProvider;
import net.md_5.bungee.config.YamlConfiguration;
import net.md_5.bungee.event.EventHandler;
import org.ipvp.queue.command.LeaveCommand;
import org.ipvp.queue.command.PauseCommand;
import org.ipvp.queue.command.QueueCommand;
import org.ipvp.queue.task.PositionNotificationTask;

import javax.xml.soap.Text;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import static net.md_5.bungee.api.ChatColor.*;

public class QueuePlugin extends Plugin implements Listener {

    private static QueuePlugin instance;

    private Configuration config;
    
    private Map<ServerInfo, Integer> maxPlayers = new HashMap<>();
    private Map<String, Queue> queues = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    private Map<ProxiedPlayer, QueuedPlayer> queuedPlayers = new ConcurrentHashMap<>();

    @Override
    public void onEnable() {
        try {
            this.config = loadConfiguration();
        } catch (IOException e) {
            getLogger().log(Level.SEVERE, "Failed to load config.yml", e);
            return;
        }

        getProxy().getServers().values().forEach(this::setupServer);
        getProxy().registerChannel("Queue");
        getProxy().getPluginManager().registerListener(this, this);
        getProxy().getScheduler().schedule(this, () -> {
            getQueues().stream().filter(Queue::canSend).forEach(Queue::sendNext);
        }, 50, 50, TimeUnit.MILLISECONDS);
        getProxy().getScheduler().schedule(this, new PositionNotificationTask(this), 1, 1, TimeUnit.MINUTES);
        getProxy().getScheduler().schedule(this, () -> {
            queuedPlayers.forEach((pp, qp) -> {
                if (!qp.isInQueue()) {
                    return;
                }

                ByteArrayDataOutput out = ByteStreams.newDataOutput();
                out.writeUTF("QueuePosition");
                out.writeUTF(pp.getUniqueId().toString());
                out.writeInt(qp.getPosition());
                out.writeUTF(qp.getQueue().getTarget().getName());
                out.writeInt(qp.getQueue().size());
                pp.getServer().sendData("BungeeCord", out.toByteArray());
            });
        }, 1L, 1L, TimeUnit.SECONDS);
        getProxy().getPluginManager().registerCommand(this, new LeaveCommand(this));
        getProxy().getPluginManager().registerCommand(this, new PauseCommand(this));
        getProxy().getPluginManager().registerCommand(this, new QueueCommand(this));

        this.instance = this;
    }

    /* (non-Javadoc)
     * Loads the configuration file
     */
    private Configuration loadConfiguration() throws IOException {
        File file = new File(getDataFolder(), "config.yml");

        if (file.exists()) {
            return ConfigurationProvider.getProvider(YamlConfiguration.class).load(file);
        }

        // Create the file to save
        if (!file.getParentFile().exists()) {
            file.getParentFile().mkdirs();
        }
        file.createNewFile();

        // Load the default provided configuration and save it to the file
        Configuration config = ConfigurationProvider.getProvider(YamlConfiguration.class)
                .load(getResourceAsStream("config.yml"));
        ConfigurationProvider.getProvider(YamlConfiguration.class).save(config, file);
        return config;
    }

    // Gets the max players for a server and caches it for later use
    private void setupServer(ServerInfo info) {
        final String name = info.getName();
        info.ping((p, err) -> {
            if (p == null || p.getPlayers() == null) {
                return;
            }
            int max = p.getPlayers().getMax();
            maxPlayers.put(info, max);
            if (!queues.containsKey(name)) {
                queues.put(name, new Queue(this, info));
            }
        });
    }

    /**
     * Returns all current queues for target servers
     *
     * @return Loaded queues for all servers
     */
    public Collection<Queue> getQueues() {
        return queues.values();
    }

    /**
     * Returns a Queue for a specified server
     *
     * @param server Server to check
     * @return Queue for the server
     */
    public Queue getQueue(String server) {
        return queues.get(server);
    }

    /**
     * Returns a players QueuedPlayer wrapper
     *
     * @param player Player to find
     * @return QueuedPlayer wrapper
     */
    public QueuedPlayer getQueued(ProxiedPlayer player) {
        if (!queuedPlayers.containsKey(player)) {
            QueuedPlayer queued = new QueuedPlayer(player, getPriority(player));
            queuedPlayers.put(player, queued);
            return queued;
        }
        return queuedPlayers.get(player);
    }

    /**
     * Returns all QueuedPlayers
     *
     * @return All QueuedPlayer wrappers
     */
    public Collection<QueuedPlayer> getQueued() {
        return queuedPlayers.values();
    }

    /**
     * Returns the queue priority of a player
     * 
     * @param player Player to check
     * @return players priority
     */
    public int getPriority(ProxiedPlayer player) {
        for (String rank : config.getSection("priorities").getKeys()) {
            if (player.hasPermission("queue.priority." + rank)) {
                return config.getInt("priorities." + rank);
            }
        }
        return 0;
    }

    /**
     * Gets the maximum players allowed on a server in this bungee instance
     *
     * @param server the name of the server
     * @return the cached maximum amount of players allowed on the server. will return -1
     * and make a call to load the data when the server exists but the data has not yet
     * been loaded.
     * @throws IllegalArgumentException if a server with the name does not exist
     */
    public int getMaxPlayers(ServerInfo server) {
        if (!maxPlayers.containsKey(server)) {
            setupServer(server);
            return -1;
        }

        return maxPlayers.get(server);
    }

    /**
     * Gets plugin instance
     *
     * @return QueuePlugin
     */
    public static QueuePlugin getInstance() { return instance; }

    @EventHandler
    public void onPluginMessage(PluginMessageEvent event) {
        ByteArrayDataInput in = ByteStreams.newDataInput(event.getData());
        String channel = event.getTag();

        if (channel.equals("Queue")) {
            String sub = in.readUTF();
            UUID uuid = UUID.fromString(in.readUTF());
            ProxiedPlayer player = getProxy().getPlayer(uuid);

            if (player == null) {
                return;
            }

            if (sub.equals("Join")) {
                QueuedPlayer queued = getQueued(player);
                String target = in.readUTF();
                String targetFormatted = target.substring(0, 1).toUpperCase() + target.substring(1);
                int weight = queued.getPriority();
                Queue queue = getQueue(target);

                if (queue == null) {
                    ServerInfo server = getProxy().getServerInfo(target); // Find server
                    if (server == null) {
                        player.sendMessage(TextComponent.fromLegacyText(ChatColor.RED + "Invalid server provided"));
                        return;
                    } else {
                        queue = new Queue(this, server);
                        queues.put(server.getName(), queue);
                    }
                }

                if (queued.getPriority() >= 999) {
                    ServerInfo info = getProxy().getServerInfo(target);
                    player.sendMessage(TextComponent.fromLegacyText(ChatColor.GREEN + "Attempting to send you to " + targetFormatted + "..."));
                    player.connect(info);
//                    player.connect(info, (result, error) -> {
//                        if (result) {
//                            player.sendMessage(TextComponent.fromLegacyText(YELLOW + "You have been sent to " + targetFormatted + "!"));
//                        }
//                    });
                    return;
                }

                if (queued.getQueue() != null) {
                    if (queued.getQueue().getTarget().getName().equalsIgnoreCase(target)) {
                        player.sendMessage(TextComponent.fromLegacyText(YELLOW + "You are already in the queue for " + RED + targetFormatted + YELLOW + "."));
                    } else {
                        player.sendMessage(TextComponent.fromLegacyText(ChatColor.RED + "You must leave your current queue before joining another one."));
                        player.sendMessage(TextComponent.fromLegacyText(ChatColor.GRAY + "Use /leavequeue to leave your current queue."));
                    }
                    return;
                }

                int index = queue.getIndexFor(weight);
                queue.add(index, queued);
                queued.setQueue(queue);
                player.sendMessage(TextComponent.fromLegacyText(YELLOW + "Successfully joined the queue for " + RED + targetFormatted + YELLOW + "."));
                player.sendMessage(TextComponent.fromLegacyText(RED + "Type /leavequeue to leave the current queue."));

                if (queue.isPaused()) {
                    player.sendMessage(TextComponent.fromLegacyText(ChatColor.RED + "The queue for " + targetFormatted + " is currently paused."));
                }
            }

            if (sub.equals("Position")) {
                QueuedPlayer queued = getQueued(player);
                Queue queue = queued.getQueue();

                if(queue != null) {
                    ByteArrayDataOutput out = ByteStreams.newDataOutput();
                    String targetFormatted = queue.getTarget().getName().substring(0, 1).toUpperCase() + queue.getTarget().getName().substring(1);
                    out.writeUTF("Position");
                    out.writeUTF(targetFormatted + ";" + queued.getPosition() + ";" + queue.size() + ";" + queued.getSecondsInQueue());

                    player.getServer().sendData("NSAQueue", out.toByteArray());
                } else {
                    ByteArrayDataOutput out = ByteStreams.newDataOutput();
                    out.writeUTF("Position");
                    out.writeUTF("NOT_IN_QUEUE");

                    player.getServer().sendData("NSAQueue", out.toByteArray());
                }
            }
        }
    }

    @EventHandler
    public void onDisconnect(PlayerDisconnectEvent event) {
        handleLeave(event.getPlayer());
        queuedPlayers.remove(event.getPlayer());
    }

    @EventHandler
    public void onSwitchServer(ServerSwitchEvent event) {
        handleLeave(event.getPlayer());
    }

    private void handleLeave(ProxiedPlayer player) {
        QueuedPlayer queued = queuedPlayers.get(player);
        if (queued != null && queued.getQueue() != null) {
            Queue queue = queued.getQueue();
            queue.remove(queued);
            queued.setQueue(null);
        }
    }
}
