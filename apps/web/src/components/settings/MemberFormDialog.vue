<script setup lang="ts">
import { reactive, ref, watch } from 'vue';
import type { FormInstance, FormRules } from 'element-plus';
import type { CreateUserInput, UserSummary } from '@mini-crm/shared';
import { ROLE_LABELS, RoleCode } from '@mini-crm/shared';

import { normalizeApiError } from '@/api/http';
import { createMember } from '@/api/users';

const props = defineProps<{ modelValue: boolean }>();
const emit = defineEmits<{ 'update:modelValue': [value: boolean]; saved: [member: UserSummary] }>();
const formRef = ref<FormInstance>();
const submitting = ref(false);
const errorMessage = ref<string | null>(null);
const form = reactive<CreateUserInput>({ name: '', email: null, phone: null, password: '', roleCodes: [RoleCode.Sales] });

function contactValidator(_rule: unknown, _value: string | null | undefined, callback: (error?: Error) => void): void {
  if (form.email?.trim() || form.phone?.trim()) callback();
  else callback(new Error('邮箱和手机号至少填写一项'));
}

const rules: FormRules<CreateUserInput> = {
  name: [{ required: true, whitespace: true, message: '请输入姓名', trigger: 'blur' }, { max: 80, message: '姓名不能超过 80 个字符', trigger: 'blur' }],
  email: [{ type: 'email', message: '请输入有效邮箱', trigger: 'blur' }, { max: 254, message: '邮箱不能超过 254 个字符', trigger: 'blur' }, { validator: contactValidator, trigger: 'blur' }],
  phone: [{ max: 32, message: '手机号不能超过 32 个字符', trigger: 'blur' }, { validator: contactValidator, trigger: 'blur' }],
  password: [{ required: true, message: '请输入初始密码', trigger: 'blur' }, { min: 8, max: 128, message: '密码长度为 8～128 个字符', trigger: 'blur' }],
  roleCodes: [{ required: true, type: 'array', min: 1, message: '至少选择一个角色', trigger: 'change' }],
};

watch(() => props.modelValue, (open) => {
  if (!open) return;
  Object.assign(form, { name: '', email: null, phone: null, password: '', roleCodes: [RoleCode.Sales] });
  errorMessage.value = null;
});

async function submit(): Promise<void> {
  if (!(await formRef.value?.validate())) return;
  submitting.value = true;
  errorMessage.value = null;
  try {
    const member = await createMember({
      name: form.name.trim(),
      email: form.email?.trim() || null,
      phone: form.phone?.trim() || null,
      password: form.password,
      roleCodes: form.roleCodes,
    });
    ElMessage.success('成员已创建');
    emit('saved', member);
    emit('update:modelValue', false);
  } catch (error) {
    errorMessage.value = normalizeApiError(error).message;
  } finally {
    submitting.value = false;
  }
}
</script>

<template>
  <el-dialog :model-value="modelValue" title="新增团队成员" width="580px" @update:model-value="emit('update:modelValue', $event)">
    <el-alert v-if="errorMessage" :title="errorMessage" type="error" :closable="false" class="form-error" />
    <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
      <el-form-item label="姓名" prop="name"><el-input v-model="form.name" maxlength="80" /></el-form-item>
      <div class="form-grid"><el-form-item label="邮箱" prop="email"><el-input v-model="form.email" maxlength="254" /></el-form-item><el-form-item label="手机号" prop="phone"><el-input v-model="form.phone" maxlength="32" /></el-form-item></div>
      <el-form-item label="初始密码" prop="password"><el-input v-model="form.password" type="password" show-password maxlength="128" /></el-form-item>
      <el-form-item label="角色" prop="roleCodes"><el-select v-model="form.roleCodes" multiple class="full-width"><el-option v-for="role in RoleCode" :key="role" :label="ROLE_LABELS[role]" :value="role" /></el-select></el-form-item>
    </el-form>
    <template #footer><el-button @click="emit('update:modelValue', false)">取消</el-button><el-button type="primary" :loading="submitting" @click="submit">创建</el-button></template>
  </el-dialog>
</template>

<style scoped>
.form-error { margin-bottom: 16px; }
.form-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 20px; }
</style>
