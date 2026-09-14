package com.elyther.esettings;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
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

public final class ESettings extends JavaPlugin implements Listener {

    private volatile boolean publicChatEnabled;
    private volatile boolean mobSpawnEnabled;
    private volatile boolean phantomSpawnEnabled;

    @Override
    public void onEnable() {

        saveDefaultConfig();
        loadSettings();

        Bukkit.getPluginManager().registerEvents(this, this);

        Objects.requireNonNull(getCommand("settings"))
                .setExecutor(this::onCommand);

        getLogger().info("ESettings aktif edildi.");
    }

    @Override
    public void onDisable() {
        getLogger().info("ESettings kapatildi.");
    }

    // =========================================================
    // CONFIG
    // =========================================================

    private void loadSettings() {

        publicChatEnabled = getConfig().getBoolean(
                "features.public-chat",
                true
        );

        mobSpawnEnabled = getConfig().getBoolean(
                "features.mob-spawn",
                true
        );

        phantomSpawnEnabled = getConfig().getBoolean(
                "features.phantom-spawn",
                true
        );
    }

    // =========================================================
    // COMMAND
    // =========================================================

    private boolean onCommand(
        CommandSender sender,
        Command command,
        String label,
        String[] args
) {
        if (!command.getName().equalsIgnoreCase("settings")) {
            return false;
        }

        // /settings reload
        if (args.length > 0 &&
                args[0].equalsIgnoreCase("reload")) {

            if (!sender.hasPermission("esettings.reload")) {

                sender.sendMessage(color(
                        getConfig().getString(
                                "messages.no-permission",
                                "&cBu işlemi yapmak için yetkin yok."
                        )
                ));

                return true;
            }

            reloadConfig();
            loadSettings();

            sender.sendMessage(color(
                    getConfig().getString(
                            "messages.reload",
                            "&aESettings ayarlari yenilendi."
                    )
            ));

            return true;
        }

        // Sadece oyuncu GUI açabilir
        if (!(sender instanceof Player player)) {

            sender.sendMessage(
                    "Bu komutu oyun icinde kullanmalisin."
            );

            return true;
        }

        openSettings(player);

        return true;
    }

    // =========================================================
    // GUI AÇ
    // =========================================================

    private void openSettings(Player player) {

        String title = getConfig().getString(
                "gui.title",
                "&8Sunucu Ayarlari"
        );

        int rows = getConfig().getInt(
                "gui.rows",
                3
        );

        // 1-6 satır
        rows = Math.max(1, Math.min(6, rows));

        int size = rows * 9;

        SettingsHolder holder = new SettingsHolder();

        Inventory inventory = Bukkit.createInventory(
                holder,
                size,
                Component.text(color(title))
        );

        holder.inventory = inventory;

        ConfigurationSection items =
                getConfig().getConfigurationSection("items");

        if (items != null) {

            for (String id : items.getKeys(false)) {

                String path = "items." + id;

                // GUI'de görünsün mü?
                if (!getConfig().getBoolean(
                        path + ".enabled",
                        true
                )) {
                    continue;
                }

                int slot = getConfig().getInt(
                        path + ".slot",
                        -1
                );

                if (slot < 0 || slot >= size) {
                    continue;
                }

                String materialName =
                        getConfig().getString(
                                path + ".material",
                                "STONE"
                        );

                Material material =
                        Material.matchMaterial(materialName);

                if (material == null) {
                    material = Material.STONE;
                }

                ItemStack item =
                        new ItemStack(material);

                ItemMeta meta =
                        item.getItemMeta();

                if (meta != null) {

                    // -------------------------------------------------
                    // NAME
                    // -------------------------------------------------

                    String name =
                            getConfig().getString(
                                    path + ".name",
                                    "&f" + id
                            );

                    name = replacePlaceholders(
                            name,
                            id
                    );

                    meta.displayName(
                            Component.text(
                                    color(name)
                            )
                    );

                    // -------------------------------------------------
                    // LORE
                    // -------------------------------------------------

                    List<String> lore =
                            getConfig().getStringList(
                                    path + ".lore"
                            );

                    List<Component> finalLore =
                            new ArrayList<>();

                    for (String line : lore) {

                        line = replacePlaceholders(
                                line,
                                id
                        );

                        finalLore.add(
                                Component.text(
                                        color(line)
                                )
                        );
                    }

                    meta.lore(finalLore);

                    item.setItemMeta(meta);
                }

                inventory.setItem(
                        slot,
                        item
                );

                holder.slots.put(
                        slot,
                        id
                );
            }
        }

        player.openInventory(inventory);
    }

