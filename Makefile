R := https://github.com/makeplus/makes
M := .cache/makes
$(shell [ -d '$M' ] || git clone -q $R '$M')

MAKES_LOCAL_DIR ?= $(CURDIR)/.cache/local
GRENADINE-VERSION := 0.1.9
ifdef GLOAT_DIR
GLOAT-DIR := $(GLOAT_DIR)
endif
CLJ_CONFIG := $(CURDIR)/.cache/clojure
export CLJ_CONFIG
JOLT_CACHE_DIR := $(CURDIR)/.cache/jolt
JOLT_GITLIBS_DIR := $(CURDIR)/.cache/jolt/gitlibs
JOLT_MAVEN_REPOSITORY := $(CURDIR)/.cache/m2
export JOLT_CACHE_DIR JOLT_GITLIBS_DIR JOLT_MAVEN_REPOSITORY
JOLT_SOURCE_DIR ?= $(abspath $(CURDIR)/../jolt)
GOBB_SOURCE_DIR ?= $(abspath $(CURDIR)/../gobb)

include $M/init.mk
include $M/clojure.mk
include $M/glojure.mk
include $M/gloat.mk
include $M/gobb.mk
include $M/jolt.mk
include $M/gh.mk
include $M/powershell.mk
include $M/shellcheck.mk
include $M/yq.mk
include $M/clean.mk
include $M/shell.mk

