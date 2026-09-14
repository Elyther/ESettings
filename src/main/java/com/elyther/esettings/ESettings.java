package com.elyther.esettings;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public final class ESettings extends JavaPlugin implements Listener, CommandExecutor {

    private volatile boolean publicChatEnabled;
    private volatile boolean mobSpawnEnabled;
    private volatile boolean phantomSpawnEnabled;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        loadSettings();

        Bukkit.getPluginManager().registerEvents(this, this);

        Objects.requireNonNull(getCommand("settings")).setExecutor(this);

        getLogger().info("ESettings aktiv edildi.");
    }

    @Override
    public void onDisable() {
        getLogger().info("ESettings deaktiv edildi.");
    }

    // =========================================================
    // CONFIG
    // =========================================================

    private void loadSettings() {
        publicChatEnabled = getConfig().getBoolean("features.public-chat", true);
        mobSpawnEnabled = getConfig().getBoolean("features.mob-spawn", true);
        phantomSpawnEnabled = getConfig().getBoolean("features.phantom-spawn", true);
    }

    // =========================================================
    // COMMAND
    // =========================================================

    @Override
    public boolean onCommand(
            CommandSender sender,
            Command command,
            String label,
            String[] args
    ) {

        if (!command.getName().equalsIgnoreCase("settings")) {
            return false;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {

            if (!sender.hasPermission("esettings.reload")) {
                sender.sendMessage(color(
                        getConfig().getString(
                                "messages.no-permission",
                                "&cBu komutu kullanmak üçün icazen yoxdur."
                        )
                ));
                return true;
            }

            reloadConfig();
            loadSettings();

            sender.sendMessage(color(
                    getConfig().getString(
                            "messages.reload",
                            "&aESettings config yeniləndi."
                    )
            ));

            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage("Bu komutu oyun daxilinde istifade et.");
            return true;
        }

        openSettings(player);

        return true;
    }

    // =========================================================
    // GUI
    // =========================================================

    private void openSettings(Player player) {

        String title = color(
                getConfig().getString(
                        "gui.title",
                        "&8Sunucu Ayarları"
                )
        );

        int rows = getConfig().getInt("gui.rows", 3);

        if (rows < 1) {
            rows = 1;
        }

        if (rows > 6) {
            rows = 6;
        }

        int size = rows * 9;

        SettingsHolder holder = new SettingsHolder();

        Inventory inventory = Bukkit.createInventory(
                holder,
                size,
                Component.text(title)
        );

        holder.inventory = inventory;

        ConfigurationSection itemsSection =
                getConfig().getConfigurationSection("items");

        if (itemsSection != null) {

            for (String id : itemsSection.getKeys(false)) {

                String path = "items." + id;

                if (!getConfig().getBoolean(path + ".enabled", true)) {
                    continue;
                }

                int slot = getConfig().getInt(path + ".slot", -1);

                if (slot < 0 || slot >= size) {
                    continue;
                }

                String materialName =
                        getConfig().getString(path + ".material", "STONE");

                Material material =
                        Material.matchMaterial(materialName);

                if (material == null) {
                    material = Material.STONE;
                }

                ItemStack item = new ItemStack(material);

                ItemMeta meta = item.getItemMeta();

                if (meta != null) {

                    String name =
                            getConfig().getString(
                                    path + ".name",
                                    "&f" + id
                            );

                    name = replacePlaceholders(name, id);

                    meta.displayName(
                            Component.text(color(name))
                    );

                    List<String> lore =
                            getConfig().getStringList(
                                    path + ".lore"
                            );

                    List<Component> finalLore = new ArrayList<>();

                    for (String line : lore) {

                        line = replacePlaceholders(line, id);

                        finalLore.add(
                                Component.text(color(line))
                        );
                    }

                    meta.lore(finalLore);

                    item.setItemMeta(meta);
                }

                inventory.setItem(slot, item);

                holder.slots.put(slot, id);
            }
        }

        player.openInventory(inventory);
    }

    // =========================================================
    // GUI CLICK
    // =========================================================

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        if (!(event.getView().getTopInventory().getHolder()
                instanceof SettingsHolder holder)) {
            return;
        }

        event.setCancelled(true);

        if (event.getClickedInventory() == null) {
            return;
        }

        if (event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }

        int slot = event.getSlot();

        String id = holder.slots.get(slot);

        if (id == null) {
            return;
        }

        handleItemClick(player, id);
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {

        if (event.getView().getTopInventory().getHolder()
                instanceof SettingsHolder) {

            event.setCancelled(true);
        }
    }

    // =========================================================
    // ITEM CLICK
    // =========================================================

    private void handleItemClick(Player player, String id) {

        String path = "items." + id;

        String type =
                getConfig().getString(
                        path + ".type",
                        "COMMAND"
                ).toUpperCase(Locale.ROOT);

        switch (type) {

            case "PUBLIC_CHAT" -> togglePublicChat(player);

            case "MOB_SPAWN" -> toggleMobSpawn(player);

            case "PHANTOM_SPAWN" -> togglePhantomSpawn(player);

            case "COMMAND" -> executeCommands(
                    player,
                    getConfig().getStringList(
                            path + ".commands"
                    )
            );

            default -> {
                player.sendMessage(color(
                        "&cESettings: Bilinmeyen item tipi: " + type
                ));
            }
        }

        boolean close =
                getConfig().getBoolean(
                        path + ".close",
                        false
                );

        if (close) {
            player.closeInventory();
        } else {
            openSettings(player);
        }
    }

    // =========================================================
    // PUBLIC CHAT
    // =========================================================

    private void togglePublicChat(Player player) {

        publicChatEnabled = !publicChatEnabled;

        getConfig().set(
                "features.public-chat",
                publicChatEnabled
        );

        saveConfig();

        String message;

        if (publicChatEnabled) {

            message = getConfig().getString(
                    "messages.public-chat-enabled",
                    "&aPublic chat açıldı."
            );

        } else {

            message = getConfig().getString(
                    "messages.public-chat-disabled",
                    "&cPublic chat bağlandı."
            );
        }

        player.sendMessage(color(message));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onChat(AsyncChatEvent event) {

        if (!publicChatEnabled) {

            event.setCancelled(true);

            event.getPlayer().sendMessage(
                    color(
                            getConfig().getString(
                                    "messages.chat-disabled",
                                    "&cPublic chat hazırda bağlıdır."
                            )
                    )
            );
        }
    }

    // =========================================================
    // MOB SPAWN
    // =========================================================

    private void toggleMobSpawn(Player player) {

        mobSpawnEnabled = !mobSpawnEnabled;

        getConfig().set(
                "features.mob-spawn",
                mobSpawnEnabled
        );

        saveConfig();

        String message;

        if (mobSpawnEnabled) {

            message = getConfig().getString(
                    "messages.mob-spawn-enabled",
                    "&aMob spawn açıldı."
            );

        } else {

            message = getConfig().getString(
                    "messages.mob-spawn-disabled",
                    "&cMob spawn bağlandı."
            );
        }

        player.sendMessage(color(message));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCreatureSpawn(CreatureSpawnEvent event) {

        if (event.getSpawnReason() != CreatureSpawnEvent.SpawnReason.NATURAL) {
            return;
        }

        EntityType type = event.getEntityType();

        if (type == EntityType.PHANTOM) {

            if (!phantomSpawnEnabled) {
                event.setCancelled(true);
            }

            return;
        }

        if (!mobSpawnEnabled) {
            event.setCancelled(true);
        }
    }

    // =========================================================
    // PHANTOM
    // =========================================================

    private void togglePhantomSpawn(Player player) {

        phantomSpawnEnabled = !phantomSpawnEnabled;

        getConfig().set(
                "features.phantom-spawn",
                phantomSpawnEnabled
        );

        saveConfig();

        String message;

        if (phantomSpawnEnabled) {

            message = getConfig().getString(
                    "messages.phantom-spawn-enabled",
                    "&aPhantom spawn açıldı."
            );

        } else {

            message = getConfig().getString(
                    "messages.phantom-spawn-disabled",
                    "&cPhantom spawn bağlandı."
            );
        }

        player.sendMessage(color(message));
    }

    // =========================================================
    // CUSTOM COMMANDS
    // =========================================================

    private void executeCommands(
            Player player,
            List<String> commands
    ) {

        for (String command : commands) {

            command = command
                    .replace("%player%", player.getName())
                    .replace("%player_name%", player.getName());

            if (command.startsWith("[console]")) {

                command = command
                        .substring("[console]".length())
                        .trim();

                Bukkit.dispatchCommand(
                        Bukkit.getConsoleSender(),
                        command
                );

            } else if (command.startsWith("[player]")) {

                command = command
                        .substring("[player]".length())
                        .trim();

                player.performCommand(command);

            } else {

                // Prefix yoxdursa player komandası kimi işləyir.
                player.performCommand(command);
            }
        }
    }

    // =========================================================
    // PLACEHOLDERS
    // =========================================================

    private String replacePlaceholders(
            String text,
            String id
    ) {

        String status = "";

        switch (id) {

            case "public-chat" ->
                    status = publicChatEnabled
                            ? color("&aAçıq")
                            : color("&cBağlı");

            case "mob-spawn" ->
                    status = mobSpawnEnabled
                            ? color("&aAçıq")
                            : color("&cBağlı");

            case "phantom-spawn" ->
                    status = phantomSpawnEnabled
                            ? color("&aAçıq")
                            : color("&cBağlı");

            default ->
                    status = "";
        }

        return text
                .replace("%status%", status)
                .replace("%player%", "");
    }

    // =========================================================
    // COLOR
    // =========================================================

    private String color(String text) {

        if (text == null) {
            return "";
        }

        return text.replace("&", "§");
    }

    // =========================================================
    // GUI HOLDER
    // =========================================================

    private static class SettingsHolder implements InventoryHolder {

        private Inventory inventory;

        private final Map<Integer, String> slots =
                new HashMap<>();

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
                  }
