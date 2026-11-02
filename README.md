# TimeGrapher

An Android app that turns your phone into a timegrapher for mechanical clocks and watches. It listens to the ticks through the microphone, shows the key figures live and charts them over time. The design follows [Android-Time-Services](https://github.com/pschatzmann/Android-Time-Services).

> **Status:** working prototype, tested on one phone (Android 16). See [Important limits](https://github.com/pschatzmann/Android-TimeGrapher/wiki/Important-Limits) and the [Roadmap](https://github.com/pschatzmann/Android-TimeGrapher/wiki/Roadmap).

## Highlights

- **KPIs:** rate (s/day), beat error, amplitude, beat rate, tick/tock ratio, jitter and SNR.
- **Charts:** the standard timegrapher chart, a rolling volume chart and KPI trends over time.
- **Background operation:** keeps measuring with the screen off.

## Documentation

The documentation is in the [project wiki](https://github.com/pschatzmann/Android-TimeGrapher/wiki/Home) (sources in [wiki/](wiki/)):

- [User manual](https://github.com/pschatzmann/Android-TimeGrapher/wiki/User-Manual) (a screenshot and description of every tab)
- [Features](https://github.com/pschatzmann/Android-TimeGrapher/wiki/Features)
- [Important limits](https://github.com/pschatzmann/Android-TimeGrapher/wiki/Important-Limits)
- [Build and install](https://github.com/pschatzmann/Android-TimeGrapher/wiki/Build-and-Install)
- [Permissions](https://github.com/pschatzmann/Android-TimeGrapher/wiki/Permissions)
- [Project layout](https://github.com/pschatzmann/Android-TimeGrapher/wiki/Project-Layout)
- [Roadmap](https://github.com/pschatzmann/Android-TimeGrapher/wiki/Roadmap)

## Install

Requires Android 8.0 or newer. To build it yourself: `cd android && ./gradlew installDebug` (see [Build and install](https://github.com/pschatzmann/Android-TimeGrapher/wiki/Build-and-Install)).

## License

[MIT](LICENSE)
