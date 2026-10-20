<p align="center">
  <img src="docs/icon.png" width="160" alt="Chaos Bell icon: a black chaos star with a bell in its center ring">
</p>

<h1 align="center">Chaos Bell</h1>
<p align="center"><code>Ch40s B311</code></p>
<p align="center">
  <a href="https://github.com/Aunt13psychot1c/Chaos-Bell/actions/workflows/build-my-apk.yml"><img src="https://github.com/Aunt13psychot1c/Chaos-Bell/actions/workflows/build-my-apk.yml/badge.svg" alt="Build My APK"></a>
</p>
<p align="center"><b>AI in a box for Android. Type a command anywhere you type, and Bell answers.</b></p>

---

## What she is

Chaos Bell is a small AI assistant that lives inside your phone's text boxes. Texts, Discord, notes, browser, wherever. Type your words, add a command like `?fix` or `?talk` on the end, and Bell rewrites it or answers it right there in place.

No keyboard to switch to. No app to open. Just type and ring the bell. 🔔

## Commands

**Answer commands** (Bell talks back instead of rewriting)

| Command | What it does |
|---|---|
| `?talk` | Answers your question or does the task, straight up |
| `?huh` | Explains the text like you're smart but new to the topic, ending with a STILL FUZZY section |
| `?search` | Searches the web first, then answers |
| `?look` | Same as `?search` |

**Rewrite commands** (yours to edit or delete)

| Command | What it does |
|---|---|
| `?fix` | Fixes grammar, spelling, punctuation |
| `?improve` | Better clarity and flow |
| `?shorten` | Says it in fewer words |
| `?expand` | Adds detail without making stuff up |
| `?formal` / `?casual` | Changes the tone |
| `?emoji` | Sprinkles in emoji |
| `?human` | Makes it sound like a person wrote it |
| `?reply` | Writes a reply to the message |

**Built-in tricks**

| Command | What it does |
|---|---|
| `?chaos` | Surprise me. One random effect, taken all the way |
| `?undo` | Puts back whatever was there before |
| `?translate:xx` | Translates, like `?translate:es` or `?translate:fr` |
| `?copy` `?cut` `?paste` `?replace` | Clipboard moves |

You can make your own commands too, and change the `?` to another symbol if you like.

## What makes her Bell

- **Claude gets her own slot.** Add an Anthropic key and Bell talks to Claude directly. Gemini, Groq, and any OpenAI-compatible service work too.
- **Web search on every provider.** `?search` and `?look` always check the web first.
- **A small memory.** Write an "About you" note in Settings, and Bell remembers your last few exchanges for an hour so a follow-up makes sense.
- **Pick her colors.** Choose your own color layout from swatches.
- **Pause button.** One tap on the Dashboard and Bell ignores every command until you resume. Your keys stay put.
- **Each provider keeps its own keys,** sorted into their own drawers.
- **No analytics.** Your keys are stored encrypted on your phone.

## Get her on your phone

No computer, no zip files. Just your phone.

**1. Download**
Open **[the latest release](https://github.com/Aunt13psychot1c/Chaos-Bell/releases/latest)** and tap the file that ends in **.apk**.

**2. Install**
Open the downloaded file. Android will say your browser isn't allowed to install apps yet. Tap **Settings**, switch on **Allow from this source**, go back, and tap **Install**.
If Play Protect pops up a warning, tap **More details**, then **Install anyway**. Bell isn't from the Play Store yet, so Google just doesn't know her.

**3. Let her work**
Bell uses Android's accessibility service to see the command you type. Because she didn't come from a store, Android locks that switch the first time. To unlock it:
- Go to **Settings > Apps > Chaos Bell**
- Tap the **⋮** in the top corner, then **Allow restricted settings**
- Now go to **Settings > Accessibility > Chaos Bell** and switch her on

**4. Ring the bell**
Open Chaos Bell, add an API key, then type something anywhere, add `?fix` or `?talk` on the end, and watch. 🔔

**Updating later:** grab the newest .apk from the same Releases page and install it right over the old one. Your keys, commands, and settings stay put.

## Building from source

```bash
git clone https://github.com/aunt13psychot1c/chaos-bell.git
cd chaos-bell
./gradlew assemblePreview
# APK lands in app/build/outputs/apk/preview/
```

Needs JDK 21 and the Android SDK. The `preview` build is signed with a throwaway debug key and installs as a separate "Chaos Bell Preview" app, handy for testing. Real releases come from the **Release Chaos Bell** workflow.

## Credits

Chaos Bell is built on [SwiftSlate](https://github.com/Musheer360/SwiftSlate) by Musheer Alam. Huge thanks to him for the foundation she grew out of. 🌹

Made by **Aunt13Psychot1c**, Queen of Chaos.

## License

MIT. See [LICENSE](LICENSE).
