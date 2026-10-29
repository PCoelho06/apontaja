package com.apontaja.back.account.application;

import com.apontaja.back.account.domain.Account;
import com.apontaja.back.shared.domain.EmailSender;
import com.apontaja.back.shared.domain.TokenHasher;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Locale;

/**
 * Prévient le titulaire d'un compte qu'une inscription a été tentée avec son adresse (anti-énumération :
 * l'inscription répond pareil que l'adresse soit connue ou non). Au plus un email par adresse et par fenêtre
 * (15 minutes par défaut) pour qu'on ne puisse pas s'en servir pour harceler quelqu'un. Les compteurs sont en
 * mémoire locale (une seule instance, comme la limitation de débit) ; la clé est un hachage de l'adresse.
 */
@Component
class ExistingAccountNotifier {

    private static final long MAX_TRACKED_ADDRESSES = 10_000;

    private final TokenHasher tokenHasher;
    private final EmailSender emailSender;
    private final FrontendLinks frontendLinks;
    private final Cache<String, Boolean> recentlyNotified;

    @Autowired
    ExistingAccountNotifier(TokenHasher tokenHasher, EmailSender emailSender, FrontendLinks frontendLinks,
            @Value("${apontaja.security.account-exists-notice.window:15m}") Duration window) {
        this(tokenHasher, emailSender, frontendLinks, window, Ticker.systemTicker());
    }

    ExistingAccountNotifier(TokenHasher tokenHasher, EmailSender emailSender, FrontendLinks frontendLinks,
            Duration window, Ticker ticker) {
        this.tokenHasher = tokenHasher;
        this.emailSender = emailSender;
        this.frontendLinks = frontendLinks;
        this.recentlyNotified = Caffeine.newBuilder().expireAfterWrite(window).maximumSize(MAX_TRACKED_ADDRESSES)
                .ticker(ticker).build();
    }

    /** Envoie l'email « já tem uma conta », sauf si un email identique est parti dans la fenêtre. */
    void notifyAccountExists(Account account, FrontendPortal portal) {
        String key = tokenHasher.hash(account.getEmail().trim().toLowerCase(Locale.ROOT));
        if (recentlyNotified.asMap().putIfAbsent(key, Boolean.TRUE) != null) {
            return;
        }
        AuthEmailComposer.AuthEmail email = AuthEmailComposer.accountExists(portal,
                frontendLinks.loginLink(portal), frontendLinks.forgotPasswordLink(portal));
        emailSender.send(account.getEmail(), email.subject(), email.body());
    }
}
