# :playback:service

The main service doing media playback.

`Media3PlaybackService` is the active implementation, built on AndroidX Media3/ExoPlayer.
`PlaybackService` is a legacy helper holding a few static playback-state fields and the
`getPlayerActivityIntent()` helper used to launch the audio/video player.

External callers should interact with the service through `PlaybackController`, which provides a
`bindToMedia3Service()` helper that connects a `MediaController` and runs a callback on it.
The `MediaController` exposes the standard Media3 `Player` interface: `seekTo(positionMs)`,
`play()`, `pause()`, `getCurrentPosition()`, `getPlaybackParameters()`, etc.
Each call to `bindToMedia3Service()` creates a short-lived connection that is released after the
callback returns.
