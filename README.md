# Apontaja - Application Full Stack

Application web avec Spring Boot (Java 21), Vue.js et PostgreSQL, déployée sur VPS OVHcloud avec Docker.

## 🏗️ Architecture

```
┌─────────────────────────────────────────────────────┐
│                    Internet                          │
└────────────────────┬────────────────────────────────┘
                     │
                     ▼
          ┌──────────────────────┐
          │  Nginx (Port 80/443) │
          │   Reverse Proxy      │
          │   + SSL/TLS          │
          └──────────┬───────────┘
                     │
         ┌───────────┴───────────┐
         │                       │
         ▼                       ▼
┌─────────────────┐    ┌──────────────────┐
│  Frontend       │    │  Backend         │
│  Vue.js + Nginx │    │  Spring Boot     │
│  (Port 80)      │    │  (Port 8080)     │
└─────────────────┘    └────────┬─────────┘
                                │
                                ▼
                      ┌──────────────────┐
                      │  PostgreSQL      │
                      │  (Port 5432)     │
                      └──────────────────┘
```

## 📁 Structure du projet

```
apontaja/
├── .github/
│   └── workflows/
│       └── deploy.yml          # CI/CD GitHub Actions
├── backend/
│   ├── src/                    # Code Spring Boot
│   ├── pom.xml
│   └── Dockerfile
├── frontend/
│   ├── src/                    # Code Vue.js
│   ├── package.json
│   ├── Dockerfile
│   └── nginx-spa.conf
├── nginx/
│   └── nginx.conf              # Configuration reverse proxy
├── docker-compose.yml
├── .env                        # Variables d'environnement (à créer)
├── .env.example               # Template des variables
├── init.sql                    # Script init PostgreSQL
├── setup-ssl.sh               # Script configuration SSL
├── deploy.sh                  # Script de déploiement
└── README.md
```

## 🚀 Déploiement initial

### 1. Configuration DNS (OVHcloud)

Ajoutez ces enregistrements dans la zone DNS de `apontaja.com` :

```
Type: A    | Sous-domaine: @   | Cible: 51.77.203.198
Type: A    | Sous-domaine: www | Cible: 51.77.203.198
```

Vérifiez la propagation :
```bash
dig apontaja.com +short
```

### 2. Configuration du VPS

Connectez-vous au VPS :
```bash
ssh -p 53794 pcoelho@51.77.203.198
```

Clonez le projet :
```bash
cd ~
git clone https://github.com/VOTRE_USERNAME/apontaja.git
cd apontaja
```

### 3. Configuration des variables d'environnement

Créez le fichier `.env` :
```bash
cp .env.example .env
nano .env
```

Modifiez les valeurs :
- `POSTGRES_PASSWORD` : Mot de passe fort pour PostgreSQL
- `JWT_SECRET` : Clé secrète longue et aléatoire
- Les autres variables selon vos besoins

**⚠️ Ne commitez JAMAIS le fichier .env !**

### 4. Configuration SSL (Let's Encrypt)

**Attendez que le DNS soit propagé avant cette étape !**

Modifiez votre email dans le script :
```bash
nano setup-ssl.sh
# Changez "votre-email@example.com" par votre vraie adresse
```

Rendez le script exécutable et lancez-le :
```bash
chmod +x setup-ssl.sh
./setup-ssl.sh
```

Le script va :
1. Vérifier que le DNS pointe vers le VPS
2. Démarrer Nginx en HTTP
3. Obtenir le certificat SSL via Let's Encrypt
4. Reconfigurer Nginx en HTTPS
5. Le certificat se renouvellera automatiquement

### 5. Premier déploiement

```bash
chmod +x deploy.sh
./deploy.sh
```

Votre application est maintenant accessible sur `https://apontaja.com` ! 🎉

## 🔧 Développement local

### Backend (Spring Boot)

```bash
cd backend
./mvnw spring-boot:run
```

Application disponible sur `http://localhost:8080`

### Frontend (Vue.js)

```bash
cd frontend
npm install
npm run dev
```

Application disponible sur `http://localhost:5173`

### Base de données locale

Utilisez Docker :
```bash
docker run -d \
  --name postgres-local \
  -e POSTGRES_DB=apontaja_db \
  -e POSTGRES_USER=apontaja_user \
  -e POSTGRES_PASSWORD=password \
  -p 5432:5432 \
  postgres:16-alpine
```

## 🔄 CI/CD avec GitHub Actions

### Configuration des secrets GitHub

Allez dans **Settings** → **Secrets and variables** → **Actions** de votre repo GitHub.

Ajoutez ces secrets :

| Nom | Valeur | Description |
|-----|--------|-------------|
| `SSH_PRIVATE_KEY` | Contenu de votre clé privée | Clé SSH pour se connecter au VPS |
| `VPS_IP` | `51.77.203.198` | IP du VPS |
| `SSH_PORT` | `53794` | Port SSH personnalisé |
| `SSH_USER` | `pcoelho` | Nom d'utilisateur SSH |

### Récupérer votre clé privée SSH

Sur votre **machine locale** :
```bash
cat ~/.ssh/id_ed25519
```

Copiez **tout le contenu** (y compris les lignes BEGIN et END) dans le secret `SSH_PRIVATE_KEY`.

### Fonctionnement

Une fois configuré, chaque push sur `main` déclenchera automatiquement :
1. Connexion au VPS via SSH
2. Pull du code depuis GitHub
3. Rebuild et redémarrage des containers
4. Vérification du déploiement

Vous pouvez aussi déclencher manuellement le déploiement depuis l'onglet **Actions** de GitHub.

## 📊 Commandes utiles

### Docker Compose

