# :net:discovery

This module contains the podcast search/discovery APIs.
Each podcast directory (iTunes, fyyd, etc.) is a separate class implementing a common search interface.

`ItunesCategoryLoader` provides browse-by-category support for Apple Podcasts: it enumerates the
podcast genres from the iTunes genre service 