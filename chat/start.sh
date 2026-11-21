#!/data/data/com.termux/files/usr/bin/bash
# Run with bash in Termux's private $HOME, never in shared Downloads/storage.
set -eu
umask 077
cd "$(dirname "$0")"
if ! node -e 'process.exit(Number(process.versions.node.split(".")[0]) >= 22 ? 0 : 1)' 2>/dev/null; then
  echo 'Install Node 22 or newer in Termux: pkg install nodejs-lts'; exit 1
fi
# Install from the checked-in lockfile. Never install dependencies while holding an API key.
if ! cmp -s package-lock.json node_modules/.mimi-install-lock; then
  npm ci --omit=optional --no-audit --no-fund && cp package-lock.json node_modules/.mimi-install-lock || exit 1
fi
printf '\nMimi · Linked chat test (unofficial client). Only pair a test account you control.\n'
read -rp 'Your WhatsApp Business number, with country code (e.g. +91...): ' MIMI_OWNER
read -rp 'ONE consenting tester’s WhatsApp number, with country code: ' MIMI_TESTER
read -rp 'Pavit is [busy (default) / sleeping / driving]: ' MIMI_MODE
MIMI_MODE=${MIMI_MODE:-busy}
case "$MIMI_MODE" in busy|sleeping|driving) ;; *) echo 'Choose busy, sleeping or driving.'; exit 1;; esac
read -rp 'Provider [1 = message-taking, no key (default), 2 = Gemini, 3 = Groq]: ' choice
case "$choice" in
  ''|1) MIMI_PROVIDER=test; MIMI_KEY='' ;;
  2) MIMI_PROVIDER=gemini ;;
  3) MIMI_PROVIDER=groq ;;
  *) echo 'Choose 1, 2 or 3.'; exit 1 ;;
esac
if [[ $MIMI_PROVIDER != test ]]; then
  read -rsp "Paste $MIMI_PROVIDER API key (hidden, not saved): " MIMI_KEY; echo
  [[ -n $MIMI_KEY ]] || { echo 'Key is empty'; exit 1; }
fi
export MIMI_MODE MIMI_OWNER MIMI_TESTER MIMI_PROVIDER MIMI_KEY
exec node main.mjs
