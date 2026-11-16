# AGENTS.md

Application Android (Kotlin + Jetpack Compose) de suivi multi-portefeuilles crypto : soldes en euros
ou autre devise, historique, cours et graphiques. UI entièrement en français.

## Commandes

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"   # JDK 25, testé
.\gradlew.bat assembleDebug        # APK : app/build/outputs/apk/debug/app-debug.apk
.\gradlew.bat exportDebugApk       # APK nommé : dist/CryptoWalletTracker-v<version>-debug.apk
.\gradlew.bat exportReleaseApk     # APK release signé : dist/CryptoWalletTracker-v<version>-release.apk
```

La release est signée avec `keystore/cryptowallettracker.jks` (identifiants dans
`keystore.properties`, tous deux ignorés par git) et optimisée par R8. Garder la même clé pour les
mises à jour. Les règles R8 indispensables à kotlinx.serialization **et à WorkManager/Room** sont dans
`app/proguard-rules.pro` : sans elles l'app plante au démarrage
(`Failed to create an instance of class androidx.work.impl.WorkDatabase`).

JDK 17+ suffit. Pas de tests, pas de lint. Vérification = build + installation sur l'émulateur
(`%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe`), puis
`adb install -r ...`, `adb shell am force-stop com.jclg.cryptowallettracker`.

Chaîne d'outils : Gradle 9.9.1, AGP 9.5.1, Kotlin 2.2.10 **built-in AGP**, compileSdk/targetSdk 37,
minSdk 26, build-tools 36.0.0, Compose BOM 2026.09.00.

- AGP 9 compile Kotlin tout seul : **ne pas réappliquer `org.jetbrains.kotlin.android`**.
  Seuls `plugin.compose` et `plugin.serialization` sont déclarés, à la version du KGP d'AGP.
- `material3` ne tire plus `material-icons-core` : l'icône `Icons.*` exige la dépendance explicite.
- `targetSdk` 35+ impose l'edge-to-edge : `enableEdgeToEdge()` + `AppTheme` règle la couleur des
  icônes de barre système selon le thème.
- Le SDK 36 n'est pas installé ; c'est `platforms/android-37.0`. Les anciens SDK restent installés.
- `local.properties` est propre à la machine, ne pas le committer.
- Si Gradle échoue avec « Could not move temporary workspace » / « workspace metadata » :
  supprimer `%USERPROFILE%\.gradle\caches\<version>` et les daemons java restants.

## Structure

Un seul module `:app`, aucune DI, aucune lib de navigation : `MainActivity.kt` garde une route texte
(`home`, `wallet/<id>`, `edit/<id|new>`, `price/<coinId>`, `settings`, `about`) et un `when`.
`ui/AppViewModel.kt` détient tout l'état (`StateFlow<UiState>`) et orchestre les appels réseau.

- `data/Coins.kt` : définitions des cryptos **avec source de solde** (id CoinGecko, symbole, décimales,
  couleur, paire Binance, `sources`, regex d'adresse, `explorerAddress`). `Coins.priceOnly(...)` génère
  les autres cryptos du top 100 (cours seulement, ajout impossible).
- `data/Markets.kt` : top 100 CoinGecko (`coins/markets?per_page=100`) → cours, logos, mini-courbes,
  catalogue (`MarketSnapshot.catalog`), taux, graphiques (CoinGecko ≤ 365 j, Binance > 1 an, Kraken).
- `data/Balances.kt` : une implémentation par `SourceId` (solde) + historiques (Blockscout, mempool,
  dcrdata). `SourceId.history` pilote ce que l'UI annonce.
- `data/SnapshotStore.kt` : `snapshot.json` (soldes + horodatage) pour l'affichage hors ligne et la
  synchro de fond.
- `sync/BalanceSyncWorker.kt` : Worker périodique (WorkManager) + `SyncScheduler`.
- `ui/` : `Home.kt`, `Detail.kt`, `Edit.kt`, `Price.kt` (graphique), `Settings.kt`, `About.kt`,
  `Dialogs.kt`, `Theme.kt`, `Components.kt`, `Scrollbars.kt`.
- `data/Settings.kt` (`SharedPreferences` JSON) et `data/WalletStore.kt` (`filesDir/wallets.json`).
- `Wallet` stocke `coinId` + `source` ; les anciens fichiers 1.0 (`chain: ETHEREUM`) sont migrés au
  chargement via `Coins.fromLegacy`.

## Règles de conception

- L'accueil n'affiche **jamais** l'adresse d'un portefeuille (uniquement nom, source, commentaire, solde).
- L'accueil ne liste que les cryptos cochées dans `Settings.homeCharts` (sinon message d'invite).
- Plusieurs portefeuilles peuvent partager la même crypto ; l'import déduplique sur `coinId + adresse`.
- Tous les montants passent par `UiState.price()` (USD × taux) ; la devise est globale (`Settings`).
- Les cours sont normalisés en USD ; les taux viennent des cotations BTC en EUR/GBP/JPY/CNY.
- `Settings.chartRanges` mémorise la période de graphique par crypto.
- Chaque source affichée est accompagnée de sa **date de dernière synchro** (accueil, fiche
  portefeuille, graphique) ; le pull-to-refresh est disponible sur l'accueil, la fiche et le graphique.
- Le top 100 vient de CoinGecko à chaque synchro des cours : `UiState.catalog` est la seule liste à
  utiliser pour les marchés et les réglages (ne pas coder 100 cryptos en dur).
- Fonctions transverses : `Settings.hideBalances` (icône œil sur le total), `Settings.lockEnabled`
  (BiometricPrompt + code du téléphone, `MainActivity` hérite de `FragmentActivity`),
  `Settings.backgroundSyncHours` + `Settings.notifyBalanceChange` (WorkManager), lien explorateur via
  `Coin.explorerUrl(adresse)`.
- L'ajout d'un portefeuille propose le scan d'un QR (`WalletCodec`), et la fiche a une icône QR à côté
  de l'adresse.
- Les graphiques viennent de CoinGecko (≤ 365 j), Binance (2 ans/5 ans/max), Kraken en secours
  (XMR, DCR) ; la source réelle est affichée sous la courbe.

## Limites d'API et cache

- CoinGecko public : ~5 à 15 requêtes/minute et **365 jours d'historique maximum** (HTTP 401 au-delà).
  D'où Binance/Kraken pour 2 ans, 5 ans et max.
- Politique de synchronisation (à respecter) :
  - cours, variations 24 h, mini-courbes et taux : revalidés toutes les **24 h** au lancement et à
    chaque retour au premier plan (`AppViewModel.sync(force = false)`), ou immédiatement sur
    pull-to-refresh / bouton (force).
  - solde et historique d'un portefeuille : **24 h** par portefeuille (`WALLET_TTL`), conservés
    indéfiniment en mémoire et réaffichés tels quels si une synchro échoue.
  - graphiques : **1 h** pour 1 h → 30 j, **24 h** pour 1 an → max ; jamais préchargés hors de la
    page de graphique.
  - logos : gardés toute la session ; le menu « Mettre à jour les logos » force le rechargement
    (`LogoLoader.clear()`).
- Les anciennes données ne sont jamais effacées au début d'une synchro (`Balance.updatedAt`,
  `HistoryState.updatedAt`, `ChartData.fetchedAt`).
- Tout échec conserve l'ancienne valeur et alimente `UiState.syncErrors` avec la donnée **et** la
  source (ex. « Solde BTC — mempool.space (pas de connexion) »), affiché dans une boîte de dialogue.
- Les mini-courbes d'accueil viennent de `coins/markets?sparkline=true` : une seule requête pour tout,
  jamais un appel `market_chart` par crypto.
- Binance `ticker/price` renvoie toutes les paires : un appel `symbols=[...]` échoue en bloc si un
  symbole est invalide.

## Sources de données (sans clé)

- Cours : CoinGecko `coins/markets` (logos + mini-courbes inclus) ; `simple/price` pour les taux.
  Alternative Binance `ticker/price`.
- Graphiques : CoinGecko `market_chart` (≤ 365 j), Binance `klines` (2 ans/5 ans/max),
  Kraken `OHLC` en secours (XMR, DCR).
- Soldes : RPC Ethereum/BSC/Avalanche (dont `balanceOf` ERC-20), Blockscout, mempool.space,
  Blockstream, Ripple `s1.ripple.com`, Solana RPC, dogechain.info, Koios, TronGrid, Horizon,
  litecoinspace.org (API mempool), toncenter, dcrdata, LWS Monero.
- Monero exige la clé d'affichage + un enregistrement préalable sur
  `https://lightwallet.monero.fail/register` (le LWS répond 500 sinon). Aucun historique possible.
