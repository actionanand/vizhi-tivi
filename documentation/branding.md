# Brand and layouts

App: **Vizhi TiVi** (`vizhi-tivi`). Exact taglines:

- Tamil (`ta`): உலகம் உங்கள் விழிகளில்!
- English (`en`): The world before your eyes.
- Sanskrit (`sa`): विश्वं तव नयनयोः पुरतः

The common source-of-truth assets live at `common/branding/`. Run `python3 scripts/sync-branding.py` to copy shared launcher PNG and TV launcher banner into Android resources. These are **placeholders** until final artwork is supplied.

This starter contains one Android `app` module and one simple Compose screen. Product flavors choose either the mobile launcher or TV launcher; later features should share Kotlin data/repository/player logic while implementing remote/D-pad focus and appropriate layouts for TV. A basic Compose screen does **not** imply Google Play TV quality compliance. Test on emulators and physical TVs.

For future production images, use appropriate Android adaptive launcher icons and high-quality TV banners. The existing PNG assets are only there to make the starter manifest resolvable.
