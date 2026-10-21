package com.apontaja.back.account.application;

/**
 * Portail front vers lequel pointent les liens envoyés par email. Liste fermée : un client ne peut jamais fournir
 * une URL libre (risque de phishing par redirection).
 */
public enum FrontendPortal {
    SALON, CLIENT
}
