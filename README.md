# Ascend — Real-Life Solo Leveling (MVP)

Open this folder in Android Studio (Koala or newer), let Gradle sync, then Build > Build APK(s).
Requires JDK 17 and Android SDK 34. Works fully offline.

Screens: Splash, Onboarding, System/Home, Quests (list, details, create/edit), Stats, Rise (progression + charts),
Achievements, History, Focus Timer, Profile, Settings, Reset, Level-Up/Quest-Complete overlay, hidden Console and Developer tools.

Hidden features
- Console: tap the "SYSTEM" title on Home 3 times (read-only commands, /help).
- Developer tools: tap the version text in Settings 7 times; then Settings > Developer. Test mode runs on a temporary copy and restores your real save when turned off.

Structure: engine/ (XP, rank, quests, streaks, achievements, analytics), data/ (local JSON store with backup),
GameViewModel (all state changes), ui/ (theme, components, charts, screens), notify/ (reminders, optional sound).
