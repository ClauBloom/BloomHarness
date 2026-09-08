package com.claubloom.harness.storage.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 映射到 SQLite 'session_stats' 表的实体，记录单个会话的消息数与令牌用量统计。
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
