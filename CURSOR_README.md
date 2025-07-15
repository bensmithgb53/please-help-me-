# WebView Cursor Navigation for TV

## Overview

A cursor navigation system has been added to the WebView player to improve navigation on TV devices. This feature provides a visual cursor that can be controlled using the TV remote's D-pad, making it easier to interact with web content that doesn't have native TV navigation support.

## Features

- **Visual Cursor**: A circular cursor that appears on screen and can be moved with the D-pad
- **Smooth Movement**: Cursor moves smoothly with acceleration and deceleration
- **Auto-hide**: Cursor automatically disappears after 5 seconds of inactivity
- **Touch Simulation**: Converts D-pad center/enter presses to touch events for the WebView
- **Scroll Support**: Automatically scrolls the WebView when cursor reaches screen edges
- **Focus Management**: Integrates with existing TV navigation focus system

## How to Use

1. **Navigate to WebView**: Open any content that uses the WebView player (movies, TV shows)
2. **Use D-pad**: Use your TV remote's D-pad to move the cursor around the screen
3. **Click**: Press the center button or enter key to simulate a touch/click at the cursor position
4. **Scroll**: Move the cursor to screen edges to automatically scroll the content

## Controls

- **D-pad Up/Down/Left/Right**: Move the cursor in the corresponding direction
- **Center/Enter**: Simulate a touch/click at the current cursor position
- **Back**: Exit the WebView player (existing functionality)

## Technical Implementation

### Files Added/Modified

1. **`CursorLayout.kt`** - New custom view that handles cursor rendering and D-pad input
2. **`fragment_player_webview.xml`** - Updated to wrap WebView in CursorLayout
3. **`PlayerWebViewFragment.kt`** - Updated to initialize and manage the cursor

### Key Features

- **Responsive Design**: Cursor size and speed automatically adjust based on screen size
- **Performance Optimized**: Uses efficient drawing and event handling
- **TV Integration**: Works seamlessly with existing TV navigation patterns
- **Accessibility**: Provides visual feedback for all interactions

## Customization

The cursor appearance and behavior can be customized by modifying the constants in `CursorLayout.kt`:

- `CURSOR_DISAPPEAR_TIMEOUT`: How long before cursor auto-hides (default: 5000ms)
- `CURSOR_RADIUS`: Size of the cursor (calculated as screen width / 110)
- `CURSOR_STROKE_WIDTH`: Border thickness of the cursor
- `MAX_CURSOR_SPEED`: Maximum movement speed
- `SCROLL_START_PADDING`: Distance from edge to trigger scrolling

## Compatibility

- Works on Android TV devices with D-pad navigation
- Compatible with existing TV navigation patterns in the app
- No impact on mobile or touch-based navigation
- Integrates with existing focus management system 