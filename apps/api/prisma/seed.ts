import { PrismaClient, RoleCode } from '@prisma/client';
import argon2 from 'argon2';

const prisma = new PrismaClient();

const ORGANIZATION_ID = '00000000-0000-4000-8000-000000000001';
const ADMIN_ID = '00000000-0000-4000-8000-000000000011';
const SALES_ID = '00000000-0000-4000-8000-000000000012';
const VIEWER_ID = '00000000-0000-4000-8000-000000000013';

type SeedStage = {
  id: string;
  code: string;
  name: string;
  sortOrder: number;
  isDefault?: boolean;
  isWon?: boolean;
  isLost?: boolean;
};

const stages: SeedStage[] = [
  { id: '00000000-0000-4000-8000-000000000101', code: 'new', name: '新建', sortOrder: 1, isDefault: true },
  { id: '00000000-0000-4000-8000-000000000102', code: 'contacted', name: '已联系', sortOrder: 2 },
  { id: '00000000-0000-4000-8000-000000000103', code: 'qualified', name: '需求确认', sortOrder: 3 },
  { id: '00000000-0000-4000-8000-000000000104', code: 'proposal', name: '报价/方案', sortOrder: 4 },
  { id: '00000000-0000-4000-8000-000000000105', code: 'negotiation', name: '谈判', sortOrder: 5 },
  { id: '00000000-0000-4000-8000-000000000106', code: 'won', name: '赢单', sortOrder: 6, isWon: true },
  { id: '00000000-0000-4000-8000-000000000107', code: 'lost', name: '输单', sortOrder: 7, isLost: true },
];

const tags = [
  { id: '00000000-0000-4000-8000-000000000201', name: '高意向', color: '#E11D48' },
  { id: '00000000-0000-4000-8000-000000000202', name: '重点客户', color: '#2563EB' },
  { id: '00000000-0000-4000-8000-000000000203', name: '待回访', color: '#D97706' },
] as const;

async function main(): Promise<void> {
  const passwordHash = await argon2.hash(process.env.SEED_PASSWORD ?? 'MiniCrm-Dev-Only-ChangeMe');

  await prisma.organization.upsert({
    where: { id: ORGANIZATION_ID },
    update: { name: 'Mini CRM Demo Team', slug: 'mini-crm-demo', timezone: 'Asia/Shanghai', isActive: true },
    create: { id: ORGANIZATION_ID, name: 'Mini CRM Demo Team', slug: 'mini-crm-demo', timezone: 'Asia/Shanghai' },
  });

  const users = [
    { id: ADMIN_ID, name: 'Demo Admin', email: 'admin@example.com', role: RoleCode.admin },
    { id: SALES_ID, name: 'Demo Sales', email: 'sales@example.com', role: RoleCode.sales },
    { id: VIEWER_ID, name: 'Demo Viewer', email: 'viewer@example.com', role: RoleCode.viewer },
  ] as const;

  for (const user of users) {
    await prisma.user.upsert({
      where: { id: user.id },
      update: { name: user.name, email: user.email, normalizedEmail: user.email, passwordHash, isActive: true },
      create: {
        id: user.id,
        organizationId: ORGANIZATION_ID,
        name: user.name,
        email: user.email,
        normalizedEmail: user.email,
        passwordHash,
      },
    });
    await prisma.userRole.upsert({
      where: { userId_code: { userId: user.id, code: user.role } },
      update: {},
      create: { userId: user.id, code: user.role },
    });
  }

  for (const stage of stages) {
    await prisma.pipelineStage.upsert({
      where: { organizationId_code: { organizationId: ORGANIZATION_ID, code: stage.code } },
      update: { name: stage.name, sortOrder: stage.sortOrder, isDefault: stage.isDefault ?? false, isWon: stage.isWon ?? false, isLost: stage.isLost ?? false, isActive: true },
      create: { ...stage, organizationId: ORGANIZATION_ID },
    });
  }

  for (const tag of tags) {
    await prisma.tag.upsert({
      where: { organizationId_normalizedName: { organizationId: ORGANIZATION_ID, normalizedName: tag.name } },
      update: { name: tag.name, color: tag.color },
      create: { ...tag, organizationId: ORGANIZATION_ID, normalizedName: tag.name },
    });
  }

  const newStage = stages[0];
  const contactedStage = stages[1];
  const wonStage = stages[5];
  const lostStage = stages[6];
  const demoLeads: Array<{
    id: string;
    name: string;
    company: string;
    source: string;
    stageId: string;
    ownerId: string;
    wonAt?: Date;
    lostAt?: Date;
  }> = [
    { id: '00000000-0000-4000-8000-000000000301', name: '示例线索甲', company: '示例企业甲', source: 'website', stageId: newStage.id, ownerId: SALES_ID },
    { id: '00000000-0000-4000-8000-000000000302', name: '示例线索乙', company: '示例企业乙', source: 'referral', stageId: contactedStage.id, ownerId: SALES_ID },
    { id: '00000000-0000-4000-8000-000000000303', name: '示例线索丙', company: '示例企业丙', source: 'event', stageId: wonStage.id, ownerId: ADMIN_ID, wonAt: new Date('2026-09-01T00:00:00.000Z') },
    { id: '00000000-0000-4000-8000-000000000304', name: '示例线索丁', company: '示例企业丁', source: 'import', stageId: lostStage.id, ownerId: SALES_ID, lostAt: new Date('2026-09-02T00:00:00.000Z') },
  ];

  for (const lead of demoLeads) {
    await prisma.lead.upsert({
      where: { id: lead.id },
      update: { name: lead.name, company: lead.company, source: lead.source, stageId: lead.stageId, ownerId: lead.ownerId, wonAt: lead.wonAt, lostAt: lead.lostAt },
      create: { ...lead, organizationId: ORGANIZATION_ID, createdById: ADMIN_ID },
    });
  }
}

main()
  .catch((error: unknown) => {
    console.error(error);
    process.exitCode = 1;
  })
  .finally(async () => {
    await prisma.$disconnect();
  });
