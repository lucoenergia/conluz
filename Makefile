.PHONY: run debug

run:
	./gradlew bootRun --args='--spring.profiles.active=local'

debug:
	./gradlew bootRun --debug-jvm --args='--spring.profiles.active=local'
