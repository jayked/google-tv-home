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

Chromecast with Google TV has no setting to pick a launcher, so the script disables the
stock one:

```sh
scripts/takeover.sh on      # Home button now opens TV Home
scripts/takeover.sh off     # restore the stock Google TV home
scripts/takeover.sh status  # show which activity handles Home
```

With the stock launcher disabled you lose Google TV's own home features: profiles,
the ambient/screensaver integration and the Google account recommendations. Turn it
back on before installing system updates if something misbehaves.

## Weather location

By default the location comes from the TV's IP address (ipwho.is). To set it yourself,
fill these in `gradle.properties` and reinstall:

```properties
tvhome.weather.lat=52.37
tvhome.weather.lon=4.90
tvhome.weather.place=Amsterdam
```

## Limits

Continue watching only shows apps that publish to Android's Watch Next provider. Google TV's
stock row also uses Google server-side data, so some apps (often Netflix and some
Google-integrated services) may not appear here.
