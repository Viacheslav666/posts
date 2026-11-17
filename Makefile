.PHONY: up up-emulator up-full down backend frontend test openapi lint emulator help \
	media-base images images-backend images-web images-run-smoke wait-full \
	e2e-smoke contract-smoke load-smoke

SHELL := /bin/bash
COMPOSE_FILE := infra/docker-compose/docker-compose.yml
COMPOSE := docker compose -f $(COMPOSE_FILE)

REGISTRY ?= ghcr.io/viacheslav666
MEDIA_BASE_IMAGE ?= posts-java21-ffmpeg:21
API_BASE_URL ?= http://127.0.0.1:8080

help:
	@echo "Targets: up up-emulator up-full down backend frontend test openapi lint emulator"
	@echo "         media-base images images-backend images-web images-run-smoke"
	@echo "         e2e-smoke contract-smoke load-smoke"

up:
	$(COMPOSE) up -d

up-emulator:
	cd tests/social-emulator && ./gradlew jibDockerBuild
	$(COMPOSE) --profile emulator up -d social-emulator

# DEV-4: infra + all apps (local images) + web + social-emulator
up-full: images
	$(COMPOSE) --profile full up -d
	@$(MAKE) wait-full

wait-full:
	@set -euo pipefail; \
	echo "==> waiting for API /api/v1/ping"; \
	ok=0; \
	for i in $$(seq 1 90); do \
		if curl -sf http://127.0.0.1:8080/api/v1/ping >/tmp/posts-full-api-ping.json; then ok=1; break; fi; \
		sleep 2; \
	done; \
	if [ "$$ok" != 1 ]; then echo "API ping failed"; docker compose -f $(COMPOSE_FILE) --profile full logs api --tail=80; exit 1; fi; \
	cat /tmp/posts-full-api-ping.json; echo; \
	echo "==> waiting for web /health"; \
	ok=0; \
	for i in $$(seq 1 60); do \
		if curl -sf http://127.0.0.1:3000/health >/dev/null; then ok=1; break; fi; \
		sleep 2; \
	done; \
	if [ "$$ok" != 1 ]; then echo "web /health failed"; docker compose -f $(COMPOSE_FILE) --profile full logs web --tail=80; exit 1; fi; \
	echo "web /health OK"; \
	echo "==> social-emulator /_control/health"; \
	curl -sf http://127.0.0.1:8090/_control/health | tee /tmp/posts-full-emulator-health.json; \
	echo; \
	echo "Full stack is up (profile full)"

down:
	$(COMPOSE) --profile full --profile emulator down

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

# QA-2 smoke harness (API must already answer /api/v1/ping unless you use openapi boot below)
e2e-smoke:
	pnpm --dir frontend install
	cd tests/e2e && pnpm install && pnpm exec playwright install chromium && pnpm test:smoke

contract-smoke:
	chmod +x tests/contract/run-smoke.sh
	API_BASE_URL=$(API_BASE_URL) ./tests/contract/run-smoke.sh

load-smoke:
	API_BASE_URL=$(API_BASE_URL) k6 run tests/load/smoke.js

media-base:
	docker build -t $(MEDIA_BASE_IMAGE) infra/docker/java21-ffmpeg

images-backend: media-base
	cd backend && IMAGE_TAGS=latest \
		./gradlew jibDockerBuild \
		-PpostsMediaBaseImage=docker://$(MEDIA_BASE_IMAGE)

images-web:
	docker build -f frontend/apps/web/Dockerfile -t posts-web:local frontend

images: images-backend images-web
	cd tests/social-emulator && IMAGE_TAGS=latest ./gradlew jibDockerBuild

images-run-smoke:
	@echo "==> scheduler health"
	docker rm -f posts-smoke-scheduler >/dev/null 2>&1 || true
	docker run --rm -d --name posts-smoke-scheduler -p 18081:8080 \
		$(REGISTRY)/posts-scheduler:latest
	@for i in $$(seq 1 30); do \
		curl -sf http://127.0.0.1:18081/actuator/health && break; \
		sleep 1; \
	done
	curl -sf http://127.0.0.1:18081/actuator/health | tee /tmp/posts-scheduler-health.json
	docker rm -f posts-smoke-scheduler
	@echo "==> api health (openapi profile)"
	docker rm -f posts-smoke-api >/dev/null 2>&1 || true
	docker run --rm -d --name posts-smoke-api -p 18080:8080 \
		-e SPRING_PROFILES_ACTIVE=openapi \
		$(REGISTRY)/posts-api:latest
	@for i in $$(seq 1 45); do \
		curl -sf http://127.0.0.1:18080/actuator/health && break; \
		sleep 1; \
	done
	curl -sf http://127.0.0.1:18080/actuator/health | tee /tmp/posts-api-health.json
	docker rm -f posts-smoke-api
	@echo "==> web health"
	docker rm -f posts-smoke-web >/dev/null 2>&1 || true
	docker run --rm -d --name posts-smoke-web -p 13000:3000 posts-web:local
	@for i in $$(seq 1 30); do \
		curl -sf http://127.0.0.1:13000/health && break; \
		sleep 1; \
	done
	curl -sf http://127.0.0.1:13000/health | tee /tmp/posts-web-health.html
	docker rm -f posts-smoke-web
	@echo "Smoke OK"
