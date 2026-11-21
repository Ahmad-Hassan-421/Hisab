# Signup password visibility

Signup previously had a visibility control on the main password only. Its keyboard Next action also dismissed the keyboard. Both fields now use the same icon button with distinct accessibility labels and independent visibility state. Both start masked when entering signup, and the keyboard Next action focuses Confirm Password.

Instrumentation on the C51 passed. It checked that each field can reveal and hide independently, the confirmation cursor stays at its selected position when visibility changes, typing at that position preserves the original value, keyboard Next focuses confirmation, and keyboard Done reports a mismatch. The Android build, JVM tests and lint passed. The emulator attempt hit an Android startup timeout before instrumentation could run.
