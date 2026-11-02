# TimeGrapher

Android app that turns your phone into a timegrapher for mechanical clocks and watches. It listens to the ticks
through the built-in microphone, shows the key figures live and charts them over time. The design (Kotlin, Jetpack
Compose, Material 3, one engine object with StateFlows, bottom navigation, hand-drawn charts, dark slate theme)
follows [Android-Time-Services](../Android-Time-Services).

## Features

- **Live** tab: start/stop, input level, KPI tiles, a rolling volume chart (dB, with the detection threshold) and the standard timegrapher chart.
- **Trends** tab: rate, beat error and amplitude over time (1 min / 5 min / 30 min / session) with min/avg/max.
- **Settings** tab: beat rate (auto or fixed), lift angle, analysis window, sensitivity, filter band,
  calibration offset, keep screen on.
- Keeps measuring with the screen off through a microphone foreground service.

### KPIs

| KPI | Meaning |
| --- | --- |
| Rate (s/day) | Gain (+) or loss (-) per day, from a linear fit of the tick times |
| Beat error (ms) | Offset between tick and tock |
| Amplitude (deg) | `lift / (2 sin(pi * t / T))` from the unlock-to-drop time `t`, `T = 7200 / bph` |
| Beat rate | Nominal (snapped to 14400 ... 36000 bph) and measured |
| Tick/tock ratio | Mean loudness of even beats divided by odd beats |
| Jitter (ms) | Standard deviation of the tick times around the fit |
| SNR (dB) | Last tick peak against the tracked noise floor |

### Timegrapher chart

One row is one balance oscillation (two beats), newest at the bottom. A steady watch gives two vertical dotted
lines (tick cyan, tock amber). A slope shows the rate error, a shifted tock line shows the beat error.

## How it works

Sample timestamps come from the audio sample counter, not the wall clock.
`TickDetector` band-pass filters the signal (default 2-8 kHz), tracks the noise floor and detects the onset of each beat;
`KpiCalculator` turns the beats of the analysis window into KPIs. Code is in
`android/app/src/main/java/com/pschatzmann/timegrapher/`:

```
audio/AudioCapture      AudioRecord, unprocessed source preferred
dsp/TickDetector        filter, envelope, adaptive threshold, sub-peak capture
dsp/KpiCalculator       rate, beat error, amplitude, ratio, jitter
TimeGrapherEngine       capture loop and StateFlows (kpis, ticks, history, config)
TimeGrapherService      foreground service (microphone)
ui/                     Live, Trends, Settings, TimegrapherChart, Theme
```

## Build

```bash
export ANDROID_HOME=~/Android/Sdk
cd android
./gradlew testDebugUnitTest   # DSP tests with synthetic ticks
./gradlew installDebug
```

Release signing uses the file named by `TIMEGRAPHER_KEYSTORE_PROPERTIES` or `~/Android/keystore/keystore.properties`
(`storeFile`, `storePassword`, `keyAlias`, `keyPassword`); without it the release build is unsigned.

## Usage tips

- Put the phone's microphone against the case back or movement, or on a table next to a clock, in a quiet room.
- Leave it 10-30 s to settle. Pick the beat rate manually if auto-detection snaps to the wrong value.
- The rate depends on the phone's audio clock (typically a few s/day off). Measure a reference watch and enter
  the difference as calibration offset.

## Limitations

- v1 is English only.
- Amplitude needs two clearly separated sounds per beat; it shows `--` when they cannot be resolved.

## License

MIT, see [LICENSE](LICENSE).
