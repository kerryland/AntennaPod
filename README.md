# AntennaPod Fork
This is my fork of [AntennaPod](https://antennapod.org) that does stuff I want. The official development team are rightly reluctant
to make large changes to their codebase because they effect thousands of users on dozens of devices,
but here I can go crazy doing whatever I want, screwing stuff up, and fixing things only if I feel like it.

If you want something to rely on, you should use the official build.

## Podcast Priorities
Some podcasts are more important than others. I want them to appear in the inbox or queue according to
their priority. Can drag and drop subscriptions to specify priority.

## Podcast Playback Order
Some podcasts are topical news shows, and I only care about the most recent episode --
it's OK if I miss some. Other podcasts are serialised and I want listen to them in order, 
hell, they might even be many years old.

Put a limit on the number of episodes to download per podcast, so my inbox doesn't keep filling forever.

## VPN Downloads
I like listening to advertisements from other countries, so there is now a "VPN Required" 
preference which launches the VPN connection dialog if a VPN has not been established.

## Subscription Browsing
Can now browse Apple's podcast directory to find new podcasts.

## Menus and Icons
- Added words to Swipe Actions to supplement icons
- Added icons to Menus to supplement words.
- The "speed dial" floating menu is replaced with a traditional menu because it's easier to read and navigate.
- New icon on episodes to indicate when they are never going to automatically download. Changed some icons. 

## Other
- Skip forward/back buttons added to bottom of each page.
- "Remove from Queue" skips to the next episode in the queue.
- "Move to Play Next" (also with multi-select).
- Multi-select supports swipe actions.
- New episodes are easier to spot (dates are highlighted).
- Episode list now shows the podcast name too (and removed file size).
- Preferences stored in the database for easier migration to new devices.

## Building AntennaPod

You can build AntennaPod just like any other Android project. Refer to the [instructions](https://github.com/AntennaPod/AntennaPod/blob/develop/CONTRIBUTING.md) for more details.

