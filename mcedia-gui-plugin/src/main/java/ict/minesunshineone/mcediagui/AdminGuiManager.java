package ict.minesunshineone.mcediagui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.text.SimpleDateFormat;
import java.util.*;

/**
 * 管理员箱子菜单（大箱子 54 格）。
 * 前 45 格展示播放器条目，最后一行为翻页导航。
 * 左键传送、右键删除。
 */
public class AdminGuiManager implements Listener {

    private static final int CHEST_SIZE = 54; // 大箱子
    private static final int ITEMS_PER_PAGE = 45; // 前5行
    private static final Component TITLE_PREFIX = Component.text("Mcedia 管理面板", NamedTextColor.DARK_PURPLE)
            .decoration(TextDecoration.BOLD, true);

    private final McediaGuiPlugin plugin;

    /** 玩家当前查看页码 */
    private final Map<UUID, Integer> viewerPage = new HashMap<>();
    /** 玩家当前页面对应的数据快照 */
    private final Map<UUID, List<PlayerDataManager.TrackedArmorStand>> viewerData = new HashMap<>();

    public AdminGuiManager(McediaGuiPlugin plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void open(Player admin) {
        open(admin, 0);
    }

    private void open(Player admin, int page) {
        List<PlayerDataManager.TrackedArmorStand> allTracked = plugin.getDataManager().getAllTracked();
        if (allTracked.isEmpty()) {
            admin.sendMessage(Component.text("当前没有任何活跃的播放器", NamedTextColor.YELLOW));
            return;
        }

        int totalPages = (allTracked.size() - 1) / ITEMS_PER_PAGE + 1;
        page = Math.max(0, Math.min(page, totalPages - 1));

        // 当前页数据切片
        int start = page * ITEMS_PER_PAGE;
        int end = Math.min(start + ITEMS_PER_PAGE, allTracked.size());
        List<PlayerDataManager.TrackedArmorStand> pageData = allTracked.subList(start, end);

        // 标题包含页码
        Component title = TITLE_PREFIX.append(
                Component.text(" (" + (page + 1) + "/" + totalPages + ")", NamedTextColor.GRAY)
                        .decoration(TextDecoration.BOLD, false));

        Inventory inv = Bukkit.createInventory(null, CHEST_SIZE, title);
        SimpleDateFormat sdf = new SimpleDateFormat("MM-dd HH:mm");

        for (int i = 0; i < pageData.size(); i++) {
            inv.setItem(i, buildItem(pageData.get(i), sdf));
        }

        // 导航栏（第6行）
        if (page > 0) {
            inv.setItem(45, buildNavItem(Material.ARROW,
                    Component.text("上一页", NamedTextColor.GREEN)));
        }
        inv.setItem(49, buildNavItem(Material.PAPER,
                Component.text("第 " + (page + 1) + " / " + totalPages + " 页", NamedTextColor.WHITE)));
        if (page < totalPages - 1) {
            inv.setItem(53, buildNavItem(Material.ARROW,
                    Component.text("下一页", NamedTextColor.GREEN)));
        }

        viewerPage.put(admin.getUniqueId(), page);
        viewerData.put(admin.getUniqueId(), pageData);
        admin.openInventory(inv);
    }

    private ItemStack buildItem(PlayerDataManager.TrackedArmorStand tracked, SimpleDateFormat sdf) {
        ItemStack item = new ItemStack(Material.ARMOR_STAND);
        ItemMeta meta = item.getItemMeta();

        // 标题：创建人：ID
        meta.displayName(
                Component.text(tracked.ownerName() + " : " + tracked.armorStandUUID().toString().substring(0, 8),
                        NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));

        String urlPreview = tracked.url().length() > 50
                ? tracked.url().substring(0, 50) + "..."
                : tracked.url();

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(""));
        lore.add(loreLine("世界", tracked.worldName()));
        lore.add(loreLine("位置", String.format("%.0f, %.0f, %.0f", tracked.x(), tracked.y(), tracked.z())));
        lore.add(loreLine("链接", urlPreview));
        lore.add(loreLine("时间", sdf.format(new Date(tracked.createdAt()))));
        lore.add(Component.text(""));
        lore.add(Component.text("左键 传送到播放器", NamedTextColor.GREEN)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("右键 删除播放器", NamedTextColor.RED)
                .decoration(TextDecoration.ITALIC, false));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private Component loreLine(String label, String value) {
        return Component.text(label + ": ", NamedTextColor.GRAY)
                .append(Component.text(value, NamedTextColor.WHITE))
                .decoration(TextDecoration.ITALIC, false);
    }

    private ItemStack buildNavItem(Material material, Component name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(name.decoration(TextDecoration.ITALIC, false));
        item.setItemMeta(meta);
        return item;
    }

    private boolean isAdminGui(Component title) {
        // 检查标题是否以 "Mcedia 管理面板" 开头
        if (title == null)
            return false;
        return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                .plainText().serialize(title).startsWith("Mcedia 管理面板");
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player))
            return;
        if (!isAdminGui(event.getView().title()))
            return;

        event.setCancelled(true);

        int slot = event.getRawSlot();
        if (slot < 0 || slot >= CHEST_SIZE)
            return;

        UUID uid = player.getUniqueId();
        Integer page = viewerPage.get(uid);
        if (page == null)
            return;

        // 导航按钮
        if (slot == 45 && page > 0) {
            player.closeInventory();
            player.getScheduler().run(plugin, t -> open(player, page - 1), null);
            return;
        }
        if (slot == 53) {
            player.closeInventory();
            player.getScheduler().run(plugin, t -> open(player, page + 1), null);
            return;
        }

        // 数据条目
        List<PlayerDataManager.TrackedArmorStand> tracked = viewerData.get(uid);
        if (tracked == null || slot >= tracked.size())
            return;

        PlayerDataManager.TrackedArmorStand target = tracked.get(slot);

        if (event.getClick() == ClickType.LEFT) {
            Location loc = plugin.getDataManager().getLocation(target);
            if (loc != null) {
                player.closeInventory();
                player.teleportAsync(loc);
                player.sendMessage(Component.text("已传送到播放器位置", NamedTextColor.GREEN));
            } else {
                player.sendMessage(Component.text("目标世界未加载", NamedTextColor.RED));
            }
        } else if (event.getClick() == ClickType.RIGHT) {
            plugin.getDataManager().deleteArmorStand(target);
            player.sendMessage(Component.text("播放器已删除: " + target.ownerName(), NamedTextColor.GREEN));
            player.closeInventory();
            player.getScheduler().run(plugin, t -> open(player, page), null);
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (isAdminGui(event.getView().title())) {
            event.setCancelled(true);
        }
    }
}