```bash
# Voir les logs en temps réel
docker compose logs -f

# Logs d'un service spécifique
docker compose logs -f backend

# Redémarrer un service
docker compose restart backend

# Arrêter tous les services
docker compose down

# Arrêter et supprimer les volumes (⚠️ PERTE DE DONNÉES)
docker compose down -v

# Voir l'état des containers
docker compose ps

# Rebuilder sans cache
docker compose build --no-cache

# Accéder au shell d'un container
docker compose exec backend sh
docker compose exec postgres psql -U apontaja_user -d apontaja_db
```

### PostgreSQL

```bash
# Se connecter à PostgreSQL
docker compose exec postgres psql -U apontaja_user -d apontaja_db

# Backup de la base de données
docker compose exec postgres pg_dump -U apontaja_user apontaja_db > backup.sql

# Restaurer un backup
docker compose exec -T postgres psql -U apontaja_user -d apontaja_db < backup.sql
```

### Nginx

```bash
# Tester la configuration
docker compose exec nginx nginx -t

# Recharger la config sans redémarrer
docker compose exec nginx nginx -s reload

# Voir les logs d'accès
docker compose exec nginx tail -f /var/log/nginx/access.log

# Voir les logs d'erreur
docker compose exec nginx tail -f /var/log/nginx/error.log
```

### SSL/Certbot

```bash
# Renouveler manuellement le certificat
docker compose run --rm certbot renew

# Tester le renouvellement (dry-run)
docker compose run --rm certbot renew --dry-run
```

## 🔒 Sécurité

### Checklist de sécurité appliquée

- ✅ SSH sur port non-standard (53794)
- ✅ Authentification par clé SSH uniquement
- ✅ Utilisateur non-root avec sudo
- ✅ Pare-feu UFW configuré
- ✅ Fail2ban actif contre les tentatives de connexion
- ✅ SSL/TLS avec Let's Encrypt
- ✅ Headers de sécurité HTTP
- ✅ Variables d'environnement pour les secrets
- ✅ Containers non-root quand possible
- ✅ Rate limiting sur l'API
- ✅ CORS configuré

### Bonnes pratiques

1. **Ne commitez jamais** :
    - `.env`
    - Clés privées
    - Mots de passe
    - Tokens

2. **Mises à jour régulières** :
   ```bash
   # Sur le VPS
   sudo apt update && sudo apt upgrade -y
   
   # Rebuild les images Docker
   docker compose build --no-cache
   docker compose up -d
   ```

3. **Monitoring des logs** :
   ```bash
   # Surveiller les tentatives de connexion SSH
   sudo tail -f /var/log/auth.log
   
   # Vérifier les bans Fail2ban
   sudo fail2ban-client status sshd
   ```

4. **Backups réguliers** :
   ```bash
   # Créer un script de backup automatique
   # À placer dans /home/pcoelho/backup.sh
   
   #!/bin/bash
   DATE=$(date +%Y%m%d_%H%M%S)
   BACKUP_DIR="/home/pcoelho/backups"
   
   mkdir -p $BACKUP_DIR
   
   # Backup PostgreSQL
   docker compose exec -T postgres pg_dump -U apontaja_user apontaja_db > $BACKUP_DIR/db_$DATE.sql
   
   # Garder seulement les 7 derniers backups
   ls -t $BACKUP_DIR/db_*.sql | tail -n +8 | xargs rm -f
   ```

   Automatiser avec cron :
   ```bash
   crontab -e
   # Ajouter : 0 2 * * * /home/pcoelho/backup.sh
   ```

## 🐛 Dépannage

### Le site n'est pas accessible

```bash
# Vérifier que les containers tournent
docker compose ps

# Vérifier les logs
docker compose logs

# Vérifier que les ports sont ouverts
sudo ufw status
sudo ss -tlnp | grep -E '80|443'
```

### Erreur de connexion à la base de données

```bash
# Vérifier que PostgreSQL est prêt
docker compose logs postgres

# Tester la connexion
docker compose exec postgres psql -U apontaja_user -d apontaja_db -c "SELECT 1"
```

### Problème SSL

```bash
# Vérifier les certificats
docker compose exec nginx ls -la /etc/letsencrypt/live/apontaja.com/

# Renouveler le certificat
docker compose run --rm certbot renew --force-renewal
docker compose restart nginx
```

### Le backend ne démarre pas

```bash
# Voir les logs détaillés
docker compose logs -f backend

# Vérifier les variables d'environnement
docker compose exec backend env | grep SPRING

# Rebuilder l'image
docker compose build --no-cache backend
docker compose up -d backend
```

## 📚 Ressources

- [Documentation Spring Boot](https://spring.io/projects/spring-boot)
- [Documentation Vue.js](https://vuejs.org/)
- [Documentation PostgreSQL](https://www.postgresql.org/docs/)
- [Documentation Docker](https://docs.docker.com/)
- [Documentation Nginx](https://nginx.org/en/docs/)
- [Let's Encrypt](https://letsencrypt.org/)

## 📝 TODO / Améliorations futures

- [ ] Ajouter un système de monitoring (Prometheus + Grafana)
- [ ] Configurer les logs centralisés (ELK Stack)
- [ ] Ajouter des tests automatisés dans le CI/CD
- [ ] Mettre en place un système de rollback automatique
- [ ] Configurer des alertes (email/Slack) en cas de problème
- [ ] Ajouter Redis pour le cache
- [ ] Optimiser les images Docker (multi-stage builds)
- [ ] Documenter l'API avec Swagger/OpenAPI

## 📄 Licence

[À définir]

## 👥 Auteurs

Pierre Coelho - [GitHub](https://github.com/PCoelho06) - [LinkedIn](https://www.linkedin.com/in/pierre-coelho/)