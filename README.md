# Shield
Android adult-content blocker (sideload only). Build: push to GitHub, run the "Build APK" action, download the artifact.

## Image scanning (optional)
Text, URL and keyword blocking works out of the box. Image scanning only turns on if a TensorFlow Lite model
named `nsfw.tflite` is placed in `app/src/main/assets/`. Expected format: input 224x224x3 float (0..1),
output 5 classes in order [drawings, hentai, neutral, porn, sexy].
