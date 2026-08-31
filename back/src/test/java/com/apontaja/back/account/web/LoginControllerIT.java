package com.apontaja.back.account.web;

import com.apontaja.back.account.domain.Account;
import com.apontaja.back.account.domain.AccountRepository;
import com.apontaja.back.account.domain.IdGenerator;
import com.apontaja.back.testsupport.PostgresTestcontainersConfiguration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Clock;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Import(PostgresTestcontainersConfiguration.class)
class LoginControllerIT {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private AccountRepository accountRepository;

  @Autowired
  private PasswordEncoder passwordEncoder;

  @Autowired
  private IdGenerator idGenerator;

  @Autowired
  private Clock clock;

  private void createAccount(String email, String rawPassword) {
    accountRepository.save(new Account(
        idGenerator.generate(), email, passwordEncoder.encode(rawPassword), clock.instant()));
  }

  @Test
  void login_reussi_renvoie_l_access_token_et_pose_le_cookie_refresh() throws Exception {
    createAccount("ivy@example.com", "un-mot-de-passe-suffisamment-long");

    mockMvc.perform(post("/api/auth/login")
        .contentType(MediaType.APPLICATION_JSON)
        .content("""
            {
              "email": "ivy@example.com",
              "password": "un-mot-de-passe-suffisamment-long"
            }
            """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.accessToken").isNotEmpty())
        .andExpect(jsonPath("$.email").value("ivy@example.com"))
        .andExpect(cookie().exists("refresh_token"))
        .andExpect(cookie().httpOnly("refresh_token", true))
        .andExpect(cookie().path("refresh_token", "/api/auth"));
  }

  @Test
  void login_repond_401_avec_mauvais_mot_de_passe() throws Exception {
    createAccount("jack@example.com", "un-mot-de-passe-suffisamment-long");

    mockMvc.perform(post("/api/auth/login")
        .contentType(MediaType.APPLICATION_JSON)
        .content("""
            {
              "email": "jack@example.com",
              "password": "mauvais-mot-de-passe"
            }
            """))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void login_repond_401_avec_email_inconnu() throws Exception {
    mockMvc.perform(post("/api/auth/login")
        .contentType(MediaType.APPLICATION_JSON)
        .content("""
            {
              "email": "personne@example.com",
              "password": "peu-importe-le-mot-de-passe"
            }
            """))
        .andExpect(status().isUnauthorized());
  }
}
