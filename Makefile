R := https://github.com/makeplus/makes
M := .cache/makes
$(shell [ -d '$M' ] || git clone -q $R '$M')

MAKES_LOCAL_DIR ?= $(CURDIR)/.cache/local
GLOAT-VERSION ?= 0.1.68
ifdef GLOAT_DIR
GLOAT-DIR := $(GLOAT_DIR)
endif
CLJ_CONFIG := $(CURDIR)/.cache/clojure
export CLJ_CONFIG
JOLT_CACHE_DIR := $(CURDIR)/.cache/jolt
JOLT_GITLIBS := $(CURDIR)/.cache/jolt/gitlibs
JOLT_LOCAL_REPO := $(CURDIR)/.cache/m2
export JOLT_CACHE_DIR JOLT_GITLIBS JOLT_LOCAL_REPO

include $M/init.mk
include $M/clojure.mk
include $M/babashka.mk
include $M/glojure.mk
include $M/gloat.mk
include $M/jolt.mk
include $M/let-go.mk
include $M/gh.mk
include $M/powershell.mk
include $M/shellcheck.mk
include $M/clean.mk
include $M/shell.mk

VERSION-FILE := $(CURDIR)/VERSION
GRENADINE := $(CURDIR)/bin/grenadine
SOURCE-STAGE := $(CURDIR)/.cache/source-stage
SOURCE-STAGE-STAMP := $(SOURCE-STAGE)/.stamp
STAGE-SOURCES := $(CURDIR)/util/stage-sources
RELEASE := $(CURDIR)/util/release
RELEASE-DIST := $(CURDIR)/util/release-dist
DIST := $(CURDIR)/dist
RELEASE-BUILD := $(CURDIR)/.cache/release
PREFIX ?= $(if $(filter 0,$(shell id -u)),/usr/local,$(HOME)/.local)
GRENADINE-SOURCES := $(wildcard src/grenadine/*.clj src/grenadine/*.cljc src/grenadine/host/*.clj)

default:: build

$(SOURCE-STAGE-STAMP): $(GRENADINE-SOURCES) $(VERSION-FILE) $(STAGE-SOURCES)
	@$(ECHO) "* Staging Grenadine sources"
	$Q '$(STAGE-SOURCES)' '$(CURDIR)' '$(SOURCE-STAGE)'
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

install: $(GRENADINE)
	$Q install -d '$(DESTDIR)$(PREFIX)/bin'
	$Q install -m 0755 '$(GRENADINE)' '$(DESTDIR)$(PREFIX)/bin/grenadine'

test: test-all test-cli test-scripts

test-clj: $(CLOJURE)
	$(CLOJURE) -M:test

test-bb: $(BB)
	$(BB) -cp src:test -m grenadine.test-runner

test-glj: $(GLJ)
	GLJ_CLASSPATH=src:test $(GLJ) -e \
	  "(require 'grenadine.test-runner) (grenadine.test-runner/-main)"

test-jolt: $(JOLT)
	JOLT_PWD=. $(JOLT) -Sdeps '{:paths ["src" "test"]}' \
	  run -m grenadine.test-runner

test-lg: $(LG)
	LG_SOURCE_PATHS=src:test $(LG) -e \
	  "(require 'grenadine.test-runner) (grenadine.test-runner/-main)"

test-all: test-clj test-bb test-glj test-jolt test-lg

oracle: $(CLOJURE)
	$(CLOJURE) -M:oracle -m grenadine.oracle

test-cli: $(GRENADINE)
	GRENADINE='$(GRENADINE)' test/cli

test-scripts: $(SHELLCHECK) $(PWSH)
	$(SHELLCHECK) util/stage-sources util/release util/release-dist test/cli www/get
	$(PWSH) -NoProfile -Command '$$tokens = $$null; $$errors = $$null; [System.Management.Automation.Language.Parser]::ParseFile("www/get.ps1", [ref] $$tokens, [ref] $$errors) > $$null; if ($$errors.Count) { $$errors | Out-String | Write-Error; exit 1 }'

site:
	$(MAKE) -C www site VERSION=$$(cat '$(VERSION-FILE)')

serve:
	$(MAKE) -C www serve VERSION=$$(cat '$(VERSION-FILE)')

publish publish-www:
	$(MAKE) -C www publish VERSION=$$(cat '$(VERSION-FILE)')

release-prep:
	@$(if $(filter command line,$(origin VERSION)),,\
	  $(error VERSION is required on the command line))
	$Q '$(RELEASE)' prepare '$(VERSION)'

release-dist: $(SOURCE-STAGE-STAMP) $(GLOAT)
	@$(if $(filter command line,$(origin VERSION)),,\
	  $(error VERSION is required on the command line))
	$Q '$(RELEASE-DIST)' \
	  '$(VERSION)' '$(GLOAT)' '$(SOURCE-STAGE)' \
	  '$(CURDIR)' '$(DIST)' '$(RELEASE-BUILD)'

release: $(GH)
	@$(if $(filter command line,$(origin VERSION)),,\
	  $(error VERSION is required on the command line))
	$Q GH='$(GH)' '$(RELEASE)' publish '$(VERSION)'

MAKES-CLEAN += .cache/clojure .cache/jolt .cache/source-stage .cache/release .cpcache target bin dist www/site
