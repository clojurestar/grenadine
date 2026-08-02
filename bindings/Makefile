R := https://github.com/makeplus/makes
M := .cache/makes
$(shell [ -d '$M' ] || git clone -q $R '$M')

MAKES_LOCAL_DIR ?= $(abspath .cache/local)
CLJ_CONFIG := $(abspath .cache/bindings/clojure-config)
JOLT_CACHE_DIR := $(abspath .cache/bindings/jolt-cache)
export CLJ_CONFIG JOLT_CACHE_DIR

include $M/init.mk
include $M/clojure.mk
include $M/babashka.mk
include $M/glojure.mk
include $M/jolt.mk
include $M/let-go.mk
include $M/clean.mk
include $M/shell.mk

GRENADINE := $(abspath ..)
CACHE := $(GRENADINE)/.cache/bindings
MEDLEY-ROOT := $(CACHE)/medley-root
REPOSITORY := ./m2

default:: test

test: run-clojure run-babashka run-glojure run-jolt run-jolt2 run-let-go

$(MEDLEY-ROOT): prepare/main.clj $(wildcard ../src/grenadine/*.clj ../src/grenadine/*.cljc) | $(BB)
	mkdir -p '$(@D)'
	GRENADINE_EXAMPLE_REPOSITORY='$(REPOSITORY)' \
	  $(BB) -cp '$(GRENADINE)/src' '$<' > '$@.tmp'
	mv '$@.tmp' '$@'

run-clojure: $(CLOJURE)
	GRENADINE_EXAMPLE_REPOSITORY='$(REPOSITORY)' \
	  $(CLOJURE) -Srepro \
	  -Sdeps '{:mvn/local-repo "$(REPOSITORY)" :paths ["$(GRENADINE)/src" "$(CURDIR)/clojure"]}' \
	  -M -m main

run-babashka: $(BB)
	GRENADINE_EXAMPLE_REPOSITORY='$(REPOSITORY)' \
	  $(BB) -cp '$(GRENADINE)/src' babashka/main.clj

run-glojure: $(GLJ) $(MEDLEY-ROOT)
	@set +e; \
	  output=$$(GRENADINE_MEDLEY_ROOT="$$(< '$(MEDLEY-ROOT)')" \
	    GLJ_CLASSPATH='$(GRENADINE)/src' \
	    $(GLJ) glojure/main.clj 2>&1); \
	  status=$$?; \
	  set -e; \
	  printf '%s\n' "$$output"; \
	  [[ $$status -ne 0 ]]

run-jolt: $(JOLT) $(MEDLEY-ROOT)
	GRENADINE_MEDLEY_ROOT="$$(< '$(MEDLEY-ROOT)')" \
	  JOLT_PWD='$(GRENADINE)' \
	  $(JOLT) -Scp '$(GRENADINE)/src' run '$(CURDIR)/jolt/main.clj'

run-jolt2: $(JOLT)
	JOLT_PWD='$(CURDIR)' \
	  $(JOLT) run '$(CURDIR)/jolt2/main.clj'

run-let-go: $(LG) $(MEDLEY-ROOT)
	$(LG) \
	  -source-paths '$(GRENADINE)/src' \
	  '$(CURDIR)/let-go/main.clj' "$$(< '$(MEDLEY-ROOT)')"

MAKES-CLEAN += $(CACHE) $(REPOSITORY)
