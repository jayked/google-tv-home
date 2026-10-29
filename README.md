# TV Home

A custom home screen (launcher) for Chromecast with Google TV and other Google TV devices.
Google TV-style layout without ads or recommendations:

- Clock, date and current weather (Open-Meteo)
- "Continue watching" hero and row, from the system Watch Next list
- Favorites row and a 5-column grid of all apps (long-press an app to favorite or move it)
- Settings shortcuts (and HDMI inputs, if any) as icons in the top-right corner; press Up from
  the top row to reveal them

Built with Kotlin and Compose for TV. `minSdk` 29.

## Connect to the Chromecast

1. On the TV: Settings → System → About → tap **Android TV OS build** 7 times.
2. Settings → System → Developer options → enable **USB debugging**.
3. Find the IP under Settings → Network & Internet, then:

   ```sh
   adb connect <tv-ip>:5555
   ```

   Accept the prompt on the TV.

## Install

```sh
scripts/install.sh release   # or no argument for a debug build
```

This builds, installs and opens the app. On first launch it may ask for access to TV data; allow it so Continue watching works.

## Make it the home screen

Chromecast with Google TV can't switch launchers, and disabling the stock one leaves Home
broken. Instead, TV Home's accessibility service catches the Home button and opens TV Home,
and also takes over whenever the Google TV home screen appears (e.g. after Back out of an app):

```sh
scripts/takeover.sh on      # Home button opens TV Home
scripts/takeover.sh off     # Home button opens Google TV again
scripts/takeover.sh status
```

Long-pressing Home no longer opens Google TV's side panel; use the settings icons in TV Home's
top-right menu instead.

## Weather location

By default the location comes from the TV's IP address (ipwho.is). To set it yourself,
add these to `local.properties` (or `gradle.properties`) and reinstall:

```properties
tvhome.weather.lat=52.37
tvhome.weather.lon=4.90
tvhome.weather.place=Amsterdam
```

## Wallpaper

The background shows a new [Unsplash](https://unsplash.com) photo every minute. Create a free
app at <https://unsplash.com/oauth/applications> and put its access key in `local.properties`
(not committed), then reinstall:

```properties
unsplash.access_key=YOUR_ACCESS_KEY
```

Change the search phrase (default "landscape") from the wallpaper icon in the top-right menu.
Without a key the background stays plain dark.

## Limits

Continue watching only shows apps that publish to Android's Watch Next provider. Google TV's
stock row also uses Google server-side data, so some apps (often Netflix and some
Google-integrated services) may not appear here.

