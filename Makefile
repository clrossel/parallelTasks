PWD          := $(shell pwd)
DOCKER       := docker
DOCKER_MOUNT := -v $(PWD):/root/parallelTasks -v $(HOME)/.m2:/root/.m2
DOCKER_FLAGS := docker run --rm -i $(DOCKER_MOUNT)

# if this session isn't interactive, then we don't want to allocate a
# TTY, which would fail, but if it is interactive, we do want to attach
# so that the user can send e.g. ^C through.
INTERACTIVE := $(shell [ -t 0 ] && echo 1 || echo 0)
ifeq ($(INTERACTIVE), 1)
	DOCKER_FLAGS += -t
endif

DOCKER_BUILD  := $(DOCKER) build --force-rm -t
DOCKER_RUN   := $(DOCKER) run --rm $(DOCKER_MOUNT)
DOCKER_RUN_IT := $(DOCKER_RUN) -it
DOCKER_PULL := $(DOCKER) pull

JAVA := paralleltasks/java
DEV := paralleltasks/devenv

THIS_FILE := $(lastword $(MAKEFILE_LIST))

default: help

copyFiles:
	@for file in $(PWD)/docker/*; do \
       cp -f $$file $(PWD) ; \
    done

removeFiles:
	@for file in $(PWD)/docker/*; do \
       rm -f $(PWD)/$$(basename $$file) ; \
    done

build:
	@$(MAKE) -f $(THIS_FILE) copyFiles
	@$(DOCKER_BUILD) $(JAVA) -f $(PWD)/java.docker .
	@$(DOCKER_BUILD) $(DEV) -f $(PWD)/devenv.docker .
	@$(MAKE) -f $(THIS_FILE) removeFiles

compile: build ## compile parallelTasks
	@$(DOCKER_RUN) $(DEV) mvn clean package

shell: build ## start a shell inside the build env
	@clear && $(DOCKER_RUN_IT) $(DEV) bash

help: ## this help
	@awk 'BEGIN {FS = ":.*?## "} /^[a-zA-Z_-]+:.*?## / {sub("\\\\n",sprintf("\n%22c"," "), $$2);printf "\033[36m%-20s\033[0m %s\n", $$1, $$2}' $(MAKEFILE_LIST)
