# portail-client

Portail client final : inscription, connexion, compte. La réservation arrive en 4g.

**État (4d-2)** : socle d'authentification (connexion, inscription, vérification de l'email,
mot de passe oublié/réinitialisation, accueil du compte). Pas de liste de salons ni de réservation.

```
pnpm dev:client   # http://localhost:5174, proxy /api -> http://localhost:8080
```

## À savoir en développement

Les deux portails (salon 5173, client 5174) partagent le **cookie de refresh** : un cookie n'est
pas isolé par port, il l'est par hôte (`localhost`). Se connecter sur un portail remplace la session
de l'autre au prochain rafraîchissement. En production, les deux portails auront des hôtes distincts.

## Choix

- Même `Account` pour les deux portails ; le store envoie `portal: "CLIENT"` (`defineAuthStore`).
- Les liens d'email pointent vers `/confirmer-email?token=` et `/reinitialiser-mot-de-passe?token=`.
- `/cgu` et `/confidentialite` sont des pages provisoires (texte à rédiger).
