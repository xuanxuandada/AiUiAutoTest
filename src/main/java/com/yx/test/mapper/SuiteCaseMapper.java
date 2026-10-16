package com.yx.test.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yx.test.entity.SuiteCase;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface SuiteCaseMapper extends BaseMapper<SuiteCase> {

    @Delete("DELETE FROM suite_case WHERE suite_id = #{suiteId}")
    int physicalDeleteBySuiteId(@Param("suiteId") Long suiteId);

    @Delete("DELETE FROM suite_case WHERE case_id = #{caseId}")
    int physicalDeleteByCaseId(@Param("caseId") Long caseId);

    @Delete("DELETE FROM suite_case WHERE suite_id = #{suiteId} AND case_id = #{caseId}")
    int physicalDeleteBySuiteCase(@Param("suiteId") Long suiteId, @Param("caseId") Long caseId);

    @Update("UPDATE regression_suite SET login_fetch_command = #{logintext}, login_enabled = #{loginEnabled} WHERE id = #{suiteId}")
    int updateLoginFetchCommandById(@Param("suiteId") Long suiteId, @Param("logintext") String logintext, @Param("loginEnabled") Integer loginEnabled);
}
