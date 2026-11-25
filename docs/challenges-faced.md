# Challenges faced

Problems hit while building the project, what caused them, and what was done. Newest first.

## Dashboard showed the old version after a rebuild (browser cached `index.html`)

**Symptom**
After the backend changed `GET /getReports` to return a page object, the dashboard sat on "Loading..."
and the console showed `this.reports.map is not a function` from `get firms`. That getter only exists in the
old client code, so the old app was still running against the new backend. The app only appeared to work
after opening DevTools with the Network tab open.

**Cause**
- The client is built into an nginx container. Every build gives the JS and CSS files new names
  (for example `main-AVB7MBMC.js` became `main-TQNCEHIQ.js`).
- Only `index.html` tells the browser which file names to load. The browser had cached `index.html`,
  so a normal refresh kept loading the old bundle.
- Opening DevTools with "Disable cache" on made the browser fetch everything again, which is why the
  new version showed up.
- The container itself was already up to date. Restarting it would not have helped.

**Fix**
`nginx/default.conf` now sends `Cache-Control: no-cache` for `index.html`, so the browser checks it on
every load and sees the new bundle name. Verified with `curl -I`: both `/` and an unknown path (which
falls back to `index.html`) return the header. The hashed JS and CSS files stay cacheable, because
their names change whenever their content changes.

**Lesson**
- A single-page app has one file that must never be cached blindly: `index.html`.
- Rebuilding the image is not the same as restarting it: `docker compose up -d --build`.
- When the front end and the backend change an API together, an old cached client against a new
  backend shows up as a confusing runtime error rather than a clean failure. Checking the bundle name in
  the Network tab shows straight away which version is running.
