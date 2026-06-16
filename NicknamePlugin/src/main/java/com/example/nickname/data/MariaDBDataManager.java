package com.example.nickname.data;

import com.example.nickname.NicknamePlugin;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * MariaDB 백엔드. HikariCP 풀링 + 메모리 캐시.
 * 멀티서버(공유 DB) 환경에서는 {@code updated_at} 기준 주기 폴링으로 캐시를 맞춘다.
 *
 * <h3>동시성 모델</h3>
 * <ul>
 *   <li>캐시 변경 메서드({@code touch/setFakeName/clearFakeName/mergeRemoteRow})는
 *       {@code synchronized(this)} 로 보호 — 메인 스레드와 폴링 스레드 간 복합 연산 원자성 보장</li>
 *   <li>DB 쓰기는 비동기 처리; {@link #pendingWrites} 카운터로 종료 시 완료 대기</li>
 * </ul>
 */
public class MariaDBDataManager implements DataManager {

    private final NicknamePlugin plugin;
    private HikariDataSource dataSource;
    private String table;

    private final Map<UUID, NicknameData> byUuid = new ConcurrentHashMap<>();
    private final Map<String, UUID> byReal = new ConcurrentHashMap<>();
    private final Map<String, UUID> byFake = new ConcurrentHashMap<>();

    private volatile Timestamp lastSyncWatermark;
    private BukkitTask multiServerSyncTask;

    /** 종료 플래그 — true 가 되면 runAsync 가 동기 실행으로 전환 */
    private volatile boolean closing = false;

    /** 아직 완료되지 않은 비동기 DB 쓰기 수 */
    private final AtomicInteger pendingWrites = new AtomicInteger(0);

    /**
     * 폴링 시 워터마크에서 이 시간만큼 과거까지 겹쳐서 다시 조회한다 (밀리초).
     * MariaDB TIMESTAMP 기본 해상도가 1초라, 같은 초에 여러 서버가 동시에 변경하면
     * {@code > watermark} 비교로는 일부 행을 놓칠 수 있다. 겹침 구간을 두고 재조회하되,
     * 이미 캐시와 동일한 행은 {@link #mergeRemoteRow}가 false 를 반환해 중복 처리하지 않는다.
     */
    private static final long SYNC_OVERLAP_MILLIS = 2000L;

    public MariaDBDataManager(NicknamePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void init() {
        FileConfiguration cfg = plugin.getConfig();
        String host     = cfg.getString("storage.mariadb.host",     "localhost");
        int    port     = cfg.getInt   ("storage.mariadb.port",     3306);
        String db       = cfg.getString("storage.mariadb.database", "nickname");
        String user     = cfg.getString("storage.mariadb.username", "root");
        String pass     = cfg.getString("storage.mariadb.password", "");
        int    pool     = cfg.getInt   ("storage.mariadb.pool-size", 5);
        this.table      = cfg.getString("storage.mariadb.table-name", "nicknames");

        boolean multiSync  = cfg.getBoolean("storage.mariadb.multi-server-sync.enabled", true);
        int     pollSeconds = cfg.getInt("storage.mariadb.multi-server-sync.poll-interval-seconds", 3);

        HikariConfig hc = new HikariConfig();
        // MariaDB JDBC 3.x 전용 URL — MySQL 커넥터 파라미터(useSSL/allowPublicKeyRetrieval)는 사용 불가
        hc.setJdbcUrl("jdbc:mariadb://" + host + ":" + port + "/" + db + "?sslMode=disable");
        hc.setDriverClassName("org.mariadb.jdbc.Driver");
        hc.setUsername(user);
        hc.setPassword(pass);

        // 연결 풀 크기: 4서버 × pool 크기의 총합이 MariaDB max_connections 를 넘지 않도록
        hc.setMaximumPoolSize(pool);
        // 유휴 연결은 최소 1개만 유지해 불필요한 DB 연결 낭비를 방지
        hc.setMinimumIdle(Math.max(1, pool / 5));

        // 연결 만료/갱신 설정 (MariaDB 서버가 wait_timeout 으로 끊기 전에 교체)
        hc.setMaxLifetime(600_000L);           // 10분 — 서버 wait_timeout(기본 8시간) 보다 짧게
        hc.setKeepaliveTime(180_000L);          // 3분마다 keepalive 핑
        hc.setConnectionTimeout(10_000L);       // 연결 획득 대기 최대 10초
        hc.setIdleTimeout(300_000L);            // 유휴 5분 후 연결 반납

        hc.setPoolName("NicknamePlugin-Hikari");

        // PreparedStatement 캐시 (MariaDB JDBC 3.x 지원 옵션)
        hc.addDataSourceProperty("cachePrepStmts",    "true");
        hc.addDataSourceProperty("prepStmtCacheSize", "250");

        // 모든 연결에서 utf8mb4 보장 (한글 닉네임 필수)
        hc.setConnectionInitSql("SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci");

        this.dataSource = new HikariDataSource(hc);

        // DDL — TIMESTAMP(3) 으로 밀리초 정밀도 확보 (폴링 정확도 향상)
        final String ddl =
                "CREATE TABLE IF NOT EXISTS `" + table + "` ("
                + "  `uuid`       CHAR(36)     NOT NULL,"
                + "  `real_name`  VARCHAR(16)  NOT NULL,"
                + "  `fake_name`  VARCHAR(32)  NULL,"
                + "  `updated_at` TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) "
                + "               ON UPDATE CURRENT_TIMESTAMP(3),"
                + "  PRIMARY KEY  (`uuid`),"
                + "  KEY          `idx_updated` (`updated_at`),"
                + "  UNIQUE KEY   `uq_fake` (`fake_name`)"
                + ") DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;";

        try (Connection c = dataSource.getConnection();
             Statement st = c.createStatement()) {
            st.executeUpdate(ddl);
            ensureIndex(c, "idx_updated", "(`updated_at`)");
        } catch (SQLException ex) {
            throw new RuntimeException("테이블 생성 실패", ex);
        }

        reloadAllFromDatabase();
        this.lastSyncWatermark = fetchDbNow();
        if (this.lastSyncWatermark == null) {
            this.lastSyncWatermark = new Timestamp(System.currentTimeMillis());
        }

        plugin.getLogger().info("MariaDB 데이터 로드: " + byUuid.size() + "건");

        if (multiSync && pollSeconds > 0) {
            long intervalTicks = pollSeconds * 20L;
            multiServerSyncTask = Bukkit.getScheduler().runTaskTimerAsynchronously(
                    plugin,
                    this::pollRemoteChanges,
                    intervalTicks,
                    intervalTicks);
            plugin.getLogger().info("멀티서버 DB 동기화 활성화 (주기 " + pollSeconds + "초, TIMESTAMP(3) 기준)");
        }
    }

    // ──────────────────────────────────────────────────────────
    //  읽기 (캐시 조회만 — 비동기 안전)
    // ──────────────────────────────────────────────────────────

    @Override
    public Optional<NicknameData> getByUuid(UUID uuid) {
        return uuid == null ? Optional.empty() : Optional.ofNullable(byUuid.get(uuid));
    }

    @Override
    public Optional<NicknameData> getByRealName(String realName) {
        if (realName == null) return Optional.empty();
        UUID uuid = byReal.get(realName.toLowerCase(Locale.ROOT));
        return uuid == null ? Optional.empty() : Optional.ofNullable(byUuid.get(uuid));
    }

    @Override
    public Optional<NicknameData> getByFakeName(String fakeName) {
        if (fakeName == null) return Optional.empty();
        UUID uuid = byFake.get(fakeName.toLowerCase(Locale.ROOT));
        return uuid == null ? Optional.empty() : Optional.ofNullable(byUuid.get(uuid));
    }

    // ──────────────────────────────────────────────────────────
    //  쓰기 — synchronized 로 캐시 원자성 보장, DB 쓰기는 비동기
    // ──────────────────────────────────────────────────────────

    @Override
    public synchronized void touch(UUID uuid, String realName) {
        NicknameData data = byUuid.get(uuid);
        if (data != null && realName.equalsIgnoreCase(data.getRealName())) {
            return;
        }

        if (data == null) {
            data = new NicknameData(uuid, realName, null);
            byUuid.put(uuid, data);
        } else {
            byReal.remove(data.getRealName().toLowerCase(Locale.ROOT));
            data.setRealName(realName);
        }
        byReal.put(realName.toLowerCase(Locale.ROOT), uuid);

        final NicknameData finalData = data;
        runAsync(() -> {
            try (Connection c = dataSource.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                         "INSERT INTO `" + table + "` (uuid, real_name, fake_name) VALUES (?, ?, NULL) "
                         + "ON DUPLICATE KEY UPDATE real_name = VALUES(real_name)")) {
                ps.setString(1, uuid.toString());
                ps.setString(2, realName);
                ps.executeUpdate();
                bumpLocalWatermark(c);
            } catch (SQLException ex) {
                plugin.getLogger().severe("touch 실패 [" + uuid + "]: " + ex.getMessage());
            }
        });
    }

    @Override
    public synchronized void setFakeName(UUID uuid, String realName, String fakeName) {
        if (uuid == null || realName == null || fakeName == null || fakeName.isEmpty()) {
            return;
        }
        NicknameData data = byUuid.get(uuid);
        if (data != null
                && realName.equalsIgnoreCase(data.getRealName())
                && Objects.equals(fakeName, data.getFakeName())) {
            return;
        }

        if (data == null) {
            data = new NicknameData(uuid, realName, fakeName);
            byUuid.put(uuid, data);
            byReal.put(realName.toLowerCase(Locale.ROOT), uuid);
        } else {
            if (data.hasFakeName()) {
                byFake.remove(data.getFakeName().toLowerCase(Locale.ROOT));
            }
            if (!realName.equalsIgnoreCase(data.getRealName())) {
                byReal.remove(data.getRealName().toLowerCase(Locale.ROOT));
                data.setRealName(realName);
                byReal.put(realName.toLowerCase(Locale.ROOT), uuid);
            }
            data.setFakeName(fakeName);
        }
        byFake.put(fakeName.toLowerCase(Locale.ROOT), uuid);

        final String fakeNameFinal = fakeName;
        runAsync(() -> {
            try (Connection c = dataSource.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                         /*
                          * uq_fake 충돌 시 (다른 플레이어가 이미 같은 fake_name 사용 중) 데이터 손상 방지.
                          *
                          * 기존 `ON DUPLICATE KEY UPDATE real_name=VALUES(real_name), fake_name=VALUES(fake_name)`
                          * 은 uq_fake 가 트리거될 때 '충돌된 기존 행(다른 플레이어)' 을 업데이트하여
                          * 해당 플레이어의 real_name 을 덮어씌우는 데이터 손상 버그가 있었다.
                          *
                          * IF(uuid = VALUES(uuid), ...) 를 사용해:
                          *   - PK(uuid) 충돌 → 정상 UPDATE ✓
                          *   - uq_fake 충돌  → 기존 행 변경 없음(no-op) ✓
                          */
                         "INSERT INTO `" + table + "` (uuid, real_name, fake_name) VALUES (?, ?, ?) "
                         + "ON DUPLICATE KEY UPDATE "
                         + "  real_name = IF(uuid = VALUES(uuid), VALUES(real_name), real_name), "
                         + "  fake_name = IF(uuid = VALUES(uuid), VALUES(fake_name), fake_name)")) {
                ps.setString(1, uuid.toString());
                ps.setString(2, realName);
                ps.setString(3, fakeNameFinal);
                int affected = ps.executeUpdate();
                bumpLocalWatermark(c);

                // affected == 0: uq_fake 충돌로 no-op 발생 → DB 에 반영 안 됨 → 캐시 롤백
                if (affected == 0) {
                    plugin.getLogger().warning(
                            "setFakeName: fake_name '" + fakeNameFinal + "' 이미 사용 중(동시 충돌). 캐시 롤백.");
                    rollbackFakeInCache(uuid, fakeNameFinal, c);
                }
            } catch (SQLException ex) {
                plugin.getLogger().severe("setFakeName 실패 [" + uuid + "]: " + ex.getMessage());
            }
        });
    }

    @Override
    public synchronized void clearFakeName(UUID uuid) {
        NicknameData data = byUuid.get(uuid);
        if (data == null || !data.hasFakeName()) {
            return;
        }
        byFake.remove(data.getFakeName().toLowerCase(Locale.ROOT));
        data.setFakeName(null);

        runAsync(() -> {
            try (Connection c = dataSource.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                         "UPDATE `" + table + "` SET fake_name = NULL WHERE uuid = ?")) {
                ps.setString(1, uuid.toString());
                ps.executeUpdate();
                bumpLocalWatermark(c);
            } catch (SQLException ex) {
                plugin.getLogger().severe("clearFakeName 실패 [" + uuid + "]: " + ex.getMessage());
            }
        });
    }

    @Override
    public boolean isFakeNameTaken(String fakeName, UUID exclude) {
        if (fakeName == null) return false;
        UUID owner = byFake.get(fakeName.toLowerCase(Locale.ROOT));
        return owner != null && !owner.equals(exclude);
    }

    @Override
    public void saveAll() {
        // DB 는 쓰기 즉시 반영 — 별도 saveAll 불필요
    }

    @Override
    public void close() {
        closing = true;

        if (multiServerSyncTask != null) {
            multiServerSyncTask.cancel();
            multiServerSyncTask = null;
        }

        // 미완료 비동기 쓰기가 있을 경우 최대 5초 대기 (데이터 유실 방지)
        long deadline = System.currentTimeMillis() + 5_000L;
        while (pendingWrites.get() > 0 && System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        if (pendingWrites.get() > 0) {
            plugin.getLogger().warning("종료 대기 중 " + pendingWrites.get() + "건의 DB 쓰기가 완료되지 않았습니다.");
        }

        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }

    // ──────────────────────────────────────────────────────────
    //  내부 — DB 로드 / 폴링
    // ──────────────────────────────────────────────────────────

    private void reloadAllFromDatabase() {
        byUuid.clear();
        byReal.clear();
        byFake.clear();
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT uuid, real_name, fake_name FROM `" + table + "`");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                UUID uuid = UUID.fromString(rs.getString("uuid"));
                String real = rs.getString("real_name");
                String fake = normalizeFake(rs.getString("fake_name"));
                putCache(uuid, real, fake);
            }
        } catch (SQLException ex) {
            plugin.getLogger().severe("초기 로드 실패: " + ex.getMessage());
        }
    }

    private void pollRemoteChanges() {
        Timestamp watermark = lastSyncWatermark;
        if (watermark == null) return;

        List<RowUpdate> updates = new ArrayList<>();
        Timestamp maxSeen = watermark;

        // 겹침 버퍼: watermark 보다 SYNC_OVERLAP_MILLIS 만큼 과거부터 재조회 (같은 초 누락 방지)
        Timestamp queryFrom = new Timestamp(Math.max(0L, watermark.getTime() - SYNC_OVERLAP_MILLIS));

        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT uuid, real_name, fake_name, updated_at FROM `" + table
                     + "` WHERE updated_at >= ? ORDER BY updated_at ASC")) {
            ps.setTimestamp(1, queryFrom);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Timestamp updatedAt = rs.getTimestamp("updated_at");
                    if (updatedAt != null && updatedAt.after(maxSeen)) {
                        maxSeen = updatedAt;
                    }
                    updates.add(new RowUpdate(
                            UUID.fromString(rs.getString("uuid")),
                            rs.getString("real_name"),
                            normalizeFake(rs.getString("fake_name"))));
                }
            }
        } catch (SQLException ex) {
            plugin.getLogger().warning("멀티서버 동기화 조회 실패: " + ex.getMessage());
            return;
        }

        lastSyncWatermark = maxSeen;

        if (updates.isEmpty()) return;

        List<UUID> changedOnline = new ArrayList<>();
        for (RowUpdate row : updates) {
            if (mergeRemoteRow(row.uuid(), row.realName(), row.fakeName())) {
                if (Bukkit.getPlayer(row.uuid()) != null) {
                    changedOnline.add(row.uuid());
                }
            }
        }

        if (!changedOnline.isEmpty()) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                for (UUID uuid : changedOnline) {
                    Player p = Bukkit.getPlayer(uuid);
                    if (p != null) {
                        plugin.getNicknameManager().refreshDisplay(p);
                        plugin.getNicknameManager().pushPacketRefresh(p);
                    }
                }
            });
        }
    }

    /** @return 캐시가 실제로 바뀌었으면 true */
    private synchronized boolean mergeRemoteRow(UUID uuid, String real, String fake) {
        NicknameData existing = byUuid.get(uuid);
        if (existing != null) {
            boolean sameReal = existing.getRealName().equalsIgnoreCase(real);
            boolean sameFake = Objects.equals(
                    existing.hasFakeName() ? existing.getFakeName() : null,
                    fake);
            if (sameReal && sameFake) return false;

            if (!sameReal) {
                byReal.remove(existing.getRealName().toLowerCase(Locale.ROOT));
                existing.setRealName(real);
                byReal.put(real.toLowerCase(Locale.ROOT), uuid);
            }
            if (!sameFake) {
                if (existing.hasFakeName()) {
                    byFake.remove(existing.getFakeName().toLowerCase(Locale.ROOT));
                }
                existing.setFakeName(fake);
                if (fake != null) {
                    byFake.put(fake.toLowerCase(Locale.ROOT), uuid);
                }
            }
            return true;
        }
        putCache(uuid, real, fake);
        return true;
    }

    private void putCache(UUID uuid, String real, String fake) {
        NicknameData data = new NicknameData(uuid, real, fake);
        byUuid.put(uuid, data);
        byReal.put(real.toLowerCase(Locale.ROOT), uuid);
        if (fake != null) {
            byFake.put(fake.toLowerCase(Locale.ROOT), uuid);
        }
    }

    private static String normalizeFake(String fake) {
        return (fake == null || fake.isEmpty()) ? null : fake;
    }

    // ──────────────────────────────────────────────────────────
    //  내부 — 유틸리티
    // ──────────────────────────────────────────────────────────

    /**
     * DB 서버의 현재 시각을 반환한다 (TIMESTAMP(3) 밀리초 정밀도).
     * JVM 시각과의 클럭 스큐를 피하기 위해 DB 시각을 워터마크로 사용한다.
     */
    private Timestamp fetchDbNow() {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT NOW(3)");
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) return rs.getTimestamp(1);
        } catch (SQLException ex) {
            plugin.getLogger().warning("DB 시각 조회 실패: " + ex.getMessage());
        }
        return null;
    }

    /**
     * 로컬 쓰기 직후 워터마크를 DB 현재 시각으로 앞당긴다.
     * 이 서버가 방금 쓴 행을 다음 폴링 사이클에서 원격 변경으로 재처리하지 않도록 한다.
     *
     * <p>기존의 {@code SELECT MAX(updated_at)} 방식 대비:
     * <ul>
     *   <li>대용량 테이블에서 풀스캔 없이 O(1) 응답</li>
     *   <li>인덱스 미사용 문제 없음</li>
     * </ul>
     */
    private void bumpLocalWatermark(Connection c) {
        try (PreparedStatement ps = c.prepareStatement("SELECT NOW(3)");
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                lastSyncWatermark = rs.getTimestamp(1);
            }
        } catch (SQLException ex) {
            plugin.getLogger().warning("워터마크 갱신 실패, 로컬 시각 사용: " + ex.getMessage());
            lastSyncWatermark = new Timestamp(System.currentTimeMillis());
        }
    }

    /**
     * setFakeName 의 DB 쓰기가 uq_fake 충돌로 no-op 가 된 경우,
     * 메모리 캐시를 DB 상태로 되돌린다.
     */
    private void rollbackFakeInCache(UUID uuid, String failedFake, Connection c) {
        String dbFake = null;
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT fake_name FROM `" + table + "` WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) dbFake = normalizeFake(rs.getString(1));
            }
        } catch (SQLException ex) {
            plugin.getLogger().warning("rollbackFakeInCache 조회 실패: " + ex.getMessage());
            return;
        }

        final String actualFake = dbFake;
        Bukkit.getScheduler().runTask(plugin, () -> {
            synchronized (this) {
                NicknameData data = byUuid.get(uuid);
                if (data == null) return;
                // 아직 우리가 잘못 세팅한 값이 그대로 있는 경우에만 롤백
                if (!failedFake.equals(data.getFakeName())) return;

                byFake.remove(failedFake.toLowerCase(Locale.ROOT));
                data.setFakeName(actualFake);
                if (actualFake != null) {
                    byFake.put(actualFake.toLowerCase(Locale.ROOT), uuid);
                }
            }
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                plugin.getNicknameManager().refreshDisplay(p);
                plugin.getNicknameManager().pushPacketRefresh(p);
                plugin.getMessageManager().send(p, "gui.nickname-taken");
            }
        });
    }

    /**
     * 예전 스키마(인덱스 없음) 마이그레이션용.
     * {@code IF NOT EXISTS} 를 사용해 다중 서버 동시 기동 시 중복 생성 오류를 방지한다.
     */
    private void ensureIndex(Connection c, String indexName, String columnList) throws SQLException {
        String check = "SELECT 1 FROM information_schema.statistics "
                + "WHERE table_schema = DATABASE() AND table_name = ? AND index_name = ? LIMIT 1";
        try (PreparedStatement ps = c.prepareStatement(check)) {
            ps.setString(1, table);
            ps.setString(2, indexName);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return;
            }
        }
        try (Statement st = c.createStatement()) {
            // IF NOT EXISTS 로 경쟁 조건에서 두 번째 서버의 중복 DDL 오류 방지
            st.executeUpdate("ALTER TABLE `" + table + "` ADD KEY IF NOT EXISTS `"
                    + indexName + "` " + columnList);
        } catch (SQLException ex) {
            // 두 서버가 동시에 IF NOT EXISTS 통과했을 경우 이미 존재 오류는 무시
            if (!ex.getMessage().contains("Duplicate key name")) throw ex;
        }
    }

    /**
     * 비동기 실행 헬퍼.
     * <ul>
     *   <li>종료 중({@link #closing}) 또는 이미 비동기 스레드이면 직접 실행</li>
     *   <li>메인 스레드에서 호출하면 BukkitScheduler 비동기 작업으로 제출</li>
     * </ul>
     * {@link #pendingWrites} 카운터를 사용해 {@link #close()} 의 완료 대기를 지원한다.
     */
    private void runAsync(Runnable r) {
        if (closing || !plugin.isEnabled()) {
            // 종료 중: 메인 스레드 또는 현재 스레드에서 즉시 실행
            r.run();
            return;
        }
        if (!Bukkit.isPrimaryThread()) {
            r.run();
            return;
        }
        pendingWrites.incrementAndGet();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                r.run();
            } finally {
                pendingWrites.decrementAndGet();
            }
        });
    }

    private record RowUpdate(UUID uuid, String realName, String fakeName) {}
}
