#!/bin/bash
set -e

export JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
export GRADLE_USER_HOME='D:\hub\Aries-AI\.gradle'
export ANDROID_USER_HOME='D:\hub\Aries-AI\.android'
export ANDROID_SDK_ROOT='C:\Users\xuany\AppData\Local\Android\Sdk'

cd 'D:\hub\Aries-AI'
./gradlew assembleDebug --dry-run
