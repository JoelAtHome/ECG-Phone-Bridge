# Saved HRV list — host handoff

**Phone:** **v1.0.0-beta.74**. Discover `features` includes `ritual_list` and `ritual_unavailable`.  
**Hosts:** Hertz & Hearts and FlareTracker.  
**Wire:** [PROTOCOL.md](./PROTOCOL.md) §7.3.

The phone now keeps the last 5 Record sessions as a visible list. Each new recording stores the Feather patient that was active at Stop (`profile_id`, `profile_display_name`). Recordings already on a phone have no patient name.

## What the phone does without a host change

- Capture panel, one row per stored recording, newest first: local time, RMSSD, duration, patient name, `sent` or `pending`. Only a long patient name shrinks. Select a row, then **Send** or **Delete** under the list. Delete asks for confirmation.
- **Send** transmits that `session_id` when a PC is connected. It does not remove the recording.
- If a PC is still connected at Stop, that new recording is sent immediately (`transfer_reason: live_stop`) and the row shows sent. Older rows are not sent with it.
- If the PC is gone at Stop, the row stays pending until the phone sends it or a host requests it.

## What hosts should build

Replace the unlabeled **Request saved HRV** action with the phone's list. The user picks a row. Do not ask them to type a `session_id`.

1. While linked, send `{"type":"ritual_list"}`.
2. Show `recordings` newest first. Suggested row: local time from `emitted_at`, `rmssd_ms`, `duration_s`, `profile_display_name`, and whether `acked` is true. Omit the patient name when the field is absent.
3. On pick, send `{"type":"ritual_request","session_id":"<id from the row>"}`. The phone already accepts that and resends the package even if `acked` is true.
4. If that recording was deleted on the phone, the reply is `{"type":"ritual_unavailable","session_id":"<id>","reason":"not_found"}` instead of a package. Drop that row and send `ritual_list` again. This message is only for a named id. **Request saved HRV** with no `session_id` never receives it, including when the phone has no recordings.
5. Keep `ritual_ack` and dedupe by `session_id`. FlareTracker may still ignore IBI/ECG chunks and ack after the official `rmssd`.

`session_summary` on the transfer also includes `profile_id` and `profile_display_name` when the phone has them.

## Leave this alone until both hosts ship the picker

A `ritual_request` with `session_id` null or omitted still means: newest unacked package, otherwise the newest package. Today's **Request saved HRV** button depends on that. After both Hertz & Hearts and FlareTracker show the list, that no-id request should mean only the newest recording. Do not change it on the phone before then.

Delete stays on the phone. There is no host delete message.
