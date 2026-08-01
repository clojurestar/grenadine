R := https://github.com/makeplus/makes
M := $(or $(MAKES_REPO_DIR),../.cache/makes)
$(shell [ -d '$M' ] || git clone -q $R '$M')

include $M/init.mk

ifdef MAKES_REPO_DIR
MAKES_LOCAL_DIR ?= $(TOP)/.cache/local
endif

include $M/clean.mk

PYTHON-VENV := $(ROOT)/venv

include $M/python.mk

SHELL-DEPS += $(PYTHON-VENV)

include $M/shell.mk

export UV_LINK_MODE := copy
export UV_CACHE_DIR := $(TOP)/.cache/uv
export NO_MKDOCS_2_WARNING := 1

REPO ?= git@github.com:clojurestar/grenadine

MAKES-CLEAN := site
MAKES-REALCLEAN := $(PYTHON-VENV)

DEPS := $(PYTHON-VENV)
DOCS := $(shell find docs -type f)

default:: site

site: $(DEPS) $(DOCS) mkdocs.yaml
	mkdocs build --strict -d $@

serve: $(DEPS)
	mkdocs serve --livereload

publish: site
	cd $< && \
	  git init && \
	  git add -A && \
	  git commit -m 'Deploy Grenadine website' && \
	  git push -f $(REPO) HEAD:gh-pages
	$(RM) -r $<
