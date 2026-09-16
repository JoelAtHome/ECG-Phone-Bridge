# Host handoff: Feather profile hint from `client_info`

**Audience:** Hertz & Hearts and VNS-TA maintainers  
**Phone:** **v1.0.0-beta.59+**  
**Wire:** [PROTOCOL.md](./PROTOCOL.md) §8.2 · checklist in [HOST_HANDOFF.md](./HOST_HANDOFF.md)

---

## What the phone already does

On **every** TCP `client_info`, the phone soft-matches `pc_user` to a local Feather patient profile:

1. Exact `profile_id` (case-insensitive)  
2. Exact `display_name`  
3. Sanitized slug of `pc_user` vs `profile_id` (spaces → `_`, etc.)

If the match is unique, the active profile would change, and the phone source is **Feather** or **Simulate**, Tech sees:

**Keep** / **Switch** — *“{app} selected Payton. Switch Feather profile from Patient 2 → Payton?”*

- **Switch** → sets active Feather profile only (does **not** push BLE coeffs; Connect / Save still does that).  
- **Keep** → leaves active profile; same `pc_user` suppressed ~30s (reconnect nag).  
- **Polar** → no dialog.  
- **ECG-Box Tuner** → no auto-prompt (still owns `profile_*`).  
- No / ambiguous match → status text only, no dialog.

Phone may send non-blocking `status` lines (safe to show as a banner; **no second OK/Cancel on PC**):

```json
{"type":"status","message":"Feather profile confirm: Payton?","connected":true}
{"type":"status","message":"Feather profile switched: Payton","connected":true}
{"type":"status","message":"Feather profile kept: Patient 2","connected":true}
{"type":"status","message":"No Feather profile for Sandy","connected":true}
```

---

## What we need from HnH / VNS-TA

### Required (small)

**Re-send `client_info` whenever the active patient/profile changes**, not only on TCP connect.

Same shape you already send:

```json
{"type":"client_info","pc_user":"Payton","client_app":"hertz_and_hearts"}
```

```json
{"type":"client_info","pc_user":"Payton","client_app":"vns_ta"}
```

| Rule | Detail |
|------|--------|
| `pc_user` | **Subject / patient** display name or id — not the clinician |
| When | On connect **and** on every patient switch (end of session / new session / mid-day change) |
| No special menu | Patient change on the PC **is** the signal; no app restart |

Without re-send on switch, the phone only learns the patient at link-up.

### Optional

- Non-blocking PC banner from the `status` messages above (pending → switched / kept).  
- Later: `patient_id` + phone `host_aliases` (not required for β.59).

### Not required

- Dual confirm on PC  
- `session_control`  
- Creating Feather profiles on the phone  
- Pushing coeffs from the host

---

## Field tips

1. On the phone, name Feather profiles like host patients (e.g. display name **Payton** or id `payton`). Factory **Patient 1** will not match **Payton** until renamed.  
2. Select **Feather** (or Simulate) on the phone before expecting the confirm dialog.  
3. Sideload phone **≥ v1.0.0-beta.59**.

---

## Copy-paste for HnH / VNS-TA issue or chat

```text
Phone bridge β.59+: soft-match client_info.pc_user → local Feather profile.
Tech Keep/Switch confirm when Feather/Simulate would override active patient.

Please re-send client_info on every patient/profile change (not only TCP connect),
with pc_user = subject name (not clinician). client_app as today (hertz_and_hearts / vns_ta).

Optional: show phone status lines as a non-blocking banner
("Feather profile confirm/switched/kept…") — no second confirm dialog on PC.

Docs: ECG-Phone-Bridge docs/HOST_FEATHER_PROFILE_HINT.md and PROTOCOL §8.2
```
