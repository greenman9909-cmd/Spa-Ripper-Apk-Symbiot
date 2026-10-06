# Account registration deployment findings

2026-10-06; project Spa-Ripper-AniPM (`yhccrdatocqqniblpshm`). APK 0.4.7 remains unchanged.

## Verified configuration

The owner connected the correct Supabase organization through the browser. Read-only dashboard inspection established:

- Custom SMTP is **disabled**. Signup/email are enabled and email confirmation is required.
- Site URL initially was **http://localhost:3000** with no additional redirect URLs. With explicit user approval, it is now saved as **https://greenman9909-cmd.github.io/Spa-Ripper-Apk-Symbiot/**; the dashboard displayed the saved value and disabled Save changes.
- Supabase's [default SMTP documentation](https://supabase.com/docs/guides/auth/auth-smtp) says email delivery without custom SMTP is restricted to project-team addresses. This blocks ordinary external users from completing confirmation-required signup.
- The user said they have an SMTP provider and will configure it directly in Supabase. Credentials must not be pasted into chat, committed or embedded in the APK.

## Prepared return page

[Public return page](https://greenman9909-cmd.github.io/Spa-Ripper-Apk-Symbiot/) is deployed through GitHub Pages from this branch's `/docs` folder. No new APK is needed for the backend redirect change.

The page has no authentication form, analytics or network requests to the Auth API. It clears URL fragments/query strings with history replacement, never logs/persists tokens, and uses no-referrer/CSP restrictions. It instructs users to return to the Android app after confirmation. Error links show fixed text without echoing provider/error input. Recovery links explicitly show that password reset is not implemented in this preview; this page does not complete recovery or sign in.

`node test_auth_return.cjs`: six privacy/state cases passed, including initial and repeat fragment visits, errors, recovery and DOM readiness. Live browser checks with synthetic tokens verified fragment clearing on repeat visits and expired-link messaging. No real signup or delivery was tested by those checks.

The user approved replacing localhost, and the dashboard visibly confirmed the saved HTTPS Site URL. The default return page supports manual confirmation-then-login; no extra redirect allowlist or deep-link handler is required for that limited flow. Secure automated login and working recovery remain separate work.

## Physical USB login diagnosis (2026-10-06)

The user's existing physical-phone installation was updated from 0.4.6 to 0.4.7 with an in-place install preserving app data. App-scoped captures produced no matching crash trace; this does not prove that the reported navigation/playback bugs are fixed.

For the reported login returning to the previous screen, a bounded project-log aggregate showed two HTTP 400 `email_not_confirmed` responses. A read-only account aggregate confirmed that the user's app account exists and is unconfirmed. No credentials, account identifiers, device serials or raw logs are included here.

The user reported not having opened the confirmation email, then that its link failed or expired. With explicit authorization, one standard signup-confirmation resend was requested. Supabase returned HTTP 200; this verifies request acceptance only, not delivery, link validity or successful login. Custom SMTP was still visibly disabled. Next: user opens the newest email, completes confirmation, then logs into the phone themselves; verify Home before diagnosing scrolling/playback. Do not silently auto-confirm the account, reuse chat passwords or create a fake session. The generic original error UI still needs a clear, correctly mapped confirmation message.

## Remaining registration acceptance

Follow-up: after the user opened a confirmation email, a read-only aggregate verified that their existing account is now confirmed and has no pending confirmation token. A displayed invalid-link return page therefore does not establish that their account remains unconfirmed. The return page now recommends trying native login first, explains already-used links and explicitly states it cannot check account status. No server-side confirmation bypass or native APK change was made. The user subsequently reported that access works. The updated page was visibly verified live after reload with a synthetic expired-link fragment; six privacy/state tests passed. This one delivered-email and user-reported login case does not establish production-wide SMTP readiness, scrolling stability or playback acceptance.

1. User configures sender identity and provider SMTP directly in [SMTP settings](https://supabase.com/dashboard/project/yhccrdatocqqniblpshm/auth/smtp). Confirm provider domain verification and delivery quotas. Do not silently purchase/upgrade services.
2. Site URL was applied and verified in [URL configuration](https://supabase.com/dashboard/project/yhccrdatocqqniblpshm/auth/url-configuration). Retain this HTTPS URL unless a verified replacement is approved.
3. A real test inbox was requested after SMTP is saved. With explicit authorization, exercise native signup, email receipt, confirmation, return page and subsequent login. Then test duplicate email, expired/pending confirmation and resend. The original signup pending-confirmation UI still needs acceptance.
4. Implement real password recovery/reset UX; sending the reset email alone is not sufficient. Verify account deletion/password-change routes and multi-device state before calling production ready.

An earlier signup shell test was rejected by automatic approval review with only “blocked by policy.” It was not rerouted. Confirmed own database-fixture login tests do not verify account creation or email delivery. See [AGENT_HANDOFF.md](AGENT_HANDOFF.md) for broader remaining acceptance.
