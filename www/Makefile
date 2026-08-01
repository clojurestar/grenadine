TOP := ..
SITE := site
REPO ?= git@github.com:clojurestar/grenadine
VERSION ?= $(shell cat $(TOP)/VERSION)

.PHONY: site

site: $(SITE)/index.html $(SITE)/get $(SITE)/get.ps1

$(SITE)/.dir:
	mkdir -p '$(SITE)'
	touch '$@'

$(SITE)/index.html: index.html $(TOP)/ReadMe.md $(TOP)/VERSION | $(SITE)/.dir
	sed 's/@VERSION@/$(VERSION)/g' '$<' > '$@'

$(SITE)/get: get | $(SITE)/.dir
	cp '$<' '$@'

$(SITE)/get.ps1: get.ps1 | $(SITE)/.dir
	cp '$<' '$@'

serve: site
	cd '$(SITE)' && python3 -m http.server 8000

publish: site
	cd '$(SITE)' && \
	  git init -q && \
	  git add -A && \
	  git commit -qm 'Deploy Grenadine website' && \
	  git push -f '$(REPO)' HEAD:gh-pages
	rm -rf '$(SITE)'

clean:
	rm -rf '$(SITE)'
