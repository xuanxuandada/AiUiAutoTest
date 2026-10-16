package com.yx.test.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yx.test.entity.TestCaseScript;
import com.yx.test.entity.TestCaseScriptHistory;
import com.yx.test.mapper.TestCaseScriptHistoryMapper;
import com.yx.test.service.TestCaseScriptHistoryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Slf4j
@Service
public class TestCaseScriptHistoryServiceImpl extends ServiceImpl<TestCaseScriptHistoryMapper, TestCaseScriptHistory>
        implements TestCaseScriptHistoryService {

    @Override
    public void backupScript(TestCaseScript script) {
        if (script == null || script.getScriptSteps() == null || script.getScriptSteps().isEmpty()) {
            return; // 空脚本不备份
        }

        TestCaseScriptHistory history = new TestCaseScriptHistory();
        history.setScriptId(script.getId());
        history.setTestCaseId(script.getTestCaseId());
        history.setScriptSteps(script.getScriptSteps());
        history.setModelName(script.getModelName());
        history.setStatus(script.getStatus());
        history.setBackupTime(LocalDateTime.now());

        save(history);
        log.info("脚本已备份到历史表，scriptId: {}, testCaseId: {}", script.getId(), script.getTestCaseId());
    }
}
