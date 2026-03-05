APP_NAME = LogParser
VERSION = 1.0
MAIN_CLASS = com.logparser.MainApp
JAVAFX_JMODS = javafx-jmods-21.0.8
ICON = src/main/resources/icons/logparser.icns
JAVA_HOME := $(shell /usr/libexec/java_home -v 21)
DEST = ~/Downloads

.PHONY: package clean run

package:
	@mkdir -p runtime-image 2>/dev/null || true
	@if [ ! -f "runtime-image/release" ]; then \
		echo "Creating Java runtime..."; \
		rm -rf runtime-image; \
		jlink \
			--module-path "$(JAVA_HOME)/jmods:$(JAVAFX_JMODS)" \
			--add-modules java.base,java.desktop,java.logging,java.naming,java.prefs,java.xml,jdk.crypto.ec,jdk.unsupported,javafx.controls,javafx.fxml,javafx.graphics,javafx.base \
			--strip-debug --no-header-files --no-man-pages --compress=2 \
			--output runtime-image; \
	fi
	@mvn package -q -DskipTests
	@rm -f $(DEST)/$(APP_NAME)-*.dmg 2>/dev/null || true
	@hdiutil detach "/Volumes/$(APP_NAME)" 2>/dev/null || true
	@jpackage \
		--input target \
		--name $(APP_NAME) \
		--main-jar logparser.jar \
		--main-class $(MAIN_CLASS) \
		--icon $(ICON) \
		--type dmg \
		--dest $(DEST) \
		--app-version $(VERSION) \
		--runtime-image runtime-image \
		--java-options "--enable-native-access=ALL-UNNAMED" \
		--mac-package-identifier "com.logparser.app"
	@echo "Done: $(DEST)/$(APP_NAME)-$(VERSION).dmg"

run:
	@mvn javafx:run -q

clean:
	@rm -rf target runtime-image
	@rm -f $(DEST)/$(APP_NAME)-*.dmg
