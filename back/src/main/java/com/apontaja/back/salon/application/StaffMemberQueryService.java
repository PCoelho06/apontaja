package com.apontaja.back.salon.application;

import com.apontaja.back.account.application.AccountQueryService;
import com.apontaja.back.account.application.AccountSummary;
import com.apontaja.back.salon.domain.StaffMembershipRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Lectures sur l'équipe d'un salon destinées à l'API et aux autres domaines
 * (resource.application y vérifie qu'un membre existe avant de le lier).
 */
@Service
public class StaffMemberQueryService {

    private final StaffMembershipManagementService managementService;
    private final StaffMembershipRepository staffMembershipRepository;
    private final AccountQueryService accountQueryService;

    StaffMemberQueryService(StaffMembershipManagementService managementService,
            StaffMembershipRepository staffMembershipRepository, AccountQueryService accountQueryService) {
        this.managementService = managementService;
        this.staffMembershipRepository = staffMembershipRepository;
        this.accountQueryService = accountQueryService;
    }

    /** Un appel par membre pour l'email : une équipe de salon reste de petite taille. */
    @Transactional(readOnly = true)
    public List<StaffMemberDetails> listMembersWithEmail(UUID salonId) {
        return managementService.listMembers(salonId).stream()
                .map(member -> new StaffMemberDetails(member.staffMembershipId(), member.accountId(),
                        accountQueryService.findAliveById(member.accountId()).map(AccountSummary::email)
                                .orElse(null),
                        member.role(), member.since()))
                .toList();
    }

    /** Vrai seulement si le membre existe, n'est pas retiré et appartient à CE salon. */
    @Transactional(readOnly = true)
    public boolean existsAliveInSalon(UUID salonId, UUID staffMembershipId) {
        return staffMembershipRepository.findAliveById(staffMembershipId)
                .filter(membership -> membership.getSalonId().equals(salonId)).isPresent();
    }
}
