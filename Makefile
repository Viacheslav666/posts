.PHONY: up down backend frontend test openapi lint help

COMPOSE_FILE := infra/docker-compose/docker-compose.yml
COMPOSE := docker compose -f $(COMPOSE_FILE)

help:
	@echo "Targets: up down backend frontend test openapi lint"

up:
	$(COMPOSE) up -d

down:
	$(COMPOSE) down

backend:
	cd backend && ./gradlew :apps:api:bootRun --args='--spring.profiles.active=local'

frontend:
	pnpm --dir frontend install
	pnpm --dir frontend --filter @posts/web dev

test:
	cd backend && ./gradlew build integrationTest
	pnpm --dir frontend -r test

openapi:
	cd backend && ./gradlew :apps:api:generateOpenApiDocs
	pnpm --dir frontend --filter @posts/api-client gen:api

lint:
	cd backend && ./gradlew spotlessCheck
	pnpm --dir frontend -r lint
	pnpm --dir frontend -r typecheck
