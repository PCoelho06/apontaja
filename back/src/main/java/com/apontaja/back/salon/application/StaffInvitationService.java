package com.apontaja.back.salon.application;

import com.apontaja.back.account.application.AccountQueryService;
import com.apontaja.back.account.application.AccountSummary;
import com.apontaja.back.salon.domain.Salon;
import com.apontaja.back.salon.domain.SalonRepository;
import com.apontaja.back.salon.domain.StaffInvitation;
import com.apontaja.back.salon.domain.StaffInvitationRepository;
import com.apontaja.back.salon.domain.StaffMembership;
import com.apontaja.back.salon.domain.StaffMembershipRepository;
import com.apontaja.back.salon.domain.StaffRole;
import com.apontaja.back.shared.domain.EmailSender;
import com.apontaja.back.shared.domain.IdGenerator;
import com.apontaja.back.shared.domain.OpaqueTokenGenerator;
import com.apontaja.back.shared.domain.TokenHasher;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class StaffInvitationService {

    private final StaffInvitationRepository staffInvitationRepository;
    private final StaffMembershipRepository staffMembershipRepository;
    private final SalonRepository salonRepository;
    private final AccountQueryService accountQueryService;
    private final EmailSender emailSender;
    private final OpaqueTokenGenerator opaqueTokenGenerator;
    private final TokenHasher tokenHasher;
    private final IdGenerator idGenerator;
    private final Clock clock;
    private final Duration invitationTtl;
    private final String frontendBaseUrl;

    StaffInvitationService(StaffInvitationRepository staffInvitationRepository,
            StaffMembershipRepository staffMembershipRepository, SalonRepository salonRepository,
            AccountQueryService accountQueryService, EmailSender emailSender, OpaqueTokenGenerator opaqueTokenGenerator,
            TokenHasher tokenHasher, IdGenerator idGenerator, Clock clock,
            @Value("${apontaja.security.staff-invitation.ttl:7d}") Duration invitationTtl,
            @Value("${apontaja.frontend.base-url:http://localhost:5173}") String frontendBaseUrl) {
        this.staffInvitationRepository = staffInvitationRepository;
        this.staffMembershipRepository = staffMembershipRepository;
        this.salonRepository = salonRepository;
        this.accountQueryService = accountQueryService;
        this.emailSender = emailSender;
        this.opaqueTokenGenerator = opaqueTokenGenerator;
        this.tokenHasher = tokenHasher;
        this.idGenerator = idGenerator;
        this.clock = clock;
        this.invitationTtl = invitationTtl;
        this.frontendBaseUrl = frontendBaseUrl;
    }

    @Transactional
    public CreateStaffInvitationResult createInvitation(CreateStaffInvitationCommand command) {
        StaffRole role = parseRole(command.role());
        String email = command.email().trim();

        accountQueryService.findAliveAccountIdByEmail(email)
                .filter(accountId -> staffMembershipRepository
                        .findAliveByAccountIdAndSalonId(accountId, command.salonId()).isPresent())
                .ifPresent(accountId -> {
                    throw new AccountAlreadyStaffMemberException();
                });

        if (staffInvitationRepository.existsPendingBySalonIdAndEmail(command.salonId(), email)) {
            throw new StaffInvitationAlreadyPendingException();
        }

        Instant now = clock.instant();
        String rawToken = opaqueTokenGenerator.generate();

        StaffInvitation invitation = new StaffInvitation(idGenerator.generate(), command.salonId(), email, role,
                command.invitedBy(), tokenHasher.hash(rawToken), now.plus(invitationTtl), now);
        staffInvitationRepository.save(invitation);

        String link = frontendBaseUrl + "/invitations/accepter?token=" + rawToken;
        emailSender.send(email, "Invitation à rejoindre un salon sur Apontaja",
                "Vous avez été invité à rejoindre un salon (rôle : " + role + "). " + "Lien valable "
                        + invitationTtl.toDays() + " jours : " + link);

        return new CreateStaffInvitationResult(invitation.getId());
    }

    public List<StaffInvitationSummary> listPending(UUID salonId) {
        return staffInvitationRepository.findPendingBySalonId(salonId).stream()
                .map(invitation -> new StaffInvitationSummary(invitation.getId(), invitation.getEmail(),
                        invitation.getRole().name(), invitation.getCreatedAt(), invitation.getExpiresAt()))
                .toList();
    }

    /**
     * Public, non authentifié — le front en a besoin avant même que la personne ait
     * un compte.
     */
    public StaffInvitationLookupResult lookup(String rawToken) {
        StaffInvitation invitation = staffInvitationRepository.findByTokenHash(tokenHasher.hash(rawToken))
                .filter(inv -> inv.isUsable(clock.instant())).orElseThrow(InvalidOrExpiredInvitationException::new);

        String salonName = salonRepository.findAliveById(invitation.getSalonId()).map(Salon::getName)
                .orElseThrow(InvalidOrExpiredInvitationException::new);

        boolean accountExists = accountQueryService.findAliveAccountIdByEmail(invitation.getEmail()).isPresent();

        return new StaffInvitationLookupResult(invitation.getId(), invitation.getEmail(), invitation.getRole().name(),
                salonName, accountExists, invitation.getExpiresAt());
    }

    /**
     * L'email du compte connecté doit correspondre exactement à l'email de
     * l'invitation — empêche d'accepter l'invitation destinée à quelqu'un d'autre
     * avec son propre compte.
     */
    @Transactional
    public void accept(UUID accountId, String rawToken) {
        StaffInvitation invitation = staffInvitationRepository.findByTokenHash(tokenHasher.hash(rawToken))
                .filter(inv -> inv.isUsable(clock.instant())).orElseThrow(InvalidOrExpiredInvitationException::new);

        AccountSummary account = accountQueryService.findAliveById(accountId)
                .orElseThrow(InvalidOrExpiredInvitationException::new);

        if (!account.email().equalsIgnoreCase(invitation.getEmail())) {
            throw new InvitationEmailMismatchException();
        }

        Instant now = clock.instant();
        staffMembershipRepository.save(new StaffMembership(idGenerator.generate(), accountId, invitation.getSalonId(),
                invitation.getRole(), now));

        invitation.markAccepted(now);
        staffInvitationRepository.save(invitation);
    }

    private static StaffRole parseRole(String role) {
        try {
            return StaffRole.valueOf(role);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new InvalidStaffRoleException(role);
        }
    }
}
