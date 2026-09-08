package com.claubloom.harness.storage.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 映射到 SQLite 'session_stats' 表的实体。
 * 与 pi 的 001_initial.sql session_stats 表结构直接对应。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("session_stats")
public class SessionStatsEntity {

    @TableId
    private String sessionId;

    private Integer messageCount;

    private Double cachedTokens;

    private Double uncachedTokens;

    private Double totalTokens;

    private Double costTotal;
}
