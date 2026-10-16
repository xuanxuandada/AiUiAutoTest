package com.yx.test.util;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * 密码工具类 —— 全项目只有这里碰 BCrypt，其它地方一律调用本类的方法。
 *
 * <p>为什么用 BCrypt 而不是 MD5/SHA：
 * <ul>
 *   <li>BCrypt 自带「随机盐」，同一个密码每次加密出来的结果都不一样，
 *       所以攻击者没法用一张「密码 → 哈希」对照表（彩虹表）批量反查。</li>
 *   <li>BCrypt 故意设计得慢（可调强度），暴力枚举的成本极高。</li>
 *   <li>MD5/SHA 是「快」哈希，专门为计算速度优化，正好方便攻击者爆破，不适合存密码。</li>
 * </ul>
 *
 * <p>注意：BCrypt 是「单向」的，加密后无法还原成明文。
 * 所以校验密码不能再写 {@code 明文.equals(库里的值)}，只能用 {@link #matches} 让 BCrypt
 * 把明文按同样的规则算一遍再比对。
 */
public final class PasswordUtils {

    /**
     * BCrypt 计算器。强度 10 是业界默认值：
     * 数字每 +1，计算耗时翻倍；10 大约十几毫秒，兼顾安全与登录速度。
     */
    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder();

    /** BCrypt 哈希的统一特征：以 $2a$ / $2b$ / $2y$ 开头 */
    private static final String BCRYPT_PREFIX_2A = "$2a$";
    private static final String BCRYPT_PREFIX_2B = "$2b$";
    private static final String BCRYPT_PREFIX_2Y = "$2y$";

    private PasswordUtils() {
    }

    /**
     * 把明文密码加密成可以入库的哈希串（长度固定 60 个字符）。
     * 数据库 user.password 字段是 varchar(100)，装得下。
     */
    public static String encode(String rawPassword) {
        if (rawPassword == null) {
            return null;
        }
        return ENCODER.encode(rawPassword);
    }

    /**
     * 校验密码是否正确。
     *
     * <p>兼容历史数据：如果库里存的是老数据（明文），就走 equals 比对；
     * 如果库里已经是 BCrypt 哈希，就走 BCrypt 校验。
     * 这一步是必须的 —— 把明文丢进 BCrypt 校验会直接抛异常，而不是返回 false。
     *
     * @param rawPassword    用户这次输入的明文密码
     * @param storedPassword 数据库里存的密码（可能是哈希，也可能是老明文）
     */
    public static boolean matches(String rawPassword, String storedPassword) {
        if (rawPassword == null || storedPassword == null) {
            return false;
        }
        if (isEncoded(storedPassword)) {
            return ENCODER.matches(rawPassword, storedPassword);
        }
        // 老数据：库里还是明文，直接比。等用户下次登录成功会自动升级成哈希。
        return storedPassword.equals(rawPassword);
    }

    /**
     * 判断库里存的是不是 BCrypt 哈希。
     * 用来区分「老数据明文」和「已加密数据」，也是自动升级的判断依据。
     */
    public static boolean isEncoded(String storedPassword) {
        if (storedPassword == null) {
            return false;
        }
        return storedPassword.startsWith(BCRYPT_PREFIX_2A)
                || storedPassword.startsWith(BCRYPT_PREFIX_2B)
                || storedPassword.startsWith(BCRYPT_PREFIX_2Y);
    }
}
