#!/bin/bash
# SmartRAG v2 - Rust Build Script for Android
# Version: 2.0.0
# Date: 2025-10-26
# Author: YAKKI SMART Team
#
# NOTE: Запустится на компьютере с установленным NDK

set -e

echo "=== SmartRAG v2 Rust Build ==="
echo "NOTE: This script will work on computer with Android NDK"
echo ""

# TODO: Раскомментировать на компьютере после установки cargo-ndk
# if ! command -v cargo-ndk &> /dev/null; then
#     echo "Installing cargo-ndk..."
#     cargo install cargo-ndk
# fi

# rustup target add aarch64-linux-android
# rustup target add armv7-linux-androideabi
# rustup target add x86_64-linux-android

# cd ../src/main/rust

# echo "Building for arm64-v8a..."
# cargo ndk -t arm64-v8a -o ../jniLibs build --release

# echo "Building for armeabi-v7a..."
# cargo ndk -t armeabi-v7a -o ../jniLibs build --release

# echo "Building for x86_64..."
# cargo ndk -t x86_64 -o ../jniLibs build --release

# echo ""
# echo "=== Build Complete ==="
# ls -lh ../jniLibs/*/libsmartrag_vector.so

echo "Script is ready. Will be executed on computer with NDK."
