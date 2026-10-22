# 💗 How Bell's Heart and Ping work

A plain-English guide to the 4.1.0 update: what was built, where it lives, and what each piece of code does. You don't need to be a programmer to follow it.

---

## The big idea

Bell already had a **short-term memory**: your last 6 back-and-forths, kept for an hour. This update gives her two new things:

1. **💗 Heart:** a long-term memory. Little facts she keeps about you for good, like "has a plush sloth named Erin the Slew."
2. **📡 Ping, the attention check:** every few messages, Bell stops herself and checks: *am I really listening to what they mean?* Then she decides whether anything is worth keeping in her Heart.

The old background timer (where Bell messages you on her own) is now called **🔔 Check-ins**, so it doesn't get mixed up with Ping.

---

## How one message flows through Bell

```
You type:  "my sloth Erin knows too much?talk"
     │
     ▼
1. Bell counts the message.            (BellMemory.nextTurnIsPing)
   Is this the 3rd one? Then it's a Ping message.
     │
     ▼
2. Bell builds the note she sends the AI:  (CommandRunner)
     - her Soul (who she is)
     - About you
     - her Heart (everything she's kept)
     - the last hour of chat
     - your question
     - on a Ping message: the attention-check instructions
     │
     ▼
3. The AI answers. On a Ping message it may add hidden lines like:
     [[remember: has a plush sloth named Erin the Slew]]
     │
     ▼
4. Bell pulls those lines out of the answer   (Keepsakes.extract)
   and saves each one in her Heart.            (BellMemory.keep)
     │
     ▼
You see:   just her answer. The [[remember]] lines never show.
```

---

## The files, one at a time

### 1. `manager/BellMemory.kt`: where the Heart is stored

This file already held the Soul, About you, and short-term chat. It now also holds the Heart.

**A keepsake is just a time and some text:**
```kotlin
data class Keepsake(val time: Long, val text: String)
```
`time` is when she kept it. It also works as its ID, so the 🗑️ button knows which one to delete.

**Keeping something:**
```kotlin
fun keep(text: String): Boolean {
    val clean = text.trim().replace(Regex("\\s+"), " ").take(MAX_KEEPSAKE_CHARS)
    if (clean.isEmpty()) return false
    val heart = heart()
    if (heart.any { it.text.equals(clean, ignoreCase = true) }) return false
    ...
    saveHeart((heart + Keepsake(time, clean)).takeLast(MAX_KEEPSAKES))
    return true
}
```
In plain words:
- **`trim()`** cuts spaces off the ends. **`replace(Regex("\\s+"), " ")`** squishes extra spaces down to one. **`take(200)`** cuts anything longer than 200 characters.
- If it's empty, she keeps nothing.
- **`equals(..., ignoreCase = true)`**: if she already has the same thing (even with different capital letters), she skips it. No repeats.
- **`takeLast(60)`**: she holds 60 at most. When she's full, the oldest one makes room.

**Counting messages for Ping:**
```kotlin
fun nextTurnIsPing(): Boolean {
    val every = pingEvery
    if (every <= 0) return false
    val turn = prefs.getInt(KEY_TURNS, 0) + 1
    prefs.edit().putInt(KEY_TURNS, turn % every).apply()
    return turn % every == 0
}
```
- `pingEvery` is the slider on the screen (0 = off).
- `%` means "remainder after dividing." With every = 3, the count goes 1, 2, **0 (Ping!)**, 1, 2, **0 (Ping!)**...
- `prefs` is the phone's little settings storage, so the count survives closing the app.

**The Heart goes into every answer:**
```kotlin
if (heart.isNotEmpty()) {
    sb.append("What you keep in your heart about the user (things you chose to remember):\n")
    heart.forEach { sb.append("- ").append(it.text).append('\n') }
}
```
`sb` is a "StringBuilder," a box you keep adding text to. This writes each keepsake as a `- bullet` in the note Bell sends the AI.

**Backups:** `export()` and `import()` now save and restore the Soul and Heart too.

### 2. `ping/Keepsakes.kt` (new): the attention check

**The instruction added on a Ping message:**
> PING (your attention check): before you answer, stop and check yourself. Are you really paying attention to what the user is saying underneath the words... If yes, add one line per thing at the very end of your reply, in exactly this form: [[remember: a short fact about the user]]...

**Pulling out the hidden lines:**
```kotlin
private val TAG = Regex("""\[\[\s*remember\s*:\s*(.+?)\s*]]""", RegexOption.IGNORE_CASE)
```
That's a **regex**, a search pattern. Piece by piece:
- `\[\[` means two `[` characters (the backslash means "a real bracket, not a special symbol")
- `\s*` means "any number of spaces, even none"
- `remember` is the word itself
- `(.+?)` means "grab the text in here." That's the fact.
- `]]` is the closing brackets
- `IGNORE_CASE` means `Remember` and `REMEMBER` work too

`extract()` finds every match, collects the facts, and removes those lines so you only see the real answer.

### 3. `service/CommandRunner.kt`: where it all connects

This is the part that talks to the AI. Two small additions:

**Before asking**, it checks whether this is a Ping message and adds the instruction:
```kotlin
val attentionPing = memory != null && remember && memory.nextTurnIsPing()
...
if (attentionPing) instruction += "\n\n" + Keepsakes.ATTENTION_PING
```
`remember` is false for background check-ins, so only **real conversation** counts toward Ping.

**After the answer comes back**, it strips the reminders, then the keepsakes, and saves them:
```kotlin
val (clean, keepsakes) = Keepsakes.extract(noReminders)
keepsakes.forEach { memory?.keep(it) }
```

### 4. `ui/PingScreen.kt`: the screen you see

In **Settings > 🧠 Soul & Ping**, there's a new **💗 Heart** section:
- the **Attention Ping slider:** off, or every 1, 2, 3, 5 or 10 messages
- every keepsake, with the date it was kept and a 🗑️
- **"Tell her something to keep"** plus **💗 Keep it**, to add your own
- **Forget all**, which asks you to tap twice

### 5. Tests: how we know it works

Tests are little programs that check the real code automatically, every time GitHub builds Bell.
- `KeepsakesTest.kt`: hidden lines get pulled out, normal answers stay the same, empty ones are thrown away
- `BellMemoryTest.kt`: no repeats, the 60 limit, deleting one or all, the Ping counter landing on every 3rd message, and backups keeping the Soul and Heart

All of them passed ✅

---

## Try it yourself

1. Install the 4.1.0 test copy, or the release when it's out.
2. **Settings > 🧠 Soul & Ping > 💗 Heart:** set Attention Ping to **every message** while testing.
3. Chat with Bell and tell her a few real things about yourself.
4. Go back to 💗 Heart. Her keepsakes should be there.
5. Set it back to every 3 messages for normal use.

---

## Little dictionary

- **Kotlin:** the programming language Bell is written in.
- **Function** (`fun`): a named set of steps, like a recipe.
- **`val`:** a box holding a value that doesn't change.
- **`var`:** a box whose value can change.
- **`return`:** hands the answer back to whoever asked.
- **`if`:** "only do this when..."
- **List:** a row of things in order.
- **Regex:** a search pattern for finding text.
- **Prompt:** the note Bell sends the AI with your question.
- **prefs (SharedPreferences):** Android's small settings storage on your phone.
- **Commit:** a saved snapshot of the code on GitHub.
- **Pull request:** a proposed change, waiting for you to approve (merge) it.
- **Release:** a finished APK people can download.
