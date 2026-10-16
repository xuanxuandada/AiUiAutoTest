package com.yx.test.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yx.test.entity.SuiteCaseScript;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SuiteCaseScriptMapper extends BaseMapper<SuiteCaseScript> {

    @Delete("DELETE FROM suite_case_script WHERE suite_id = #{suiteId} AND case_id = #{caseId}")
    int physicalDeleteBySuiteCase(@Param("suiteId") Long suiteId, @Param("caseId") Long caseId);

    @Delete("DELETE FROM suite_case_script WHERE suite_id = #{suiteId}")
    int physicalDeleteBySuiteId(@Param("suiteId") Long suiteId);
}
