// 自定义校验器

export const validators = {
  // 手机号校验（允许为空，为空时跳过校验——适合选填字段）
  phone: (rule: any, value: string, callback: Function) => {
    if (!value) {
      callback()  // 选填，空值直接通过
      return
    }
    const reg = /^1[3-9]\d{9}$/
    if (!reg.test(value)) {
      callback(new Error('请输入正确的11位手机号'))
    } else {
      callback()
    }
  },

  // 手机号校验（必填版本）
  phoneRequired: (rule: any, value: string, callback: Function) => {
    if (!value) {
      callback(new Error('请输入手机号'))
      return
    }
    const reg = /^1[3-9]\d{9}$/
    if (!reg.test(value)) {
      callback(new Error('请输入正确的11位手机号'))
    } else {
      callback()
    }
  },

  // 邮箱校验
  email: (rule: any, value: string, callback: Function) => {
    if (!value) {
      callback(new Error('请输入邮箱'))
      return
    }
    const reg = /^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$/
    if (!reg.test(value)) {
      callback(new Error('请输入正确邮箱'))
    } else {
      callback()
    }
  },

  // 不能早于当前时间
  futureTime: (rule: any, value: string, callback: Function) => {
    if (!value) {
      callback(new Error('请选择时间'))
      return
    }
    if (new Date(value) < new Date()) {
      callback(new Error('不能选择过去时间'))
    } else {
      callback()
    }
  },

  // 必须选择至少一项
  atLeastOne: (rule: any, value: any[], callback: Function) => {
    if (!value || value.length === 0) {
      callback(new Error('请至少选择一项'))
    } else {
      callback()
    }
  },

  // 字符长度范围校验
  lengthRange: (min: number, max: number) => {
    return (rule: any, value: string, callback: Function) => {
      if (!value) {
        callback(new Error(`请输入内容`))
        return
      }
      if (value.length < min || value.length > max) {
        callback(new Error(`长度应在 ${min} 到 ${max} 个字符之间`))
      } else {
        callback()
      }
    }
  },

  // 数值范围校验
  numberRange: (min: number, max: number) => {
    return (rule: any, value: number, callback: Function) => {
      if (value === null || value === undefined) {
        callback(new Error('请输入数值'))
        return
      }
      if (value < min || value > max) {
        callback(new Error(`数值应在 ${min} 到 ${max} 之间`))
      } else {
        callback()
      }
    }
  }
}

export default validators