    // =========================================================
    // GUI CLICK
    // =========================================================

    @EventHandler
    public void onInventoryClick(
            InventoryClickEvent event
    ) {

        if (!(event.getWhoClicked()
                instanceof Player player)) {
            return;
        }

        if (!(event.getView()
                .getTopInventory()
                .getHolder()
                instanceof SettingsHolder holder)) {
            return;
        }

        // GUI içindeki itemler taşınamaz
        event.setCancelled(true);

        if (event.getClickedInventory() == null) {
            return;
        }

        // Sadece üst GUI
        if (event.getClickedInventory()
                != event.getView()
                .getTopInventory()) {
            return;
        }

        int slot = event.getSlot();

        String id = holder.slots.get(slot);

        if (id == null) {
            return;
        }

        handleItemClick(
                player,
                id
        );
    }

    // =========================================================
    // GUI DRAG
    // =========================================================

    @EventHandler
    public void onInventoryDrag(
            InventoryDragEvent event
    ) {

        if (event.getView()
                .getTopInventory()
                .getHolder()
                instanceof SettingsHolder) {

            event.setCancelled(true);
        }
    }

    // =========================================================
    // ITEM CLICK
    // =========================================================

    private void handleItemClick(
            Player player,
            String id
    ) {

        String path =
                "items." + id;

        String type =
                getConfig().getString(
                        path + ".type",
                        "COMMAND"
                ).toUpperCase(Locale.ROOT);

        /*
         * Önce özel hazır sistemleri kontrol ediyoruz.
         *
         * Bunlar:
         *
         * PUBLIC_CHAT
         * MOB_SPAWN
         * PHANTOM_SPAWN
         *
         * Diğer bütün type'lar ise configdeki
         * commands listesini çalıştırır.
         */

        switch (type) {

            case "PUBLIC_CHAT" -> {

                togglePublicChat(player);

            }

            case "MOB_SPAWN" -> {

                toggleMobSpawn(player);

            }

            case "PHANTOM_SPAWN" -> {

                togglePhantomSpawn(player);

            }

            default -> {

                /*
                 * ÖNEMLİ:
                 *
                 * Artıq "Bilinmeyen item tipi"
                 * mesajı yoxdur.
                 *
                 * Type nə olursa olsun commands:
                 * varsa işləyəcək.
                 */

                executeCommands(
                        player,
                        getConfig().getStringList(
                                path + ".commands"
                        )
                );
            }
        }

        // GUI bağlansın?
        boolean close =
                getConfig().getBoolean(
                        path + ".close",
                        false
                );

        if (close) {

            player.closeInventory();

        } else {

            // Status yenilənsin deyə GUI-ni yenidən açırıq
            openSettings(player);
        }
    }

    // =========================================================
    // PUBLIC CHAT
    // =========================================================

    private void togglePublicChat(
            Player player
    ) {

        publicChatEnabled =
                !publicChatEnabled;

        getConfig().set(
                "features.public-chat",
                publicChatEnabled
        );

        saveConfig();

        if (publicChatEnabled) {

            player.sendMessage(color(
                    getConfig().getString(
                            "messages.public-chat-enabled",
                            "&aGenel sohbet açıldı."
                    )
            ));

        } else {

            player.sendMessage(color(
                    getConfig().getString(
                            "messages.public-chat-disabled",
                            "&cGenel sohbet kapatıldı."
                    )
            ));
        }
    }

    // Public chat kapalıysa mesaj gönderilemez
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onChat(
            AsyncChatEvent event
    ) {

        if (!publicChatEnabled) {

            event.setCancelled(true);

            event.getPlayer().sendMessage(
                    color(
                            getConfig().getString(
                                    "messages.chat-disabled",
                                    "&cGenel sohbet şu anda kapalı."
                            )
                    )
            );
        }
    }

    // =========================================================
    // MOB SPAWN
    // =========================================================

