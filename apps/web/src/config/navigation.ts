import type { Component } from 'vue';
import {
  Calendar,
  DataBoard,
  Setting,
  TrendCharts,
  User,
} from '@element-plus/icons-vue';
import { RoleCode } from '@mini-crm/shared';

export interface NavigationItem {
  key: string;
  path: string;
  label: string;
  icon: Component;
  roles: RoleCode[];
}

export const navigationItems: NavigationItem[] = [
  {
    key: 'dashboard',
    path: '/dashboard',
    label: '工作台',
    icon: DataBoard,
    roles: [RoleCode.Owner, RoleCode.Admin, RoleCode.Sales, RoleCode.Support],
  },
  {
    key: 'leads',
    path: '/leads',
    label: '线索',
    icon: User,
    roles: [RoleCode.Owner, RoleCode.Admin, RoleCode.Sales, RoleCode.Support],
  },
  {
    key: 'tasks',
    path: '/tasks',
    label: '任务',
    icon: Calendar,
    roles: [RoleCode.Owner, RoleCode.Admin, RoleCode.Sales, RoleCode.Support],
  },
  {
    key: 'stats',
    path: '/stats',
    label: '统计',
    icon: TrendCharts,
    roles: [RoleCode.Owner, RoleCode.Admin, RoleCode.Sales, RoleCode.Support],
  },
  {
    key: 'settings',
    path: '/settings',
    label: '设置',
    icon: Setting,
    roles: [RoleCode.Owner, RoleCode.Admin],
  },
];
