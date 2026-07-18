# Order Management — Microservices

> Work in progress. Full documentation will be added once all phases are complete.

## Local infrastructure

Copy `.env.example` to `.env`, then start MySQL and RabbitMQ:

```bash
cp .env.example .env
docker compose up -d
```

| Service   | URL                          |
|-----------|------------------------------|
| MySQL     | `localhost:3306`             |
| RabbitMQ  | `localhost:15672` (mgmt UI)  |

Stop everything:

```bash
docker compose down
```
