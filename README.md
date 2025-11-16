проба пера )

## Quick Start

```bash
mvn clean javafx:run 
# or
make run
```

## Building for macOS

```bash
make package
```

The DMG will be created in `~/Downloads/LogParser-1.0.dmg`

## macOS Permissions & Troubleshooting

### Issue: Profiles are not being saved

If you're experiencing issues with profile saving on macOS, this is typically a **permissions problem**. macOS requires explicit Full Disk Access permission for apps to write to certain directories.

#### Quick Check

Run the permission checker script:
```bash
./check-permissions.sh
```

#### Manual Fix

1. **Grant Full Disk Access:**
   - Open **System Settings** → **Privacy & Security** → **Full Disk Access**
   - Click the **+** button
   - Navigate to and add `LogParser.app`
   - **Important:** Restart the application after granting access

2. **Check Console for Errors:**
   - Open **Console.app**
   - Filter for "LogParser"
   - Look for "Permission denied" errors

3. **Run from Terminal to see errors:**
   ```bash
   cd ~/Downloads/LogParser.app/Contents/MacOS
   ./LogParser
   ```
   
   This will show any permission errors in the terminal output.

#### Where Profiles Are Saved

- **Profile data:** `~/Library/Application Support/LogParser/profiles.json.enc`
- **Encryption key:** `~/.config/LogParser/.key`

#### Why Permission Dialog Doesn't Show

macOS only shows permission dialogs for **sandboxed apps** or when specific entitlements are properly configured. If the dialog doesn't appear:

1. The app needs to be **signed** (or run from a trusted location)
2. User must **manually grant** Full Disk Access in System Settings
3. Alternatively, create the directories with proper permissions before first run:
   ```bash
   mkdir -p ~/Library/Application\ Support/LogParser
   mkdir -p ~/.config/LogParser
   chmod 755 ~/Library/Application\ Support/LogParser
   chmod 755 ~/.config/LogParser
   ```

#### Reset TCC Permissions (if needed)

```bash
tccutil reset SystemPolicyAllFiles
```

Then grant Full Disk Access again in System Settings.

## Development

// structure
tree src/main

// all files
find src -type f | while read file; do
echo "=== $file ==="
cat "$file"
done