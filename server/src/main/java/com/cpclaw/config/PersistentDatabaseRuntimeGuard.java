package com.cpclaw.config;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Refuses to expose a normal CPClaw runtime when configuration would be written to an ephemeral store.
 * H2 remains available only to tests that explicitly disable this guard in test resources.
 */
@Component
public class PersistentDatabaseRuntimeGuard implements SmartInitializingSingleton {

    private final boolean enabled;
    private final DataSource dataSource;
    private final ObjectProvider<Flyway> flywayProvider;

    public PersistentDatabaseRuntimeGuard(
        @Value("${cpclaw.persistence.runtime-guard-enabled:true}") boolean enabled,
        DataSource dataSource,
        ObjectProvider<Flyway> flywayProvider
    ) {
        this.enabled = enabled;
        this.dataSource = dataSource;
        this.flywayProvider = flywayProvider;
    }

    @Override
    public void afterSingletonsInstantiated() {
        if (!enabled) {
            return;
        }

        Flyway flyway = flywayProvider.getIfAvailable();
        if (flyway == null) {
            throw new IllegalStateException("持久化运行要求启用 Flyway，拒绝启动未迁移的服务");
        }
        if (flyway.info().pending().length > 0) {
            throw new IllegalStateException("Flyway 存在未执行迁移，拒绝启动可能造成配置不一致的服务");
        }

        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metadata = connection.getMetaData();
            String databaseName = metadata.getDatabaseProductName();
            String jdbcUrl = metadata.getURL();
            if (jdbcUrl == null || jdbcUrl.startsWith("jdbc:h2:") || !"MySQL".equalsIgnoreCase(databaseName)) {
                throw new IllegalStateException("CPClaw 运行时只允许持久化 MySQL；检测到 " + databaseName + " (" + jdbcUrl + ")");
            }
        } catch (java.sql.SQLException exception) {
            throw new IllegalStateException("无法验证持久化数据库连接，拒绝启动服务", exception);
        }
    }
}
