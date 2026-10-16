package com.yx.test;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * AI 测试平台 · 应用启动入口
 *
 * <p>技术栈: Spring Boot + MyBatis-Plus + MySQL, 配合 Node.js (midscene-runner) 驱动真实浏览器执行.</p>
 * <p>核心能力: 测试用例管理, AI 脚本生成, 回归套件, 执行报告, 多租户与三层权限体系.</p>
 *
 * <p>从第一行代码到最后一处联调, 皆是亲手所写.
 * 谨以此项目, 纪念那些与 Bug 斗智斗勇的日子.</p>
 *
 * @author 梁梓轩
 * @since 2026
 */
@SpringBootApplication
@EnableAsync  // 开启异步支持
public class AiTestApplication {

    public static void main(String[] args) {
        System.out.print("                      _oo0oo_                     \n");
        System.out.print("                     o8888888o                    \n");
        System.out.print("                     88' . '88                    \n");
        System.out.print("                     (| -_- |)                    \n");
        System.out.print("                     0\\  =  /0                    \n");
        System.out.print("                   ___/`---'\\___                  \n");
        System.out.print("                 .' \\\\|     |// '.                \n");
        System.out.print("                / \\\\|||  :  |||// \\               \n");
        System.out.print("               / _||||| -:- |||||_ \\              \n");
        System.out.print("              |   | \\\\\\  -  /// |   |             \n");
        System.out.print("              | \\_|  ''\\---/''  |_/ |             \n");
        System.out.print("              \\.-\\___  '-'  ___/-. /             \n");
        System.out.print("            ___'. .'  /--.--\\  `. .'___           \n");
        System.out.print("          .'' '< `.___\\_<|>_/___.' >'  ''.        \n");
        System.out.print("        | | ： `- \\`.;`\\ _ /`;.`/ - ` : | |       \n");
        System.out.print("        \\  \\ `_.   \\_ __\\ /__ _/   .-` /  /       \n");
        System.out.print("    =====`-.____`.___ \\_____/___.-`___.-`=====    \n");
        System.out.print("                      `=---='                     \n");
        System.out.print("    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~    \n");
        System.out.print("\033[1;32m                菩提本无树  明镜亦非台              \n");
        System.out.print("                本来无BUG  何必常修改              \n");
        System.out.print("                Written by  梁梓轩              \n");




        SpringApplication.run(AiTestApplication.class, args);
    }
}
