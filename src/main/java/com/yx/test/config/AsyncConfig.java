package com.yx.test.config;

import com.yx.test.util.TenantContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;
import org.springframework.lang.NonNull;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * 异步任务配置：让 TenantContext 能跨 @Async 线程传递
 *
 * <p><b>要解决的问题（一个很容易踩的坑）：</b></p>
 * <p>项目用 {@link TenantContext} 的 ThreadLocal 保存「当前登录用户ID」。
 * 但 ThreadLocal 是<b>线程私有</b>的，而 {@code @Async} 会把方法丢到别的线程去执行，
 * 新线程里读 ThreadLocal 一定是 null —— 也就是「明明登录了，异步任务里却找不到用户」。</p>
 *
 * <p>一旦取不到 userId，就没法确定该用谁的 API Key，会导致 AI 功能在异步链路里失效。</p>
 *
 * <p><b>解决办法：TaskDecorator（任务装饰器）。</b>
 * 它会在「提交任务的那一刻」把调用线程（请求线程）里的 ThreadLocal 值复制下来，
 * 等任务在异步线程里真正执行时再塞进去，执行完在 finally 里清理掉。
 * 这样既解决了跨线程丢失，又避免了线程池复用导致的「串号」（A 用户的任务读到了 B 用户的身份）。</p>
 *
 * <p>另外这里显式定义了线程池，替代 Spring 默认的 SimpleAsyncTaskExecutor。
 * 默认实现会为每个任务新建线程且不设上限，高并发下容易把机器打爆。</p>
 */
@Slf4j
@Configuration
public class AsyncConfig implements AsyncConfigurer {

    @Override
    public Executor getAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(16);
        executor.setQueueCapacity(200);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("ai-async-");
        // 关键：挂上「身份传递」装饰器
        executor.setTaskDecorator(new TenantContextTaskDecorator());
        // 优雅停机：等已提交的异步任务跑完再退出，避免任务被中断
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();

        log.info("异步线程池已初始化: core=4, max=16, queue=200, 已启用 TenantContext 跨线程传递");
        return executor;
    }

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (ex, method, params) ->
                log.error("异步任务执行异常, 方法: {}", method, ex);
    }

    /**
     * 把「提交任务线程」的登录身份复制到「执行任务线程」
     */
    static class TenantContextTaskDecorator implements TaskDecorator {

        @Override
        @NonNull
        public Runnable decorate(@NonNull Runnable runnable) {
            // ① 在「提交任务的线程」（=HTTP 请求线程）里把身份取出来
            Long userId = TenantContext.getCurrentUserId();
            Long tenantId = TenantContext.getCurrentTenantId();
            Long roleId = TenantContext.getCurrentRoleId();

            return () -> {
                try {
                    // ② 在「执行任务的线程」里把身份放回去
                    if (userId != null) {
                        TenantContext.setCurrentUserId(userId, tenantId, roleId);
                    }
                    runnable.run();
                } finally {
                    // ③ 必须清理！线程池的线程会被复用，
                    //    不清理会导致下一个任务读到上一个用户的身份（越权事故）
                    TenantContext.remove();
                }
            };
        }
    }
}
