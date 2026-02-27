package ict.minesunshineone.mcediagui;

import org.bukkit.plugin.Plugin;

import java.io.File;
import java.sql.*;
import java.util.*;
import java.util.logging.Level;

/**
 * H2 嵌入式数据库管理。
 * 所有数据库操作在调用线程执行（应通过异步调度调用）。
 */
public class DatabaseManager {

    private final Plugin plugin;
    private Connection connection;

    public DatabaseManager(Plugin plugin) {
        this.plugin = plugin;
    }

    public void init() {
        try {
            File dbFile = new File(plugin.getDataFolder(), "data");
            plugin.getDataFolder().mkdirs();
            String url = "jdbc:h2:" + dbFile.getAbsolutePath() + ";MODE=MySQL";
            connection = DriverManager.getConnection(url);
            createTable();
            plugin.getLogger().info("H2 数据库已连接");
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "H2 数据库初始化失败", e);
        }
    }

    private void createTable() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS media_players (
                        armor_stand_uuid VARCHAR(36) PRIMARY KEY,
                        owner_uuid       VARCHAR(36) NOT NULL,
                        owner_name       VARCHAR(64) NOT NULL,
                        world_name       VARCHAR(128) NOT NULL,
                        x                DOUBLE NOT NULL,
                        y                DOUBLE NOT NULL,
                        z                DOUBLE NOT NULL,
                        url              VARCHAR(1024) NOT NULL,
                        created_at       BIGINT NOT NULL
                    )
                    """);
            // 索引：按 owner 查询
            stmt.execute("""
                    CREATE INDEX IF NOT EXISTS idx_owner ON media_players(owner_uuid)
                    """);
        }
    }

    /**
     * 插入或更新一条记录（MERGE = upsert）
     */
    public void upsert(PlayerDataManager.TrackedArmorStand t) {
        String sql = """
                MERGE INTO media_players (armor_stand_uuid, owner_uuid, owner_name,
                    world_name, x, y, z, url, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, t.armorStandUUID().toString());
            ps.setString(2, t.ownerUUID().toString());
            ps.setString(3, t.ownerName());
            ps.setString(4, t.worldName());
            ps.setDouble(5, t.x());
            ps.setDouble(6, t.y());
            ps.setDouble(7, t.z());
            ps.setString(8, t.url());
            ps.setLong(9, t.createdAt());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "保存播放器数据失败", e);
        }
    }

    /**
     * 删除一条记录
     */
    public void delete(UUID armorStandUUID) {
        try (PreparedStatement ps = connection.prepareStatement(
                "DELETE FROM media_players WHERE armor_stand_uuid = ?")) {
            ps.setString(1, armorStandUUID.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "删除播放器数据失败", e);
        }
    }

    /**
     * 删除指定玩家的所有记录
     */
    public void deleteByOwner(UUID ownerUUID) {
        try (PreparedStatement ps = connection.prepareStatement(
                "DELETE FROM media_players WHERE owner_uuid = ?")) {
            ps.setString(1, ownerUUID.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "删除玩家数据失败", e);
        }
    }

    /**
     * 加载所有记录
     */
    public List<PlayerDataManager.TrackedArmorStand> loadAll() {
        List<PlayerDataManager.TrackedArmorStand> result = new ArrayList<>();
        try (Statement stmt = connection.createStatement();
                ResultSet rs = stmt.executeQuery(
                        "SELECT * FROM media_players ORDER BY created_at DESC")) {
            while (rs.next()) {
                result.add(fromRow(rs));
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "加载播放器数据失败", e);
        }
        return result;
    }

    /**
     * 加载指定玩家的记录
     */
    public List<PlayerDataManager.TrackedArmorStand> loadByOwner(UUID ownerUUID) {
        List<PlayerDataManager.TrackedArmorStand> result = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT * FROM media_players WHERE owner_uuid = ? ORDER BY created_at DESC")) {
            ps.setString(1, ownerUUID.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(fromRow(rs));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "加载玩家数据失败", e);
        }
        return result;
    }

    /**
     * 获取指定玩家的播放器数量
     */
    public int countByOwner(UUID ownerUUID) {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT COUNT(*) FROM media_players WHERE owner_uuid = ?")) {
            ps.setString(1, ownerUUID.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next())
                    return rs.getInt(1);
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "查询播放器数量失败", e);
        }
        return 0;
    }

    /**
     * 获取总记录数
     */
    public int countAll() {
        try (Statement stmt = connection.createStatement();
                ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM media_players")) {
            if (rs.next())
                return rs.getInt(1);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "查询总数失败", e);
        }
        return 0;
    }

    /**
     * 分页查询（管理菜单用）
     */
    public List<PlayerDataManager.TrackedArmorStand> loadPage(int offset, int limit) {
        List<PlayerDataManager.TrackedArmorStand> result = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT * FROM media_players ORDER BY created_at DESC LIMIT ? OFFSET ?")) {
            ps.setInt(1, limit);
            ps.setInt(2, offset);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(fromRow(rs));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "分页查询失败", e);
        }
        return result;
    }

    private PlayerDataManager.TrackedArmorStand fromRow(ResultSet rs) throws SQLException {
        return new PlayerDataManager.TrackedArmorStand(
                UUID.fromString(rs.getString("armor_stand_uuid")),
                UUID.fromString(rs.getString("owner_uuid")),
                rs.getString("owner_name"),
                rs.getString("world_name"),
                rs.getDouble("x"),
                rs.getDouble("y"),
                rs.getDouble("z"),
                rs.getString("url"),
                rs.getLong("created_at"));
    }

    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                plugin.getLogger().info("H2 数据库已关闭");
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "关闭数据库失败", e);
        }
    }
}
