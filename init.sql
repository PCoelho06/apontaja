-- Script d'initialisation PostgreSQL (optionnel)
-- La base et l'utilisateur sont déjà créés par les variables d'environnement

-- Extensions utiles
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pg_trgm"; -- Pour la recherche full-text