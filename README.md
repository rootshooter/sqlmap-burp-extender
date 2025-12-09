# Burp Suite SQLMap Integration Extension

A Burp Suite Professional extension that integrates SQLMap for automated SQL injection testing. When vulnerabilities are found, they are automatically reported to Burp's Scanner Issues.

## Features

- **Context Menu Integration**: Right-click on any request to scan with SQLMap
- **Automatic Path Detection**: Finds SQLMap automatically from system PATH or common locations
- **Flexible Path Configuration**: Use system PATH or specify custom SQLMap location
- **Persistent Settings**: Configuration is saved between Burp sessions
- **Scanner Integration**: Findings are added to Burp's Scanner Issues tab
- **GUI Configuration**: Full control over SQLMap parameters
- **Concurrent Scanning**: Multiple scans can run simultaneously
- **Results Table**: Track all findings in the extension tab

## Requirements

- Burp Suite Professional (required for Scanner API)
- Java 8 or higher
- SQLMap installed and accessible
- Python (if using sqlmap.py)

## SQLMap Installation

### Linux/Mac (via package manager)
```bash
# Kali Linux / Debian / Ubuntu
sudo apt-get install sqlmap

# macOS (Homebrew)
brew install sqlmap
```

### Manual Installation (All platforms)
```bash
# Clone from GitHub
git clone --depth 1 https://github.com/sqlmapproject/sqlmap.git sqlmap-dev

# SQLMap will be at: ./sqlmap-dev/sqlmap.py
```

### Windows
```powershell
# Download from GitHub
git clone --depth 1 https://github.com/sqlmapproject/sqlmap.git C:\sqlmap

# Or download ZIP from: https://github.com/sqlmapproject/sqlmap/archive/master.zip
# SQLMap will be at: C:\sqlmap\sqlmap.py
```

## Building the Extension

### Prerequisites
1. Download Burp Suite API JAR:
   - Open Burp Suite
   - Go to Extender > APIs
   - Click "Save interface files"
   - Save to your working directory

2. Ensure you have `javac` (Java compiler) available:
```bash
javac -version
```

### Compilation

#### Linux/Mac:
```bash
# Compile the extension
javac -cp burpsuite_pro.jar:. SqlmapExtension.java

# Create JAR file
jar -cf sqlmap-extension.jar burp/*.class

# Or use the provided build script
chmod +x build.sh
./build.sh
```

#### Windows:
```powershell
# Compile the extension
javac -cp "burpsuite_pro.jar;." SqlmapExtension.java

# Create JAR file
jar -cf sqlmap-extension.jar burp/*.class

# Or use the provided build script
build.bat
```

## Installation in Burp Suite

1. Open Burp Suite Professional
2. Go to **Extender** > **Extensions**
3. Click **Add**
4. Set Extension type to **Java**
5. Click **Select file** and choose `sqlmap-extension.jar`
6. Click **Next**
7. Check the **Output** tab for any errors
8. If successful, you'll see "SQLMap Extension loaded successfully"

## Configuration

### Setting SQLMap Path

The extension will try to auto-detect SQLMap on first load. You can also configure it manually:

1. Go to the **SQLMap** tab in Burp
2. Configure the SQLMap path using one of these methods:

   **Option 1: Auto-detect**
   - Click the **Auto-detect** button
   - The extension will search common locations

   **Option 2: Browse**
   - Click the **Browse** button
   - Navigate to your SQLMap executable/script
   - Select `sqlmap` or `sqlmap.py`

   **Option 3: Manual Entry**
   - Enter the path directly in the text field
   - Examples:
     - `sqlmap` (if in system PATH)
     - `/usr/bin/sqlmap`
     - `/home/user/sqlmap-dev/sqlmap.py`
     - `C:\sqlmap\sqlmap.py`

3. Click **Test** to verify the path works
4. Click **Save Settings** to persist configuration

### SQLMap Options

Configure SQLMap behavior:

- **Risk Level**: 1 (safe) to 3 (aggressive)
- **Test Level**: 1 (minimal) to 5 (extensive)
- **Verbose Mode**: Show detailed SQLMap output
- **Batch Mode**: Never ask for user input (recommended)
- **Random User-Agent**: Randomize User-Agent header
- **Additional Arguments**: Add custom SQLMap flags

Example additional arguments:
```
--threads=5 --dbms=MySQL --technique=BEUST
```

## Usage

### Basic Scan

1. In Burp, navigate to any tool (Proxy History, Target, Repeater, etc.)
2. Right-click on a request
3. Select **"Scan with SQLMap"**
4. The scan runs in the background
5. Check these locations for results:
   - **SQLMap tab** > Results table
   - **Target** > **Issues** tab (if injection found)
   - Burp's main **Output** tab (for verbose logs)

