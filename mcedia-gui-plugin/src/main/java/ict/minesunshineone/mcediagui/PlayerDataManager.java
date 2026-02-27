package ict.minesunshineone.mcediagui;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 管理玩家与播放器盔甲架的所有权关系，处理数量限制。
 * <p>
 * 内存层使用 ConcurrentHashMap 缓存，持久层使用 H2 数据库。
 * 写入操作采用 dirty flag + 延迟批量写入策略。
 */
public class PlayerDataManager {

    public record TrackedArmorStand(
            UUID armorStandUUID,
            UUID ownerUUID,
            String ownerName,
            String worldName,
            double x, double y, double z,
            String url,
            long createdAt) {
    }

    private final Plugin plugin;
    private final DatabaseManager db;
    private final ConcurrentHashMap<UUID, List<TrackedArmorStand>> playerData = new ConcurrentHashMap<>();
    private int defaultLimit;

    private final AtomicBoolean dirty = new AtomicBoolean(false);
    private final AtomicBoolean saveScheduled = new AtomicBoolean(false);
    private static final long SAVE_DELAY_SECONDS = 3;

    /** 记录需要写入数据库的变更 */
    private final List<Runnable> pendingDbOps = Collections.synchronizedList(new ArrayList<>());

    public PlayerDataManager(Plugin plugin, DatabaseManager db, int defaultLimit) {
        this.plugin = plugin;
        this.db = db;
        this.defaultLimit = defaultLimit;
        loadFromDb();
    }

    public void setDefaultLimit(int limit) {
        this.defaultLimit = limit;
    }

    public int getMaxLimit(Player player) {
        if (player.hasPermission("mcedia.admin")) {
            return Integer.MAX_VALUE;
        }
        int max = defaultLimit;
        for (var perm : player.getEffectivePermissions()) {
            String name = perm.getPermission();
            if (name.startsWith("mcedia.limit.") && perm.getValue()) {
                try {
                    int n = Integer.parseInt(name.substring("mcedia.limit.".length()));
                    max = Math.max(max, n);
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return max;
    }

    public int getPlayerCount(Player player) {
        List<TrackedArmorStand> list = playerData.get(player.getUniqueId());
        return list == null ? 0 : list.size();
    }

    /**
     * 注册新的播放器盔甲架。超限时自动清除最旧的。
     */
    public void addArmorStand(Player player, ArmorStand armorStand, String url) {
        UUID playerUUID = player.getUniqueId();
        UUID asUUID = armorStand.getUniqueId();
        List<TrackedArmorStand> list = playerData.computeIfAbsent(playerUUID,
                k -> Collections.synchronizedList(new ArrayList<>()));

        List<TrackedArmorStand> toRemove = new ArrayList<>();

        // 跨玩家去重
        for (var entry : playerData.entrySet()) {
            if (!entry.getKey().equals(playerUUID)) {
                List<TrackedArmorStand> otherList = entry.getValue();
                synchronized (otherList) {
                    otherList.removeIf(t -> t.armorStandUUID.equals(asUUID));
                }
            }
        }

        synchronized (list) {
            list.removeIf(t -> t.armorStandUUID.equals(asUUID));

            int maxLimit = getMaxLimit(player);
            while (list.size() >= maxLimit && !list.isEmpty()) {
                toRemove.add(list.removeFirst());
            }

            Location loc = armorStand.getLocation();
            TrackedArmorStand tracked = new TrackedArmorStand(
                    asUUID, playerUUID, player.getName(),
                    loc.getWorld().getName(), loc.getX(), loc.getY(), loc.getZ(),
                    url, System.currentTimeMillis());
            list.add(tracked);

            // 数据库操作入队
            pendingDbOps.add(() -> db.upsert(tracked));
        }

        for (TrackedArmorStand oldest : toRemove) {
            clearArmorStandBooks(oldest);
            pendingDbOps.add(() -> db.delete(oldest.armorStandUUID()));
            player.getScheduler().run(plugin, task -> player.sendMessage(
                    net.kyori.adventure.text.Component.text(
                            "⚠ 已自动移除旧播放器: " + oldest.url,
                            net.kyori.adventure.text.format.NamedTextColor.YELLOW)),
                    null);
        }

        markDirty();
    }

    public void removeArmorStand(UUID armorStandUUID) {
        for (var entry : playerData.entrySet()) {
            List<TrackedArmorStand> list = entry.getValue();
            synchronized (list) {
                list.removeIf(t -> t.armorStandUUID.equals(armorStandUUID));
            }
        }
        pendingDbOps.add(() -> db.delete(armorStandUUID));
        markDirty();
    }

    public void deleteArmorStand(TrackedArmorStand tracked) {
        clearArmorStandBooks(tracked);
        removeArmorStand(tracked.armorStandUUID);
    }

    public List<TrackedArmorStand> getAllTracked() {
        List<TrackedArmorStand> all = new ArrayList<>();
        for (var list : playerData.values()) {
            synchronized (list) {
                all.addAll(list);
            }
        }
        all.sort(Comparator.comparingLong(TrackedArmorStand::createdAt).reversed());
        return all;
    }

    public List<TrackedArmorStand> getPlayerTracked(UUID playerUUID) {
        List<TrackedArmorStand> list = playerData.get(playerUUID);
        if (list == null)
            return List.of();
        synchronized (list) {
            return new ArrayList<>(list);
        }
    }

    private void clearArmorStandBooks(TrackedArmorStand tracked) {
        Entity entity = Bukkit.getEntity(tracked.armorStandUUID);
        if (entity instanceof ArmorStand armorStand) {
            armorStand.getScheduler().run(plugin, task -> {
                armorStand.getEquipment().setItemInMainHand(null);
                armorStand.getEquipment().setItemInOffHand(null);
            }, null);
        }
    }

    public Location getLocation(TrackedArmorStand tracked) {
        World world = Bukkit.getWorld(tracked.worldName);
        if (world == null)
            return null;
        return new Location(world, tracked.x, tracked.y, tracked.z);
    }

    // ===== 持久化 =====

    private void markDirty() {
        dirty.set(true);
        if (saveScheduled.compareAndSet(false, true)) {
            Bukkit.getAsyncScheduler().runDelayed(plugin, task -> {
                saveScheduled.set(false);
                if (dirty.compareAndSet(true, false)) {
                    flushDbOps();
                }
            }, SAVE_DELAY_SECONDS, TimeUnit.SECONDS);
        }
    }

    /**
     * 批量执行待持久化的数据库操作
     */
    private void flushDbOps() {
        List<Runnable> ops;
        synchronized (pendingDbOps) {
            ops = new ArrayList<>(pendingDbOps);
            pendingDbOps.clear();
        }
        for (Runnable op : ops) {
            op.run();
        }
    }

    public void saveNow() {
        dirty.set(false);
        flushDbOps();
    }

    private void loadFromDb() {
        List<TrackedArmorStand> all = db.loadAll();
        for (TrackedArmorStand t : all) {
            playerData.computeIfAbsent(t.ownerUUID(),
                    k -> Collections.synchronizedList(new ArrayList<>())).add(t);
        }
        plugin.getLogger().info("已从数据库加载 " + all.size() + " 个播放器记录");
    }
}
