# Laghari TV roadmap

## Implemented
- [x] Android phone and Android TV launcher support
- [x] Compose navigation and dark interface
- [x] M3U EXTINF parser and HTTPS playlist URL import
- [x] HTTPS Xtream authentication
- [x] Xtream Live TV, VOD, and Series catalogue loading
- [x] Xtream category-name mapping
- [x] Series episode lookup through get_series_info
- [x] Media3 ExoPlayer playback controls
- [x] Search and persisted favorites

## Remaining before a production-ready release
- [ ] XMLTV EPG parsing and programme timeline
- [ ] Catch-up playback where supported by the authorized provider
- [ ] Subtitles and audio-track selection UI
- [x] Local M3U playlist file import
- [ ] Automatic playlist reload
- [ ] Recently watched history
- [ ] Parental controls and PIN-protected settings
- [ ] TV D-pad focus, channel zapping, and phone portrait layout polish
- [ ] Robust playback errors, retry, and stream format compatibility
- [ ] Unit/UI tests and real-device testing
- [ ] App icon, versioning, and signed release APK/AAB

A successful GitHub Actions build verifies compilation and artifact upload only; it does not replace provider-specific or physical-device testing.
