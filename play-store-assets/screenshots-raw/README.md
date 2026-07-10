# Raw screenshots for the Play Store listing

Drop your real device captures here, named **exactly** as below. Then run:

```powershell
powershell -ExecutionPolicy Bypass -File ..\generate-screenshots.ps1
```

Framed, Play-ready slides land in `../screenshots/` (1080×1920, 9:16).

| File name        | Screen to capture                              | Headline it gets                 |
| ---------------- | ---------------------------------------------- | -------------------------------- |
| `home-owe.png`   | Home with the "You owe" summary + tabs         | Always know what you owe         |
| `loan-detail.png`| A loan's detail screen (Total due, terms)      | Every term in black and white    |
| `new-loan.png`   | The "New loan" form                            | Set up a loan in under a minute  |
| `sign-in.png`    | The Continue-with-Google sign-in screen        | For loans between people you trust |
| `active.png`     | The Active (or Sent/Received) list             | Offers, requests and repayments  |

Tips for clean captures:
- Full-screen phone screenshots (any modern portrait phone); the script scales
  and rounds the corners for you.
- Turn on airplane mode or crop later if you'd rather hide the status-bar clock
  and battery — not required.
- Any missing file renders as a neutral placeholder so you can still preview the
  layout; replace it and re-run.

Headlines and their order live in the `$slides` block at the top of
`generate-screenshots.ps1` — edit there.
