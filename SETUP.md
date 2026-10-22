# VpnClient — Setup Guide (Step 1 & 2 done)

This is a minimal, buildable Android project skeleton. It proves the build
pipeline works before any VPN logic is added. Next steps (VpnService, SSH
tunnel, config import/export, hotspot) build on top of this.

## Two ways to actually get an APK — pick one

### Option A: GitHub Actions (recommended — free, reliable, no Termux SDK pain)

Building a full Android project *inside Termux* means fighting SDK tooling
that was never built for ARM/bionic. The free, much more reliable path:
let GitHub's own servers (which have a real, full Android SDK) build it for
you, for free, every time you push code from your phone.

1. Create a free GitHub account if you don't have one.
2. Create a new **public** repo (public repos get unlimited free Actions
   minutes; private repos get a smaller free quota, still enough for this).
3. From Termux:
   ```
   pkg install git
   cd vpnclient
   git init
   git remote add origin https://github.com/<you>/vpnclient.git
   git add .
   git commit -m "Step 1-2: project skeleton"
   git branch -M main
   git push -u origin main
   ```
4. Go to your repo's **Actions** tab on github.com (or the GitHub mobile app)
   — a workflow run starts automatically. When it finishes (a couple
   minutes), open the run and download the `vpnclient-debug-apk` artifact.
   That's your installable APK.
5. Every time you edit code and `git push`, a new APK builds automatically.

This means your actual coding loop in Termux is just: edit files with
`nano`/`vim`/Termux's text editor, `git commit`, `git push`, download APK.
You never need Android SDK tools installed locally at all.

### Option B: Build locally in Termux (optional, harder, not required)

Possible but genuinely painful: Termux's CPU architecture (ARM, bionic libc)
doesn't match the official Android SDK command-line tools (built for
x86_64/glibc), so `sdkmanager` won't run directly. Workarounds exist
(`proot-distro` to run a glibc Ubuntu chroot, then install the SDK inside
that) but add real complexity for no benefit over Option A. Only do this if
you specifically want everything on-device with no GitHub dependency —
ask me and I'll walk you through the proot-distro route.

## What's in this skeleton

- `app/src/main/java/.../MainActivity.kt` — one screen, one button, no real
  VPN logic yet (toggles a fake "Connected" label). This is just to prove
  the app installs and runs.
- `app/src/main/AndroidManifest.xml` — has the VPN-related permissions
  already declared, and a commented-out `<service>` block for the
  VpnService we'll add in the next step.
- `app/build.gradle.kts` — has a commented-out SSH library dependency
  (`sshj`) we'll uncomment when we build the tunnel engine.

## Next step

Once you've got this building and installed on your phone (even showing
the fake "Connected" toggle), tell me and we move to Step 3: the real
`VpnService` + SSH tunnel engine, tested against your SSHOcean free account.
