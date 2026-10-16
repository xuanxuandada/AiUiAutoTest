/** 用户信息 */
export interface UserInfo {
  userId: string | number
  name: string
  token: string
  tenantId: string | number
  roleId: string | number
}

/** 租户信息 */
export interface TenantInfo {
  id: string | number
  name: string
  code?: string
}

/** 登录参数 */
export interface LoginParams {
  name: string
  password: string | number
  tenantid?: string | number
}

/** 验证密码参数 */
export interface VerifyParams {
  name: string
  password: string | number
}

/** 验证密码返回 */
export interface VerifyResult {
  userId: string | number
  message: string
}

/** 注册参数 */
export interface RegisterParams {
  name: string
  phone: string
  password: string
}

/** 发送验证码参数 */
export interface SendCodeParams {
  phone: string
}

/** 忘记密码参数 */
export interface ForgotPasswordParams {
  phone: string
  username: string
  smsCode: string
  password: string
}

/** 修改密码参数 */
export interface ChangePasswordParams {
  oldPassword: string
  newPassword: string
}
