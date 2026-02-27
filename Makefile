# ===========================
# Variables
# ===========================
ARCH_DIR        = docs/c4
RENDERED_DIR    = docs/c4/rendered
ADR_DIR         = docs/adr
PLANTUML_IMAGE  = plantuml/plantuml
DOCKER_RUN      = docker run --rm -v $(PWD)/docs:/workspace $(PLANTUML_IMAGE)

# ===========================
# Help (default target)
# ===========================
.DEFAULT_GOAL := help

help:
	@echo ""
	@echo "Available commands:"
	@echo ""
	@echo "  Architecture"
	@echo "    make diagrams-validate    Validate all PlantUML C4 diagrams"
	@echo "    make diagrams-generate    Generate PNG diagrams from .puml files"
	@echo "    make diagrams-clean       Remove generated diagram images"
	@echo ""
	@echo "  ADR"
	@echo "    make adr-validate         Validate all ADR documents"
	@echo "    make adr-new NAME=ADR-005 Create a new ADR file from template"
	@echo ""
	@echo "  CI/CD"
	@echo "    make ci                   Run full CI pipeline locally"
	@echo ""
	@echo "  Application"
	@echo "    make build                Build the application"
	@echo "    make test                 Run tests"
	@echo "    make run                  Run the application locally"
	@echo "    make clean                Clean all build artifacts"
	@echo ""

# ===========================
# Architecture Diagrams
# ===========================
diagrams-validate:
	@echo "Validating C1 diagram..."
	$(DOCKER_RUN) -checkonly /workspace/c4/c1_context.puml
	@echo "Validating C2 diagram..."
	$(DOCKER_RUN) -checkonly /workspace/c4/c2_containers.puml
	@echo "Validating C3 diagram..."
	$(DOCKER_RUN) -checkonly /workspace/c4/c3_components.puml
	@echo "All diagrams are valid"

diagrams-generate: diagrams-validate
	@echo "Generating PNG diagrams..."
	@mkdir -p $(RENDERED_DIR)
	$(DOCKER_RUN) -tpng /workspace/c4/*.puml -output /workspace/c4/rendered
	@echo "Diagrams generated in $(RENDERED_DIR)"
	@ls -la $(RENDERED_DIR)

diagrams-clean:
	@echo "Removing generated diagrams..."
	@rm -rf $(RENDERED_DIR)/*.png
	@echo "Done"

# ===========================
# ADR Validation
# ===========================
adr-validate:
	@echo "Validating ADR documents..."
	@FAILED=0; \
	for adr in $(ADR_DIR)/ADR-*.md $(ADR_DIR)/ADR-*.MD; do \
		[ -f "$$adr" ] || continue; \
		echo "Checking $$adr..."; \
		for section in "Status" "Context" "Decision" "Consequences" "Alternatives" "Observability / Metrics"; do \
			if ! grep -qi "## $$section" "$$adr"; then \
				echo "ERROR: Missing section '$$section' in $$adr"; \
				FAILED=1; \
			fi; \
		done; \
		STATUS=$$(grep -i "## Status" -A2 "$$adr" | grep -v "## Status" | grep -v "^$$" | head -1 | tr -d ' *#\r'); \
		if ! echo "$$STATUS" | grep -qE "^(Proposed|Accepted|Deprecated|Superseded)$$"; then \
			echo "ERROR: Invalid status '$$STATUS' in $$adr"; \
			FAILED=1; \
		fi; \
	done; \
	if [ "$$FAILED" -eq 1 ]; then exit 1; fi; \
	echo "All ADRs are valid"

build:
	./gradlew build

test:
	./gradlew test

run:
	./gradlew bootRun

clean:
	./gradlew clean
	$(MAKE) diagrams-clean

# ===========================
# Full CI Pipeline (local)
# ===========================
ci: diagrams-validate adr-validate diagrams-generate
	@echo ""
	@echo "CI pipeline passed successfully"

# Prevent make from confusing targets with files
.PHONY: help diagrams-validate diagrams-generate diagrams-clean \
		adr-validate adr-new build test run clean ci