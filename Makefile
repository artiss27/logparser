APP_NAME = LogParser
MAIN_CLASS = com.example.MainApp
JAVAFX_JMODS = javafx-jmods-21.0.8
ICON_PATH = src/main/resources/icons/logparser.icns

.PHONY: run package clean

install: clean package

run:
	mvn javafx:run

clean:
	rm -rf target
	rm -f logparser.jar

package: clean
	mvn package

	jpackage \
		--input target \
		--name $(APP_NAME) \
		--main-jar logparser.jar \
		--main-class $(MAIN_CLASS) \
		--icon $(ICON_PATH) \
		--type dmg \
		--dest ~/Downloads \
		--module-path $(JAVAFX_JMODS) \
		--add-modules javafx.controls,javafx.fxml \
		--java-options "--enable-native-access=ALL-UNNAMED -Duser.home=$(shell echo $$HOME)" \
		--verbose

	@echo "✅ Done! DMG created at ~/Downloads/$(APP_NAME).dmg"