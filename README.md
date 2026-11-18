# Crypto Wallet Tracker

Application Android pour suivre le solde de plusieurs portefeuilles crypto sans y toucher :
seules des adresses publiques sont enregistrées, aucune clé privée.

## Fonctionnalités

- **Top 100 des cryptos** pour les cours et les graphiques ; **36 cryptos** ont en plus une source de
  solde (Ethereum, Bitcoin, BNB, Solana, XRP, Cardano, TRON, Stellar, Litecoin, TON, Monero, Decred,
  Polygon, Arbitrum, Optimism, NEAR, Algorand, Cosmos, Hedera, Stacks, VeChain, Aptos, Filecoin, UNI,
  AAVE, PEPE…).
- Plusieurs portefeuilles par crypto, avec nom et commentaire ; les adresses restent sur la fiche.
- **Verrouillage** de l'application par PIN ou schéma dessiné, propre à l'application.
- **Soldes masquables** d'un appui sur l'icône œil (total, portefeuilles, fiche).
- **Synchro en arrière-plan** toutes les 6, 12 ou 24 h (même application fermée) et **notification** en
  cas de changement de solde.
- Ajout d'un portefeuille par saisie de l'adresse **ou scan d'un QR code** (caméra de l'appareil) ;
  icône QR à côté de l'adresse pour la partager.
- Lien vers l'explorateur de la blockchain depuis chaque fiche (mempool.space, Blockscout, xrpscan…).
- Solde par portefeuille et **total dans la devise choisie** : euro, dollar, livre, yen, yuan, BTC
  ou ETH — un appui sur le solde total change la devise partout (aussi depuis la fiche).
- Thème **clair, sombre ou automatique**, palette ocre et icône dorée.
- **Pull-to-refresh** sur l'accueil (tous les portefeuilles + cours + taux + mini-courbes), sur chaque
  fiche de portefeuille (solde + historique) et sur les graphiques (bouton en plus).
- **Date de dernière synchro** affichée à côté de chaque source.
- **Solde total partiel** : si un portefeuille n'a pas pu être actualisé, le total reste affiché avec
  une icône d'avertissement.
- Menu ⋮ → « Mettre à jour les logos » pour recharger les logos des cryptos.
- Données conservées hors ligne : une synchro ratée garde les anciennes valeurs et affiche une alerte
  précisant la donnée et la source en cause. Rafraîchissement automatique toutes les 24 h (cours,
  soldes, historique, graphiques longs) ou toutes les heures (graphiques courts), uniquement quand
  l'application est ouverte.
- Choix de la **source de données** par portefeuille (RPC, explorateur…), avec ce qui est disponible
  (solde seul ou solde + transactions) indiqué à l'ajout et modifiable ensuite. Ethereum ajoute
  etherscan.io et beaconcha.in, qui demandent une clé API gratuite (Réglages › Clés API).
- Cours : CoinGecko par défaut, Binance en alternative. Graphiques : CoinGecko jusqu'à 1 an,
  Binance (ou Kraken) pour 2 ans, 5 ans et max.
- Graphiques interactifs (1 h, 24 h, 7 j, 30 j, 1 an, 2 ans, 5 ans, max) : axes gradués, appui ou
  glissement pour lire le cours d'un point, source affichée sous la courbe, période mémorisée par
  crypto. L'accueil n'affiche que les cryptos ajoutées avec le + de la section Marchés.
- Historique des transactions pour Ethereum (source Blockscout), Bitcoin et Decred, avec lien vers
  l'explorateur et message quand seules les transactions les plus récentes sont affichées.
- Export / import des portefeuilles par fichier JSON ; le QR affiché sur une fiche sert à partager un
  portefeuille. Un **code optionnel** chiffre l'export (AES-GCM) et est demandé à l'import.
- Listes de cryptos triées par ordre alphabétique (accueil et ajout).
- Appui long sur un portefeuille : le déplacer vers le haut ou le bas, ou le supprimer. Appui long sur
  un graphique : même choix ; le bouton + à côté de « Marchés » en ajoute un. L'ordre des deux listes
  est mémorisé.
- Page À propos (en haut des réglages), changelog, version affichée `v1.5`.

## Installation

APK de release signé :

```
dist/CryptoWalletTracker-v1.1-release.apk
```

Transférer sur le téléphone et autoriser l'installation de sources inconnues. Ou :

```powershell
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" install -r dist\CryptoWalletTracker-v1.1-release.apk
```

Une version de debug (`...-debug.apk`) est aussi produite pour les tests.

## Limites (liées aux chaînes et aux API publiques, pas à l'app)

- **Cours seulement** (aucune API gratuite sans clé pour un solde) : Sui, Polkadot, Bitcoin Cash.
  Elles restent consultables dans les marchés si cochées dans les réglages, mais ne peuvent pas être
  ajoutées comme portefeuille.
- **Monero** : l'adresse seule ne suffit pas ; il faut la **clé d'affichage privée** et enregistrer le
  portefeuille sur <https://lightwallet.monero.fail/register>. Aucun historique disponible.
- **Decred** : le montant des envois n'est pas toujours calculable ; l'app affiche alors « — ».
- Les soldes viennent de services publics gratuits : indisponibles hors ligne, soumis à leurs limites
  de débit. Les cours Binance peuvent être bloqués selon le pays (repli automatique : prix CoinGecko
  conservés).
- **Cours et graphiques** : CoinGecko limite l'historique public à **365 jours** (erreur 401 au-delà).
  Les périodes 2 ans, 5 ans et max utilisent donc Binance (ou Kraken pour Monero/Decred) ; la source
  réellement utilisée est indiquée sous le graphique.
- Les données sont conservées jusqu'à une synchro réussie : cours, taux, soldes et historique sont
  revalidés toutes les 24 h, les graphiques courts toutes les heures, les longs toutes les 24 h.
  Une synchro ratée garde l'ancienne valeur et affiche une alerte (donnée + source).

## Compilation

Prérequis : JDK 17+ (le JDK 25 d'Android Studio convient), Android SDK platform 37 et
build-tools 36.0.0 (Gradle 9.9.1, AGP 9.5.1).

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat exportReleaseApk
```

L'APK nommé est copié dans `dist/CryptoWalletTracker-v1.1-release.apk` (signé, optimisé par R8).
`exportDebugApk` produit la variante de debug, `assembleDebug` seul l'APK brut.
Les SDK 34 et build-tools 34.0.0 restent installés pour les autres projets.

## Version

`1.1` — voir `app/src/main/assets/CHANGELOG.md`.
