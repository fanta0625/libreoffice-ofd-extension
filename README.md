# LibreOffice OFD Extension

A LibreOffice extension that enables opening OFD (Open Fixed-layout Document) files by converting them to SVG for viewing in LibreOffice Draw.

## Overview

This extension enables OFD support by converting OFD → SVG. Key features:
- Automatic font mapping (Windows fonts → Linux open-source fonts)
- Font missing warnings
- Read-only mode
- Chinese language support

## Project Structure

```
src/main/java/org/loongoffice/ofd/
├── reader/          # Core filter (OFDImportFilter)
├── converter/       # OFD → SVG converter
├── document/        # Document handling
├── font/            # Font checking utilities
├── util/            # Utilities (MessageBox, TempFileManager, etc.)
└── config/          # Font mapping configuration
```

## Usage

### Installation

**Method 1: GUI Installation (Recommended)**

1. Build OXT file:
   ```bash
   mvn clean package -DskipTests
   ```

2. Install in LibreOffice:
   - Open LibreOffice
   - Menu: **Tools** → **Extension Manager**
   - Click **Add** button
   - Select `target/ofd-reader.oxt` file
   - Restart LibreOffice

**Method 2: Command Line Installation**

```bash
/usr/bin/unopkg add target/ofd-reader.oxt
```

### Opening OFD Files

After installation, simply open `.ofd` files in LibreOffice.

### Integrating into LibreOffice Build

To bundle this extension with LibreOffice (built-in, cannot be removed by users):

```bash
cd /path/to/libreoffice/source

# Create extension directory
mkdir -p external/ofdreader

# Copy OXT file
cp /path/to/ofd-reader.oxt external/ofdreader/

# Create Module_ofdreader.mk
cat > external/ofdreader/Module_ofdreader.mk << 'EOF'
$(eval $(call gb_Module_Module,ofdreader))
$(eval $(call gb_Module_add_targets,ofdreader,\
    ExtensionPackage_ofdreader \
))
EOF

# Create ExtensionPackage_ofdreader.mk
cat > external/ofdreader/ExtensionPackage_ofdreader.mk << 'EOF'
$(eval $(call gb_ExtensionPackage_ExtensionPackage,ofdreader,$(SRCDIR)/external/ofdreader/ofd-reader.oxt))
EOF

# Add to configure.ac (alongside other libo_CHECK_EXTENSION lines)
# libo_CHECK_EXTENSION([OFDReader],[OFDREADER],[ofdreader],[ofdreader],[ofd-reader.oxt])
```

Then rebuild LibreOffice. The extension will be installed in `share/extensions/` and cannot be removed by users.

### Font Mapping Configuration

Font mapping file locations (by priority):
1. `~/.libreffice/font-mappings.properties`
2. `/etc/libreffice/font-mappings.properties`
3. Default configuration in JAR

## Documentation

- [CLAUDE.md](CLAUDE.md) - Development notes
- [docs/](docs/) - Technical documentation
- [CHANGELOG.md](CHANGELOG.md) - Detailed changelog

## Changelog

### [1.0.0] - 2026-03-16

Initial release. Enables LibreOffice Draw to open OFD files.

## Tech Stack

- Java 17
- ofdrw 2.3.8 (OFD processing library)
- LibreOffice UNO 24.8.4
- Maven

## License

MPL-2.0
