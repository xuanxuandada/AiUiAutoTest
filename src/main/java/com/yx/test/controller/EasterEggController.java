package com.yx.test.controller;

import com.yx.test.common.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 彩蛋接口 —— 与业务无关，纯粹留给「读代码的人」。
 *
 * <p>这个类不注入任何 Service、不碰数据库，删掉它系统功能一点不受影响。
 * 之所以留着，是因为：功能是写给用户的，彩蛋是写给后来者的。</p>
 *
 * <p>三个入口：</p>
 * <ul>
 *   <li>{@code GET /api/easter-egg}         —— 作者名片；带上 {@code ?knock=暗号} 可解锁隐藏寄语</li>
 *   <li>{@code GET /api/easter-egg/coffee}  —— 想让我冲咖啡？418 茶壶了解一下</li>
 *   <li>{@code GET /api/easter-egg/lucky}   —— 抽一支「程序员玄学签」，全绿在此一举</li>
 * </ul>
 *
 * @author 梁梓轩
 */
@Slf4j
@RestController
@RequestMapping("/api/easter-egg")
public class EasterEggController {

    /** 作者署名，全类复用 */
    private static final String AUTHOR = "梁梓轩";

    /** 对暗号的问答表：喊对上半句，才给下半句 */
    private static final Map<String, String> KNOCK_KNOCK = Map.of(
            "天王盖地虎", "宝塔镇河妖",
            "床前明月光", "疑是地上霜",
            "芝麻开门", "门开了，作者正在写 BUG 呢"
    );

    /** 程序员玄学签，抽到哪支全看天意 */
    private static final List<String> LUCKY_SIGNS = List.of(
            "今天写的代码，明天不用改。",
            "测试全绿，部署顺利，线上无告警。",
            "这个 BUG 是同事写的，与你无关。",
            "别慌，先重启试试。",
            "空指针绕道走，缓存命中率百分百。",
            "你改的那一行，恰好就是出问题的那一行。",
            "需求不会再变了（真的）。"
    );

    /**
     * 作者名片。
     *
     * @param knock 可选的「暗号」，对上了才返回隐藏寄语
     */
    @GetMapping
    public Result<Map<String, Object>> hello(@RequestParam(required = false) String knock) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("author", AUTHOR);
        data.put("project", "AI 测试平台 · yx-test-AiTest");
        data.put("year", 2026);
        data.put("blessing", "菩提本无树，明镜亦非台；本来无 BUG，何必常修改。");

        // 对暗号：不喊口号只给名片，喊对了才解锁隐藏的这一层
        String reply = KNOCK_KNOCK.get(knock);
        if (reply != null) {
            data.put("knock", knock);
            data.put("reply", reply);
            data.put("secret", "能找到这里，说明你真的在读代码 —— 荣幸之至。");
        } else if (knock != null) {
            data.put("reply", "暗号对不上，门就不开了。");
        }

        log.info("有人来翻彩蛋了，暗号是否对上: {}", reply != null);
        return Result.success(data);
    }

    /**
     * 茶壶彩蛋：HTTP 418 「I'm a teapot」。
     *
     * <p>418 出自 RFC 2324（愚人节协议）—— 服务器是个茶壶，拒绝为你冲咖啡。
     * 业务代码里永远不会返回它，所以一旦看见 418，基本可以确定是人手写的。</p>
     */
    @GetMapping("/coffee")
    public Result<Map<String, Object>> coffee() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("status", 418);
        data.put("message", "我是茶壶，不是咖啡机。");
        data.put("advice", "加班归加班，茶壶也有摸鱼的权利。");
        return new Result<>(418, "I'm a teapot", data);
    }

    /**
     * 程序员玄学签：抽一支，图个心安。
     */
    @GetMapping("/lucky")
    public Result<Map<String, Object>> lucky() {
        int index = ThreadLocalRandom.current().nextInt(LUCKY_SIGNS.size());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("sign", LUCKY_SIGNS.get(index));
        data.put("drawTime", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        data.put("note", "签文仅供参考，BUG 还得自己修。");
        return Result.success(data);
    }
}
