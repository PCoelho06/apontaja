# portail-client

Portail client final : inscription, connexion, compte, réservation en ligne, « mes marcações ».
**Toute l'interface est en portugais du Portugal (pt-PT)**, y compris les chemins des routes.

**État (4g-3)** : authentification (entrar, registar, verificar/confirmar email, palavra-passe
esquecida/reposta), page publique du salon, réservation, « as minhas marcações ». Pas d'annuaire de salons.
Les messages d'erreur du back (en français) ne sont jamais affichés : `lib/authErrors.ts` et
`lib/bookingErrors.ts` traduisent d'après le statut ou le `code`.

```
pnpm dev:client   # http://localhost:5174, proxy /api -> http://localhost:8080
```

## À savoir en développement

Les deux portails (salon 5173, client 5174) partagent le **cookie de refresh** : un cookie n'est
pas isolé par port, il l'est par hôte (`localhost`). Se connecter sur un portail remplace la session
de l'autre au prochain rafraîchissement. En production, les deux portails auront des hôtes distincts.

## Choix

- Même `Account` pour les deux portails ; le store envoie `portal: "CLIENT"` (`defineAuthStore`).
- Les liens d'email pointent vers `/confirmar-email?token=` et `/repor-palavra-passe?token=`.
- `/termos` et `/privacidade` sont des pages provisoires (texte à rédiger).


