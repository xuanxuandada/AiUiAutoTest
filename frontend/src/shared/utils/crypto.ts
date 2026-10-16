import CryptoJS from 'crypto-js'

/**
 * 密码加盐（固定盐值，防彩虹表）
 */
const SALT = 'yx_front_salt_2024'

/**
 * 对密码进行 SHA256 哈希（不可逆）
 * 后端直接存储哈希值，登录时比对哈希值即可，无需解密
 * @param password 明文密码
 * @returns SHA256 哈希后的十六进制字符串
 */
export function encryptPassword(password: string | number): string {
  const salted = String(password) + SALT
  return CryptoJS.SHA256(salted).toString()
}
