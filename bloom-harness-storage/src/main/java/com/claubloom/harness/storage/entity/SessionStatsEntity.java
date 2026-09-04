package com.claubloom.harness.storage.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Entity mapped to SQLite 'session_stats' table.
 * Directly mirrors pi's 001_initial.sql session_stats schema.
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
