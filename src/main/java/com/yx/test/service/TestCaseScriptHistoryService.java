package com.yx.test.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.yx.test.entity.TestCaseScript;
import com.yx.test.entity.TestCaseScriptHistory;

public interface TestCaseScriptHistoryService extends IService<TestCaseScriptHistory> {

    /**
     * 备份脚本到历史表
     * @param script 要备份的脚本
     */
    void backupScript(TestCaseScript script);
}
