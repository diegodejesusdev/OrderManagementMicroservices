# Order Management Microservices — Plan del Proyecto

> Documento maestro de contexto. Este archivo describe la arquitectura completa
> del sistema. Sirve como referencia de estudio y como contexto para Claude Code
> al iniciar cada fase de implementación.

---

## 1. Objetivo

Construir un sistema de gestión de pedidos usando **arquitectura de microservicios**
como proyecto de portafolio para roles de desarrollo backend. El sistema debe poder
levantarse completo en local con un solo comando (`docker compose up`), sin depender
de ningún servicio de pago en la nube.

Continuidad con el portafolio: reemplaza el enfoque monolítico del proyecto previo
de e-commerce (Spring Boot) por una arquitectura distribuida, y reutiliza la lógica
de clasificación de inventario del proyecto ConstruNorte en el Inventory Service.

---

## 2. Stack tecnológico

| Capa                | Tecnología                                  |
|---------------------|---------------------------------------------|
| Lenguaje            | Java 21 (LTS)                               |
| Framework           | Spring Boot 3.x                             |
| Build tool          | Maven                                       |
| Base de datos       | MySQL 8 (en Docker, un schema por servicio) |
| Mensajería async    | RabbitMQ (en Docker)                        |
| Seguridad           | Spring Security + JWT (jjwt)                |
| Persistencia        | Spring Data JPA + Hibernate                 |
| Gateway             | Spring Cloud Gateway                        |
| Testing             | JUnit 5 + Mockito + Testcontainers          |
| Orquestación local  | Docker Compose                             |
| CI/CD               | GitHub Actions                             |

**Regla de costo:** todo corre en local o en contenedores Docker. Cero servicios
en la nube de pago.

---

## 3. Servicios

### 3.1 Auth Service
Responsable de la identidad. Registro de usuarios, login, emisión de tokens JWT y
gestión de roles (ej. `USER`, `ADMIN`).
- Expone: `POST /auth/register`, `POST /auth/login`, `GET /auth/validate`
- Base de datos: schema `auth_db`

### 3.2 Inventory Service
Catálogo de productos y control de stock. Aquí se reutiliza la lógica de
clasificación ABC/XYZ del proyecto ConstruNorte.
- Expone: `GET /products`, `GET /products/{id}`, `POST /products`,
  `PATCH /products/{id}/stock`
- Escucha el evento `order.created` para descontar stock automáticamente
- Base de datos: schema `inventory_db`

### 3.3 Orders Service
El corazón del sistema. Crea pedidos, valida el token del usuario contra Auth,
y publica un evento cuando se crea un pedido.
- Expone: `POST /orders`, `GET /orders`, `GET /orders/{id}`
- Llama de forma síncrona a Auth para validar el token (REST)
- Publica el evento `order.created` a RabbitMQ (async)
- Base de datos: schema `orders_db`

### 3.4 Notification Service (opcional — Fase posterior)
Servicio ligero que consume eventos y "notifica" (para el MVP, registra en log o
simula un envío de email).
- No expone endpoints públicos — solo consume de RabbitMQ
- Escucha `order.created`

### 3.5 Gateway Service
Puerta de entrada única al sistema. El cliente (o el reclutador probando la demo)
solo conoce el Gateway; este enruta cada petición al servicio correcto.
- Enruta `/auth/**` → Auth, `/products/**` → Inventory, `/orders/**` → Orders

---

## 4. Comunicación entre servicios

Se usan **los dos estilos** a propósito, para demostrar que se entiende cuándo va
cada uno:

**Síncrono (REST) — cuando el que llama necesita la respuesta AHORA:**
- Orders → Auth, para validar el token antes de aceptar un pedido.
  Si el token es inválido, el pedido se rechaza en el acto.

**Asíncrono (RabbitMQ) — cuando el que llama NO necesita esperar:**
- Orders publica `order.created` y responde al cliente inmediatamente.
- Inventory y Notification reaccionan por su cuenta cuando pueden.
  Si Notification está caído un momento, el mensaje espera en la cola y se procesa
  después — el pedido no se pierde.

