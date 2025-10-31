#!/bin/bash

# Переменные
APP_NAME="LogParser"
MAIN_JAR="logparser-1.0-SNAPSHOT-shaded.jar"
MAIN_CLASS="com.logparser.MainApp"
TARGET_DIR="target"
OUTPUT_DIR="$HOME/Downloads"
ICON_PATH="src/main/resources/icons/logparser.icns"

# Чистим старые сборки
echo "Cleaning and packaging jar..."
mvn clean package

if [ $? -ne 0 ]; then
  echo "❌ Maven build failed. Aborting."
  exit 1
fi

# Проверка, существует ли jar
if [ ! -f "$TARGET_DIR/$MAIN_JAR" ]; then
  echo "❌ Main JAR not found: $TARGET_DIR/$MAIN_JAR"
  exit 1
fi

# Удаляем старые .app и .dmg
echo "Cleaning previous builds..."
rm -rf "$OUTPUT_DIR/$APP_NAME.app"
rm -f "$OUTPUT_DIR/$APP_NAME.dmg"

# Создаем приложение
echo "Packaging into DMG..."
jpackage \
  --input "$TARGET_DIR" \
  --dest "$OUTPUT_DIR" \
  --name "$APP_NAME" \
  --main-jar "$MAIN_JAR" \
  --main-class "$MAIN_CLASS" \
  --type dmg \
  --icon "$ICON_PATH" \
  --java-options "--add-opens=javafx.graphics/javafx.scene=ALL-UNNAMED" \
  --java-options "--add-opens=javafx.base/com.sun.javafx.event=ALL-UNNAMED" \
  --java-options "--add-modules=javafx.controls,javafx.fxml" \
  --app-version "1.0" \
  --verbose

if [ $? -eq 0 ]; then
  echo "✅ Done! Your DMG is ready in: $OUTPUT_DIR"
else
  echo "❌ jpackage failed."
fi