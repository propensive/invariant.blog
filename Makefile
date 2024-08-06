compile:
	wrath

build: compile
	cp .wrath/dist/* out/WEB-INF/lib/
	cp .wrath/lib/* out/WEB-INF/lib/

run: build
	java_dev_appserver.sh out

deploy: build
	gcloud app deploy --project=propensive-invariant out/

.PHONY: compile build run deploy
