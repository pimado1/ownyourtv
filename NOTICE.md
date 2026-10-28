# Third-party components

OwnYourTV ships these, each under its own licence:

| Component | Licence | Where |
|---|---|---|
| [dadb](https://github.com/mobile-dev-inc/dadb), the ADB client the app talks to its TV with | Apache License 2.0 | Gradle dependency |
| Kotlin standard library | Apache License 2.0 | Gradle dependency |
| Gradle wrapper (`gradlew`, `gradle/wrapper/`) | Apache License 2.0 | build tool |
| Bricolage Grotesque, Manrope and Space Grotesk fonts | SIL Open Font License 1.1 | `app/src/main/assets/fonts/OFL-*.txt` |

The TV database (`app/src/main/assets/tvs.json`, built by `tools/build_tvs.py`) is derived from the tested lists
and notes of [CleanYourTV](https://github.com/Gathin23/cleanyourtv), used under its MIT licence:

> Copyright (c) 2026 Gathin
>
> Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
> documentation files (the "Software"), to deal in the Software without restriction, including without limitation
> the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and
> to permit persons to whom the Software is furnished to do so, subject to the following conditions:
>
> The above copyright notice and this permission notice shall be included in all copies or substantial portions of
> the Software.
>
> THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO
> THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
> AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF
> CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS
> IN THE SOFTWARE.

Everything else in this repository is OwnYourTV's own work, under the MIT licence in `LICENSE`.
