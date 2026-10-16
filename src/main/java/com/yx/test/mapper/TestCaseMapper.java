package com.yx.test.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yx.test.entity.TestCase;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TestCaseMapper extends BaseMapper<TestCase> {
}
