#!/bin/bash

# LogParser Permission Checker and Fixer for macOS
# This script helps diagnose and fix permission issues

echo "🔍 LogParser Permission Checker"
echo "================================"
echo ""

# Check OS
if [[ "$OSTYPE" != "darwin"* ]]; then
    echo "❌ This script is only for macOS"
    exit 1
fi

# Check directories
CONFIG_DIR="$HOME/Library/Application Support/LogParser"
KEY_DIR="$HOME/.config/LogParser"

echo "📁 Checking directories..."
echo ""

# Profile directory
if [ -d "$CONFIG_DIR" ]; then
    echo "✅ Profile directory exists: $CONFIG_DIR"
    if [ -w "$CONFIG_DIR" ]; then
        echo "   ✅ Directory is writable"
    else
        echo "   ❌ Directory is NOT writable"
        echo "   Attempting to fix permissions..."
        chmod 755 "$CONFIG_DIR"
        if [ $? -eq 0 ]; then
            echo "   ✅ Fixed!"
        else
            echo "   ❌ Failed to fix. Run: sudo chmod 755 '$CONFIG_DIR'"
        fi
    fi
else
    echo "⚠️  Profile directory does not exist: $CONFIG_DIR"
    echo "   Creating directory..."
    mkdir -p "$CONFIG_DIR"
    if [ $? -eq 0 ]; then
        echo "   ✅ Created successfully"
    else
        echo "   ❌ Failed to create directory"
    fi
fi

echo ""

# Key directory
if [ -d "$KEY_DIR" ]; then
    echo "✅ Key directory exists: $KEY_DIR"
    if [ -w "$KEY_DIR" ]; then
        echo "   ✅ Directory is writable"
    else
        echo "   ❌ Directory is NOT writable"
        echo "   Attempting to fix permissions..."
        chmod 755 "$KEY_DIR"
        if [ $? -eq 0 ]; then
            echo "   ✅ Fixed!"
        else
            echo "   ❌ Failed to fix. Run: sudo chmod 755 '$KEY_DIR'"
        fi
    fi
else
    echo "⚠️  Key directory does not exist: $KEY_DIR"
    echo "   Creating directory..."
    mkdir -p "$KEY_DIR"
    if [ $? -eq 0 ]; then
        echo "   ✅ Created successfully"
    else
        echo "   ❌ Failed to create directory"
    fi
fi

echo ""
echo "📋 File Status:"
echo ""

# Check for existing files
PROFILE_FILE="$CONFIG_DIR/profiles.json.enc"
KEY_FILE="$KEY_DIR/.key"

if [ -f "$PROFILE_FILE" ]; then
    echo "✅ Profile file exists: $PROFILE_FILE"
    ls -lh "$PROFILE_FILE"
else
    echo "⚠️  No profile file yet (will be created on first save)"
fi

echo ""

if [ -f "$KEY_FILE" ]; then
    echo "✅ Encryption key exists: $KEY_FILE"
    ls -lh "$KEY_FILE"
else
    echo "⚠️  No encryption key yet (will be created on first use)"
fi

echo ""
echo "🔐 macOS Privacy Settings:"
echo ""

# Check if running from .app bundle
APP_PATH="/Applications/LogParser.app"
DOWNLOADS_APP="$HOME/Downloads/LogParser.app"

if [ -d "$APP_PATH" ]; then
    echo "✅ Found LogParser.app in /Applications"
    APP_TO_CHECK="$APP_PATH"
elif [ -d "$DOWNLOADS_APP" ]; then
    echo "✅ Found LogParser.app in ~/Downloads"
    APP_TO_CHECK="$DOWNLOADS_APP"
else
    echo "⚠️  LogParser.app not found in /Applications or ~/Downloads"
    APP_TO_CHECK=""
fi

echo ""
echo "⚠️  IMPORTANT: macOS Full Disk Access"
echo ""
echo "If profiles are not saving, you need to grant Full Disk Access:"
echo ""
echo "1. Open System Settings"
echo "2. Go to Privacy & Security → Full Disk Access"
echo "3. Click the + button"
echo "4. Navigate to and select LogParser.app"
if [ -n "$APP_TO_CHECK" ]; then
    echo "5. Select: $APP_TO_CHECK"
fi
echo "6. Restart LogParser"
echo ""
echo "Alternatively, you can run this command:"
echo "   tccutil reset SystemPolicyAllFiles"
echo ""
echo "================================"
echo "✅ Permission check complete!"

