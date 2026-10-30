package com.apontaja.back.appointment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.apontaja.back.account.domain.AccessTokenIssuer;
import com.apontaja.back.appointment.domain.RequestExpiryPolicy;
import com.apontaja.back.shared.domain.EmailSender;
import com.apontaja.back.testsupport.PostgresTestcontainersConfiguration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Expiration de bout en bout. Les lignes sont posées en SQL natif (états inatteignables par l'API : statut et dates
 * de création). Exception documentée à la règle des dates fixes : l'horloge réelle est utilisée, de sorte que
 * le service n'expire que ce qui est réellement échu (les autres ITs partagent la base) ; les assertions portent
 * uniquement sur les identifiants créés ici.
 */
@SpringBootTest(properties = "apontaja.security.rate-limiting.enabled=false")
@AutoConfigureMockMvc
@Import(PostgresTestcontainersConfiguration.class)
class RequestExpiryIT {

    @Autowired
    private RequestExpiryService expiryService;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private AccessTokenIssuer tokenIssuer;
    @MockitoBean
    private EmailSender emailSender;

    private Instant now;
    private UUID salonId;
    private UUID resourceId;
    private UUID serviceId;
    private UUID accountId;
    private UUID profileId;
    private String clientEmail;

    @BeforeEach
    void fixtures() {
        now = Instant.now();
        UUID organizationId = UUID.randomUUID();
        salonId = UUID.randomUUID();
        resourceId = UUID.randomUUID();
        serviceId = UUID.randomUUID();
        accountId = UUID.randomUUID();
        profileId = UUID.randomUUID();
        clientEmail = "client-" + accountId + "@example.com";
        jdbc.update("INSERT INTO organization (id, name) VALUES (?, 'Org Test')", organizationId);
        jdbc.update("INSERT INTO salon (id, organization_id, name, address, postal_code, city, country, timezone) "
                + "VALUES (?, ?, 'Salon Test', '1 rua do Teste', '1000-001', 'Lisboa', 'Portugal', "
                + "'Europe/Lisbon')", salonId, organizationId);
        jdbc.update("INSERT INTO resource (id, salon_id, type, name) VALUES (?, ?, 'EMPLOYEE', 'Ana')", resourceId,
                salonId);
        jdbc.update("INSERT INTO service (id, salon_id, name, default_duration_minutes, default_price_cents) "
                + "VALUES (?, ?, 'Corte', 30, 2500)", serviceId, salonId);
        jdbc.update("INSERT INTO service_resource (service_id, resource_id) VALUES (?, ?)", serviceId, resourceId);
        jdbc.update("INSERT INTO account (id, email, first_name, last_name, email_verified_at, password_hash) "
                + "VALUES (?, ?, 'Maria', 'Silva', now(), 'hash')", accountId, clientEmail);
        jdbc.update("INSERT INTO customer_profile (id, account_id, first_name, last_name, email) "
                + "VALUES (?, ?, 'Maria', 'Silva', ?)", profileId, accountId, clientEmail);
    }

    @Test
    void expiresADueRequestFreesTheResourceAndEmailsTheClient() {
        UUID due = insertAppointment("SCHEDULED", now.minus(Duration.ofDays(3)), now.plus(Duration.ofDays(10)));

        int expired = expiryService.expireDueRequests();

        assertThat(expired).isGreaterThanOrEqualTo(1);
        Map<String, Object> row = jdbc.queryForMap(
                "SELECT status, cancelled_by, cancellation_reason, cancelled_at FROM appointment WHERE id = ?", due);
        assertThat(row.get("status")).isEqualTo("CANCELLED");
        assertThat(row.get("cancelled_by")).isNull();
        assertThat(row.get("cancellation_reason")).isEqualTo(RequestExpiryPolicy.EXPIRY_REASON);
        assertThat(row.get("cancelled_at")).isNotNull();
        Boolean active = jdbc.queryForObject("SELECT is_active FROM appointment_resource WHERE appointment_id = ?",
                Boolean.class, due);
        assertThat(active).isFalse();
        verify(emailSender, timeout(2000)).send(eq(clientEmail), contains("expirado"), anyString());
    }

    @Test
    void leavesARequestThatIsNotDueYetAndAConfirmedAppointment() {
        UUID recent = insertAppointment("SCHEDULED", now.minus(Duration.ofHours(1)), now.plus(Duration.ofDays(10)));
        UUID confirmed = insertAppointment("CONFIRMED", now.minus(Duration.ofDays(5)),
                now.plus(Duration.ofDays(11)));

        expiryService.expireDueRequests();

        assertThat(statusOf(recent)).isEqualTo("SCHEDULED");
        assertThat(statusOf(confirmed)).isEqualTo("CONFIRMED");
        verify(emailSender, never()).send(eq(clientEmail), anyString(), anyString());
    }

    @Test
    void expiresARequestWhoseStartHasPassedWithoutEmail() {
        UUID past = insertAppointment("SCHEDULED", now.minus(Duration.ofDays(5)), now.minus(Duration.ofDays(1)));

        expiryService.expireDueRequests();

        assertThat(statusOf(past)).isEqualTo("CANCELLED");
        verify(emailSender, never()).send(eq(clientEmail), anyString(), anyString());
    }

    @Test
    void theClientSeesAnExpiredRequestButNotAStaffCancellationWithTheSameReason() throws Exception {
        UUID expiredId = insertAppointment("SCHEDULED", now.minus(Duration.ofDays(3)),
                now.plus(Duration.ofDays(10)));
        UUID cancelledByStaff = insertAppointment("CANCELLED", now.minus(Duration.ofDays(3)),
                now.plus(Duration.ofDays(12)));
        jdbc.update("UPDATE appointment SET cancelled_by = ?, cancelled_at = now(), cancellation_reason = ? "
                + "WHERE id = ?", accountId, RequestExpiryPolicy.EXPIRY_REASON, cancelledByStaff);

        expiryService.expireDueRequests();

        mockMvc.perform(get("/api/me/appointments").param("scope", "past")
                .header("Authorization", "Bearer " + tokenIssuer.issue(accountId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.appointmentId=='" + expiredId + "')].status").value("CANCELLED"))
                .andExpect(jsonPath("$.content[?(@.appointmentId=='" + expiredId + "')].expired").value(true))
                .andExpect(jsonPath("$.content[?(@.appointmentId=='" + cancelledByStaff + "')].expired")
                        .value(false));
    }

    private String statusOf(UUID appointmentId) {
        return jdbc.queryForObject("SELECT status FROM appointment WHERE id = ?", String.class, appointmentId);
    }

    /** Un seul RDV actif par ressource à un instant donné : les débuts sont donc tous distincts (au jour près). */
    private UUID insertAppointment(String status, Instant createdAt, Instant startAt) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO appointment (id, salon_id, customer_profile_id, service_id, start_at, end_at, "
                + "status, price_at_booking_cents, duration_at_booking_minutes, created_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, 2500, 30, ?)", id, salonId, profileId, serviceId,
                Timestamp.from(startAt), Timestamp.from(startAt.plus(Duration.ofMinutes(30))), status,
                Timestamp.from(createdAt));
        jdbc.update("INSERT INTO appointment_resource (appointment_id, resource_id) VALUES (?, ?)", id,
                resourceId);
        return id;
    }
}
