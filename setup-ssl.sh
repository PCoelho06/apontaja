#!/bin/bash

# Script pour obtenir le certificat SSL Let's Encrypt
# À exécuter APRÈS avoir vérifié que le DNS pointe bien vers le VPS

set -e

echo "=== Configuration SSL Let's Encrypt pour apontaja.com ==="

# Vérifier que le domaine pointe vers ce serveur
echo "Vérification DNS..."

# Récupérer l'IPv4 du serveur (forcer IPv4)
CURRENT_IP=$(curl -4 -s ifconfig.me)
echo "IPv4 du serveur: $CURRENT_IP"

# Récupérer l'IP du domaine (A record)
DOMAIN_IP=$(dig +short apontaja.com A | head -n1)
echo "IPv4 du domaine: $DOMAIN_IP"

if [ "$CURRENT_IP" != "$DOMAIN_IP" ]; then
    echo "⚠️  ATTENTION: Le domaine ne pointe pas vers ce serveur!"
    echo "IPv4 du serveur: $CURRENT_IP"
    echo "IPv4 du domaine: $DOMAIN_IP"
    read -p "Voulez-vous continuer quand même ? (y/n) " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        echo "Abandon de l'installation SSL."
        exit 1
    fi
else
    echo "✓ DNS configuré correctement"
fi

# Créer les dossiers nécessaires
mkdir -p certbot/conf
mkdir -p certbot/www

# Créer une config Nginx temporaire (HTTP seulement pour le challenge)
cat > nginx/nginx-temp.conf << 'EOF'
events {
    worker_connections 1024;
}

http {
    server {
        listen 80;
        server_name apontaja.com www.apontaja.com;

        location /.well-known/acme-challenge/ {
            root /var/www/certbot;
        }

        location / {
            return 200 "Waiting for SSL setup...\n";
            add_header Content-Type text/plain;
        }
    }
}
EOF

# Sauvegarder la config Nginx actuelle
if [ -f nginx/nginx.conf ]; then
    mv nginx/nginx.conf nginx/nginx.conf.backup
fi

mv nginx/nginx-temp.conf nginx/nginx.conf

# Démarrer Nginx temporairement
echo "Démarrage de Nginx pour le challenge ACME..."
docker compose up -d nginx

# Obtenir le certificat
echo "Obtention du certificat SSL..."
docker compose run --rm certbot certonly \
    --webroot \
    --webroot-path=/var/www/certbot \
    --email votre-email@example.com \
    --agree-tos \
    --no-eff-email \
    -d apontaja.com \
    -d www.apontaja.com

# Restaurer la vraie config Nginx
echo "Restauration de la configuration Nginx avec HTTPS..."
if [ -f nginx/nginx.conf.backup ]; then
    mv nginx/nginx.conf.backup nginx/nginx.conf
fi

# Redémarrer avec la config HTTPS
docker compose down
docker compose up -d

echo ""
echo "✓ SSL configuré avec succès!"
echo "Votre site est maintenant accessible en HTTPS sur https://apontaja.com"
echo ""
echo "Le certificat sera automatiquement renouvelé par le container certbot."