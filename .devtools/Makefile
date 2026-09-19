# Single entry point for local development. Run from the repo root with
#   make -C .devtools <target>
# CI runs the same `check` target so local and CI results match.

ROOT := $(abspath ..)
UV := uv --project $(CURDIR)
PRE_COMMIT := $(UV) run pre-commit
# python.org Python builds on macOS ship without a CA bundle until "Install
# Certificates" is run; hook installs download toolchains over HTTPS, so fall back
# to certifi's bundle unless one is already configured.
export SSL_CERT_FILE ?= $(shell $(UV) run --frozen python -c 'import certifi; print(certifi.where())' 2>/dev/null)

GRADLE := cd $(ROOT) && JAVA_HOME="$$(/usr/libexec/java_home -v 25 2>/dev/null || echo $$JAVA_HOME)" ./gradlew

.DEFAULT_GOAL := help
.PHONY: help setup fmt lint test build check scan lock run-fabric run-neoforge run-fabric-server run-neoforge-server clean

help: ## List targets
	@grep -hE '^[a-zA-Z_-]+:.*## ' $(MAKEFILE_LIST) | awk 'BEGIN {FS = ":.*## "}; {printf "  \033[36m%-20s\033[0m %s\n", $$1, $$2}'

setup: ## Install pinned tools and git hooks (pre-commit, commit-msg, pre-push)
	$(UV) sync --frozen
	cd $(ROOT) && $(PRE_COMMIT) install --install-hooks \
		--hook-type pre-commit --hook-type commit-msg --hook-type pre-push

fmt: ## Format Java sources
	$(GRADLE) spotlessApply

lint: ## Run every pre-commit hook on all files
	cd $(ROOT) && $(PRE_COMMIT) run --all-files

test: ## Unit tests with coverage gate
	$(GRADLE) :common:check

build: ## Compile, lint (Error Prone), test and package both loaders
	$(GRADLE) build

check: lint build ## Everything CI runs

scan: ## Secret and workflow security scans
	cd $(ROOT) && $(PRE_COMMIT) run gitleaks --all-files
	cd $(ROOT) && $(PRE_COMMIT) run zizmor --all-files

lock: ## Refresh gradle.lockfile(s) and sha256 dependency verification after version changes
	$(GRADLE) --write-verification-metadata sha256 --refresh-dependencies resolveAndLockAll buildEnvironment build --write-locks

run-fabric: ## Launch the Fabric dev client
	$(GRADLE) :fabric:runClient

run-neoforge: ## Launch the NeoForge dev client
	$(GRADLE) :neoforge:runClient

run-fabric-server: ## Launch the Fabric dev server (headless)
	$(GRADLE) :fabric:runServer

run-neoforge-server: ## Launch the NeoForge dev server (headless)
	$(GRADLE) :neoforge:runServer

clean: ## Remove build outputs
	$(GRADLE) clean
