# LogParser

JavaFX application for parsing and analyzing log files (OX and Symfony formats) with local and remote (SFTP) support.

## Build

```bash
make package
```

Creates `~/Downloads/LogParser-1.0.dmg`

## Development

```bash
mvn javafx:run
```

## Configuration

- **Profiles:** `~/Library/Application Support/LogParser/profiles.json.enc`
- **Logs:** `~/Library/Logs/LogParser/`

## Requirements

- Java 21
- JavaFX jmods 21.0.8 (in `javafx-jmods-21.0.8/`)
