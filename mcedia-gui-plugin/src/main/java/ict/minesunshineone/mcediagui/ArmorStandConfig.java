package ict.minesunshineone.mcediagui;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.ArmorStand;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.util.EulerAngle;

/**
 * 单个盔甲架播放器的配置数据。
 * 字段对应客户端 Fabric Mod 从书与笔中读取的参数。
 */
public class ArmorStandConfig {

    // ===== 媒体 =====
    public String url = "";
    public String startTime = "";

    // ===== 音量 =====
    public float volume = 5.0f;
    public float volumeRangeMin = 2.0f;
    public float volumeRangeMax = 500.0f;

    // ===== 视觉 =====
    public float scale = 1.0f;
    public float offsetX = 0, offsetY = 0, offsetZ = 1;

    // ===== 音源位置偏移 =====
    public float audioOffsetX = 0, audioOffsetY = 0, audioOffsetZ = 0;

    // ===== 旋转角度（度） =====
    public float yaw = 0;
    public float headPoseX = 0, headPoseY = 0, headPoseZ = 0;

    // ===== 标签 =====
    public boolean looping = false;

    /**
     * 从盔甲架当前状态读取配置
     */
    @SuppressWarnings("deprecation")
    public static ArmorStandConfig readFromArmorStand(ArmorStand armorStand) {
        ArmorStandConfig config = new ArmorStandConfig();

        // 读取主手书（URL + 开始时间）
        ItemStack mainHand = armorStand.getEquipment().getItemInMainHand();
        if (mainHand.getType() == Material.WRITABLE_BOOK) {
            BookMeta meta = (BookMeta) mainHand.getItemMeta();
            if (meta != null && meta.hasPages()) {
                String page1 = meta.getPage(1);
                String[] lines = page1.split("\n");
                if (lines.length > 0)
                    config.url = lines[0];
                if (lines.length > 1)
                    config.startTime = lines[1];
            }
        }

        // 读取副手书（偏移/音量/标签）
        ItemStack offHand = armorStand.getEquipment().getItemInOffHand();
        if (offHand.getType() == Material.WRITABLE_BOOK) {
            BookMeta meta = (BookMeta) offHand.getItemMeta();
            if (meta != null && meta.hasPages()) {
                if (meta.getPageCount() >= 1) {
                    try {
                        String[] v = meta.getPage(1).split("\n");
                        config.offsetX = Float.parseFloat(v[0]);
                        config.offsetY = Float.parseFloat(v[1]);
                        config.offsetZ = Float.parseFloat(v[2]);
                        config.scale = Float.parseFloat(v[3]);
                    } catch (Exception ignored) {
                    }
                }
                if (meta.getPageCount() >= 2) {
                    try {
                        String[] v = meta.getPage(2).split("\n");
                        config.audioOffsetX = Float.parseFloat(v[0]);
                        config.audioOffsetY = Float.parseFloat(v[1]);
                        config.audioOffsetZ = Float.parseFloat(v[2]);
                        config.volume = Float.parseFloat(v[3]);
                        config.volumeRangeMin = Float.parseFloat(v[4]);
                        config.volumeRangeMax = Float.parseFloat(v[5]);
                    } catch (Exception ignored) {
                    }
                }
                if (meta.getPageCount() >= 3) {
                    String flags = meta.getPage(3);
                    config.looping = flags.contains("looping") || flags.contains("循环播放");
                }
            }
        }

        // 读取旋转
        config.yaw = armorStand.getLocation().getYaw();
        EulerAngle headPose = armorStand.getHeadPose();
        config.headPoseX = (float) Math.toDegrees(headPose.getX());
        config.headPoseY = (float) Math.toDegrees(headPose.getY());
        config.headPoseZ = (float) Math.toDegrees(headPose.getZ());

        return config;
    }

    /**
     * 生成主手书与笔，displayName 为 "玩家名:时间戳" 用于客户端同步
     */
    @SuppressWarnings("deprecation")
    public ItemStack buildMainHandBook(String playerName) {
        ItemStack book = new ItemStack(Material.WRITABLE_BOOK);
        BookMeta meta = (BookMeta) book.getItemMeta();

        StringBuilder page = new StringBuilder(url);
        if (startTime != null && !startTime.isEmpty()) {
            page.append("\n").append(startTime);
        }
        meta.addPage(page.toString());
        meta.displayName(Component.text(playerName + ":" + System.currentTimeMillis()));

        book.setItemMeta(meta);
        return book;
    }

    /**
     * 生成副手书与笔（偏移/音频/标签）
     */
    @SuppressWarnings("deprecation")
    public ItemStack buildOffHandBook() {
        ItemStack book = new ItemStack(Material.WRITABLE_BOOK);
        BookMeta meta = (BookMeta) book.getItemMeta();

        meta.addPage(offsetX + "\n" + offsetY + "\n" + offsetZ + "\n" + scale);
        meta.addPage(audioOffsetX + "\n" + audioOffsetY + "\n" + audioOffsetZ + "\n"
                + volume + "\n" + volumeRangeMin + "\n" + volumeRangeMax);

        StringBuilder flags = new StringBuilder();
        if (looping)
            flags.append("looping\n");
        meta.addPage(flags.toString());

        book.setItemMeta(meta);
        return book;
    }

    /**
     * 将旋转配置应用到盔甲架实体，并隐藏盔甲架视觉（客户端 Mod 仍可读取数据）
     */
    public void applyRotation(ArmorStand armorStand) {
        armorStand.setRotation(yaw, 0);
        armorStand.setHeadPose(new EulerAngle(
                Math.toRadians(headPoseX),
                Math.toRadians(headPoseY),
                Math.toRadians(headPoseZ)));

        // 单独隐藏盔甲架名称标签
        armorStand.setCustomNameVisible(false);

        // 盔甲架被破坏时不掉落装备（书与笔）
        armorStand.getEquipment().setItemInMainHandDropChance(0);
        armorStand.getEquipment().setItemInOffHandDropChance(0);
    }
}
