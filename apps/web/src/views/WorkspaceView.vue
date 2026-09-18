<script setup lang="ts">
import { DataBoard, User } from '@element-plus/icons-vue';
</script>

<template>
  <main class="workspace-shell">
    <header class="topbar">
      <div class="brand-mark">MINI CRM</div>
      <el-tag type="info">阶段 0</el-tag>
    </header>
    <section class="workspace-content">
      <el-breadcrumb class="breadcrumb">
        <el-breadcrumb-item>工作台</el-breadcrumb-item>
      </el-breadcrumb>
      <div class="workspace-heading">
        <div>
          <p class="eyebrow">WORKSPACE</p>
          <h1>工作台占位</h1>
          <p class="muted">业务指标、线索和跟进模块将在后续阶段接入。</p>
        </div>
        <el-button :icon="User" disabled>成员管理</el-button>
      </div>
      <el-empty description="阶段 0 暂无业务数据" :image-size="120">
        <template #image>
          <el-icon :size="64" color="#94a3b8"><DataBoard /></el-icon>
        </template>
      </el-empty>
    </section>
  </main>
</template>
