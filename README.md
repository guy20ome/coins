# coins
replace the real coins with virtual coins for people without a bank account

coins let people without any bank account receive coins on their smartphone and give coins from their smartphone to pay for some daily needs
the people willing to give/receive coins will give a payment method.
the people just using coins just need the app.

> **Current status:** the app is being developed as a web app (`web/index.html`).
> It runs in any browser, for free, on any device — no Android emulator needed.
> **Try it live:** https://guy20ome.github.io/coins/
>
> The original Kotlin/Compose Android code is kept under `app/` (see [app/src/main/java/com/example/coins](app/src/main/java/com/example/coins)).
> It is not actively developed for now; it may be revived later via Compose Multiplatform once the web version is validated.

## Run the web app locally

No build tools, no dependencies. Just open the file:

    web/index.html

Or serve it (optional, closer to production):

    python3 -m http.server 8000

then open http://localhost:8000/web/index.html

## Deploy

The live site is served by GitHub Pages from the `gh-pages` branch (just `index.html` at the root).
To publish an update:

    cp web/index.html /tmp/index.html
    git checkout gh-pages
    cp /tmp/index.html .
    git commit -am "Update web app"
    git push
    git checkout main

After ~1 minute the change is live at https://guy20ome.github.io/coins/

## Roles and rules

Giver : 
- You can change up to 25€ in coins of 1€ , each unique coinid containing the position,and your unique giverid.
- You can send x number of coins to any User.
- As long as the coins are not destroyed by a Receiver, you cannot change more coins.
- The coins wallet shows the generated coins, the sent coins and the destroyed coins, the sum(generated+sent-destroyed) must be bellow 25
User :
- You can receive coins and send coins within a geographical area.
- Your geographical zone can be updated once all the coins received in the current area have been sent in the same area.
Receiver :
- You can receive coins without limitation as long as your geographical matches the one of the coins received.
- When a unique coinid is received, the position is checked, if the coin has not the same geographical area than you, the coin is rejected.
- If the geagraphical area matches, the Giver is immediatly contacted to allow the transaction between his bank account and the receiver bank account.
- This operation destroy the unique coinid
