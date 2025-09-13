# TranslateAssist - WhatsApp Translation Overlay

A personal-use Android app that provides real-time Gujarati translation for WhatsApp messages using a floating overlay button.

## Features

- **Floating Overlay Button**: Draggable button that stays on top of WhatsApp
- **Smart Text Extraction**: Reads WhatsApp messages using accessibility services
- **Gujarati Translation**: Supports English to Gujarati and Roman Gujarati to Gujarati script
- **On-Demand Translation**: Only activates when you tap the overlay button
- **Text Selection Support**: Can translate selected text or recent messages
- **Copy Functionality**: Easy copy translated text to clipboard
- **Offline Translation**: Uses Google ML Kit for offline translation

## Installation & Setup

### Prerequisites
- Android device with API level 23+ (Android 6.0+)
- Android Studio for development
- WhatsApp installed on the device

### Build Instructions

1. **Clone the repository**:
   ```bash
   git clone <repository-url>
   cd TranslateAssist/android
   ```

2. **Open in Android Studio**:
   - Open Android Studio
   - Select "Open an existing project"
   - Navigate to the `android` folder and open it

3. **Build the project**:
   - Let Android Studio sync the project
   - Build → Make Project
   - Run → Run 'app' (or use Ctrl+R)

4. **Install on device**:
   - Connect your Android device
   - Enable USB debugging
   - Install the APK

### App Setup

1. **Open TranslateAssist app**
2. **Grant Overlay Permission**:
   - Tap "Start Overlay" 
   - You'll be redirected to system settings
   - Enable "Display over other apps" for TranslateAssist

3. **Enable Accessibility Service**:
   - Tap "Enable Accessibility Service"
   - Go to Settings → Accessibility → TranslateAssist
   - Turn on the service

4. **Start the Overlay**:
   - Return to the app
   - Tap "Start Overlay" 
   - You should see a green floating button

## Usage

### Basic Translation
1. Open WhatsApp
2. Navigate to any chat
3. Tap the floating green button
4. View translation in the popup
5. Copy translated text if needed

### Text Selection Mode
1. In WhatsApp, select specific text
2. Tap the floating button
3. Only the selected text will be translated

### Supported Languages
- **English → Gujarati**: Full translation support
- **Roman Gujarati → Gujarati Script**: Transliteration from English letters to Gujarati script
- **Gujarati → Gujarati**: Detects and displays as-is

## How It Works

### Architecture
```
WhatsApp Text → Accessibility Service → Language Detection → Translation Engine → Popup Display
```

### Key Components

1. **OverlayService**: Creates and manages the floating button
2. **TranslateAccessibilityService**: Reads text from WhatsApp using accessibility APIs
3. **TranslationEngine**: Handles language detection and translation using Google ML Kit
4. **TranslationPopup**: Displays results in an overlay popup

### Text Extraction Process
1. User taps overlay button
2. Accessibility service reads WhatsApp's UI elements
3. Extracts text from TextViews and similar components
4. Filters for message-like content
5. Passes text to translation engine

### Translation Process
1. Detect input language (English, Gujarati, or Roman Gujarati)
2. For English: Translate to Gujarati using ML Kit
3. For Roman Gujarati: Transliterate to Gujarati script
4. For Gujarati: Display as-is
5. Show result in popup with copy option

## Privacy & Security

- **Local Processing**: All translation happens on-device using Google ML Kit
- **No Data Collection**: No user data is sent to external servers
- **WhatsApp Only**: Accessibility service only monitors WhatsApp
- **On-Demand**: Only activates when overlay button is tapped
- **Personal Use**: Designed for individual use, not distribution

## Troubleshooting

### Overlay Button Not Appearing
- Check if overlay permission is granted
- Restart the overlay service from the main app
- Ensure the app hasn't been killed by battery optimization

### Text Not Being Extracted
- Verify accessibility service is enabled and running
- Check if WhatsApp has been updated (may affect text extraction)
- Try selecting text manually before tapping the button

### Translation Not Working
- Ensure internet connection for initial model download
- Check if translation models are downloaded (happens automatically)
- Verify the text is in a supported language

### Performance Issues
- The app only processes text when button is tapped
- Clear recent apps if memory is low
- Restart the accessibility service if needed

## Development Notes

### Key Files
- `MainActivity.kt`: Main app interface and permission handling
- `OverlayService.kt`: Floating button implementation
- `TranslateAccessibilityService.kt`: WhatsApp text extraction
- `TranslationEngine.kt`: Language detection and translation logic
- `TranslationPopup.kt`: Result display popup

### Permissions Used
- `SYSTEM_ALERT_WINDOW`: For overlay functionality
- `BIND_ACCESSIBILITY_SERVICE`: For reading WhatsApp text
- `INTERNET`: For downloading translation models
- `FOREGROUND_SERVICE`: For persistent overlay service

### Dependencies
- Google ML Kit Translation: Offline translation
- Google ML Kit Language ID: Language detection
- AndroidX libraries: Modern Android development

## Limitations

- **WhatsApp Only**: Currently designed specifically for WhatsApp
- **Android Only**: Not available for iOS
- **Personal Use**: Not intended for Play Store distribution
- **Recent Messages**: Accessibility service can only read visible text
- **Language Support**: Currently focuses on English-Gujarati translation

## Future Enhancements

Potential improvements (not implemented):
- Support for other messaging apps
- More language pairs
- Better Roman Gujarati detection
- Text size adjustment
- Theme customization
- Translation history

## Legal Disclaimer

This app is for personal use only. It uses Android's accessibility services to read text from WhatsApp for translation purposes. Users are responsible for complying with all applicable terms of service and local laws. The app does not modify WhatsApp or interfere with its normal operation.

## Support

Since this is a personal project, support is limited. However, you can:
- Check the troubleshooting section above
- Review the code for understanding the implementation
- Modify the code to suit your specific needs

---

**Note**: This app requires careful setup of Android permissions and may need adjustments based on different Android versions or WhatsApp updates.