- Crypto sans source de solde (cours seulement) : **SUI, DOT, BCH**. Ne pas inventer de solde :
  l'UI les marque « Cours seulement » et l'ajout est désactivé.
  (Testés sans succès : BitPay Insight pour BCH, Blockchair 430, JSON-RPC SUI déprécié.)
- CryptoCompare exige désormais une clé API (401) : ne pas le réintroduire sans clé.
- Decred : `/api/address/{addr}` répète le même txid → dédupliquer ; les montants envoyés nécessitent
  de remonter les entrées (cache `TxFetcher`), sinon `null` → l'UI affiche « — ».

## À ne pas casser

- Format d'échange export/import = `WalletPayload` (`c`, `a`, `v`, `n`, `m`, `s`) dans `data/Models.kt`.
  Le QR encode un seul payload. `c` accepte encore les anciens noms `ETHEREUM`/`DECRED`/`MONERO`.
- Version : `val appVersionName` dans `app/build.gradle.kts` (affichée `v1.1`, centrée sur l'accueil)
  **et** entrée dans `app/src/main/assets/CHANGELOG.md` (les `#` Markdown sont retirés à l'affichage).
- `versionCode` à incrémenter en même temps.
- Montants en `Double` à l'affichage ; conversions wei/atoms/drops via `BigDecimal`.
