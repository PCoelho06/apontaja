package com.apontaja.backend.service;

import com.apontaja.backend.exception.TokenRefreshException;
import com.apontaja.backend.model.RefreshToken;
import com.apontaja.backend.model.Role;
import com.apontaja.backend.model.User;
import com.apontaja.backend.repository.RefreshTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceImplTest {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @InjectMocks
    private RefreshTokenServiceImpl refreshTokenService;

    @Captor
    private ArgumentCaptor<RefreshToken> refreshTokenCaptor;

    private User user;
    private RefreshToken validToken;
    private RefreshToken expiredToken;
    private static final Long REFRESH_TOKEN_DURATION_MS = 86400000L; // 24 heures

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(refreshTokenService, "refreshTokenDurationMs", REFRESH_TOKEN_DURATION_MS);

        user = User.builder()
                .id(1L)
                .email("test@example.com")
                .password("encoded-password")
                .firstName("John")
                .lastName("Doe")
                .role(Role.USER)
                .enabled(true)
                .build();

        validToken = RefreshToken.builder()
                .id(1L)
                .token("valid-token-123")
                .user(user)
                .expiryDate(Instant.now().plusMillis(REFRESH_TOKEN_DURATION_MS))
                .build();

        expiredToken = RefreshToken.builder()
                .id(2L)
                .token("expired-token-456")
                .user(user)
                .expiryDate(Instant.now().minusMillis(3600000L)) // Expiré il y a 1 heure
                .build();
    }

    @Test
    void createRefreshToken_WithValidUser_ShouldCreateAndSaveToken() {
        // Given
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(invocation -> {
            RefreshToken token = invocation.getArgument(0);
            return RefreshToken.builder()
                    .id(1L)
                    .token(token.getToken())
                    .user(token.getUser())
                    .expiryDate(token.getExpiryDate())
                    .build();
        });

        // When
        RefreshToken result = refreshTokenService.createRefreshToken(user);

        // Then
        verify(refreshTokenRepository).save(refreshTokenCaptor.capture());
        RefreshToken savedToken = refreshTokenCaptor.getValue();

        assertThat(result).isNotNull();
        assertThat(savedToken.getUser()).isEqualTo(user);
        assertThat(savedToken.getToken()).isNotNull().isNotEmpty();
        assertThat(savedToken.getExpiryDate()).isAfter(Instant.now());
        assertThat(savedToken.getExpiryDate()).isBefore(Instant.now().plusMillis(REFRESH_TOKEN_DURATION_MS + 1000));
    }

    @Test
    void createRefreshToken_ShouldGenerateUniqueToken() {
        // Given
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        RefreshToken token1 = refreshTokenService.createRefreshToken(user);
        RefreshToken token2 = refreshTokenService.createRefreshToken(user);

        // Then
        assertThat(token1.getToken()).isNotEqualTo(token2.getToken());
        verify(refreshTokenRepository, times(2)).save(any(RefreshToken.class));
    }

    @Test
    void findByToken_WithExistingToken_ShouldReturnToken() {
        // Given
        when(refreshTokenRepository.findByToken("valid-token-123")).thenReturn(Optional.of(validToken));

        // When
        Optional<RefreshToken> result = refreshTokenService.findByToken("valid-token-123");

        // Then
        assertThat(result).isPresent().contains(validToken);
        verify(refreshTokenRepository).findByToken("valid-token-123");
    }

    @Test
    void findByToken_WithNonExistingToken_ShouldReturnEmpty() {
        // Given
        when(refreshTokenRepository.findByToken("non-existing-token")).thenReturn(Optional.empty());

        // When
        Optional<RefreshToken> result = refreshTokenService.findByToken("non-existing-token");

        // Then
        assertThat(result).isEmpty();
        verify(refreshTokenRepository).findByToken("non-existing-token");
    }

    @Test
    void verifyExpiration_WithValidToken_ShouldReturnToken() {
        // Given & When
        RefreshToken result = refreshTokenService.verifyExpiration(validToken);

        // Then
        assertThat(result).isEqualTo(validToken);
        verify(refreshTokenRepository, never()).delete(any(RefreshToken.class));
    }

    @Test
    void verifyExpiration_WithExpiredToken_ShouldDeleteTokenAndThrowException() {
        // Given
        doNothing().when(refreshTokenRepository).delete(expiredToken);

        // When & Then
        assertThatThrownBy(() -> refreshTokenService.verifyExpiration(expiredToken))
                .isInstanceOf(TokenRefreshException.class)
                .hasMessageContaining("Refresh token was expired")
                .hasMessageContaining("expired-token-456");

        verify(refreshTokenRepository).delete(expiredToken);
    }

    @Test
    void deleteByUser_WithValidUser_ShouldDeleteTokens() {
        // Given
        doNothing().when(refreshTokenRepository).deleteByUser(user);

        // When
        refreshTokenService.deleteByUser(user);

        // Then
        verify(refreshTokenRepository).deleteByUser(user);
    }

    @Test
    void deleteToken_WithValidToken_ShouldDeleteToken() {
        // Given
        doNothing().when(refreshTokenRepository).delete(validToken);

        // When
        refreshTokenService.deleteToken(validToken);

        // Then
        verify(refreshTokenRepository).delete(validToken);
    }

    @Test
    void createRefreshToken_WithCustomExpiration_ShouldRespectConfiguration() {
        // Given
        Long customDuration = 3600000L; // 1 heure
        ReflectionTestUtils.setField(refreshTokenService, "refreshTokenDurationMs", customDuration);
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        RefreshToken result = refreshTokenService.createRefreshToken(user);

        // Then
        Instant expectedExpiry = Instant.now().plusMillis(customDuration);
        assertThat(result.getExpiryDate()).isBefore(expectedExpiry.plusMillis(1000));
        assertThat(result.getExpiryDate()).isAfter(Instant.now());
    }
}
