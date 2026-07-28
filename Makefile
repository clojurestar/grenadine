R := https://github.com/makeplus/makes
M := .cache/makes
$(shell [ -d '$M' ] || git clone -q $R '$M')

MAKES_LOCAL_DIR ?= $(CURDIR)/.cache/local
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
include $M/jolt.mk
include $M/let-go.mk
include $M/clean.mk
include $M/shell.mk

.PHONY: test-clj test-bb test-glj test-jolt test-lg test-all oracle

default:: test

test:: test-clj

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

MAKES-CLEAN += .cache/clojure .cache/jolt .cpcache target
