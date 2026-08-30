#!/usr/bin/env sh
# Creates the release signing key once. KEEP THE FILE AND PASSWORDS SAFE AND BACKED UP:
# Google Play and every future update are tied to this key. Never commit it (it is git-ignored).
set -e

# keytool needs a JDK. If none is on the PATH, use the one bundled with Android Studio.
if ! command -v keytool >/dev/null 2>&1 || ! keytool -help >/dev/null 2>&1; then
  for jbr in "/Applications/Android Studio.app/Contents/jbr/Contents/Home" "$HOME/Applications/Android Studio.app/Contents/jbr/Contents/Home"; do
    if [ -x "$jbr/bin/keytool" ]; then JAVA_HOME="$jbr"; PATH="$jbr/bin:$PATH"; export JAVA_HOME PATH; break; fi
  done
fi
if ! keytool -help >/dev/null 2>&1; then echo "No JDK found: install Android Studio or set JAVA_HOME."; exit 1; fi

KS="${1:-release.jks}"
ALIAS="${2:-coverled}"
keytool -genkeypair -v -keystore "$KS" -alias "$ALIAS" -keyalg RSA -keysize 4096 -validity 10000
cat <<MSG

Created $KS (alias $ALIAS).

Local signed builds: create keystore.properties in the repo root (git-ignored):
  storeFile=$PWD/$KS
  storePassword=...
  keyAlias=$ALIAS
  keyPassword=...

GitHub Actions: add repository secrets
  SIGNING_KEYSTORE_BASE64   = $(printf 'base64 -i %s | tr -d "\\n"' "$KS")
  SIGNING_STORE_PASSWORD, SIGNING_KEY_ALIAS ($ALIAS), SIGNING_KEY_PASSWORD
Then: git tag v1.0.0 && git push --tags
MSG
