package ict.minesunshineone.mcediagui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.UUID;

/**
 * Mcedia GUI 插件主类。
 * <p>
 * 蹲下右键 mcedia 盔甲架 → Dialog 配置播放器
 * 不蹲下右键 → 保留原有书本放置逻辑
 * /mcedia admin → 管理员面板
 * <p>
 * Folia 兼容：不使用 BukkitScheduler，所有实体操作通过 entity.getScheduler() 调度。
 */
public class McediaGuiPlugin extends JavaPlugin implements Listener {

    private DatabaseManager dbManager;
    private PlayerDataManager dataManager;
    private DialogHelper dialogHelper;
    private AdminGuiManager adminGui;
    private List<String> armorStandPatterns;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        armorStandPatterns = getConfig().getStringList("armor-stand-patterns");
        int defaultLimit = getConfig().getInt("default-limit", 1);

        dbManager = new DatabaseManager(this);
        dbManager.init();

        dataManager = new PlayerDataManager(this, dbManager, defaultLimit);
        dialogHelper = new DialogHelper(this);
        adminGui = new AdminGuiManager(this);

        Bukkit.getPluginManager().registerEvents(this, this);
        getLogger().info("Mcedia GUI 插件已启用");
    }

    @Override
    public void onDisable() {
        if (dataManager != null) {
            dataManager.saveNow();
        }
        if (dbManager != null) {
            dbManager.close();
        }
        getLogger().info("Mcedia GUI 插件已禁用");
    }

    public PlayerDataManager getDataManager() {
        return dataManager;
    }

    public DialogHelper getDialogHelper() {
        return dialogHelper;
    }

    // ===== 事件监听 =====

    /**
     * 蹲下 + 右键 mcedia 盔甲架 → 打开配置 Dialog
     * 使用 HIGH 优先级确保先于其他插件处理
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteractAtEntity(PlayerInteractAtEntityEvent event) {
        if (!(event.getRightClicked() instanceof ArmorStand armorStand))
            return;
        if (!event.getPlayer().isSneaking())
            return;
        if (!isMediaPlayer(armorStand))
            return;
        if (event.getHand() != EquipmentSlot.HAND)
            return;

        Player player = event.getPlayer();
        event.setCancelled(true);

        if (!player.hasPermission("mcedia.use")) {
            player.sendMessage(Component.text("你没有使用 Mcedia 的权限", NamedTextColor.RED));
            return;
        }

        dialogHelper.openConfigDialog(player, armorStand);
    }

    /**
     * mcedia 盔甲架被破坏时自动清除数据库记录
     */
    @EventHandler
    public void onEntityDeath(org.bukkit.event.entity.EntityDeathEvent event) {
        if (!(event.getEntity() instanceof ArmorStand armorStand))
            return;
        if (!isMediaPlayer(armorStand))
            return;

        dataManager.removeArmorStand(armorStand.getUniqueId());
        getLogger().fine("mcedia 盔甲架被破坏，已清除记录: " + armorStand.getUniqueId());
    }

    /**
     * 玩家退出时清理缓存，防止内存泄漏
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        if (dialogHelper != null) {
            dialogHelper.cleanupPlayer(uuid);
        }
    }

    // ===== 命令处理 =====

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
            @NotNull String label, @NotNull String[] args) {
        if (!command.getName().equalsIgnoreCase("mcediagui"))
            return false;

        if (!(sender instanceof Player player)) {
            sender.sendMessage("此命令只能由玩家执行");
            return true;
        }

        if (args.length >= 1 && args[0].equalsIgnoreCase("admin")) {
            if (!player.hasPermission("mcedia.admin")) {
                player.sendMessage(Component.text("你没有管理员权限", NamedTextColor.RED));
                return true;
            }
            adminGui.open(player);
            return true;
        }

        // 帮助信息
        player.sendMessage(Component.text("Mcedia GUI 插件", NamedTextColor.GOLD));
        player.sendMessage(Component.text("  蹲下右键 mcedia 盔甲架 → 配置播放器", NamedTextColor.GRAY));
        player.sendMessage(Component.text("  /mcediagui admin → 管理面板（需管理员权限）", NamedTextColor.GRAY));

        int count = dataManager.getPlayerCount(player);
        int max = dataManager.getMaxLimit(player);
        String limitText = max == Integer.MAX_VALUE ? "无限制" : String.valueOf(max);
        player.sendMessage(Component.text("  当前播放器: " + count + "/" + limitText, NamedTextColor.AQUA));
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
            @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1 && sender.hasPermission("mcedia.admin")) {
            return List.of("admin");
        }
        return List.of();
    }

    // ===== 工具方法 =====

    private boolean isMediaPlayer(ArmorStand armorStand) {
        String name = armorStand.getName().toLowerCase();
        for (String pattern : armorStandPatterns) {
            if (name.contains(pattern.toLowerCase()))
                return true;
        }
        return false;
    }
}
