# How to get your installable APK — no computer skills needed

GitHub's servers will build the app for you, free. You only click buttons.
Total time: about 20 minutes, mostly waiting. You can do every step on your phone.

---

## STEP 1 — Make a free GitHub account (5 min, skip if you have one)
1. Go to https://github.com/signup
2. Enter an email, a password, a username. Verify the email. That's it. Free, no card.

---

## STEP 2 — Create an empty project ("repository")
1. Go to https://github.com/new
2. **Repository name:** type  vocab-registry
3. Leave everything else as default. Make sure it's set to **Public** (free builds).
4. Click the green **Create repository** button.

You now land on a page that says "…quick setup". Keep this tab open.

---

## STEP 3 — Upload the project files
The downloaded file is a ZIP. GitHub needs the files *inside* it, not the zip itself.

**On a computer (easiest):**
1. Unzip `VocabRegistry-android.zip`. You get a folder `VocabRegistry`.
2. **Open that folder** — you should see `gradlew`, `settings.gradle.kts`, `app`, etc.
   Select ALL of these items (Ctrl+A / Cmd+A).
3. On your GitHub repo page, click **uploading an existing file** (a link in the quick-setup text).
4. Drag all the selected items into the browser window. Wait for them to finish.
5. Scroll down, click the green **Commit changes** button.

**On a phone:** use an app like "ZArchiver" (free) to unzip, then on github.com tap
**Add file → Upload files** and pick the files. (A computer is genuinely easier here —
a library, school lab, or a friend's laptop works for just this one step.)

> IMPORTANT: the files like `gradlew` and the `app` folder must sit at the **top level**
> of the repository, NOT inside an extra `VocabRegistry` folder. If you accidentally get
> a nested folder, that's the usual cause of a failed build.

---

## STEP 4 — Let GitHub build it
1. As soon as you commit, GitHub starts building automatically.
2. Click the **Actions** tab (top of your repo page).
3. You'll see a job called "Build APK" with a spinning yellow dot. Wait ~5–8 minutes.
4. When it turns into a **green check ✓**, it's done.
   (If it's a red ✗, see Troubleshooting below — then tell me what the red step said.)

---

## STEP 5 — Download your APK
1. Click on the finished build (the line with the green check).
2. Scroll to the bottom to a section called **Artifacts**.
3. Click **VocabRegistry-apk** — it downloads a zip containing `app-debug.apk`.
4. Unzip it. `app-debug.apk` is your app.

---

## STEP 6 — Install on your Android 11 phone
1. Put `app-debug.apk` on your phone (download it directly on the phone, or transfer it).
2. Tap it. Android will say "install blocked" or ask about "unknown sources".
3. Tap **Settings** on that popup → enable **Allow from this source** → back → **Install**.
   (This is normal for any app not from the Play Store. It's your own app; it's safe.)
4. Open it. In **Settings** inside the app you can leave the API key blank —
   it uses free self-grading. Done.

---

## Troubleshooting
- **Red ✗ on the build:** click the failed job, click the red step, copy the last ~15 lines
  of red text, and paste them to me. I'll give you the one-line fix.
- **"too many files" / upload stalls:** upload in two batches — first the loose files
  (`gradlew`, `*.kts`, `*.properties`, the `.github` and `gradle` folders), commit,
  then **Add file → Upload files** again for the `app` folder.
- **Build succeeded but app crashes on open:** tell me — generated code occasionally needs
  a tiny import fix, and I'll patch the source for you to re-upload.

You do not need to understand any of this. Follow the clicks; if anything turns red, send me
the red words. That's the whole job.
