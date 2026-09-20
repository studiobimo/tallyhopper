# Changelog

## 0.1.0 (2026-09-20)


### Features

* add fabric and neoforge entrypoints ([fba0776](https://github.com/studiobimo/tallyhopper/commit/fba07769a0c2ac7df5ee3efd3b70a75d66922f1c))
* add gamerules for offline cap, backlog cap and calibration ([c15d506](https://github.com/studiobimo/tallyhopper/commit/c15d506e40399a37b4172922e02e01997226c138))
* add per-hopper rate overrides and /tallyhopper info ([725244e](https://github.com/studiobimo/tallyhopper/commit/725244e76c4dbb19f6ce7d3db01175e91aeaf304))
* add the energy estimate and config/tallyhopper.json ([a14c673](https://github.com/studiobimo/tallyhopper/commit/a14c6736df68ae08379005263fd1fb2b11568ef8))
* add the tally hopper screen ([c6dd935](https://github.com/studiobimo/tallyhopper/commit/c6dd935a7d1f2c477bffc518a30caf725fdf59c7))
* bound what a hopper may credit ([95e151a](https://github.com/studiobimo/tallyhopper/commit/95e151a6e2722b34c541376de73de727ef346abd))
* **common:** add capped item backlog ([d1814ec](https://github.com/studiobimo/tallyhopper/commit/d1814eca9befefff516a7ac31c8159a23c9380a5))
* **common:** add offline credit with fractional carry ([0150494](https://github.com/studiobimo/tallyhopper/commit/0150494359b61db679836f9454599486086bfbf0))
* **common:** add rate tracker for measured intake rates ([b8a87d0](https://github.com/studiobimo/tallyhopper/commit/b8a87d0273dc8b9a9e45584f6cf966d90142991a))
* **common:** add session clock and offline window math ([d3b2f73](https://github.com/studiobimo/tallyhopper/commit/d3b2f73932bc98b9d299fd35a324f4cba148a471))
* **common:** add tally hopper models, textures and names ([05a27ea](https://github.com/studiobimo/tallyhopper/commit/05a27eaa309e67fade1347bf2a2cb178884ed49c))
* **common:** count items entering a tally hopper ([ea0d458](https://github.com/studiobimo/tallyhopper/commit/ea0d4586b4d9e864d31267c295a1678f906d1428))
* **common:** credit offline time into a backlog ([b8c9fd2](https://github.com/studiobimo/tallyhopper/commit/b8c9fd21de710a8336482567bd600886ffb12764))
* **common:** keep the clock and use a sapling when converting in place ([3bda189](https://github.com/studiobimo/tallyhopper/commit/3bda18909e526237a42e573a296b4d98dd89cafe))
* convert hoppers in place by using a clock ([485861c](https://github.com/studiobimo/tallyhopper/commit/485861c4e16ac603cb246a97727d7dc2e2ac9fdd))
* craft the tally hopper from a hopper, clock and sapling ([f3a6be2](https://github.com/studiobimo/tallyhopper/commit/f3a6be2c89713e8f0100d443fb7efa16fefd6ff3))
* deliver credit into the storage a hopper faces ([f3682d2](https://github.com/studiobimo/tallyhopper/commit/f3682d29bdd7fd3505b3ec34a3a53bf1f59481bc))
* draw the brewing stand's apparatus behind the sapling slot ([efadba6](https://github.com/studiobimo/tallyhopper/commit/efadba6218ed58589a4a29d1e8a6173837d0daba))
* generate recipes, loot table and mineable tag with fabric datagen ([b123cbb](https://github.com/studiobimo/tallyhopper/commit/b123cbbdf7340f04526643fca79215913143fc2e))
* keep the backlog on the item when a tally hopper is broken ([9e07794](https://github.com/studiobimo/tallyhopper/commit/9e07794045c8a8d01688e8a53494e971b6b050d1))
* rebuild the screen from vanilla parts, paid for with saplings ([0d7ee66](https://github.com/studiobimo/tallyhopper/commit/0d7ee6619652fc3b59123365a2bd6c0d15c082e9))
* register the tally hopper block, item and block entity ([aca8a25](https://github.com/studiobimo/tallyhopper/commit/aca8a25459afa298725f8882ee3f8d3ee3359385))
* show a carried backlog in the item tooltip ([fd86524](https://github.com/studiobimo/tallyhopper/commit/fd8652484819b60e2ba81ecea092f69fd2db7b49))
* sync what the screen needs and add the tally hopper menu ([c74ece3](https://github.com/studiobimo/tallyhopper/commit/c74ece3c95a68a9e8f44e4b2a328ebdd95506f3a))
* tell players what their hoppers earned while away ([4b32508](https://github.com/studiobimo/tallyhopper/commit/4b325085008e81a6e32c4d42863d00d298884fad))
* track how long the world was closed ([9e6f50a](https://github.com/studiobimo/tallyhopper/commit/9e6f50a4c4359282146faebe4cb299f949fd3e9b))


### Bug Fixes

* **common:** refuse the padlock while a backlog drains ([f7d5986](https://github.com/studiobimo/tallyhopper/commit/f7d59869f2f245e9edf8a9329d7b3ade7b1953cb))
* resync the branch check with the canonical copy ([6587d2f](https://github.com/studiobimo/tallyhopper/commit/6587d2f5b13566f547afc54adb555fdc2dbbaeb0))
* resync the branch check with the canonical copy ([#53](https://github.com/studiobimo/tallyhopper/issues/53)) ([8196da3](https://github.com/studiobimo/tallyhopper/commit/8196da3d9dba702581e8a07f81e29a1ddc053a78))
* start releases at 0.1.0 rather than 1.0.0 ([c11da13](https://github.com/studiobimo/tallyhopper/commit/c11da13af573fa934708da3879f073c134f51c5d))
* start releases at 0.1.0 rather than 1.0.0 ([#52](https://github.com/studiobimo/tallyhopper/issues/52)) ([54a6bd7](https://github.com/studiobimo/tallyhopper/commit/54a6bd7b26fb391f566427de4ad7cbc6d8c17aaa))


### Documentation

* add project governance, roadmap and architecture decisions ([9a14d1b](https://github.com/studiobimo/tallyhopper/commit/9a14d1b606cf6eb2a0acf76ba38d1460b80952b6))
* add the energy methodology and the manual test script ([7ffae58](https://github.com/studiobimo/tallyhopper/commit/7ffae581903f4bbcfebca14f01a36cabb35006dd))
* explain the mod to someone who has not read the code ([aa4898d](https://github.com/studiobimo/tallyhopper/commit/aa4898dd8e0e1bdb11a3df96757705081ab2b02e))
* write down what storage and chunk loading the mod supports ([871fe7b](https://github.com/studiobimo/tallyhopper/commit/871fe7bccb682225d7f3bc4ee3a89d244c6c768a))
