<p align="center">
  <img src="docs/icon.png" width="110" alt="Chaos Bell icon">
</p>

<h1 align="center">The Chaos Bell Manual</h1>
<p align="center">Everything you need, in plain words. No tech degree required. 🔔</p>

---

## What is Chaos Bell?

Chaos Bell is a little AI helper that lives inside your phone.

You don't open her to use her. You're texting, posting, or writing an email like normal, and when you want help, you call her right there:

- **Type a command** at the end of your words, like `?fix`, and she fixes them in place.
- Or **highlight some text** and send it to her from the pop-up menu.

That's it. That's the whole idea.

---

## Part 1: Set her up (one time, about 5 minutes)

### Step 1. Install her

1. Open **[the latest release](https://github.com/Aunt13psychot1c/Chaos-Bell/releases/latest)** on your phone.
2. Tap the file that ends in **.apk**. It downloads.
3. Tap the download to open it, then tap **Install**.
   - If your phone says your browser **isn't allowed to install apps**, tap **Settings**, turn on **Allow from this source**, go back, and tap **Install** again.
   - If **Play Protect** warns you, tap **More details**, then **Install anyway**. Bell isn't on the Play Store, so Google just doesn't know her. That's all the warning means.

### Step 2. Get her a brain (an API key)

Bell doesn't come with AI built in. You give her a "key," which is like a password that lets her talk to an AI service using your account.

The easiest free one is **Groq**:

1. Go to **[console.groq.com](https://console.groq.com)** and make an account.
2. Tap **API Keys**, then **Create API Key**. Name it *Chaos Bell*.
3. **Copy the key right away.** Groq only shows it once.

Other choices: **Google Gemini** (free) and **Claude** (paid, the smartest). Bell has a **Get your API key** link for each one.

### Step 3. Give her the key

1. Open Chaos Bell.
2. Tap the **gear** (Settings) at the bottom. Under **Model Provider**, pick **Groq**.
3. Tap the **lock** (Keys) at the bottom. Paste your key and tap **Add Key**. Bell tests it and says **Valid key added!**
4. Back in **Settings**, under **Model**, pick **openai/gpt-oss-120b**.

### Step 4. Turn her on (the tricky part, so go slow)

Bell needs a phone permission called **Accessibility** so she can see when you type a command. Android protects this switch extra hard for apps that don't come from the Play Store, and it **hides the unlock until it has told you no once.** So this part has to happen in this exact order:

1. Open your phone's **Settings > Accessibility > Installed apps** (on some phones it's **Downloaded apps**).
2. Tap **Chaos Bell**. She'll look greyed out. You'll get a message that says **"Restricted setting"** or, on Samsung, **"App was denied access."** Tap **OK** or **Close**.
   - That "no" is on purpose. It's what unlocks the next step.
3. Now go to **Settings > Apps > Chaos Bell**.
4. Tap the **three dots ⋮** in the top right corner. Tap **Allow restricted settings**. Confirm with your PIN or fingerprint.
5. Go back to **Settings > Accessibility > Installed apps > Chaos Bell** and turn her **on**. Tap **Allow** on the warning.

You only do this once. Updates keep the permission.

> 💡 Bell's Help screen has buttons that jump you straight to these settings pages.

### Step 5. Keep her awake (Samsung and some other phones)

Some phones put apps to sleep to save battery, and that can switch Bell off without telling you. To stop that:

- **Settings > Apps > Chaos Bell > Battery** and pick **Unrestricted**.
- On Samsung, also turn off **Remove permissions if app is unused** in the same app page.

---

## Part 2: How to use her

### Way 1: Type a command 🔔

1. Type your message like normal in any app.
2. At the very end, add a command. Example:
   `i cant beleive its already tuesday?fix`
3. Wait a second. Your text swaps to:
   `I can't believe it's already Tuesday.`

Changed your mind? Type `?undo` and your old text comes back.

### Way 2: Highlight and send ✂️

1. Long-press on some words to highlight them.
2. A little menu pops up (Copy, Share, and so on). Tap the **three dots ⋮** or the **arrow** for more options.
3. Tap **Chaos Bell**.
4. Pick a command from the list.
5. When she's done, tap **Replace** to swap the text, or **Copy** to keep her answer.
6. Want to ask more? Type in the **Ask her more…** box under her answer and tap **Send**. You can keep chatting right there, and she remembers what you were talking about.

This way works even in apps where typing a command doesn't, and even when Bell's main switch is off.

---

## Part 3: Her commands

### Commands that answer you 💬
These talk back instead of changing your words.

| Type this | What she does |
|---|---|
| `?talk` | Answers your question or does what you asked |
| `?huh` | Explains the text like you're smart but new to the topic |
| `?search` or `?look` | Looks it up on the web first, then answers |

### Commands that rewrite your words ✏️

| Type this | What she does |
|---|---|
| `?fix` | Fixes spelling, grammar, and punctuation |
| `?improve` | Makes it read smoother |
| `?shorten` | Says it in fewer words |
| `?expand` | Adds more detail |
| `?formal` | Makes it sound professional |
| `?casual` | Makes it sound relaxed and friendly |
| `?emoji` | Sprinkles in emoji |
| `?human` | Makes it sound like a real person wrote it |
| `?reply` | Writes a reply to a message you got |

### Little tricks 🎩

| Type this | What she does |
|---|---|
| `?chaos` | Surprise! One random effect, taken all the way |
| `?undo` | Puts your old text back |
| `?translate:es` | Translates (change `es` to any language code, see the cheat sheet in the README) |
| `?copy` `?cut` `?paste` `?replace` | Clipboard moves |

### Make your own 🛠️
Tap the **list** icon (Commands) at the bottom, then **+**. Give it a name like `?pirate` and tell it what to do, like *Rewrite this like a pirate.*

---

## Part 4: Where she works and where she doesn't

**Works great:** texting apps, WhatsApp, Telegram, Discord, Messenger, email, most browsers, most social media.

**Typing a command doesn't work in:**
- **Password boxes.** Android hides those from every helper app on purpose, to keep your passwords safe. That's a good thing.
- **Apps with their own special editors,** like **Samsung Notes**, **Google Keep**, and some document apps. They don't let outside apps swap their text.
- **WeChat,** which blocks helper apps on purpose.

👉 In those apps, use **Way 2 (highlight and send)** instead. It usually still works.

**Doesn't show up in the highlight menu:** some apps, like the **Claude app**, make their own short menu and skip extras. Write it somewhere else, then paste.

**Banking apps:** some banks won't open while *any* accessibility helper is on. That's the bank's rule, not Bell's. Turn Bell off, use the bank, then turn her back on. Or tap **Pause** on Bell's Dashboard instead.

---

## Part 5: Her extras

- 🧠 **About you.** In Settings, tap **About you** and tell her about yourself. She uses it when she answers you, so her answers fit you better. She also remembers your last few back-and-forths for an hour, so follow-up questions make sense.
- ⏸️ **Pause.** On the Dashboard. Bell ignores every command until you un-pause. Nothing gets sent anywhere while she's paused.
- 🎨 **Colors.** Settings, under About you. Pick your look.
- 💾 **Backup.** Settings, **Export** saves your custom commands to a file. **Import** brings them back.
- 🔔 **Updates.** Bell checks for new versions and lets you know. Install the new one right over the old one. Nothing gets lost.

---

## Part 5½: Soul, Ping and reminders 🧠

Open them from **Settings > 🧠 Soul & Ping**.

- 💜 **Soul:** write who Bell is and how she acts. She reads it before every answer, in Chat, `?talk` and Ping.
- 📡 **Ping:** turn it on and Bell checks in by herself every 15 minutes to 4 hours, only during the active hours you pick. If nothing needs saying she stays quiet. If something does, she sends you a notification. Tap **📡 Ping now** to try it. **What Ping did** lists every check-in in plain words, including why one failed.
- 💸 Each Ping uses your AI key once, like one `?talk`.
- ⏰ **Reminders:** in Chat or with `?talk`, say "remind me at 3pm to take my meds." Bell sets it, and a notification pops up at 3. You can see and delete reminders on the Soul & Ping screen.
- 🔔 Ping and reminders need notifications allowed: **Settings > Apps > Chaos Bell > Notifications**.

## Part 6: Something's wrong 🛟

**Bell is greyed out in Accessibility and won't turn on.**
Do Part 1, Step 4 in the exact order. You have to get told "no" once before the unlock shows up.

**I typed a command and nothing happened.**
- Check the Dashboard says Bell is **active** and not **paused**.
- Make sure the command is at the **very end** with no space after it.
- You might be in an app that blocks typing commands (see Part 4). Try highlight and send.
- Your phone may have put her to sleep. See Part 1, Step 5.

**It says "Your API key doesn't have access to this model."**
Your Groq project is blocking that model. In the Groq console: tap your **project name** at the top, then **Manage Projects**, then **View**. Under **Allowed Models**, tap **Edit**, check **openai/gpt-oss-120b**, and tap **Save**.

**It says "Invalid API key."**
The key was copied wrong or deleted. Make a new one and add it again.

**It says "rate limited."**
You've hit the free limit for now. Wait a minute and try again, or add a second key.

**My bank app won't open.**
See the banking note in Part 4.

---

## What Bell can see (privacy)

- She only pays attention when your text **ends with a command**. Everything else you type, she ignores.
- She **never** sees password boxes.
- When you use a command, your text goes **only** to the AI service you picked (Groq, Gemini, Claude, or your own). Nowhere else.
- For answer commands, your **About you** note and your last few exchanges go along too, so she can answer in context.
- Your keys are locked up with your phone's built-in encryption.
- **No tracking. No ads. No analytics.** The code is all public, so anyone can check.

---

<p align="center">Made by <b>Aunt13psychot1c</b>, Queen of Chaos 👑<br>
Built on <a href="https://github.com/Musheer360/SwiftSlate">SwiftSlate</a> by Musheer Alam<br>
Like her? <a href="https://buymeacoffee.com/aunt13psychot1c">☕ Buy me a coffee</a></p>
