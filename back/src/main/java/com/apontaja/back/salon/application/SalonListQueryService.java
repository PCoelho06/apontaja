package com.apontaja.back.salon.application;

import com.apontaja.back.organization.application.OrganizationMembershipQueryService;
import com.apontaja.back.salon.domain.Salon;
import com.apontaja.back.salon.domain.SalonRepository;
import com.apontaja.back.salon.domain.StaffMembership;
import com.apontaja.back.salon.domain.StaffMembershipRepository;
import com.apontaja.back.salon.domain.StaffRole;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Liste des salons accessibles à un compte : union de deux sources
 * (StaffMembership actif + tous les salons des organisations où le compte est
 * OWNER, même règle que SalonAccessGuard), dédoublonnée par salonId.
 *
 * <p>
 * Pagination appliquée EN MÉMOIRE sur cette union, pas via une requête SQL
 * paginée au niveau base : le volume réel (salons accessibles à UN compte)
 * reste borné et faible en v1 — une vraie requête UNION paginée en base serait
 * disproportionnée à ce stade. À revoir si un compte avec un grand nombre de
 * salons devient un cas réel.
 */
@Service
public class SalonListQueryService {

    private final SalonRepository salonRepository;
    private final StaffMembershipRepository staffMembershipRepository;
    private final OrganizationMembershipQueryService organizationMembershipQueryService;

    SalonListQueryService(SalonRepository salonRepository, StaffMembershipRepository staffMembershipRepository,
            OrganizationMembershipQueryService organizationMembershipQueryService) {
        this.salonRepository = salonRepository;
        this.staffMembershipRepository = staffMembershipRepository;
        this.organizationMembershipQueryService = organizationMembershipQueryService;
    }

    public Page<SalonListItem> findAccessibleSalons(UUID accountId, Pageable pageable) {
        Map<UUID, SalonAccessRole> roleBySalonId = new LinkedHashMap<>();

        for (StaffMembership membership : staffMembershipRepository.findAliveByAccountId(accountId)) {
            roleBySalonId.put(membership.getSalonId(), toAccessRole(membership.getRole()));
        }

        for (UUID organizationId : organizationMembershipQueryService.findAliveOwnedOrganizationIds(accountId)) {
            for (Salon salon : salonRepository.findAliveByOrganizationId(organizationId)) {
                roleBySalonId.putIfAbsent(salon.getId(), SalonAccessRole.ORGANIZATION_OWNER);
            }
        }

        List<Salon> salons = salonRepository.findAliveByIds(roleBySalonId.keySet()).stream()
                .sorted(Comparator.comparing(Salon::getCreatedAt)).toList();

        int total = salons.size();
        int fromIndex = Math.min((int) pageable.getOffset(), total);
        int toIndex = Math.min(fromIndex + pageable.getPageSize(), total);

        List<SalonListItem> pageContent = salons.subList(fromIndex, toIndex).stream()
                .map(salon -> toListItem(salon, roleBySalonId.get(salon.getId()))).toList();

        return new PageImpl<>(pageContent, pageable, total);
    }

    private static SalonAccessRole toAccessRole(StaffRole role) {
        return switch (role) {
        case OWNER -> SalonAccessRole.OWNER;
        case MANAGER -> SalonAccessRole.MANAGER;
        case EMPLOYEE -> SalonAccessRole.EMPLOYEE;
        };
    }

    private static SalonListItem toListItem(Salon salon, SalonAccessRole role) {
        return new SalonListItem(salon.getId(), salon.getOrganizationId(), salon.getName(), salon.getAddress(),
                salon.getPostalCode(), salon.getCity(), salon.getCountry(), salon.getPhone(), salon.getTimezone(),
                role);
    }
}
