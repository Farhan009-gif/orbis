# Publish checklist — Orbis

Run these in order in the final days, BEFORE flipping this repo public.

- [ ] **Scrub the retired key from history.** The old signing key
  (`signing/orbis.keystore.b64`) and its old plaintext password still
  exist in commits before the "Move signing to secrets" commit. That
  key is retired — it signed only the earliest APK and signs nothing
  current — but it must not ship in a public repo. Rewrite history
  (e.g. `git filter-repo --path signing/orbis.keystore.b64 --invert-paths`,
  plus a pass replacing the old password string in `app/build.gradle.kts`
  history), force-push, and confirm the file is gone from all history.
- [ ] **Verify secrets are in place** (Settings -> Secrets and variables
  -> Actions): `ORBIS_KEYSTORE_B64`, `ORBIS_STORE_PASSWORD`,
  `ORBIS_KEY_PASSWORD`, `ORBIS_KEY_ALIAS`. The build fails loudly without
  them; that is by design.
- [ ] **Confirm the tree is clean:** no `*.keystore`, `*.b64` key files,
  or plaintext passwords anywhere in the current tree (grep for
  `storePassword` values and `keystore` filenames).
- [ ] **Fresh green build** from the scrubbed history; download the APK
  and confirm its signature is the current key (the same key as the last
  pre-publish build, so existing installs keep updating).
- [ ] **Then** flip the repository visibility to public.
