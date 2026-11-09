# Laghari TV

An Android IPTV player for Android phones and Android TV. Use only playlists, streams, and credentials that you own or are authorized to access.

## Implemented
- Android phone and Android TV launcher support
- Dark Compose interface with Live TV, Movies, Series, Favorites, and Settings sections
- HTTPS M3U URL import and EXTINF parsing
- HTTPS Xtream Codes login
- Xtream Live TV, VOD, and Series catalogue loading with category labels
- Series episode retrieval through the provider's series-info API
- Media3 ExoPlayer playback screen with play, pause, and close controls
- Search and locally persisted favorites

## Current limitations
- EPG/XMLTV timeline is not implemented yet
- Catch-up, downloads, parental PIN, advanced track selection, and polished TV remote navigation are not implemented
- Local-file playlist import and automatic playlist reload are not implemented
- A successful CI build confirms compilation, not playback against every IPTV provider or real-device compatibility
- Current CI output is a debug APK, not a signed production release

## Build
GitHub Actions builds the debug APK on pushes to the main branch. Open the Actions run and download the LaghariTV-debug-apk artifact.
