package ict.minesunshineone.mcediagui;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.dialog.DialogResponseView;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Dialog 构建与回调处理。
 * 普通玩家配置 Dialog、管理员列表 Dialog、管理员详情 Dialog。
 */
public class DialogHelper {

    private final McediaGuiPlugin plugin;
    /** 记录玩家当前正在编辑的盔甲架 UUID */
    private final Map<UUID, UUID> pendingEdits = new ConcurrentHashMap<>();

    public DialogHelper(McediaGuiPlugin plugin) {
        this.plugin = plugin;
    }

    /** 玩家退出时清理待处理数据，防止内存泄漏 */
    public void cleanupPlayer(UUID playerUUID) {
        pendingEdits.remove(playerUUID);
    }

    /**
     * 打开配置 Dialog（普通玩家蹲下右键时调用）
     */
    public void openConfigDialog(Player player, ArmorStand armorStand) {
        pendingEdits.put(player.getUniqueId(), armorStand.getUniqueId());
        ArmorStandConfig current = ArmorStandConfig.readFromArmorStand(armorStand);
        player.showDialog(buildConfigDialog(current));
    }

    /**
     * 管理员远程编辑
     */
    public void openRemoteEditDialog(Player admin, PlayerDataManager.TrackedArmorStand tracked) {
        Entity entity = Bukkit.getEntity(tracked.armorStandUUID());
        if (!(entity instanceof ArmorStand armorStand)) {
            admin.sendMessage(Component.text("无法找到该盔甲架（可能未加载）", NamedTextColor.RED));
            return;
        }
        openConfigDialog(admin, armorStand);
    }

    // ======================== 普通配置 Dialog ========================