    private void toggleMobSpawn(
            Player player
    ) {

        mobSpawnEnabled =
                !mobSpawnEnabled;

        getConfig().set(
                "features.mob-spawn",
                mobSpawnEnabled
        );

        saveConfig();

        if (mobSpawnEnabled) {

            player.sendMessage(color(
                    getConfig().getString(
                            "messages.mob-spawn-enabled",
                            "&aMob doğması açıldı."
                    )
            ));

        } else {

            player.sendMessage(color(
                    getConfig().getString(
                            "messages.mob-spawn-disabled",
                            "&cMob doğması kapatıldı."
                    )
            ));
        }
    }

    // =========================================================
    // CREATURE SPAWN
    // =========================================================

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCreatureSpawn(
            CreatureSpawnEvent event
    ) {

        // Sadece doğal spawnları kontrol ediyoruz.
        // Spawner / plugin / command spawnları bozulmaz.
        if (event.getSpawnReason()
                != CreatureSpawnEvent.SpawnReason.NATURAL) {

            return;
        }

        EntityType type =
                event.getEntityType();

        // Phantom ayrı kontrol edilir
        if (type == EntityType.PHANTOM) {

            if (!phantomSpawnEnabled) {
                event.setCancelled(true);
            }

            return;
        }

        // Diğer doğal moblar
        if (!mobSpawnEnabled) {
            event.setCancelled(true);
        }
    }

    // =========================================================
    // PHANTOM
    // =========================================================

    private void togglePhantomSpawn(
            Player player
    ) {

        phantomSpawnEnabled =
                !phantomSpawnEnabled;

        getConfig().set(
                "features.phantom-spawn",
                phantomSpawnEnabled
        );

        saveConfig();

        if (phantomSpawnEnabled) {

            player.sendMessage(color(
                    getConfig().getString(
                            "messages.phantom-spawn-enabled",
                            "&aPhantom doğması açıldı."
                    )
            ));

        } else {

            player.sendMessage(color(
                    getConfig().getString(
                            "messages.phantom-spawn-disabled",
                            "&cPhantom doğması kapatıldı."
                    )
            ));
        }
    }

    // =========================================================
    // CONFIG COMMAND
    // =========================================================

    private void executeCommands(
            Player player,
            List<String> commands
    ) {

        for (String command : commands) {

            if (command == null ||
                    command.trim().isEmpty()) {

                continue;
            }

            command = command
                    .replace(
                            "%player%",
                            player.getName()
                    )
                    .replace(
                            "%player_name%",
                            player.getName()
                    );

            // -------------------------------------------------
            // CONSOLE
            // -------------------------------------------------

            if (command.startsWith("[console]")) {

                command = command
                        .substring(
                                "[console]".length()
                        )
                        .trim();

                if (!command.isEmpty()) {

                    Bukkit.dispatchCommand(
                            Bukkit.getConsoleSender(),
                            command
                    );
                }

                continue;
            }

            // -------------------------------------------------
            // PLAYER
            // -------------------------------------------------

            if (command.startsWith("[player]")) {

                command = command
                        .substring(
                                "[player]".length()
                        )
                        .trim();

                if (!command.isEmpty()) {

                    player.performCommand(
                            command
                    );
                }

                continue;
            }

            // -------------------------------------------------
            // PREFIX YOXDURSA PLAYER
            // -------------------------------------------------

            player.performCommand(
                    command.trim()
            );
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

            case "public-chat" -> {

                status = publicChatEnabled
                        ? "&aAçık"
                        : "&cKapalı";
            }

            case "mob-spawn" -> {

                status = mobSpawnEnabled
                        ? "&aAçık"
                        : "&cKapalı";
            }

            case "phantom-spawn" -> {

                status = phantomSpawnEnabled
                        ? "&aAçık"
                        : "&cKapalı";
            }

            default -> {
                status = "";
            }
        }

        return text
                .replace(
                        "%status%",
                        status
                );
    }

    // =========================================================
    // COLOR
    // =========================================================

    private String color(
            String text
    ) {

        if (text == null) {
            return "";
        }

        return text.replace(
                "&",
                "§"
        );
    }

    // =========================================================
    // GUI HOLDER
    // =========================================================

    private static class SettingsHolder
            implements InventoryHolder {

        private Inventory inventory;

        private final Map<Integer, String> slots =
                new HashMap<>();

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}
