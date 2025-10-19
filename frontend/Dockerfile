# Build stage
FROM node:20-alpine AS build
WORKDIR /app

# Argument pour l'URL de l'API
ARG VITE_API_URL
ENV VITE_API_URL=$VITE_API_URL

# Copier les fichiers de dépendances
COPY package*.json ./

# Installer les dépendances
RUN npm ci

# Copier le code source
COPY . .

# Build l'application pour la production
RUN npm run build

# Production stage avec Nginx
FROM nginx:alpine
WORKDIR /usr/share/nginx/html

# Copier les fichiers buildés
COPY --from=build /app/dist .

# Copier la configuration Nginx pour SPA
COPY nginx-spa.conf /etc/nginx/conf.d/default.conf

# Exposer le port 80
EXPOSE 80

# Health check
HEALTHCHECK --interval=30s --timeout=3s \
  CMD wget --no-verbose --tries=1 --spider http://localhost/ || exit 1

CMD ["nginx", "-g", "daemon off;"]