# Local data-clone workflow for conluz.
#
# Brings up an ISOLATED Postgres + InfluxDB stack (deploy/local/docker-compose.local.yml: distinct
# container names, ports and NAMED volumes), restores an anonymized snapshot bundle into it
# (reusing the shared restore scripts), and runs the currently checked-out branch via
# ./gradlew bootRun so Liquibase migrates on startup. It never touches your normal
# postgres/influxdb services.
#
# The restore is DESTRUCTIVE and defaults to a dry-run. Pass EXECUTE=1 to actually drop & restore.

LOCAL_DIR  := $(patsubst %/,%,$(dir $(abspath $(lastword $(MAKEFILE_LIST)))))
DEPLOY_DIR := $(abspath $(LOCAL_DIR)/..)
REPO_ROOT  := $(abspath $(DEPLOY_DIR)/..)
COMPOSE    := docker compose -p conluz-local -f $(LOCAL_DIR)/docker-compose.local.yml
ENV_FILE   := $(LOCAL_DIR)/.env

EXECUTE ?= 0
BUNDLE  ?=

.PHONY: help up down reset restore run debug loop

help:
	@echo "conluz local data-clone workflow (isolated stack)"
	@echo
	@echo "  make up                       Start the ISOLATED Postgres + InfluxDB stack"
	@echo "  make down                     Stop & remove the isolated stack containers"
	@echo "  make reset                    down -v (wipes only the isolated named volumes)"
	@echo "  make restore                  Dry-run the snapshot restore (no changes)"
	@echo "  make restore EXECUTE=1        Perform the destructive restore"
	@echo "  make restore BUNDLE=path.tgz  Restore a specific bundle (else newest in BUNDLE_DIR)"
	@echo "  make run                      ./gradlew bootRun against the isolated stack"
	@echo "  make debug                    ./gradlew bootRun in debug mode (JDWP on :5005)"
	@echo "  make loop EXECUTE=1           restore (execute) then run"

up:
	$(COMPOSE) up -d
	@echo "Up: isolated stack only (this workflow has no app container)."

down:
	$(COMPOSE) down

# Clean slate: remove the isolated containers AND their named volumes. Only the isolated
# 'conluz-local_*' volumes are affected — your normal postgres/influxdb data is never touched.
reset:
	$(COMPOSE) down -v
	@echo "Reset complete (isolated volumes wiped). Run 'make up' for a fresh stack."

restore:
	"$(LOCAL_DIR)/restore-snapshot.sh" $(if $(filter 1,$(EXECUTE)),--execute,) $(BUNDLE)

run:
	cd "$(REPO_ROOT)" && set -a && . "$(ENV_FILE)" && set +a && ./gradlew bootRun

debug:
	cd "$(REPO_ROOT)" && set -a && . "$(ENV_FILE)" && set +a && ./gradlew bootRun --debug-jvm

loop:
ifneq ($(EXECUTE),1)
	@echo "Refusing: 'make loop' performs a DESTRUCTIVE restore. Run 'make loop EXECUTE=1'." >&2
	@exit 1
else
	$(MAKE) restore EXECUTE=1 BUNDLE=$(BUNDLE)
	$(MAKE) run
endif
