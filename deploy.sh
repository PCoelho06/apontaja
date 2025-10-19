#!/bin/bash

# Script de déploiement simplifié
# Utilisé manuellement ou par GitHub Actions

set -e

echo "=== Déploiement de l'application Apontaja ==="

# Vérifier que .env existe
if [ ! -f .env ]; then
    echo "❌ Erreur: Le fichier .env n'existe pas!"
    echo "Copiez .env.example vers .env et configurez les variables."
    exit 1
fi

# Pull les dernières images si elles existent
echo "📦 Récupération des dernières images..."
docker compose pull || true

# Arrêter les anciens containers
echo "🛑 Arrêt des anciens containers..."
docker compose down

# Rebuild et démarrage
echo "🔨 Build et démarrage des containers..."
docker compose up -d --build

# Attendre que les services soient prêts
echo "⏳ Attente du démarrage des services..."
sleep 10

# Vérifier le statut
echo "✓ Statut des containers:"
docker compose ps

# Vérifier la santé des services
echo ""
echo "🏥 Vérification de la santé des services..."
docker compose ps --format "table {{.Name}}\t{{.Status}}"

# Afficher les logs récents
echo ""
echo "📋 Logs récents:"
docker compose logs --tail=20

echo ""
echo "✓ Déploiement terminé!"
echo "Application accessible sur: https://apontaja.com"
echo ""
echo "Commandes utiles:"
echo "  - Voir les logs: docker compose logs -f"
echo "  - Redémarrer: docker compose restart"
echo "  - Arrêter: docker compose down"