package com.yx.test.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.yx.test.entity.TestCase;

import java.util.List;

public interface TestCaseService extends IService<TestCase> {

    /**
     * 按「数据可见范围」分页查询用例列表。
     * 可见范围 = 我自己创建的 + 我的所有下级创建的（下级关系看 user.leader_id）。
     * type / tags / keyword 都可以不传（不传就是查全部可见的）。
     *
     * @param keyword  按用例名称模糊匹配（前置给前端的搜索框，避免拿全量到本地筛）
     * @param page     第几页，从 1 开始
     * @param pageSize 每页条数
     */
    Page<TestCase> listVisible(String type, String tags, String keyword, long page, long pageSize);

    /**
     * 根据标签查询用例（已按数据可见范围过滤）
     */
    List<TestCase> listByTags(String tags);

    /**
     * 根据类型查询用例（已按数据可见范围过滤）
     */
    List<TestCase> listByType(String type);

    /**
     * 批量保存用例
     */
    boolean saveBatch(List<TestCase> caseList);
}
