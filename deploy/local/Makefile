# Local data-clone workflow for conluz.
#
# Brings up ONLY the Postgres + InfluxDB services from deploy/docker-compose.yaml, restores a
# Phase 1 snapshot bundle into them (reusing the shared restore scripts), and runs the currently
# checked-out branch via ./gradlew bootRun so Liquibase migrates on startup.
#
# The restore is DESTRUCTIVE and defaults to a dry-run. Pass EXECUTE=1 to actually drop & restore.

LOCAL_DIR  := $(patsubst %/,%,$(dir $(abspath $(lastword $(MAKEFILE_LIST)))))
DEPLOY_DIR := $(abspath $(LOCAL_DIR)/..)
REPO_ROOT  := $(abspath $(DEPLOY_DIR)/..)
COMPOSE    := docker compose -f $(DEPLOY_DIR)/docker-compose.yaml
ENV_FILE   := $(LOCAL_DIR)/.env
SERVICES   := postgres influxdb

EXECUTE ?= 0
BUNDLE  ?=

.PHONY: help up down reset restore run loop

help:
	@echo "conluz local data-clone workflow"
	@echo
	@echo "  make up                       Start Postgres + InfluxDB services only"
	@echo "  make down                     Stop & remove those two service containers"
	@echo "  make reset                    down + clear the local DB data directories"
	@echo "  make restore                  Dry-run the snapshot restore (no changes)"
	@echo "  make restore EXECUTE=1        Perform the destructive restore"
	@echo "  make restore BUNDLE=path.tgz  Restore a specific bundle (else newest in BUNDLE_DIR)"
	@echo "  make run                      ./gradlew bootRun against the local stack"
	@echo "  make loop EXECUTE=1           restore (execute) then run"

up:
	$(COMPOSE) up -d $(SERVICES)
	@echo "Up: $(SERVICES) only (the app container is never started by this workflow)."

down:
	$(COMPOSE) rm -sf $(SERVICES)

# Clean slate: remove the service containers and clear the bind-mounted data directories so the
# init scripts run again on the next 'up'. Paths come from deploy/.env; guarded against empty/'/'.
reset: down
	@set -a; . $(DEPLOY_DIR)/.env; set +a; \
	for d in "$$PATH_TO_POSTGRES_DATA" "$$PATH_TO_INFLUXDB_DATA"; do \
	  if [ -n "$$d" ] && [ "$$d" != "/" ] && [ -d "$$d" ]; then \
	    echo "Clearing data directory: $$d"; \
	    rm -rf "$$d"/* "$$d"/.[!.]* 2>/dev/null || true; \
	  fi; \
	done
	@echo "Reset complete. Run 'make up' for a fresh stack."

restore:
	"$(LOCAL_DIR)/restore-snapshot.sh" $(if $(filter 1,$(EXECUTE)),--execute,) $(BUNDLE)

run:
	cd "$(REPO_ROOT)" && set -a && . "$(ENV_FILE)" && set +a && ./gradlew bootRun

loop:
ifneq ($(EXECUTE),1)
	@echo "Refusing: 'make loop' performs a DESTRUCTIVE restore. Run 'make loop EXECUTE=1'." >&2
	@exit 1
else
	$(MAKE) restore EXECUTE=1 BUNDLE=$(BUNDLE)
	$(MAKE) run
endif
