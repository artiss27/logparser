APP_NAME = LogParser
MAIN_CLASS = com.logparser.MainApp
JAVAFX_JMODS = javafx-jmods-21.0.8
ICON_PATH = src/main/resources/icons/logparser.icns
VERSION = 1.0
JAVA_HOME := $(shell /usr/libexec/java_home -v 21)

.PHONY: run package clean jlink-runtime

install: clean package

run:
	mvn javafx:run

clean:
	rm -rf target
	rm -f ~/Downloads/$(APP_NAME)-*.dmg
	rm -f ~/Downloads/$(APP_NAME).dmg

clean-all: clean
	rm -rf runtime-image

# Create minimal Java runtime with only required modules
jlink-runtime:
	@if [ ! -d "runtime-image" ]; then \
		echo "🔨 Creating minimal Java runtime..."; \
		jlink \
			--module-path "$(JAVA_HOME)/jmods:$(JAVAFX_JMODS)" \
			--add-modules java.base,java.desktop,java.logging,java.naming,java.prefs,java.xml,jdk.crypto.ec,jdk.unsupported,javafx.controls,javafx.fxml,javafx.graphics,javafx.base \
			--strip-debug \
			--no-header-files \
			--no-man-pages \
			--compress=2 \
			--output runtime-image; \
		echo "✅ Runtime created: $$(du -sh runtime-image | cut -f1)"; \
	else \
		echo "✅ Using existing runtime-image ($$(du -sh runtime-image | cut -f1))"; \
	fi

package: jlink-runtime
	@echo "📦 Building application..."
	mvn clean package -DskipTests

	@echo "🗑️ Cleaning up old builds..."
	-rm -rf /tmp/jpackage-logparser 2>/dev/null || true
	-rm -f ~/Downloads/$(APP_NAME)-*.dmg 2>/dev/null || true
	-hdiutil detach "/Volumes/$(APP_NAME)" 2>/dev/null || true
	sleep 2

	@echo "📦 Creating DMG package with jpackage..."
	jpackage \
		--input target \
		--name $(APP_NAME) \
		--main-jar logparser.jar \
		--main-class $(MAIN_CLASS) \
		--icon $(ICON_PATH) \
		--type dmg \
		--dest ~/Downloads \
		--app-version $(VERSION) \
		--runtime-image runtime-image \
		--java-options "--enable-native-access=ALL-UNNAMED" \
		--vendor "LogParser" \
		--copyright "2025 LogParser" \
		--mac-package-name "LogParser" \
		--mac-package-identifier "com.logparser.app" \
		--mac-entitlements packaging/macos/entitlements.plist \
		--temp /tmp/jpackage-logparser

	@echo ""
	@echo "✅ Done! DMG created at ~/Downloads/$(APP_NAME)-$(VERSION).dmg"
	@echo "📊 Size: $$(du -sh ~/Downloads/$(APP_NAME)-$(VERSION).dmg | cut -f1)"

# Create optimized package with custom runtime
package-optimized: clean-all jlink-runtime
	@echo "📦 Building application..."
	mvn package -DskipTests

	@echo "🗑️ Cleaning up old builds..."
	-rm -rf /tmp/jpackage-logparser 2>/dev/null || true
	-rm -f ~/Downloads/$(APP_NAME)-*.dmg 2>/dev/null || true
	-hdiutil detach "/Volumes/$(APP_NAME)" 2>/dev/null || true
	sleep 2

	@echo "📦 Creating optimized DMG package..."
	jpackage \
		--input target \
		--name $(APP_NAME) \
		--main-jar logparser.jar \
		--main-class $(MAIN_CLASS) \
		--icon $(ICON_PATH) \
		--type dmg \
		--dest ~/Downloads \
		--app-version $(VERSION) \
		--runtime-image runtime-image \
		--java-options "--enable-native-access=ALL-UNNAMED" \
		--vendor "LogParser" \
		--copyright "2025 LogParser" \
		--mac-package-name "LogParser" \
		--mac-package-identifier "com.logparser.app" \
		--mac-entitlements packaging/macos/entitlements.plist \
		--temp /tmp/jpackage-logparser

	@echo ""
	@echo "✅ Done! Optimized DMG created at ~/Downloads/$(APP_NAME)-$(VERSION).dmg"
	@echo "📊 DMG Size: $$(du -sh ~/Downloads/$(APP_NAME)-$(VERSION).dmg | cut -f1)"
	@echo "📊 Runtime Size: $$(du -sh runtime-image | cut -f1)"

