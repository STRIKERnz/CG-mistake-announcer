# CG Mistake Announcer

A RuneLite plugin that tracks and announces mistakes during Corrupted Gauntlet runs.

## Features

- **Mistake Tracking**: Detects and tracks common mistakes during CG runs
  - Prayer misses (taking damage without the correct protection prayer)
  - Avoidable damage tracking
- **Overhead Text Display**: Shows mistake messages as overhead text on your player (inspired by Sun Keris Enhancer)
- **Customizable Messages**: Configure your own mistake announcement messages
- **Chat Announcements**: Optionally send mistake messages to the chat box

## Configuration

- **Enable Mistake Tracking**: Toggle mistake tracking on/off
- **Mistake Messages**: Comma-separated list of messages to display when mistakes are detected
- **Show Overhead Text**: Display mistake messages as overhead text
- **Send to Chat**: Also send mistake messages to the chat box
- **Track Prayer Misses**: Announce when taking damage without correct prayer
- **Track Avoidable Damage**: Announce when taking any avoidable damage

## How It Works

The plugin monitors your Corrupted Gauntlet boss fights and detects:
1. Boss attack animations (melee, ranged, magic)
2. Damage taken by the player
3. Active protection prayers

When you take damage that could have been avoided with the correct prayer, the plugin announces the mistake with a random message from your configured list.

## Credits

- Overhead text functionality inspired by [Sun Keris Enhancer](https://github.com/STRIKERnz/Keris-partisan-of-the-sun-enhancer)