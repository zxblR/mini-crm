<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage, type FormInstance, type FormRules } from 'element-plus';
import { Lock, User } from '@element-plus/icons-vue';

import { getApiErrorMessage } from '@/api/http';
import { useAuthStore } from '@/stores/auth';

interface LoginForm {
  account: string;
  password: string;
}

const router = useRouter();
const route = useRoute();
const auth = useAuthStore();
const formRef = ref<FormInstance>();
const form = reactive<LoginForm>({ account: '', password: '' });

const rules: FormRules<LoginForm> = {
  account: [{ required: true, message: '请输入邮箱或手机号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
};

function safeRedirect(value: unknown): string {
  if (typeof value !== 'string' || !value.startsWith('/') || value.startsWith('//')) {
    return '/dashboard';
  }
  return value;
}

async function submit(): Promise<void> {
  const valid = await formRef.value?.validate();
  if (!valid) return;

  try {
    await auth.login({ account: form.account.trim(), password: form.password });
    await router.replace(safeRedirect(route.query.redirect));
  } catch (error) {
    form.password = '';
    ElMessage.error(getApiErrorMessage(error));
  }
}

onMounted(() => {
  if (auth.isAuthenticated) void router.replace('/dashboard');
});
</script>

<template>
  <main class="page-shell login-shell">
    <el-card class="login-card" shadow="never">
      <div class="brand-mark">MINI CRM</div>
      <h1>登录</h1>
      <p class="muted">登录后进入团队工作台。</p>
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-position="top"
        @submit.prevent="submit"
      >
        <el-form-item label="账号" prop="account">
          <el-input
            v-model="form.account"
            placeholder="邮箱或手机号"
            :prefix-icon="User"
            autocomplete="username"
          />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input
            v-model="form.password"
            placeholder="密码"
            type="password"
            show-password
            :prefix-icon="Lock"
            autocomplete="current-password"
            @keyup.enter="submit"
          />
        </el-form-item>
        <el-button
          type="primary"
          native-type="submit"
          class="full-width"
          :loading="auth.isLoading"
        >
          登录
        </el-button>
      </el-form>
    </el-card>
  </main>
</template>
