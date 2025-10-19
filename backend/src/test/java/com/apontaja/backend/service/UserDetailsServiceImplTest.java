package com.apontaja.backend.service;

import com.apontaja.backend.model.Role;
import com.apontaja.backend.model.User;
import com.apontaja.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserDetailsServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserDetailsServiceImpl userDetailsService;

    private User user;
    private static final String TEST_EMAIL = "test@example.com";
    private static final String NON_EXISTING_EMAIL = "nonexistent@example.com";

    @BeforeEach
    void setUp() {
        user = User.builder()
                .id(1L)
                .email(TEST_EMAIL)
                .password("encoded-password")
                .firstName("John")
                .lastName("Doe")
                .role(Role.USER)
                .enabled(true)
                .build();
    }

    @Test
    void loadUserByUsername_WithExistingEmail_ShouldReturnUserDetails() {
        // Given
        when(userRepository.findByEmail(TEST_EMAIL)).thenReturn(Optional.of(user));

        // When
        UserDetails result = userDetailsService.loadUserByUsername(TEST_EMAIL);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getUsername()).isEqualTo(TEST_EMAIL);
        assertThat(result.getPassword()).isEqualTo("encoded-password");
        assertThat(result.isEnabled()).isTrue();
        assertThat(result.getAuthorities()).hasSize(1);
        assertThat(result.getAuthorities().iterator().next().getAuthority()).isEqualTo("ROLE_USER");

        verify(userRepository).findByEmail(TEST_EMAIL);
    }

    @Test
    void loadUserByUsername_WithNonExistingEmail_ShouldThrowUsernameNotFoundException() {
        // Given
        when(userRepository.findByEmail(NON_EXISTING_EMAIL)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> userDetailsService.loadUserByUsername(NON_EXISTING_EMAIL))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessageContaining("User not found with email: " + NON_EXISTING_EMAIL);

        verify(userRepository).findByEmail(NON_EXISTING_EMAIL);
    }

    @Test
    void loadUserByUsername_WithDisabledUser_ShouldReturnUserDetailsWithDisabledFlag() {
        // Given
        User disabledUser = User.builder()
                .id(2L)
                .email("disabled@example.com")
                .password("encoded-password")
                .firstName("Jane")
                .lastName("Doe")
                .role(Role.USER)
                .enabled(false)
                .build();
        when(userRepository.findByEmail("disabled@example.com")).thenReturn(Optional.of(disabledUser));

        // When
        UserDetails result = userDetailsService.loadUserByUsername("disabled@example.com");

        // Then
        assertThat(result).isNotNull();
        assertThat(result.isEnabled()).isFalse();
        assertThat(result.getUsername()).isEqualTo("disabled@example.com");

        verify(userRepository).findByEmail("disabled@example.com");
    }

    @Test
    void loadUserByUsername_WithAdminRole_ShouldReturnUserDetailsWithAdminAuthority() {
        // Given
        User adminUser = User.builder()
                .id(3L)
                .email("admin@example.com")
                .password("encoded-password")
                .firstName("Admin")
                .lastName("User")
                .role(Role.ADMIN)
                .enabled(true)
                .build();
        when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(adminUser));

        // When
        UserDetails result = userDetailsService.loadUserByUsername("admin@example.com");

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getAuthorities()).hasSize(1);
        assertThat(result.getAuthorities().iterator().next().getAuthority()).isEqualTo("ROLE_ADMIN");

        verify(userRepository).findByEmail("admin@example.com");
    }

    @Test
    void loadUserByUsername_WithEmailContainingUppercase_ShouldHandleCorrectly() {
        // Given
        String uppercaseEmail = "Test@Example.COM";
        when(userRepository.findByEmail(uppercaseEmail)).thenReturn(Optional.of(user));

        // When
        UserDetails result = userDetailsService.loadUserByUsername(uppercaseEmail);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getUsername()).isEqualTo(TEST_EMAIL);

        verify(userRepository).findByEmail(uppercaseEmail);
    }
}
