package com.yx.test.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yx.test.entity.UserAiConfig;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户AI配置 Mapper
 *
 * <p>必须显式加 @Mapper：本项目的 MybatisPlusConfig 里没有配置 @MapperScan，
 * 缺少该注解会导致 Spring 找不到这个 Bean 而启动失败。</p>
 */
@Mapper
public interface UserAiConfigMapper extends BaseMapper<UserAiConfig> {
}
