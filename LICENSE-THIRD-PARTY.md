# Third-Party Licenses

## Create

Portions of this project are adapted from Create
(https://github.com/Creators-of-Create/Create), branch `mc1.21.1/dev`.
Create's code is licensed under the MIT License reproduced below (Create's
assets are "All Rights Reserved" and none are used in this project).

Adapted files, all in package `com.oceania.raft.collision`:

- `src/main/java/com/oceania/raft/collision/Matrix3d.java`
  from `src/main/java/com/simibubi/create/foundation/collision/Matrix3d.java`
- `src/main/java/com/oceania/raft/collision/OrientedBB.java`
  from `src/main/java/com/simibubi/create/foundation/collision/OrientedBB.java`
- `src/main/java/com/oceania/raft/collision/ContinuousOBBCollider.java`
  from `src/main/java/com/simibubi/create/foundation/collision/ContinuousOBBCollider.java`
- `src/main/java/com/oceania/raft/collision/CollisionList.java`
  from `src/main/java/com/simibubi/create/foundation/collision/CollisionList.java`
- `src/main/java/com/oceania/raft/collision/CollisionMath.java`
  minimal re-implementation of `Iterate.trueAndFalse`, `VecHelper.rotate` and
  `VecHelper.CENTER_OF_ORIGIN` from Catnip
  (https://github.com/Creators-of-Create/Catnip), also MIT licensed by the
  same copyright holder.

### Code License (MIT), as in Create's `LICENSE.md`

MIT License

Copyright (c) The Create Team / The Creators of Create

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.

## NOAA PMEL whale recordings (audio)

The whale song clips are hydrophone recordings published by the NOAA Pacific Marine
Environmental Laboratory Acoustics Program, at
https://www.pmel.noaa.gov/acoustics/whales/sounds/sounds_whales_blue.html

They are works of the United States federal government and are therefore not subject to
copyright in the United States (17 U.S.C. 105). NOAA states that its still images and audio
"generally are not copyrighted" and that, unless the item carries a credit to a third party,
it "may be used without permission", asking only that NOAA be credited as the source. None of
the files used here carries a third-party credit. NOAA's emblem is not used.

Source files, all published by NOAA at ten times their recorded speed (a blue whale calls at
10 to 40 Hz, below human hearing at its real rate):

- `whale_song1.ogg`      from `nepblue24s10x.wav`                  - NE Pacific blue whale
- `whale_song2.ogg`      from `wblue26s10x.wav`                    - West Pacific blue whale
- `whale_song3.ogg`      from `atlblue_512_64_0-50_10x.wav`        - Atlantic blue whale
- `whale_song_52hz.ogg`  from `ak52_10x.wav`                       - the 52 Hz call

All four were converted to mono Ogg Vorbis at 44.1 kHz and loudness-normalised; no other
alteration. They live in `src/main/resources/assets/oceania/sounds/entity/whale/`.

**Credit: NOAA.**

`whale_blow.ogg` is NOT from NOAA and is not a recording. It is synthesised by
`tools/make_whale_blow.py` and carries no third-party rights.