GRENADINE := $(CURDIR)/bin/grenadine
SOURCE-STAGE := $(CURDIR)/.cache/source-stage
SOURCE-STAGE-STAMP := $(SOURCE-STAGE)/.stamp
STAGE-SOURCES := $(CURDIR)/util/stage-sources
RELEASE := $(CURDIR)/util/release
RELEASE-DIST := $(CURDIR)/util/release-dist
BREW-UPDATE := $(CURDIR)/util/brew-update
DIST := $(CURDIR)/dist
RELEASE-BUILD := $(CURDIR)/.cache/release
SOURCE-PATCHES := $(CURDIR)/util/source-patches
SOURCE-MANIFEST := $(CURDIR)/patch/sources.yaml
PREFIX ?= $(if $(filter 0,$(shell id -u)),/usr/local,$(HOME)/.local)
GRENADINE-SOURCES := $(wildcard src/grenadine/*.clj src/grenadine/*.cljc src/grenadine/host/*.clj)

.PHONY: src patch src-check src-clean version

version:
	@echo '$(GRENADINE-VERSION)'

src: $(YQ) $(SOURCE-PATCHES) $(SOURCE-MANIFEST)
	$Q YQ='$(YQ)' FORCE='$(FORCE)' '$(SOURCE-PATCHES)' src '$(CURDIR)' '$(YQ)'

patch: $(YQ) $(SOURCE-PATCHES) $(SOURCE-MANIFEST)
	$Q YQ='$(YQ)' '$(SOURCE-PATCHES)' patch '$(CURDIR)' '$(YQ)'

src-check: $(YQ) $(SOURCE-PATCHES) $(SOURCE-MANIFEST)
	$Q YQ='$(YQ)' '$(SOURCE-PATCHES)' check '$(CURDIR)' '$(YQ)'

src-clean: $(YQ) $(SOURCE-PATCHES) $(SOURCE-MANIFEST)
	$Q YQ='$(YQ)' '$(SOURCE-PATCHES)' clean '$(CURDIR)' '$(YQ)'

default:: build

$(SOURCE-STAGE-STAMP): src $(GRENADINE-SOURCES) Makefile $(STAGE-SOURCES)
	@$(ECHO) "* Staging Grenadine sources"
	$Q '$(STAGE-SOURCES)' \
	  '$(CURDIR)' '$(SOURCE-STAGE)' '$(GRENADINE-VERSION)'
	$Q touch '$@'
	@$(ECHO)

stage: $(SOURCE-STAGE-STAMP)

$(GRENADINE): $(SOURCE-STAGE-STAMP) $(GLOAT)
	@$(ECHO) "* Building Grenadine"
	$Q mkdir -p '$(@D)'
	$Q $(GLOAT) '$(SOURCE-STAGE)' \
	  --out='$@' \
	  --force \
	  --quiet \
	  --module=github.com/clojurestar/grenadine
	@$(ECHO)

build: $(GRENADINE)

jar: src $(CLOJURE) util/build.clj deps.edn Makefile
	GRENADINE_VERSION='$(GRENADINE-VERSION)' \
	  $(CLOJURE) -T:build jar

test-jar: jar
	test/jar 'target/grenadine-$(GRENADINE-VERSION).jar'

deploy-clojars: jar
	@$(if $(filter command line,$(origin VERSION)),,\
	  $(error VERSION is required on the command line))
	@$(if $(filter $(GRENADINE-VERSION),$(VERSION)),,\
	  $(error VERSION=$(VERSION) does not match GRENADINE-VERSION=$(GRENADINE-VERSION)))
	$(CLOJURE) -X:deploy \
	  :artifact '"target/grenadine-$(GRENADINE-VERSION).jar"' \
	  :sign-releases? false

install: $(GRENADINE)
	$Q install -d '$(DESTDIR)$(PREFIX)/bin'
	$Q install -m 0755 '$(GRENADINE)' '$(DESTDIR)$(PREFIX)/bin/grenadine'

test: test-all test-cli test-scripts test-jar test-release

test-glj: src $(GLJ)
	GLJ_CLASSPATH=src:test $(GLJ) -e \
	  "(require 'grenadine.test-runner) (grenadine.test-runner/-main)"

ifneq (,$(wildcard $(JOLT_SOURCE_DIR)/Makefile))
test-jolt: src
	$(MAKE) -C '$(JOLT_SOURCE_DIR)' testbin
	JOLT_PWD=. '$(JOLT_SOURCE_DIR)/target/release/jolt' \
	  run test/jolt_runner.clj
else
test-jolt: src $(JOLT)
	JOLT_PWD=. $(JOLT) run test/jolt_runner.clj
endif

test-all: test-glj test-jolt

test-gobb: src
	@test -f '$(GOBB_SOURCE_DIR)/Makefile' || { \
	  echo 'test-gobb requires a Gobb source checkout at $(GOBB_SOURCE_DIR)' >&2; \
	  exit 1; \
	}
	$(MAKE) -C '$(GOBB_SOURCE_DIR)' build
	'$(GOBB_SOURCE_DIR)/bin/gobb' --classpath src:test -e \
	  "(load-file \"src/clojurestar/deps.cljc\") \
	   (require 'grenadine.test-runner) \
	   (grenadine.test-runner/-main)"

test-ecosystem: test-all test-gobb

oracle: src $(CLOJURE)
	$(CLOJURE) -M:oracle -m grenadine.oracle

test-cli: $(GRENADINE)
	GRENADINE='$(GRENADINE)' test/cli

test-release: $(YQ)
	YQ='$(YQ)' test/release

test-scripts: src-check $(SHELLCHECK) $(PWSH)
	$(SHELLCHECK) util/brew-update util/source-patches util/stage-sources util/release util/release-dist test/cli test/homebrew test/installer test/jar test/provenance test/release www/docs/get www/docs/install
	test/homebrew
	test/installer
	test/provenance
	$(PWSH) -NoProfile -Command '$$tokens = $$null; $$errors = $$null; [System.Management.Automation.Language.Parser]::ParseFile("www/docs/get.ps1", [ref] $$tokens, [ref] $$errors) > $$null; if ($$errors.Count) { $$errors | Out-String | Write-Error; exit 1 }'

site:
	$(MAKE) -C www site

serve:
	$(MAKE) -C www serve

publish publish-www:
	$(MAKE) -C www publish

release-prep:
	@$(if $(filter command line,$(origin VERSION)),,\
	  $(error VERSION is required on the command line))
	$Q '$(RELEASE)' prepare '$(VERSION)'

release-dist: src-check $(SOURCE-STAGE-STAMP) $(GLOAT)
	@$(if $(filter command line,$(origin VERSION)),,\
	  $(error VERSION is required on the command line))
	$Q '$(RELEASE-DIST)' \
	  '$(VERSION)' '$(GLOAT)' '$(SOURCE-STAGE)' \
	  '$(CURDIR)' '$(DIST)' '$(RELEASE-BUILD)' \
	  '$(GRENADINE-VERSION)'

release-homebrew:
	@$(if $(filter command line,$(origin VERSION)),,\
	  $(error VERSION is required on the command line))
	$Q '$(BREW-UPDATE)' '$(VERSION)'

release: $(GH) $(YQ)
	@$(if $(filter command line,$(origin VERSION)),,\
	  $(error VERSION is required on the command line))
	$Q YQ='$(YQ)' GH='$(GH)' '$(RELEASE)' release '$(VERSION)'

release-mark-deployed:
	@$(if $(and $(filter 1,$(DEPLOYED)),$(filter command line,$(origin VERSION))),,\
	  $(error usage: make release-mark-deployed VERSION=x.y.z DEPLOYED=1))
	$Q '$(RELEASE)' mark-deployed '$(VERSION)'

MAKES-CLEAN += .cache/clojure .cache/jolt .cache/source-stage .cache/release .cpcache target bin dist www/site pom.xml src/grenadine/basis.cljc src/grenadine/coordinate.cljc src/grenadine/expander.cljc src/grenadine/gitlibs.cljc
MAKES-REALCLEAN += .cache/homebrew-grenadine www/venv
