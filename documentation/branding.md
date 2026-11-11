# Brand and layouts

App: **Vizhi TiVi** (`vizhi-tivi`). Exact taglines:

- Tamil (`ta`): உலகம் உங்கள் விழிகளில்!
- English (`en`): The world before your eyes.
- Sanskrit (`sa`): विश्वं तव नयनयोः पुरतः

The common source-of-truth assets live at `common/branding/`. Run `python3 scripts/sync-branding.py` to copy shared launcher PNG and TV launcher banner into Android resources. These are **placeholders** until final artwork is supplied.

One Android `app` module shares catalogue, import, persistence and playback code. Product flavors choose the mobile or TV launcher and their intended layout. Mobile uses Material 3; TV uses larger cards, category rails and Compose for TV controls. Builds and unit tests do **not** establish Google Play TV quality compliance. Test D-pad behavior on emulators and physical TVs.

For future production images, use appropriate Android adaptive launcher icons and high-quality TV banners. The existing PNG assets are only there to make the starter manifest resolvable.
