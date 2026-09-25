package io.azthera.ecocore.input;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.plugin.Plugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/** Generic one-shot private chat input used by GUI flows. */
public final class PrivateChatInputManager implements Listener {
    private record Session(Consumer<String> callback, Runnable cancelCallback) {}

    private final Plugin plugin;
    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();

    public PrivateChatInputManager(Plugin plugin) {
        this.plugin = plugin;
    }

    public void request(Player player, Consumer<String> callback, Runnable cancelCallback) {
        sessions.put(player.getUniqueId(), new Session(callback, cancelCallback));
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Session session = sessions.remove(player.getUniqueId());
            if (session != null) {
                player.sendMessage("§7[EcoCore] Input expired. Type the command/GUI action again.");
                if (session.cancelCallback() != null) session.cancelCallback().run();
            }
        }, 20L * 30L);
    }

    public boolean isWaiting(Player player) {
        return sessions.containsKey(player.getUniqueId());
    }

    public void cancel(Player player) {
        Session session = sessions.remove(player.getUniqueId());
        if (session != null && session.cancelCallback() != null) {
            session.cancelCallback().run();
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        Session session = sessions.remove(event.getPlayer().getUniqueId());
        if (session == null) return;

        event.setCancelled(true);
        String input = event.getMessage().trim();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (input.equalsIgnoreCase("cancel")) {
                if (session.cancelCallback() != null) session.cancelCallback().run();
                return;
            }
            session.callback().accept(input);
        });
    }
}
