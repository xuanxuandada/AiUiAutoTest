<template>
  <div class="card case-card full-screen-table">
    <div class="case-list-header">
      <div class="case-list-title">新建测试套件</div>
      <button class="btn" @click="goBack">返回套件列表</button>
    </div>
    <div class="create-form">
      <div class="form-item">
        <label><span class="required-star">*</span>套件名称</label>
        <el-input
          v-model="form.name"
          placeholder="请输入套件名称"
          size="large"
        />
      </div>
      <div class="form-item">
        <label>描述</label>
        <el-input
          v-model="form.description"
          type="textarea"
          placeholder="请输入套件描述"
          :rows="3"
        />
      </div>
      <div class="form-actions">
        <el-button @click="goBack">取消</el-button>
        <el-button type="primary" @click="handleSave" :disabled="!form.name.trim()">保存</el-button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { reactive } from 'vue'
import { useRouter } from 'vue-router'
import { useSuiteStore } from './suiteStore'
import { ElMessage } from 'element-plus'

const router = useRouter()
const { addSuite } = useSuiteStore()

const form = reactive({
  name: '',
  description: ''
})

function goBack() {
  router.push('/suites')
}

function handleSave() {
  if (!form.name.trim()) {
    ElMessage.warning('请输入套件名称')
    return
  }
  addSuite({
    name: form.name.trim(),
    description: form.description.trim()
  }).then(() => {
    ElMessage.success('套件创建成功')
    router.push('/suites')
  }).catch(() => {
    ElMessage.error('创建失败，请重试')
  })
}
</script>

<style scoped>
.create-form {
  max-width: 500px;
  margin: 20px 0;
}
.form-item {
  margin-bottom: 20px;
}
.form-item label {
  display: block;
  font-size: 14px;
  font-weight: 600;
  color: #374151;
  margin-bottom: 6px;
}
.form-actions {
  display: flex;
  gap: 10px;
  margin-top: 30px;
}
.required-star {
  color: #dc2626;
  margin-right: 2px;
}
.full-select {
  width: 100%;
}
</style>