```
                        ┌──────────────┐
   Cliente  ───────────▶│   Gateway    │
                        └──────┬───────┘
             ┌─────────────────┼──────────────────┐
             ▼                 ▼                  ▼
       ┌──────────┐      ┌───────────┐      ┌──────────┐
       │   Auth   │◀─────│  Orders   │      │Inventory │
       └──────────┘ REST └─────┬─────┘      └────▲─────┘
                               │ publica          │ consume
                               ▼                  │
                         ┌───────────────────────┴────┐
                         │        RabbitMQ            │
                         │   evento: order.created    │
                         └────────────┬───────────────┘
                                      │ consume
                                      ▼
                               ┌──────────────┐
                               │ Notification │
                               └──────────────┘
```

---

## 5. Base de datos: patrón "database-per-service"

Cada microservicio es dueño de sus propios datos y **ningún servicio accede a la
base de datos de otro**. Si Orders necesita un dato de un usuario, se lo pide a Auth
por su API — nunca lee la tabla de usuarios directamente.

Por qué importa: es lo que permite que los servicios evolucionen de forma
independiente. Si Auth cambia su esquema interno, a Orders no le afecta mientras la
API siga igual.

Optimización de recursos: en vez de correr 3 contenedores MySQL separados (pesado
para una máquina local), corremos **un solo contenedor MySQL con 3 schemas**
(`auth_db`, `inventory_db`, `orders_db`). La separación lógica es real y respeta el
patrón; simplemente evitamos el overhead de múltiples instancias. En producción real
sí serían instancias separadas.

---

## 6. Roadmap por fases

Cada fase es una unidad de trabajo autocontenida que se le puede pasar a Claude Code
por separado. No se avanza a la siguiente sin que la anterior compile y sus tests
pasen.

- **Fase 0 — Esqueleto:** estructura de carpetas, `docker-compose.yml` con MySQL y
  RabbitMQ, un `pom.xml` padre. Verificar que la infraestructura levanta.
- **Fase 1 — Auth Service:** registro, login, JWT, roles, tests.
- **Fase 2 — Inventory Service:** CRUD de productos y stock, tests.
- **Fase 3 — Orders Service:** creación de pedidos + validación REST contra Auth.
- **Fase 4 — Eventos async:** RabbitMQ, publicación de `order.created`, consumo en
  Inventory.
- **Fase 5 — Gateway:** Spring Cloud Gateway enrutando a los 3 servicios.
- **Fase 6 — Notification Service** (opcional) + README público en inglés con
  diagrama.
- **Fase 7 — Extras opcionales:** Eureka (service discovery), config centralizada,
  observabilidad.

---

## 7. Conceptos clave (el "por qué")

**¿Qué es un microservicio?**
Una aplicación pequeña e independiente que hace una sola cosa bien, se despliega por
sí sola y se comunica con las demás por la red (REST o mensajería). Lo opuesto a un
monolito, donde todo vive en un solo proceso desplegable.

**¿Por qué un Gateway?**
Para que el cliente tenga un único punto de entrada y no necesite conocer la
dirección de cada servicio interno. También centraliza cosas como CORS,
rate-limiting o autenticación de borde.

**¿Por qué mensajería async además de REST?**
REST acopla temporalmente: el que llama espera y, si el otro está caído, falla.
La mensajería desacopla: se deja el mensaje en la cola y el consumidor lo procesa
cuando pueda. Se usa para eventos donde no se necesita respuesta inmediata.

**¿Por qué Testcontainers en vez de mocks para la base de datos?**
Levanta un MySQL/RabbitMQ real (en Docker) durante los tests. Así se prueba contra
la infraestructura verdadera en vez de simulaciones, y el mismo test corre igual en
la máquina local y en CI. Es una señal fuerte de calidad en un portafolio.

**¿Por qué Docker Compose?**
Permite levantar todo el sistema (servicios + MySQL + RabbitMQ) con un comando,
en cualquier máquina, sin instalar nada manualmente. Esencial para que un reclutador
pueda correr el proyecto sin fricción.
