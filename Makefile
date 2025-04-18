APP_NAME = LogParser
MAIN_CLASS = com.example.MainApp
JAVAFX_SDK = javafx-sdk-21.0.2
ICON_PATH = src/main/resources/icons/logparser.icns

.PHONY: run package

run:
	mvn javafx:run

package:
	mvn package

	cp target/logparser-1.0-SNAPSHOT.jar target/logparser.jar

	jpackage \
		--input target \
		--name $(APP_NAME) \
		--main-jar logparser.jar \
		--main-class $(MAIN_CLASS) \
		--icon $(ICON_PATH) \
		--type app-image \
		--dest ~/Downloads \
		--module-path $(JAVAFX_SDK)/lib \
		--add-modules javafx.controls,javafx.fxml \
		--java-options '--enable-native-access=ALL-UNNAMED'

	@echo "✅ Done! App created at ~/Downloads/$(APP_NAME).app"