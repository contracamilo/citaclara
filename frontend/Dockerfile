# Etapa 1: construcción con Node 24 LTS (versión fijada)
FROM node:26.13.1-alpine AS construccion
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run build

# Etapa 2: nginx (versión fijada) sirviendo los estáticos y redirigiendo /api al backend
FROM nginx:1.31.0-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY seguridad.conf /etc/nginx/seguridad.conf
COPY --from=construccion /app/dist /usr/share/nginx/html
EXPOSE 80
