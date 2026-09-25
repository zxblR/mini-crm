import { computed } from 'vue';
import type { FollowUp, LeadSummary } from '@mini-crm/shared';
import { RoleCode } from '@mini-crm/shared';

import { useAuthStore } from '@/stores/auth';

export function usePermissions() {
  const auth = useAuthStore();
  const isManager = computed(() =>
    auth.hasAnyRole([RoleCode.Owner, RoleCode.Admin]),
  );
  const isSales = computed(() => auth.hasRole(RoleCode.Sales));
  const isSupport = computed(() => auth.hasRole(RoleCode.Support));

  function ownsLead(lead: Pick<LeadSummary, 'ownerId'>): boolean {
    return Boolean(auth.user?.id && lead.ownerId === auth.user.id);
  }

  function canCreateLead(): boolean {
    return isManager.value || isSales.value;
  }

  function canEditLead(lead: Pick<LeadSummary, 'ownerId'>): boolean {
    return isManager.value || (isSales.value && ownsLead(lead));
  }

  function canArchiveLead(lead: Pick<LeadSummary, 'ownerId'>): boolean {
    return canEditLead(lead);
  }

  function canRestoreLead(): boolean {
    return isManager.value;
  }

  function canChangeLeadStatus(lead: Pick<LeadSummary, 'ownerId' | 'archivedAt'>): boolean {
    return !lead.archivedAt && (isManager.value || (isSales.value && ownsLead(lead)));
  }

  function canCreateFollowUp(lead: Pick<LeadSummary, 'ownerId'>): boolean {
    return isManager.value || (isSales.value && ownsLead(lead));
  }

  function canEditFollowUp(
    followUp: FollowUp,
    lead: Pick<LeadSummary, 'ownerId'>,
  ): boolean {
    return (
      isManager.value ||
      followUp.createdBy.id === auth.user?.id ||
      (isSales.value && ownsLead(lead))
    );
  }

  function canDeleteFollowUp(followUp: FollowUp): boolean {
    return isManager.value || followUp.createdBy.id === auth.user?.id;
  }

  return {
    isManager,
    isSales,
    isSupport,
    ownsLead,
    canCreateLead,
    canEditLead,
    canArchiveLead,
    canRestoreLead,
    canChangeLeadStatus,
    canCreateFollowUp,
    canEditFollowUp,
    canDeleteFollowUp,
  };
}
