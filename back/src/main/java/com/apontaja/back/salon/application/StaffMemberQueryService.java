package com.apontaja.back.salon.application;

import com.apontaja.back.account.application.AccountQueryService;
import com.apontaja.back.account.application.AccountSummary;
import com.apontaja.back.salon.domain.StaffMembershipRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.Optional;

/**
 * Lectures sur l'équipe d'un salon destinées à l'API et aux autres domaines
 * (resource.application y vérifie qu'un membre existe avant de le lier).
 */
@Service
public class StaffMemberQueryService {
        private static final String DELETED_ACCOUNT_NAME = "Compte supprimé";

        private final StaffMembershipManagementService managementService;
        private final StaffMembershipRepository staffMembershipRepository;
        private final AccountQueryService accountQueryService;
        private final StaffManagementGuard staffManagementGuard;

        StaffMemberQueryService(StaffMembershipManagementService managementService,
                        StaffMembershipRepository staffMembershipRepository, AccountQueryService accountQueryService,
                        StaffManagementGuard staffManagementGuard) {
                this.managementService = managementService;
                this.staffMembershipRepository = staffMembershipRepository;
                this.accountQueryService = accountQueryService;
                this.staffManagementGuard = staffManagementGuard;
        }

        /**
         * Équipe du salon avec le nom d'affichage de chaque membre. L'email n'est
         * renseigné que si l'acteur peut le voir (OWNER/MANAGER du salon, ou OWNER de
         * l'organisation).
         */
        @Transactional(readOnly = true)
        public List<StaffMemberDetails> listMembers(UUID salonId, UUID actorAccountId) {
                boolean withEmail = staffManagementGuard.canViewStaffEmails(actorAccountId, salonId);
                return managementService.listMembers(salonId).stream().map(member -> {
                        Optional<AccountSummary> account = accountQueryService.findAliveById(member.accountId());
                        String displayName = account.map(AccountSummary::displayName).orElse(DELETED_ACCOUNT_NAME);
                        String email = withEmail ? account.map(AccountSummary::email).orElse(null) : null;
                        return new StaffMemberDetails(member.staffMembershipId(), member.accountId(), displayName,
                                        email, member.role(), member.since());
                }).toList();
        }

        /**
         * Vrai seulement si le membre existe, n'est pas retiré et appartient à CE
         * salon.
         */
        @Transactional(readOnly = true)
        public boolean existsAliveInSalon(UUID salonId, UUID staffMembershipId) {
                return staffMembershipRepository.findAliveById(staffMembershipId)
                                .filter(membership -> membership.getSalonId().equals(salonId)).isPresent();
        }
}
