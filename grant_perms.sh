#!/bin/bash

# Configuration
RELEASE_PKG="com.sameerasw.essentials"
DEBUG_PKG="com.sameerasw.essentials.debug"

# Adds an entry to a colon-separated secure setting, keeping what is already there
add_to_secure_list() {
    local KEY=$1
    local ENTRY=$2
    local CURRENT
    CURRENT=$(adb shell settings get secure "$KEY" | tr -d '\r')

    if [ -z "$CURRENT" ] || [ "$CURRENT" = "null" ]; then
        adb shell settings put secure "$KEY" "$ENTRY"
    elif [[ ":$CURRENT:" != *":$ENTRY:"* ]]; then
        adb shell settings put secure "$KEY" "'$CURRENT:$ENTRY'"
    fi
}

# Function to grant permissions to a specific package
grant_permissions() {
    local PKG=$1
    
    # Check if package is installed
    if ! adb shell pm list packages | grep -q "$PKG"; then
        echo "Skipping $PKG (not installed)"
        return
    fi

    echo "Granting permissions for $PKG..."

    # Runtime Permissions
    adb shell pm grant $PKG android.permission.READ_CALENDAR
    adb shell pm grant $PKG android.permission.READ_PHONE_STATE
    adb shell pm grant $PKG android.permission.POST_NOTIFICATIONS
    adb shell pm grant $PKG android.permission.ACCESS_COARSE_LOCATION
    adb shell pm grant $PKG android.permission.ACCESS_FINE_LOCATION
    adb shell pm grant $PKG android.permission.ACCESS_BACKGROUND_LOCATION
    adb shell pm grant $PKG android.permission.BLUETOOTH_CONNECT
    adb shell pm grant $PKG android.permission.BLUETOOTH_SCAN

    # Secure Settings
    adb shell pm grant $PKG android.permission.WRITE_SECURE_SETTINGS

    # DND Access (Notification Policy)
    adb shell cmd notification allow_dnd $PKG

    # AppOps (Special Permissions)
    adb shell appops set $PKG GET_USAGE_STATS allow
    adb shell appops set $PKG SYSTEM_ALERT_WINDOW allow
    adb shell appops set $PKG WRITE_SETTINGS allow
    adb shell appops set $PKG REQUEST_INSTALL_PACKAGES allow
    adb shell appops set $PKG ACCESS_NOTIFICATIONS allow

    # Enable Services (added to the existing lists, never replacing other apps' entries)
    # Notification Listener
    LISTENER="$PKG/com.sameerasw.essentials.services.NotificationListener"
    adb shell cmd notification allow_listener "$LISTENER"

    # Accessibility Service
    ACCESSIBILITY="$PKG/com.sameerasw.essentials.services.tiles.ScreenOffAccessibilityService"
    add_to_secure_list enabled_accessibility_services "$ACCESSIBILITY"
    adb shell settings put secure accessibility_enabled 1

    echo "Finished $PKG"
}

# Run for both
grant_permissions "$RELEASE_PKG"
grant_permissions "$DEBUG_PKG"

echo "All tasks complete! You might need to restart the apps for some changes to take effect."