### Multiple Requests

You can select multiple requests and scan them all at once:
1. Hold Ctrl/Cmd and select multiple requests
2. Right-click > **"Scan with SQLMap"**
3. Each scan runs concurrently

### Viewing Results

**In the SQLMap Tab:**
- View all findings in the results table
- See: Time, URL, Parameter, Injection Type, Payload, DBMS

**In Scanner Issues:**
- Navigate to Target > Issue activity or Dashboard
- Look for **"SQL Injection (SQLMap)"** issues
- Click to view:
  - Detailed injection information
  - Impact and remediation advice
  - Full SQLMap output
  - Original HTTP request/response

## Common SQLMap Paths

The extension auto-detects these locations:

**Linux/Mac:**
- `/usr/bin/sqlmap`
- `/usr/local/bin/sqlmap`
- `~/sqlmap-dev/sqlmap.py`
- `~/tools/sqlmap-dev/sqlmap.py`

**Windows:**
- `C:\sqlmap\sqlmap.py`
- `C:\tools\sqlmap\sqlmap.py`

**System PATH:**
- `sqlmap` (if added to PATH)
- `sqlmap.py` (if added to PATH)

## Troubleshooting

### "SQLMap path is not configured"
- Configure the path in the SQLMap tab
- Click Test to verify it works
- Save settings

### "Error executing SQLMap"
Possible causes:
1. **Incorrect path**: Use Browse or Auto-detect to find SQLMap
2. **Python not installed**: SQLMap requires Python
3. **Permissions**: Ensure sqlmap.py is executable (`chmod +x sqlmap.py`)
4. **Missing Python**: For sqlmap.py, run `python3 sqlmap.py --version` to test

### "SQLMap not found or error occurred"
- Verify SQLMap is installed: `sqlmap --version`
- Check Python is available: `python3 --version`
- Try the full path instead of just `sqlmap`

### Extension won't load
1. Check you're using Burp Suite **Professional** (not Community)
2. Verify the JAR was built correctly
3. Check Burp's error output in Extender > Extensions
4. Ensure you have the correct Burp API version

### No issues appearing in Scanner
- SQLMap must find an actual vulnerability
- Check the SQLMap tab for results
- Verify the request has parameters to test
- Increase Risk/Test levels for more thorough scanning

## Performance Tips

1. **Adjust thread count**: Add `--threads=5` to additional arguments
2. **Limit techniques**: Use `--technique=B` for faster boolean-based only
3. **Target specific parameters**: Add `-p parameter_name`
4. **Skip static parameters**: SQLMap automatically skips non-injectable params

## Security Notes

- Only use on applications you have permission to test
- SQLMap can generate significant traffic
- Some tests may trigger WAF/IDS alerts
- High risk levels may cause application instability
- Always use batch mode in production testing

## Advanced Usage

### Custom SQLMap Configuration

Add to Additional Arguments field:

```bash
# MySQL-specific testing with 5 threads
--dbms=MySQL --threads=5

# Only test specific parameter
-p username

# Use Tor for anonymity
--tor --tor-type=SOCKS5

# Dump specific database
--dump -D database_name

# Custom injection technique
--technique=BEUST --time-sec=5

# Skip confirmation prompts
--batch --answers="crack=N,dict=N"
```

### Testing Specific Scenarios

**POST Data:**
- SQLMap automatically handles POST parameters from the request

**JSON APIs:**
- Works automatically with JSON content-type requests

**Custom Headers:**
- Add header testing: `-H "X-Custom-Header: *"`

**Cookies:**
- SQLMap tests cookies automatically from request

## Extension Settings Location

Settings are stored in Burp's extension storage:
- Linux/Mac: `~/.java/.userPrefs/burp/`
- Windows: Registry under `HKEY_CURRENT_USER\Software\JavaSoft\Prefs\burp`

## Uninstallation

1. Go to Extender > Extensions
2. Select "SQLMap Scanner Integration"
3. Click "Remove"

## Changelog

### Version 1.0
- Initial release
- Context menu integration
- Scanner issue reporting
- Auto-detection of SQLMap path
- Persistent configuration
- Results tracking table
- Concurrent scan support

## License

This extension is provided as-is for educational and authorized security testing purposes only.

## Support

For issues or questions:
1. Check the Burp output tab for error messages
2. Verify SQLMap works standalone: `sqlmap --version`
3. Test with a simple request first
4. Check SQLMap documentation: https://github.com/sqlmapproject/sqlmap/wiki

## Credits

- SQLMap: https://github.com/sqlmapproject/sqlmap
- Burp Suite: https://portswigger.net/burp
