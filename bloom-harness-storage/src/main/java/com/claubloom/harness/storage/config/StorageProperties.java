package com.claubloom.harness.storage.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * bloom-harness-storage 模块的存储配置属性。
 */
@Data
@ConfigurationProperties(prefix = "bloom.storage")
public class StorageProperties {

    /**
     * SQLite 数据库文件路径。默认为当前工作区下的 bloom-harness.db。
     */
    private String databasePath = "./bloom-harness.db";

    /**
     * 是否在应用启动时自动执行 SQLite 数据库表初始化与迁移。
     */
    private boolean autoInitialize = true;
}
