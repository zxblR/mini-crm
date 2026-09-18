<script setup lang="ts">
import { Lock, User } from '@element-plus/icons-vue';
</script>

<template>
  <main class="page-shell login-shell">
    <el-card class="login-card" shadow="never">
      <div class="brand-mark">MINI CRM</div>
      <h1>登录</h1>
      <p class="muted">阶段 0 占位页面，认证流程将在后续阶段接入。</p>
      <el-form label-position="top" @submit.prevent>
        <el-form-item label="账号">
          <el-input placeholder="邮箱或手机号" :prefix-icon="User" disabled />
        </el-form-item>
        <el-form-item label="密码">
          <el-input placeholder="密码" type="password" :prefix-icon="Lock" disabled />
        </el-form-item>
        <el-button type="primary" class="full-width" disabled>登录（占位）</el-button>
      </el-form>
    </el-card>
  </main>
</template>
