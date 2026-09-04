package com.claubloom.harness.storage.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.claubloom.harness.storage.entity.SessionStatsEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * SessionStatsEntity 的 MyBatis 数据库操作 Mapper 接口。
 */
@Mapper
public interface SessionStatsMapper extends BaseMapper<SessionStatsEntity> {
}
