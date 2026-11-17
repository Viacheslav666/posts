.PHONY: up up-emulator down backend frontend test openapi lint emulator help

COMPOSE_FILE := infra/docker-compose/docker-compose.yml
COMPOSE := docker compose -f $(COMPOSE_FILE)

help:
	@echo "Targets: up up-emulator down backend frontend test openapi lint emulator"

up:
	$(COMPOSE) up -d

up-emulator:
	cd tests/social-emulator && ./gradlew jibDockerBuild
	$(COMPOSE) --profile emulator up -d social-emulator

down:
	$(COMPOSE) down

backend:
	cd backend && ./gradlew :apps:api:bootRun --args='--spring.profiles.active=local'

frontend:
	pnpm --dir frontend install
	pnpm --dir frontend --filter @posts/web dev

emulator:
	cd tests/social-emulator && ./gradlew bootRun

test:
	cd backend && ./gradlew build integrationTest
	pnpm --dir frontend -r test
	cd tests/social-emulator && ./gradlew test

openapi:
	cd backend && ./gradlew :apps:api:generateOpenApiDocs
	pnpm --dir frontend --filter @posts/api-client gen:api

lint:
	cd backend && ./gradlew spotlessCheck
	cd tests/social-emulator && ./gradlew spotlessCheck
	pnpm --dir frontend -r lint
	pnpm --dir frontend -r typecheck
