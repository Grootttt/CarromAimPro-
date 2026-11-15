# Carrom Aim Pro

A simple Android carrom shot-planning assistant. It provides a board canvas where you can mark a striker, target coin and pocket, draw projected lines, and preview a one-cushion bank route.

## Build
The GitHub Actions workflow builds a debug APK on every push to `main`, and can also be started manually from the Actions tab.

APK artifact: open the successful **Android APK** workflow run and download **CarromAimPro-debug**.

## Use
1. Open the app.
2. Tap the board to place the striker (S), target coin (C), and pocket (P).
3. Use **Bank shot** to preview a one-cushion route.
4. Tap **Reset** to start again.

This is a manual planning aid, not an automated game controller. Shot outcomes depend on real board geometry, friction, speed, spin and collisions.
