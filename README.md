Catnap is an Android app that dynamically generates white/colored noise according to a
frequency/amplitude spectrum sketched by the user.

It is intended to be used as a sleep sound generator. It provides rapid feedback to adjustments
in the spectrum, and is designed to minimize CPU usage in the steady state.

It works by running shaped white noise through an Inverse Discrete Cosine Transform, generating
a few megabytes of distinct audio blocks. The steady-state behavior selects blocks at random, and
smoothly crossfades between them.

This app is a fork of the original app, Chroma Doze, by Paul Marks.
- Copyright © Paul Marks, original source
- Copyright © Chimbori, modifications

Licensed under the GNU GPL License v3.0