    private Dialog buildConfigDialog(ArmorStandConfig config) {
        return Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(Component.text("Mcedia 播放器配置"))
                        .canCloseWithEscape(true)
                        .body(List.of(
                                DialogBody.plainMessage(Component.text(
                                        "配置盔甲架播放器参数。确认后将自动应用。", NamedTextColor.GRAY))))
                        .inputs(buildConfigInputs(config))
                        .build())
                .type(DialogType.confirmation(
                        ActionButton.create(
                                Component.text("✔ 确认应用", TextColor.color(0x55FF55)),
                                Component.text("将配置写入盔甲架"),
                                120,
                                DialogAction.customClick(
                                        this::handleConfigConfirm,
                                        ClickCallback.Options.builder().uses(1).build())),
                        ActionButton.create(
                                Component.text("✖ 取消", TextColor.color(0xFF5555)),
                                Component.text("放弃修改"),
                                120,
                                null))));
    }

    private List<DialogInput> buildConfigInputs(ArmorStandConfig c) {
        List<DialogInput> inputs = new ArrayList<>();

        // --- 媒体 ---
        inputs.add(DialogInput.text("url", Component.text("📺 播放链接", NamedTextColor.AQUA))
                .initial(c.url).width(350).maxLength(1024).build());
        inputs.add(DialogInput.text("start_time", Component.text("⏱ 开始时间 (H:M:S)", NamedTextColor.AQUA))
                .initial(c.startTime).width(200).maxLength(32).build());

        inputs.add(DialogInput.bool("looping", Component.text("🔁 循环播放", NamedTextColor.RED))
                .initial(c.looping).build());

        // --- 音量 ---
        inputs.add(numRange("volume", "🔊 最大音量", NamedTextColor.GREEN, 0, 20, 0.5f, c.volume));
        inputs.add(numRange("volume_min", "🔉 音量近距范围", NamedTextColor.GREEN, 0, 50, 1, c.volumeRangeMin));
        inputs.add(numRange("volume_max", "🔈 音量远距范围", NamedTextColor.GREEN, 0, 1000, 10, c.volumeRangeMax));

        // --- 视觉 ---
        inputs.add(numRange("scale", "📐 缩放大小", NamedTextColor.YELLOW, 0.1f, 10, 0.1f, c.scale));

        // --- 旋转角度 ---
        inputs.add(numRange("yaw", "🔄 主体旋转 Yaw", NamedTextColor.GOLD, -180, 180, 1, c.yaw));
        inputs.add(numRange("head_x", "📐 头部旋转 X", NamedTextColor.GOLD, -180, 180, 1, c.headPoseX));
        inputs.add(numRange("head_y", "📐 头部旋转 Y", NamedTextColor.GOLD, -180, 180, 1, c.headPoseY));
        inputs.add(numRange("head_z", "📐 头部旋转 Z", NamedTextColor.GOLD, -180, 180, 1, c.headPoseZ));

        // --- 画面偏移 ---
        inputs.add(numRange("offset_x", "画面偏移 X", NamedTextColor.LIGHT_PURPLE, -10, 10, 0.1f, c.offsetX));
        inputs.add(numRange("offset_y", "画面偏移 Y", NamedTextColor.LIGHT_PURPLE, -10, 10, 0.1f, c.offsetY));
        inputs.add(numRange("offset_z", "画面偏移 Z", NamedTextColor.LIGHT_PURPLE, -10, 10, 0.1f, c.offsetZ));

        // --- 音源偏移 ---
        inputs.add(numRange("audio_x", "音源偏移 X", NamedTextColor.BLUE, -10, 10, 0.1f, c.audioOffsetX));
        inputs.add(numRange("audio_y", "音源偏移 Y", NamedTextColor.BLUE, -10, 10, 0.1f, c.audioOffsetY));
        inputs.add(numRange("audio_z", "音源偏移 Z", NamedTextColor.BLUE, -10, 10, 0.1f, c.audioOffsetZ));

        return inputs;
    }

    /** 构建数值滑动条的快捷方法 */
    private DialogInput numRange(String key, String label, NamedTextColor color,
            float min, float max, float step, float initial) {
        return DialogInput.numberRange(key, Component.text(label, color), min, max)
                .step(step).initial(initial).width(300).build();
    }

    /**
     * 配置确认回调 — Folia 兼容：通过 entity.getScheduler() 在对应区域线程执行盔甲架修改
     */
    private void handleConfigConfirm(DialogResponseView view, Audience audience) {
        if (!(audience instanceof Player player))
            return;

        UUID armorStandUUID = pendingEdits.remove(player.getUniqueId());
        if (armorStandUUID == null) {
            player.sendMessage(Component.text("配置会话已过期，请重新操作", NamedTextColor.RED));
            return;
        }

        Entity entity = Bukkit.getEntity(armorStandUUID);
        if (!(entity instanceof ArmorStand armorStand)) {
            player.sendMessage(Component.text("无法找到盔甲架", NamedTextColor.RED));
            return;
        }

        // 从 Dialog 读取所有配置值
        ArmorStandConfig config = new ArmorStandConfig();
        config.url = nvl(view.getText("url"), "");
        config.startTime = nvl(view.getText("start_time"), "");
        config.looping = safeBoolean(view, "looping", false);
        config.volume = safeFloat(view, "volume", 5f);
        config.volumeRangeMin = safeFloat(view, "volume_min", 2f);
        config.volumeRangeMax = safeFloat(view, "volume_max", 500f);
        config.scale = safeFloat(view, "scale", 1f);
        config.yaw = safeFloat(view, "yaw", 0f);
        config.headPoseX = safeFloat(view, "head_x", 0f);
        config.headPoseY = safeFloat(view, "head_y", 0f);
        config.headPoseZ = safeFloat(view, "head_z", 0f);
        config.offsetX = safeFloat(view, "offset_x", 0f);
        config.offsetY = safeFloat(view, "offset_y", 0f);
        config.offsetZ = safeFloat(view, "offset_z", 1f);
        config.audioOffsetX = safeFloat(view, "audio_x", 0f);
        config.audioOffsetY = safeFloat(view, "audio_y", 0f);
        config.audioOffsetZ = safeFloat(view, "audio_z", 0f);

        if (config.url.isEmpty()) {
            player.sendMessage(Component.text("播放链接不能为空！", NamedTextColor.RED));
            return;
        }

        // Folia 兼容：在盔甲架实体所属的区域线程执行修改
        armorStand.getScheduler().run(plugin, task -> {
            armorStand.getEquipment().setItemInMainHand(config.buildMainHandBook(player.getName()));
            armorStand.getEquipment().setItemInOffHand(config.buildOffHandBook());
            config.applyRotation(armorStand);

            plugin.getDataManager().addArmorStand(player, armorStand, config.url);

            String statusMsg = "✔ 播放器配置已应用！";
            player.getScheduler().run(plugin,
                    t -> player.sendMessage(Component.text(statusMsg, NamedTextColor.GREEN)),
                    null);

            plugin.getLogger().fine("玩家 " + player.getName() + " 配置了播放器: " + config.url);
        }, () -> player.sendMessage(Component.text("盔甲架所在区域未加载", NamedTextColor.RED)));
    }

    // ======================== 管理员 Dialog ========================

    /**
     * 打开管理员面板
     */
    public void openAdminPanel(Player admin) {
        List<PlayerDataManager.TrackedArmorStand> allTracked = plugin.getDataManager().getAllTracked();
        if (allTracked.isEmpty()) {
            admin.sendMessage(Component.text("当前没有任何活跃的播放器", NamedTextColor.YELLOW));
            return;
        }

        List<ActionButton> buttons = new ArrayList<>();
        SimpleDateFormat sdf = new SimpleDateFormat("MM-dd HH:mm");

        for (PlayerDataManager.TrackedArmorStand tracked : allTracked) {
            String label = tracked.ownerName() + " @ " + tracked.worldName();
            String urlPreview = tracked.url().length() > 40
                    ? tracked.url().substring(0, 40) + "..."
                    : tracked.url();
            String tooltip = String.format("位置: %s (%.0f, %.0f, %.0f)\n链接: %s\n时间: %s",
                    tracked.worldName(), tracked.x(), tracked.y(), tracked.z(),
                    urlPreview, sdf.format(new Date(tracked.createdAt())));

            buttons.add(ActionButton.create(
                    Component.text(label, NamedTextColor.WHITE),
                    Component.text(tooltip),
                    250,
                    DialogAction.customClick(
                            (view, audience) -> {
                                if (audience instanceof Player p) {
                                    p.showDialog(buildAdminDetailDialog(tracked));
                                }
                            },
                            ClickCallback.Options.builder().uses(1).build())));
        }

        Dialog adminDialog = Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(Component.text("Mcedia 管理面板"))
                        .canCloseWithEscape(true)
                        .body(List.of(DialogBody.plainMessage(Component.text(
                                "共 " + allTracked.size() + " 个活跃播放器。点击查看详情。",
                                NamedTextColor.GRAY))))
                        .build())
                .type(DialogType.multiAction(buttons).build()));
        admin.showDialog(adminDialog);
    }

    private Dialog buildAdminDetailDialog(PlayerDataManager.TrackedArmorStand tracked) {
        String info = String.format(
                "所属: %s\n世界: %s\n位置: (%.1f, %.1f, %.1f)\n链接: %s",
                tracked.ownerName(), tracked.worldName(),
                tracked.x(), tracked.y(), tracked.z(), tracked.url());

        // 传送按钮
        ActionButton tpButton = ActionButton.create(
                Component.text("📍 传送", TextColor.color(0x55FF55)),
                Component.text("传送到播放器位置"),
                100,
                DialogAction.customClick((v, a) -> {
                    if (a instanceof Player p) {
                        Location loc = plugin.getDataManager().getLocation(tracked);
                        if (loc != null) {
                            p.teleportAsync(loc);
                            p.sendMessage(Component.text("已传送到播放器位置", NamedTextColor.GREEN));
                        } else {
                            p.sendMessage(Component.text("目标世界未加载", NamedTextColor.RED));
                        }
                    }
                }, ClickCallback.Options.builder().uses(1).build()));

        // 编辑按钮
        ActionButton editButton = ActionButton.create(
                Component.text("✏ 编辑", TextColor.color(0xFFFF55)),
                Component.text("远程编辑播放器配置"),
                100,
                DialogAction.customClick((v, a) -> {
                    if (a instanceof Player p)
                        openRemoteEditDialog(p, tracked);
                }, ClickCallback.Options.builder().uses(1).build()));

        // 删除按钮
        ActionButton deleteButton = ActionButton.create(
                Component.text("🗑 删除", TextColor.color(0xFF5555)),
                Component.text("远程删除播放器（清除书本）"),
                100,
                DialogAction.customClick((v, a) -> {
                    if (a instanceof Player p) {
                        plugin.getDataManager().deleteArmorStand(tracked);
                        p.sendMessage(Component.text("✔ 播放器已删除", NamedTextColor.GREEN));
                    }
                }, ClickCallback.Options.builder().uses(1).build()));

        DialogType detailType = DialogType.multiAction(List.of(tpButton, editButton, deleteButton)).build();

        return Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(Component.text("播放器详情 - " + tracked.ownerName()))
                        .canCloseWithEscape(true)
                        .body(List.of(DialogBody.plainMessage(Component.text(info))))
                        .build())
                .type(detailType));
    }

    // ======================== 工具方法 ========================

    private static String nvl(String s, String def) {
        return s != null ? s : def;
    }

    private static float safeFloat(DialogResponseView view, String key, float def) {
        try {
            Float val = view.getFloat(key);
            return val != null ? val : def;
        } catch (Exception e) {
            return def;
        }
    }

    private static boolean safeBoolean(DialogResponseView view, String key, boolean def) {
        try {
            Boolean val = view.getBoolean(key);
            return val != null ? val : def;
        } catch (Exception e) {
            return def;
        }
    }
}
