package com.apontaja.back.account.infrastructure;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Seau de la réservation en ligne : 10 POST par minute et par IP, un seau pour tous les salons. */
class RateLimitingFilterBookingTest {

    private final RateLimitingFilter filter = new RateLimitingFilter(true);

    private int status(String method, String uri, String remoteAddr) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        request.setRequestURI(uri);
        request.setRemoteAddr(remoteAddr);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, response, chain);
        return chain.getRequest() == null ? response.getStatus() : 200;
    }

    private static String bookingUri() {
        return "/api/me/salons/" + UUID.randomUUID() + "/bookings";
    }

    @Test
    void limite_a_dix_reservations_par_minute_et_par_ip_tous_salons_confondus() throws Exception {
        for (int i = 0; i < 10; i++) {
            assertThat(status("POST", bookingUri(), "10.0.0.1")).isEqualTo(200);
        }

        assertThat(status("POST", bookingUri(), "10.0.0.1")).isEqualTo(429);
        assertThat(status("POST", bookingUri(), "10.0.0.2")).isEqualTo(200);
    }

    @Test
    void ne_limite_ni_les_lectures_ni_les_autres_chemins() throws Exception {
        for (int i = 0; i < 15; i++) {
            assertThat(status("GET", bookingUri(), "10.0.0.3")).isEqualTo(200);
            assertThat(status("POST", "/api/me/salons/" + UUID.randomUUID() + "/autre", "10.0.0.3"))
                    .isEqualTo(200);
        }
    }

    @Test
    void ne_limite_rien_quand_le_filtre_est_desactive() throws Exception {
        RateLimitingFilter disabled = new RateLimitingFilter(false);
        for (int i = 0; i < 15; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", bookingUri());
            request.setRequestURI(request.getRequestURI());
            MockHttpServletResponse response = new MockHttpServletResponse();
            MockFilterChain chain = new MockFilterChain();
            disabled.doFilter(request, response, chain);
            assertThat(chain.getRequest()).isNotNull();
        }
    }
}
